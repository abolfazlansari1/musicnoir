#!/usr/bin/env python3
"""tap.py "<text or content-desc>" [index]  -> taps the centre of the first UI node with that text/desc."""
import subprocess, sys, re, time
import xml.etree.ElementTree as ET
want = sys.argv[1]; idx = int(sys.argv[2]) if len(sys.argv) > 2 else 0
dx = int(sys.argv[3]) if len(sys.argv) > 3 else 0; dy = int(sys.argv[4]) if len(sys.argv) > 4 else 0
long = len(sys.argv) > 5
for attempt in range(6):
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    xml = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout.decode("utf8", "ignore")
    try: root = ET.fromstring(xml)
    except Exception: time.sleep(1); continue
    hits = []
    for n in root.iter("node"):
        if want in (n.get("text"), n.get("content-desc")):
            m = re.findall(r"\d+", n.get("bounds"))
            x1, y1, x2, y2 = map(int, m)
            hits.append(((x1 + x2) // 2, (y1 + y2) // 2))
    if len(hits) > idx:
        x, y = hits[idx]
        x += dx; y += dy
        if long: subprocess.run(["adb", "shell", "input", "swipe", str(x), str(y), str(x), str(y), "900"])
        else: subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])
        print(f"tapped {want!r} at {x},{y}")
        sys.exit(0)
    time.sleep(1)
print(f"NOT FOUND: {want!r}")
sys.exit(0)
