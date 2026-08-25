#!/usr/bin/env python3
"""校验安装包 Compose 与仓库模板的关键服务运行定义是否一致。

输入为同一 .env 渲染得到的两个 `docker compose config --format json` 文件。
仅比较 media-service、control-service、bigscreen-bff 的 environment 与 volumes；
差异只输出服务名和字段名，不输出环境变量值，避免泄露凭据。
"""

import json
import sys
from typing import Dict, List


SERVICES = ("media-service", "control-service", "bigscreen-bff")


def normalized_volumes(service: dict) -> List[Dict[str, object]]:
    volumes = []
    for volume in service.get("volumes", []):
        if isinstance(volume, str):
            volumes.append({"raw": volume})
        else:
            volumes.append({
                "type": volume.get("type"),
                "source": volume.get("source"),
                "target": volume.get("target"),
                "read_only": volume.get("read_only", False),
            })
    return sorted(volumes, key=lambda item: json.dumps(item, sort_keys=True))


def main() -> int:
    if len(sys.argv) != 3:
        print("用法: verify-compose-drift.py <仓库模板渲染结果> <安装包渲染结果>", file=sys.stderr)
        return 2

    with open(sys.argv[1], encoding="utf-8") as handle:
        expected = json.load(handle).get("services", {})
    with open(sys.argv[2], encoding="utf-8") as handle:
        actual = json.load(handle).get("services", {})

    differences = []
    for name in SERVICES:
        if name not in expected or name not in actual:
            differences.append(f"{name}: 服务缺失")
            continue
        expected_service = expected[name]
        actual_service = actual[name]
        expected_environment = expected_service.get("environment", {})
        actual_environment = actual_service.get("environment", {})
        if expected_environment != actual_environment:
            changed = sorted(
                key for key in set(expected_environment) | set(actual_environment)
                if expected_environment.get(key) != actual_environment.get(key)
            )
            differences.append(f"{name}: environment 键不一致 ({', '.join(changed)})")
        if normalized_volumes(expected_service) != normalized_volumes(actual_service):
            differences.append(f"{name}: volumes 不一致")

    if differences:
        print("安装包 Compose 与仓库模板存在漂移：", file=sys.stderr)
        for item in differences:
            print(f"- {item}", file=sys.stderr)
        return 1
    print("Compose 关键服务 environment 与 volumes 校验通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
