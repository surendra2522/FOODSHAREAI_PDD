#!/usr/bin/env python3
import urllib.request
import json

endpoints = [
    "https://data.mendeley.com/api/datasets-v2/datasets/p24v38x77r/1",
    "https://data.mendeley.com/api/datasets-v2/datasets/p24v38x77r",
    "https://data.mendeley.com/public-api/datasets/p24v38x77r/1",
    "https://data.mendeley.com/api-indexed/datasets/p24v38x77r/1",
    "https://data.mendeley.com/public-api/datasets/s5y98b7v8g/1",
    "https://data.mendeley.com/api/datasets-v2/datasets/s5y98b7v8g/1",
]

for ep in endpoints:
    try:
        req = urllib.request.Request(ep, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"[OK] {ep} -> Status {resp.status}, Content-Type: {resp.headers.get('Content-Type')}")
            data = resp.read().decode('utf-8')
            print("     Data sample:", data[:300])
    except Exception as e:
        print(f"[FAIL] {ep} -> Error: {e}")
