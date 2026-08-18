#!/usr/bin/env python3
import urllib.request
import re
import json

def parse_mendeley(url_name, url):
    print(f"\n--- Parsing {url_name} ({url}) ---")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        html = resp.read().decode("utf-8", errors="ignore")
        
        # Look for JSON state in script tags (e.g. __INITIAL_STATE__ or json-ld or data blobs)
        json_blobs = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
        found_links = []
        for blob in json_blobs:
            if "download" in blob or "file" in blob or "http" in blob:
                urls = re.findall(r'https?://[^\s"\'<>]+', blob)
                for u in urls:
                    if "download" in u or "content" in u or "files" in u:
                        found_links.append(u)
        
        print(f"Found {len(found_links)} candidate file/download URLs:")
        for l in list(set(found_links))[:10]:
            print("  ", l)

if __name__ == "__main__":
    parse_mendeley("Rice", "https://data.mendeley.com/datasets/p24v38x77r/1")
    parse_mendeley("Parotta", "https://data.mendeley.com/datasets/s5y98b7v8g/1")
