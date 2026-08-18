#!/usr/bin/env python3
import urllib.request
import json
import re

def test_sources():
    sources = {
        "Kaggle Fresh & Spoiled Food": "https://www.kaggle.com/api/v1/datasets/download/maheen00shahid/fresh-and-spoiled-food-image-dataset",
        "Kaggle Meat Quality": "https://www.kaggle.com/api/v1/datasets/download/utaytug/meat-quality-assessment-dataset",
        "Kaggle REWIVE Food Fresh": "https://www.kaggle.com/api/v1/datasets/download/zhenqili/food-fresh-detection",
        "Mendeley Rice Page": "https://data.mendeley.com/datasets/p24v38x77r/1",
        "Mendeley Parotta Page": "https://data.mendeley.com/datasets/s5y98b7v8g/1",
    }

    print("--- Testing Dataset Sources HTTP Access ---")
    for name, url in sources.items():
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
            with urllib.request.urlopen(req, timeout=15) as resp:
                status = resp.status
                ct = resp.headers.get("Content-Type")
                cl = resp.headers.get("Content-Length")
                final_url = resp.geturl()
                print(f"[OK] {name}: Status {status}, Type: {ct}, Size: {cl} bytes")
                if "mendeley" in url:
                    content = resp.read().decode("utf-8", errors="ignore")
                    matches = re.findall(r'href=["\'](https://data\.mendeley\.com/api-indexed/files/[^"\']+)["\']', content)
                    print(f"     Mendeley Direct File Links: {matches[:3]}")
        except Exception as e:
            print(f"[FAIL] {name}: {str(e)}")

if __name__ == "__main__":
    test_sources()
