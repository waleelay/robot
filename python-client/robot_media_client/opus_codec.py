"""多合一设备裸 Opus 帧编解码封装。"""

from __future__ import annotations

import ctypes
import ctypes.util

OPUS_APPLICATION_AUDIO = 2049
OPUS_MAX_PACKET_BYTES = 1275


class OpusCodecError(RuntimeError):
    """libopus 初始化或编解码失败。"""


class OpusEncoder:
    """把 16 位单声道 PCM 编码为设备要求的裸 Opus 帧。"""

    def __init__(self, sample_rate: int, channels: int = 1) -> None:
        self.lib = load_libopus()
        error = ctypes.c_int()
        self.handle = self.lib.opus_encoder_create(
            sample_rate,
            channels,
            OPUS_APPLICATION_AUDIO,
            ctypes.byref(error),
        )
        self.channels = channels
        if not self.handle or error.value != 0:
            raise OpusCodecError(f"opus encoder create failed: {error.value}")

    def encode(self, pcm: bytes, frame_samples: int) -> bytes:
        """编码一帧 PCM；frame_samples 是每声道采样数。"""
        expected = frame_samples * self.channels * 2
        if len(pcm) != expected:
            raise ValueError(f"invalid PCM frame length: got {len(pcm)}, want {expected}")
        pcm_buffer = (ctypes.c_int16 * (frame_samples * self.channels)).from_buffer_copy(pcm)
        output = (ctypes.c_ubyte * OPUS_MAX_PACKET_BYTES)()
        size = self.lib.opus_encode(
            self.handle,
            pcm_buffer,
            frame_samples,
            output,
            OPUS_MAX_PACKET_BYTES,
        )
        if size < 0:
            raise OpusCodecError(f"opus encode failed: {size}")
        return bytes(output[:size])

    def close(self) -> None:
        """释放编码器。"""
        handle = getattr(self, "handle", None)
        if handle:
            self.lib.opus_encoder_destroy(handle)
            self.handle = None

    def __del__(self) -> None:
        self.close()


class OpusDecoder:
    """把设备返回的裸 Opus 帧解码为 16 位单声道 PCM。"""

    def __init__(self, sample_rate: int, channels: int = 1) -> None:
        self.lib = load_libopus()
        error = ctypes.c_int()
        self.handle = self.lib.opus_decoder_create(sample_rate, channels, ctypes.byref(error))
        self.channels = channels
        if not self.handle or error.value != 0:
            raise OpusCodecError(f"opus decoder create failed: {error.value}")

    def decode(self, packet: bytes, max_frame_samples: int) -> tuple[bytes, int]:
        """解码一帧，返回 PCM 和每声道实际采样数。"""
        if not packet:
            raise ValueError("empty Opus packet")
        packet_buffer = (ctypes.c_ubyte * len(packet)).from_buffer_copy(packet)
        pcm_buffer = (ctypes.c_int16 * (max_frame_samples * self.channels))()
        samples = self.lib.opus_decode(
            self.handle,
            packet_buffer,
            len(packet),
            pcm_buffer,
            max_frame_samples,
            0,
        )
        if samples < 0:
            raise OpusCodecError(f"opus decode failed: {samples}")
        size = samples * self.channels * 2
        return bytes(pcm_buffer)[:size], samples

    def close(self) -> None:
        """释放解码器。"""
        handle = getattr(self, "handle", None)
        if handle:
            self.lib.opus_decoder_destroy(handle)
            self.handle = None

    def __del__(self) -> None:
        self.close()


def load_libopus() -> ctypes.CDLL:
    """加载系统 libopus 并声明本模块使用的函数签名。"""
    library_name = ctypes.util.find_library("opus")
    if not library_name:
        raise OpusCodecError("libopus not found; install libopus before enabling multi-function audio")
    lib = ctypes.CDLL(library_name)
    lib.opus_encoder_create.argtypes = [
        ctypes.c_int32,
        ctypes.c_int,
        ctypes.c_int,
        ctypes.POINTER(ctypes.c_int),
    ]
    lib.opus_encoder_create.restype = ctypes.c_void_p
    lib.opus_encoder_destroy.argtypes = [ctypes.c_void_p]
    lib.opus_encoder_destroy.restype = None
    lib.opus_encode.argtypes = [
        ctypes.c_void_p,
        ctypes.POINTER(ctypes.c_int16),
        ctypes.c_int,
        ctypes.POINTER(ctypes.c_ubyte),
        ctypes.c_int32,
    ]
    lib.opus_encode.restype = ctypes.c_int32
    lib.opus_decoder_create.argtypes = [
        ctypes.c_int32,
        ctypes.c_int,
        ctypes.POINTER(ctypes.c_int),
    ]
    lib.opus_decoder_create.restype = ctypes.c_void_p
    lib.opus_decoder_destroy.argtypes = [ctypes.c_void_p]
    lib.opus_decoder_destroy.restype = None
    lib.opus_decode.argtypes = [
        ctypes.c_void_p,
        ctypes.POINTER(ctypes.c_ubyte),
        ctypes.c_int32,
        ctypes.POINTER(ctypes.c_int16),
        ctypes.c_int,
        ctypes.c_int,
    ]
    lib.opus_decode.restype = ctypes.c_int
    return lib
