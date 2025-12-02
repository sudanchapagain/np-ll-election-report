#!/usr/bin/env python3

import json
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RAW = ROOT / "raw"
OUT_DIR = ROOT.parent.parent.parent / "generated-data"
OUT_CSV = OUT_DIR / "location.csv"


def load_json_list(path):
    try:
        with path.open("r", encoding="utf-8") as fh:
            data = json.load(fh)

            if isinstance(data, list):
                return data

            if isinstance(data, dict):
                return [data]

            print(f"warning: unexpected JSON type in {path}: {type(data)}")

            return []
    except Exception as e:
        print(f"warning: failed to load {path}: {e}")
        return []


provinces = {}
prov_dir = RAW / "province"

if prov_dir.exists():
    for p in sorted(prov_dir.glob("*.json")):
        for item in load_json_list(p):
            pid = str(item.get("value") or p.stem)

            provinces[pid] = {
                "id": pid,
                "label_en": item.get("label_en") or item.get("label"),
                "districts": [],
            }

districts_index = {}
dist_dir = RAW / "district"
if dist_dir.exists():
    for p in sorted(dist_dir.glob("*.json")):
        parent_pid = str(p.stem)
        items = load_json_list(p)

        for d in items:
            did = str(d.get("id") or d.get("value") or "")

            district_obj = {
                "id": did,
                "label_en": d.get("name_en")
                or d.get("district_en")
                or d.get("label_en"),
                "municipalities": [],
            }

            districts_index[did] = district_obj

            if parent_pid in provinces:
                provinces[parent_pid]["districts"].append(district_obj)

municipalities_index = {}
mun_dir = RAW / "municipality"

if mun_dir.exists():
    for p in sorted(mun_dir.glob("*.json")):
        parent_did = str(p.stem)
        items = load_json_list(p)

        for m in items:
            mid = str(m.get("id") or m.get("value") or "")

            mun_obj = {
                "id": mid,
                "label_en": m.get("name_en") or m.get("palika_en") or m.get("label_en"),
                "wards": [],
            }

            municipalities_index[mid] = mun_obj
            parent = districts_index.get(parent_did)

            if parent:
                parent["municipalities"].append(mun_obj)

ward_dir = RAW / "ward"
if ward_dir.exists():
    for p in sorted(ward_dir.glob("*.json")):
        parent_mid = str(p.stem)
        items = load_json_list(p)

        for w in items:
            wid = str(w.get("id") or w.get("value") or "")
            ward_obj = {"id": wid, "label_en": w.get("name")}
            parent = municipalities_index.get(parent_mid)

            if parent:
                parent["wards"].append(ward_obj)

OUT_DIR.mkdir(parents=True, exist_ok=True)
with OUT_CSV.open("w", newline="", encoding="utf-8") as fh:
    writer = csv.writer(fh)

    writer.writerow(
        [
            "province_id",
            "province_label_en",
            "district_id",
            "district_label_en",
            "municipality_id",
            "municipality_label_en",
            "ward_id",
            "ward_label_en",
        ]
    )

    for prov in provinces.values():
        for dist in prov["districts"]:
            for mun in dist["municipalities"]:
                if mun["wards"]:
                    for ward in mun["wards"]:
                        writer.writerow(
                            [
                                prov["id"],
                                prov["label_en"],
                                dist["id"],
                                dist["label_en"],
                                mun["id"],
                                mun["label_en"],
                                ward["id"],
                                ward["label_en"],
                            ]
                        )
                else:
                    writer.writerow(
                        [
                            prov["id"],
                            prov["label_en"],
                            dist["id"],
                            dist["label_en"],
                            mun["id"],
                            mun["label_en"],
                            "",
                            "",
                        ]
                    )
