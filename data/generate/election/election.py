#!/usr/bin/env python3

# this has made me pull my hair out

import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RAW = ROOT / "raw"
OUT_DIR = ROOT.parent.parent.parent / "generated-data"
OUT_CSV = OUT_DIR / "election.csv"
LOCATION = OUT_DIR / "location.csv"

TRANSLATIONS = {
    "78": "76",
}

PARTY_NORMALIZATION = {
    "nepali congress": "Nepali Congress",
    "nepal communist party (uml)": "Nepal Communist Party (UML)",
    "nepal communist party (maoist-centre)": "Nepal Communist Party (Maoist Centre)",
    "janata samajwadi party, nepal": "Janata Samajbadi Party, Nepal",
}


def normalize_row(row: dict) -> dict:
    return {
        k: (v.strip() if isinstance(v, str) and v.strip() else None)
        for k, v in row.items()
    }


def as_int(val):
    try:
        return int(float(val))
    except (TypeError, ValueError):
        return None


def as_float(val):
    try:
        return float(val)
    except (TypeError, ValueError):
        return None


def normalize_csv_district_id(raw):
    if raw is None:
        return None
    s = str(raw)
    if s.endswith(".0"):
        s = s[:-2]
    return TRANSLATIONS.get(s, s)


def choose_name(row):
    name_en = row.get("CandidateNameEng") or row.get("candidatenameeng")
    name_np = row.get("CandidateName") or row.get("candidatename")
    chosen_en = name_en or name_np
    chosen_np = name_np or name_en
    return chosen_en, chosen_np


def choose_party(row):
    p_np = row.get("PoliticalPartyName") or row.get("politicalpartyname")
    p_en = row.get("PoliticalPartyNameEng") or row.get("politicalpartynameeng")
    chosen = p_en or p_np or None

    if chosen:
        low = chosen.strip().lower()
        chosen = PARTY_NORMALIZATION.get(low, chosen.strip())

    return chosen


def load_location_index():
    loc_index = {}

    with LOCATION.open(encoding="utf-8") as f:
        reader = csv.DictReader(f)

        for row in reader:
            mid = row["municipality_id"]

            loc_index[mid] = {
                "province_id": row["province_id"],
                "province_label_en": row["province_label_en"],
                "district_id": row["district_id"],
                "district_name": row["district_label_en"],
                "municipality": {
                    "id": row["municipality_id"],
                    "name_en": row["municipality_label_en"],
                },
            }

    return loc_index


loc_index = load_location_index()
OUT_DIR.mkdir(parents=True, exist_ok=True)

csv_paths = sorted(RAW.glob("*.csv"))

with OUT_CSV.open("w", newline="", encoding="utf-8") as fh:
    writer = csv.writer(fh)
    writer.writerow(
        [
            "province_id",
            "province_label_en",
            "district_id",
            "district_id_csv",
            "municipality_id",
            "municipality_name_en",
            "candidate_id",
            "candidate_name_en",
            "candidate_name_np",
            "gender",
            "age",
            "party",
            "votes",
            "remarks",
            "ward",
        ]
    )

    for path in csv_paths:
        mid = path.stem

        with path.open("r", encoding="utf-8") as fh_r:
            reader = csv.DictReader(fh_r)
            rows = [normalize_row(r) for r in reader]

        if not rows:
            continue

        mun_meta = loc_index.get(
            mid, {"municipality": {"id": mid, "name_en": None, "name": None}}
        )

        csv_did = normalize_csv_district_id(rows[0].get("districtId"))
        district_id_meta = mun_meta.get("district_id")

        for r in rows:
            name_en, name_np = choose_name(r)
            party = choose_party(r)
            gender = r.get("Gender")
            age = as_int(r.get("Age"))

            votes = as_float(r.get("TotalVoteReceived"))
            remarks = r.get("Remarks")
            ward = r.get("Ward") or r.get("ward")
            candidate_id = r.get("CandidateID") or r.get("candidateid")

            writer.writerow(
                [
                    mun_meta.get("province_id"),
                    mun_meta.get("province_label_en"),
                    district_id_meta,
                    csv_did,
                    mid,
                    mun_meta.get("municipality", {}).get("name_en"),
                    candidate_id,
                    name_en,
                    name_np,
                    gender,
                    age,
                    party,
                    votes,
                    remarks,
                    ward,
                ]
            )
