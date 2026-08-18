#!/usr/bin/env python3
import urllib.request
import re

req = urllib.request.Request("https://data.mendeley.com/datasets/p24v38x77r/1", headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
with urllib.request.urlopen(req, timeout=15) as resp:
    html = resp.read().decode("utf-8", errors="ignore")
    print("Length:", len(html))
    print("Sample HTML:", html[:1000])
    
    # Check for script tags or initial state
    scripts = re.findall(r'<script.*?>(.*?)</script>', html, re.DOTALL)
    print("Script count:", len(scripts))
    for i, s in enumerate(scripts):
        if len(s) > 50:
            print(f"Script {i} (len {len(s)}):", s[:200])
