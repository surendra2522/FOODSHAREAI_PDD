#!/usr/bin/env python3
import urllib.request
import re

req = urllib.request.Request("https://data.mendeley.com/datasets/p24v38x77r/1", headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
with urllib.request.urlopen(req, timeout=15) as resp:
    html = resp.read().decode("utf-8", errors="ignore")
    idx = html.find("window.INITIAL_STATE")
    if idx != -1:
        print("Found window.INITIAL_STATE at index:", idx)
        print("Substr:", html[idx:idx+800])
    
    # Search for files / download in whole html
    urls = re.findall(r'https?://[^\s"\'<>]+', html)
    api_urls = [u for u in urls if "api" in u or "file" in u or "download" in u or "zip" in u]
    print("Found API/Download URLs:", api_urls[:10])
