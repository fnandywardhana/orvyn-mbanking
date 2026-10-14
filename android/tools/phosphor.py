#!/usr/bin/env python3
"""Import Phosphor icons (MIT) as Android vector drawables: tools/phosphor.py house qr-code-fill ...
Only icons we use are imported (design-system/MASTER.md §3)."""
import re, sys, urllib.request
from pathlib import Path

out = Path(__file__).resolve().parent.parent / 'app/src/main/res/drawable'
for name in sys.argv[1:]:
    weight = 'fill' if name.endswith('-fill') else 'regular'
    svg = urllib.request.urlopen(f'https://raw.githubusercontent.com/phosphor-icons/core/main/assets/{weight}/{name}.svg', timeout=30).read().decode()
    if re.search(r'<(circle|rect|line|polyline|polygon|ellipse)\b', svg):
        sys.exit(f'{name}: has non-path shapes, convert by hand')
    paths = re.findall(r'<path[^>]*\sd="([^"]+)"', svg)
    body = '\n'.join(f'    <path android:fillColor="#FF000000" android:pathData="{d}" />' for d in paths)
    (out / f"ph_{name.replace('-', '_')}.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Phosphor Icons (MIT), https://phosphoricons.com -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp" android:height="24dp" android:viewportWidth="256" android:viewportHeight="256">\n'
        f'{body}\n</vector>\n')
    print('ok', name)
