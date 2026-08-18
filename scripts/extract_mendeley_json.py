#!/usr/bin/env python3
import urllib.request
import re

req = urllib.request.Request("https://data.mendeley.com/datasets/p24v38x77r/1", headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
with urllib.request.urlopen(req, timeout=15) as resp:
    html = resp.read().decode("utf-8", errors="ignore")
    for m in re.finditer(r'window\.INITIAL_STATE\s*=\s*(.*?);</script>', html, re.DOTALL):
        snippet = m.group(1)
        print("Snippet len:", len(snippet))
        print("Snippet start:", snippet[:500])
        # Find file download URLs
        files = re.findall(r'https?://[^\s"\'\\]+', snippet)
        print("URLs in snippet:", [f for f in files if "file" in f or "download" in f or "data" in f][:5])
