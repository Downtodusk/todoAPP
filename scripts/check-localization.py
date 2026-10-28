#!/usr/bin/env python3
"""Check bilingual resource coverage and printf arguments without an Android device."""
import re
from pathlib import Path
from xml.etree import ElementTree

root = Path(__file__).resolve().parents[1] / "app/src/main/res"

def read(folder):
    elements = ElementTree.parse(root / folder / "strings.xml").getroot().findall("string")
    values = {element.attrib["name"]: element.text or "" for element in elements}
    assert len(values) == len(elements), f"Duplicate string names in {folder}"
    return values

def placeholders(value):
    return sorted(re.findall(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])", value))

en, zh = read("values"), read("values-zh")
assert en.keys() == zh.keys(), f"Missing translations: {en.keys() ^ zh.keys()}"
for name in en:
    assert placeholders(en[name]) == placeholders(zh[name]), f"Format arguments differ: {name}"
print(f"Validated {len(en)} bilingual strings and their format arguments.")
