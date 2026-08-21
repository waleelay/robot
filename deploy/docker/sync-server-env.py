#!/usr/bin/env python3
"""服务器端环境变量同步助手（由 update-services.sh 调用）。

用法：
    echo "SERVICE VAR VALUE" ... | python3 sync-server-env.py <env_path> <compose_path>

行为（全部幂等）：
1. 修改前备份 .env 与 docker-compose.yml（追加 .bak-<时间戳>）。
2. .env：变量已存在则改值，不存在则追加到文件末尾。
3. docker-compose.yml：把变量接线到对应服务（"  <service>:" 块）的
   "    environment:" 段之后，格式 "      VAR: ${VAR:-VALUE}"；已接线则跳过。
"""

import re
import shutil
import sys
import time


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: sync-server-env.py <env_path> <compose_path>", file=sys.stderr)
        return 2
    env_path, compose_path = sys.argv[1], sys.argv[2]

    entries: list[tuple[str, str, str]] = []
    for line in sys.stdin.read().splitlines():
        parts = line.split(None, 2)
        if len(parts) == 3:
            entries.append((parts[0], parts[1], parts[2]))
    if not entries:
        print("no env entries")
        return 1

    ts = time.strftime("%Y%m%d%H%M%S")
    shutil.copy2(env_path, env_path + ".bak-" + ts)
    shutil.copy2(compose_path, compose_path + ".bak-" + ts)
    print("backed up: .bak-" + ts)

    # ---- .env ----
    with open(env_path, encoding="utf-8") as fh:
        lines = fh.read().splitlines()
    seen: set[str] = set()
    for line in lines:
        m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*)(?:=|$)", line)
        if m:
            seen.add(m.group(1))
    for _svc, var, val in entries:
        if var in seen:
            for i, line in enumerate(lines):
                if re.match(r"^%s=" % re.escape(var), line):
                    lines[i] = "%s=%s" % (var, val)
                    break
        else:
            lines.append("%s=%s" % (var, val))
            seen.add(var)
    with open(env_path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print("env updated:", len(entries), "vars")

    # ---- docker-compose.yml ----
    text = open(compose_path, encoding="utf-8").read()
    for svc, var, val in entries:
        marker = "  %s:" % svc
        start = text.find(marker)
        if start < 0:
            print("skip, service block not found:", svc)
            continue
        # 服务块结束位置：下一个同为两级缩进且以非空字符开头的行（排除四空格子项）
        next_svc = re.search(r"\n  (?=[^\s])", text[start + len(marker):])
        block_end = start + len(marker) + (next_svc.start() if next_svc else len(text) - start - len(marker))
        block = text[start:block_end]
        if ("\n      %s:" % var) in block:
            print("skip, already wired:", svc, var)
            continue
        pos = block.find("\n    environment:")
        if pos < 0:
            print("skip, no environment block:", svc)
            continue
        insert_at = start + pos + len("\n    environment:") + 1
        line = "      %s: ${%s:-%s}\n" % (var, var, val)
        text = text[:insert_at] + line + text[insert_at:]
        print("wired:", svc, var)
    with open(compose_path, "w", encoding="utf-8") as fh:
        fh.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
