from __future__ import annotations

from datetime import datetime, timezone


def now_utc() -> datetime:
    """返回带 UTC 时区的当前时间。"""
    return datetime.now(timezone.utc)


def isoformat(value: datetime | None = None) -> str:
    """把时间格式化为后端和 MQTT 使用的 ISO-8601 UTC 字符串。"""
    return (value or now_utc()).isoformat().replace("+00:00", "Z")


def parse_time(value: object) -> datetime | None:
    """解析 ISO-8601 时间字符串；非法或空值返回 None。"""
    if not isinstance(value, str) or not value:
        return None
    try:
        text = value.replace("Z", "+00:00")
        return datetime.fromisoformat(text)
    except ValueError:
        return None
