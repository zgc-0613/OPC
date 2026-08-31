import json
import urllib.request

url = "https://findopc.online/api/public/policies?page=1&pageSize=500"
with urllib.request.urlopen(url, timeout=30) as response:
    payload = json.load(response)
records = payload.get("data", [])
with open("outputs/policies-public-20260826.json", "w", encoding="utf-8") as handle:
    json.dump(records, handle, ensure_ascii=False, indent=2)
print(f"records={len(records)}")
for record in records:
    print(f"{record.get('id')}|{record.get('regionName')}|{record.get('title')}|{record.get('tags','')}")
