import os
import re
import threading
import uuid
import logging
from pathlib import Path
from typing import Optional

import sherpa_onnx
import numpy as np
import soundfile as sf
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field


MODEL_DIR = Path(os.getenv("TTS_MODEL_DIR", "/opt/tts/vits-melo-tts-zh_en")).resolve()
OUTPUT_ROOT = Path(os.getenv("TTS_OUTPUT_ROOT", "/data/tts")).resolve()
MAX_TEXT_LENGTH = int(os.getenv("TTS_MAX_TEXT_LENGTH", "1000"))

MODEL_PATH = MODEL_DIR / "model.onnx"
LEXICON_PATH = MODEL_DIR / "lexicon.txt"
TOKENS_PATH = MODEL_DIR / "tokens.txt"
DICT_DIR = MODEL_DIR / "dict"
RULE_FST_PATHS = (MODEL_DIR / "date.fst", MODEL_DIR / "number.fst")
RULE_FSTS = ",".join(str(path) for path in RULE_FST_PATHS if path.exists())

FILENAME_RE = re.compile(r"^[A-Za-z0-9._-]+$")
TTS_LOCK = threading.Lock()


class TTSRequest(BaseModel):
    text: str = Field(..., min_length=1)
    outputDir: str = Field(default="")
    filename: Optional[str] = None
    speed: float = Field(default=1.0, gt=0.2, le=3.0)
    speakerId: int = Field(default=0, ge=0, le=999)
    volume: float = Field(default=1.0, gt=0.0, le=2.0)


class TTSResponse(BaseModel):
    filePath: str
    filename: str
    outputDir: str


app = FastAPI(title="sherpa-onnx TTS service")
logger = logging.getLogger("tts-service")


def load_tts() -> sherpa_onnx.OfflineTts:
    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(MODEL_PATH),
                lexicon=str(LEXICON_PATH),
                tokens=str(TOKENS_PATH),
                dict_dir=str(DICT_DIR),
            ),
            provider="cpu",
            debug=False,
            num_threads=1,
        ),
        rule_fsts=RULE_FSTS,
        max_num_sentences=1,
    )
    if not config.validate():
        raise RuntimeError("invalid sherpa-onnx TTS config")
    return sherpa_onnx.OfflineTts(config)


TTS = load_tts()


def resolve_output_dir(output_dir: str) -> Path:
    clean = output_dir.strip()
    if clean.startswith("/"):
        target = Path(clean).resolve()
    else:
        target = (OUTPUT_ROOT / clean).resolve()

    if target != OUTPUT_ROOT and OUTPUT_ROOT not in target.parents:
        raise HTTPException(status_code=400, detail="outputDir must be under TTS_OUTPUT_ROOT")
    return target


def safe_filename(filename: Optional[str]) -> str:
    if not filename:
        return f"{uuid.uuid4().hex}.wav"
    name = filename.strip()
    if not name.endswith(".wav"):
        name = f"{name}.wav"
    if "/" in name or "\\" in name or not FILENAME_RE.match(name):
        raise HTTPException(status_code=400, detail="filename only allows letters, numbers, dot, underscore and hyphen")
    return name


@app.get("/health")
def health() -> dict:
    missing = [
        str(path)
        for path in (MODEL_PATH, LEXICON_PATH, TOKENS_PATH, DICT_DIR)
        if not path.exists()
    ]
    if missing:
        raise HTTPException(status_code=503, detail={"missing": missing})
    return {"status": "ok", "modelDir": str(MODEL_DIR), "ruleFsts": RULE_FSTS}


@app.post("/api/tts", response_model=TTSResponse)
def create_tts(req: TTSRequest) -> TTSResponse:
    text = req.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="text is required")
    if len(text) > MAX_TEXT_LENGTH:
        raise HTTPException(status_code=400, detail=f"text length must be <= {MAX_TEXT_LENGTH}")

    output_dir = resolve_output_dir(req.outputDir)
    filename = safe_filename(req.filename)
    output_dir.mkdir(parents=True, exist_ok=True)

    output_file = output_dir / filename
    tmp_file = output_dir / f".{filename}.{uuid.uuid4().hex}.tmp.wav"

    try:
        with TTS_LOCK:
            audio = TTS.generate(text, sid=req.speakerId, speed=req.speed)
        if len(audio.samples) == 0:
            raise RuntimeError("empty audio generated")

        samples = np.asarray(audio.samples, dtype=np.float32)
        if req.volume != 1.0:
            samples = np.clip(samples * req.volume, -1.0, 1.0)

        sf.write(str(tmp_file), samples, samplerate=audio.sample_rate, subtype="PCM_16")
        tmp_file.replace(output_file)
    except RuntimeError as exc:
        tmp_file.unlink(missing_ok=True)
        logger.exception("tts generation failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    except Exception as exc:
        tmp_file.unlink(missing_ok=True)
        logger.exception("tts generation failed")
        raise HTTPException(status_code=500, detail=str(exc) or "tts generation failed") from exc

    return TTSResponse(
        filePath=str(output_file),
        filename=filename,
        outputDir=str(output_dir),
    )
