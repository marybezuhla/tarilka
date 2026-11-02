#!/usr/bin/env python3
"""Writes app/src/debug/assets/demo.json: an invented three-week diary for store screenshots.

Only in the debug build. Dates are tokens (@D3@ = 3 days ago, "@T3 08:15@" = that moment),
filled in by DemoActivity on the phone, so the diary always ends today. Nothing here is real data.
"""
import csv, json, random
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
rows = [r for r in csv.reader((l for l in open(ROOT / "app/src/main/assets/foods.csv", encoding="utf-8")
                                if l.strip() and not l.startswith("#")), delimiter=";")]
head = rows[0]; F = {r[0]: dict(zip(head, r)) for r in rows[1:]}

def item(code, grams, meal, day, time):
    if not code.startswith("dr"):
        grams = round(grams * 1.12 / 5) * 5  # a little more food: close to the target, as most days are
    f = F[code]; k = grams / 100
    n = lambda c: round(float(f[c] or 0) * k, 1)
    return {"day": f"@D{day}@", "meal": meal, "nameUk": f["name_uk"], "nameEn": f["name_en"], "grams": grams,
            "kcal": n("kcal"), "protein": n("protein"), "fat": n("fat"), "carbs": n("carbs"), "fiber": n("fiber"),
            "fromAi": False, "createdAt": f"@T{day} {time}@", "foodCode": code}

random.seed(7)
BREAKFAST = [[("g06", 300), ("f03", 120), ("d08", 150), ("dr01", 200)], [("e04", 150), ("b02", 60), ("v15", 150), ("u65", 260)],
             [("u14", 200), ("d07", 150), ("f01", 180), ("dr02", 250)], [("d12", 200), ("f01", 180), ("b02", 40), ("dr01", 200)]]
LUNCH = [[("u01", 400), ("b02", 60), ("u24", 200)], [("g01", 250), ("u17", 150), ("v14", 150), ("v15", 150)],
         [("u32", 400), ("e01", 50), ("b24", 60), ("u54", 150)], [("m24", 180), ("g01", 220), ("u54", 200)]]
DINNER = [[("u08", 300), ("u25", 200)], [("u70", 180), ("g01", 200), ("v15", 150), ("v14", 120)],
          [("u02", 400), ("b02", 60), ("d12", 150)], [("m06", 150), ("u24", 200), ("b02", 40)]]
SNACK = [[("f01", 180), ("d08", 150)], [("f03", 120), ("d07", 150)], [("f01", 180), ("dr14", 250)], [("d54", 250), ("f03", 120)]]

diary, fluids, weights, sleep, workouts = [], [], [], [], []
for day in range(20, -1, -1):
    meals = [(0, random.choice(BREAKFAST), "08:10"), (1, random.choice(LUNCH), "13:20")]
    if day > 0:  # today: only breakfast and lunch so far
        meals += [(2, random.choice(DINNER), "19:00"), (3, random.choice(SNACK), "16:30")]
    for meal, foods, t in meals:
        diary += [item(c, g, meal, day, t) for c, g in foods]
    for t in (["09:30", "11:00", "15:00", "17:30", "21:00"] if day else ["09:30", "11:00"]):
        fluids.append({"day": f"@D{day}@", "ml": 250, "createdAt": f"@T{day} {t}@"})
    if day % 2 == 0:
        weights.append({"day": f"@D{day}@", "kg": round(66.9 - (20 - day) * 0.045 + random.uniform(-0.15, 0.15), 1), "minutes": 7 * 60 + 15})
    if True:
        sleep.append({"day": f"@D{day}@", "hours": random.choice([6.5, 7, 7.5, 8, 7, 7.5]), "quality": random.choice([1, 2, 2])})
    if day % 3 == 1:
        workouts.append({"day": f"@D{day}@", "type": random.choice([0, 3, 4]), "minutes": random.choice([30, 40, 45]),
                         "intensity": 1, "createdAt": f"@T{day} 18:00@"})

demo = {
    "format": "tarilka-backup", "version": 3, "exportedAt": "2026-01-01T00:00:00Z",
    "profile": {"sex": "FEMALE", "age": 34, "heightCm": 168, "weightKg": 66.0, "activity": 2, "goal": "MAINTAIN",
                "customKcal": None, "isSet": True, "pregnant": False},
    "customFoods": [], "favorites": ["u01", "g01", "f01"],
    "diary": diary, "fluids": fluids, "notes": [{"day": "@D1@", "text": "Прогулянка в парку / A walk in the park"}],
    "sleep": sleep, "bowel": [], "workouts": workouts, "periods": [], "periodDays": [],
    "settings": {"cycleTypical": -1, "stepLog": {}}, "waist": [{"day": "@D14@", "cm": 74.0}, {"day": "@D0@", "cm": 73.0}],
    "weights": weights,
}
out = ROOT / "app/src/debug/assets/demo.json"
out.write_text(json.dumps(demo, ensure_ascii=False, indent=1), encoding="utf-8")
days = {}
for e in diary: days[e["day"]] = days.get(e["day"], 0) + e["kcal"]
full = [v for k, v in days.items() if k != "@D0@"]
print(f"{len(diary)} entries written to {out.relative_to(ROOT)}; about {sum(full) / len(full):.0f} kcal a day")
