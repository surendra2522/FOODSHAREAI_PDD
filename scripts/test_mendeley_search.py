#!/usr/bin/env python3
import urllib.request
import json

urls = [
    "https://data.mendeley.com/api-indexed/datasets/search?query=boiled+rice",
    "https://data.mendeley.com/api-indexed/datasets/search?query=parotta",
    "https://data.mendeley.com/public-api/datasets?search=boiled+rice"
]

for u in urls:
    try:
        req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"[OK] {u} -> Status {resp.status}")
            data = resp.read().decode("utf-8")
            print("  Data:", data[:400])
    except Exception as e:
        print(f"[FAIL] {u} -> Error: {e}")
