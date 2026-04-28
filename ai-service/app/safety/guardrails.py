import re


THINK_TAG_PATTERN = re.compile(r"</?think>", re.IGNORECASE)


def strip_think_tags(text: str) -> str:
    return THINK_TAG_PATTERN.sub("", text or "").strip()
