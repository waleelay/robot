#!/usr/bin/env python3
"""执行 Java 规则及三个服务的 OpenAPI 检查；检查失败时返回非零退出码。"""

import argparse
from collections import Counter
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("media-common", "media-service", "control-service", "bigscreen-bff")
OUT = ROOT / "target/quality"
RULESET = ROOT / "quality/java/ruleset.xml"
DEPENDENCY_GOAL = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath"
NS = {"p": "http://pmd.sourceforge.net/report/2.0.0"}


def run(command, **kwargs):
    """只执行参数数组；保留失败退出码，不将失败转换成空结果。"""
    return subprocess.run(command, cwd=ROOT, check=True, **kwargs)


def maven(offline, pom, *arguments):
    return ["mvn", "-B", "-q", *(["-o"] if offline else []), "-f", str(pom), *arguments]


def tools_classpath(offline):
    pom = ROOT / "quality/java/pom.xml"
    cp = pom.parent / "target/classpath.txt"
    run(maven(offline, pom, "compile", DEPENDENCY_GOAL, "-Dmdep.outputFile=target/classpath.txt"))
    return os.pathsep.join((str(pom.parent / "target/classes"), cp.read_text().strip()))


def source_files(root):
    return sorted(path for module in MODULES for source in ("main", "test")
                  for path in (root / module / f"src/{source}/java").rglob("*.java"))


def base_module_names(roots):
    """将旧提交中的 backend 源码归到当前 media-service 路径，以便按同一模块比较。"""
    names = {}
    for module in MODULES:
        legacy = "backend" if module == "media-service" and module not in roots else module
        if legacy not in roots:
            raise RuntimeError(f"比较基线缺少模块：{module}")
        names[module] = legacy
    return names


def auxiliary_classpath(offline):
    """使用所有模块的实际测试 classpath，避免只分析源码却缺少业务类型。"""
    run(maven(offline, ROOT / "media-common/pom.xml", "install", "-DskipTests"))
    entries = []
    for module in MODULES:
        directory = ROOT / module
        cp = OUT / "java" / f"{module}-classpath.txt"
        run(maven(offline, directory / "pom.xml", "test-compile", DEPENDENCY_GOAL,
                  "-DincludeScope=test", f"-Dmdep.outputFile={cp}"))
        entries.extend((str(directory / "target/classes"), str(directory / "target/test-classes")))
        entries.extend(cp.read_text().strip().split(os.pathsep))
    output = OUT / "java/aux-classpath.txt"
    output.write_text(os.pathsep.join(dict.fromkeys(item for item in entries if item)))
    return output


def scan(root, files, classpath, report, auxiliary=None):
    """同时检查 CLI 状态、报告和 stderr，避免沿用旧报告或忽略引擎异常。"""
    if not files:
        raise RuntimeError("扫描文件为空，不能判定检查通过")
    report.parent.mkdir(parents=True, exist_ok=True)
    file_list = report.with_suffix(".files")
    file_list.write_text("\n".join(str(path) for path in files) + "\n")
    report.unlink(missing_ok=True)
    command = ["java", "-cp", classpath, "net.sourceforge.pmd.cli.PmdCli", "check",
               "--file-list", str(file_list), "--rulesets", str(RULESET), "--format", "xml",
               "--report-file", str(report), "--use-version", "java-17", "--no-cache"]
    if auxiliary:
        command.extend(("--aux-classpath", "file:" + str(auxiliary)))
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    report.with_suffix(".log").write_text(result.stdout + result.stderr)
    if result.returncode not in (0, 4) or not report.exists():
        raise RuntimeError(f"PMD 执行失败（{result.returncode}），见 {report.with_suffix('.log')}")
    document = ET.parse(report).getroot()
    errors = document.findall(".//p:error", NS) + document.findall(".//p:configerror", NS)
    if errors or re.search(r"\b(ERROR|SEVERE)\b|maximum Iterations exceeded", result.stderr):
        raise RuntimeError(f"PMD 存在分析异常，见 {report.with_suffix('.log')}")
    findings = []
    for file in document.findall("p:file", NS):
        path = Path(file.attrib["name"])
        lines = path.read_text().splitlines()
        for violation in file.findall("p:violation", NS):
            start, end = int(violation.attrib["beginline"]), int(violation.attrib["endline"])
            # 保留行内字符（包括字符串空白），仅忽略行号与行首缩进变化。
            snippet = "\n".join(line.strip() for line in lines[start - 1:end])
            digest = hashlib.sha256(snippet.encode()).hexdigest()
            findings.append({"file": str(path.relative_to(root)), "rule": violation.attrib["rule"],
                             "line": start, "fingerprint": digest, "message": (violation.text or "").strip()})
    if (result.returncode == 0) != (not findings):
        raise RuntimeError("PMD 退出码与报告不一致")
    return findings


def new_findings(current, previous):
    """按位置无关的内容指纹及数量比较，新增相同问题不能抵消已删除问题。"""
    def key(item):
        return item["file"], item["rule"], item["fingerprint"]
    baseline = Counter(key(item) for item in previous)
    added = []
    for item in current:
        identity = key(item)
        if baseline[identity]:
            baseline[identity] -= 1
        else:
            added.append(item)
    return added


def java_check(args):
    directory = OUT / "java"
    directory.mkdir(parents=True, exist_ok=True)
    classpath = tools_classpath(args.offline)
    run([sys.executable, "-m", "unittest", "discover", "-s", "quality/java/tests"])
    auxiliary = auxiliary_classpath(args.offline)
    current = scan(ROOT, source_files(ROOT), classpath, directory / "current.xml", auxiliary)
    findings = current
    if args.base:
        if args.base.startswith("-"):
            raise RuntimeError("无效的 Git 比较基线")
        revision = run(["git", "rev-parse", "--verify", args.base + "^{commit}"],
                       capture_output=True, text=True).stdout.strip()
        roots = set(run(["git", "ls-tree", "-d", "--name-only", revision],
                        capture_output=True, text=True).stdout.splitlines())
        base_modules = base_module_names(roots)
        archive = run(["git", "archive", revision, "--", *[f"{base_modules[m]}/src" for m in MODULES]],
                      capture_output=True).stdout
        with tempfile.TemporaryDirectory(prefix="base-", dir=directory) as temporary:
            base = Path(temporary)
            with tarfile.open(fileobj=io.BytesIO(archive)) as files:
                for member in files:
                    name = member.name
                    if not member.isfile() or not name.endswith(".java"):
                        continue
                    module = next((m for m in MODULES for s in ("main", "test")
                                   if name.startswith(f"{base_modules[m]}/src/{s}/java/")), None)
                    if module is None:
                        continue
                    destination = base / module / name.split("/", 1)[1]
                    if not destination.resolve().is_relative_to(base):
                        raise RuntimeError("Git 归档包含非法路径")
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(files.extractfile(member).read())
            previous = scan(base, source_files(base), classpath, directory / "base.xml", auxiliary)
            findings = new_findings(current, previous)
        print(f"比较基线 {revision}；存量候选 {len(current)} 条，新增 {len(findings)} 条")
    else:
        print(f"全量检查发现 {len(findings)} 条候选问题；未设置存量豁免")
    (directory / "findings.json").write_text(json.dumps(findings, ensure_ascii=False, indent=2) + "\n")
    for item in findings[:30]:
        print(f"{item['file']}:{item['line']} {item['rule']}: {item['message']}")
    return 1 if findings else 0


def openapi_check(args):
    run(maven(args.offline, ROOT / "media-common/pom.xml", "install", "-DskipTests"))
    run(maven(args.offline, ROOT / "media-service/pom.xml", "test", "-Dtest=File*Test,ServiceOpenApiContractTest,TtsAudioServiceTest"))
    run(maven(args.offline, ROOT / "control-service/pom.xml", "test",
              "-Dtest=MileageServiceTest,MileageOpenApiContractTest,MileageOpenApiDisabledTest,"
              "FileOpenApiContractTest,FileOpenApiDisabledTest,ServiceOpenApiContractTest,"
              "ControlMediaServiceClientFileListTest,ControlMediaServiceClientFileDeleteTest"))
    run(["npm", "ci", "--prefix", "quality/openapi", "--ignore-scripts", "--no-audit", "--no-fund",
         *(["--offline"] if args.offline else [])])
    run(["npm", "run", "check", "--prefix", "quality/openapi"])
    run(maven(args.offline, ROOT / "bigscreen-bff/pom.xml", "test",
              "-Dtest=OpenApiContractTest,OpenApiDisabledTest,PanoramaCenterClientTest,StatisticsServiceTest,PanoramaServiceTest,SecurityConfigWebTest,CenterProxyClientTest,BusinessTaskProxyControllerTest"))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("check", choices=("java", "openapi", "all"))
    parser.add_argument("--base", help="Java 增量比较的明确 Git 基线；CI 应传入合并目标，不能默认 HEAD")
    parser.add_argument("--offline", action="store_true", help="仅使用已缓存的 Maven/npm 依赖，缓存缺失时失败")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    try:
        if args.check in ("java", "all") and java_check(args):
            return 1
        if args.check in ("openapi", "all"):
            return openapi_check(args)
        return 0
    except (subprocess.CalledProcessError, OSError, RuntimeError, ET.ParseError) as error:
        print(f"检查失败：{error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
