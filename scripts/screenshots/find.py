#!/usr/bin/env python3
"""Prints the centre "x y" of the first on-screen element whose text or description is exactly the given label
(from a `uiautomator dump`). Exit 1 if there is none."""
import re, sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
label = sys.argv[2]
for n in root.iter("node"):
    if label in (n.get("text"), n.get("content-desc")):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
