#!/usr/bin/env python3
"""Sanity-checks app/src/main/assets/foods.csv.

- every row has the right number of columns and valid numbers
- codes are unique, names are present in both languages
- calories agree with macronutrients (Atwater: 4 kcal/g protein & carbs,
  9 kcal/g fat, 7 kcal/g alcohol) within a tolerance

Exit code 1 if any problem is found.
"""
import csv
import re
import sys
from pathlib import Path

CSV = Path(__file__).resolve().parent.parent / "app/src/main/assets/foods.csv"
COLUMNS = ["code", "category", "name_uk", "name_en", "kcal", "protein", "fat", "carbs",
           "fiber", "alcohol", "portion_g", "portion_uk", "portion_en", "source", "ref"]
CATEGORIES = {"grains", "bread", "veg", "fruit", "meat", "fish", "dairy", "eggs", "legumes",
              "nuts", "fats", "sweets", "drinks", "dishes", "fastfood", "sauces"}
SOURCES = {"USDA", "LABEL", "RECIPE"}
# Kvass, okroshka on kvass and non-alcoholic beer are not counted as alcohol in the app on purpose.
ALCOHOL_EXEMPT = {"dr10", "dr32", "u07"}


def rows():
    with CSV.open(encoding="utf-8") as f:
        lines = [l for l in f if l.strip() and not l.startswith("#")]
    reader = csv.reader(lines, delimiter=";")
    header = next(reader)
    if header != COLUMNS:
        raise SystemExit(f"Bad header: {header}")
    return list(reader)


def main() -> int:
    problems = []
    seen = set()
    data = rows()
    for i, r in enumerate(data, start=1):
        if len(r) != len(COLUMNS):
            problems.append(f"row {i}: {len(r)} columns: {r}")
            continue
        d = dict(zip(COLUMNS, r))
        code = d["code"]
        if code in seen:
            problems.append(f"{code}: duplicate code")
        seen.add(code)
        if d["category"] not in CATEGORIES:
            problems.append(f"{code}: unknown category {d['category']}")
        if d["source"] not in SOURCES:
            problems.append(f"{code}: unknown source {d['source']}")
        if d["source"] == "USDA" and not d["ref"]:
            problems.append(f"{code}: USDA row without ref")
        if not d["name_uk"] or not d["name_en"]:
            problems.append(f"{code}: missing name")
        try:
            kcal, p, f, c, fib, alc = (float(d[k]) for k in
                                       ("kcal", "protein", "fat", "carbs", "fiber", "alcohol"))
        except ValueError as e:
            problems.append(f"{code}: bad number ({e})")
            continue
        if d["portion_g"]:
            try:
                float(d["portion_g"])
            except ValueError:
                problems.append(f"{code}: bad portion")
        if p + f + c + alc > 100.5:
            problems.append(f"{code}: macros exceed 100 g")
        if fib > c + 0.01 and c > 0:
            problems.append(f"{code}: fiber > carbs")
        calc = 4 * p + 9 * f + 4 * c + 7 * alc
        tol = max(12.0, 0.15 * kcal)
        # USDA rows are official values (food-specific Atwater factors, fiber, organic acids):
        # check the hand-entered rows only.
        if d["source"] != "USDA" and abs(calc - kcal) > tol:
            problems.append(f"{code} {d['name_en']}: kcal {kcal} vs macros {calc:.0f}")
    # Food groups: known names, existing codes.
    groups_file = Path(__file__).resolve().parent.parent / "app/src/main/assets/food_groups.csv"
    known = {"PROCESSED_MEAT", "RED_MEAT", "POULTRY", "FISH", "OILY_FISH", "LEGUMES", "NUTS", "WHOLE_GRAIN",
             "REFINED_GRAIN", "SFA_RICH", "FASTFOOD", "SUGARY_DRINK", "SWEETS", "VEG_FRUIT"}
    codes = seen
    for line in groups_file.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#") or line.startswith("code;"):
            continue
        c, g = line.split(";")[:2]
        if c not in codes:
            problems.append(f"food_groups: unknown code {c}")
        if g not in known:
            problems.append(f"food_groups: unknown group {g} for {c}")
    # Hand-kept code lists in the app must match the database.
    src = Path(__file__).resolve().parent.parent / "app/src/main/java/ua/tarilka/data"
    habits = (src / "Habits.kt").read_text(encoding="utf-8")
    m = re.search(r"ALCOHOL_G = mapOf\((.*?)\)\n", habits, re.S)
    app_alc = dict((c, float(v)) for c, v in re.findall(r'"(\w+)" to ([\d.]+)', m.group(1))) if m else {}
    if not app_alc:
        problems.append("Habits.kt: ALCOHOL_G not found")
    csv_alc = {r[0]: float(r[9] or 0) for r in data if len(r) == len(COLUMNS) and float(r[9] or 0) > 0}
    for c, v in csv_alc.items():
        if c not in ALCOHOL_EXEMPT and abs(app_alc.get(c, -1) - v) > 0.05:
            problems.append(f"Habits.kt ALCOHOL_G: {c} should be {v}")
    for c in app_alc:
        if c not in csv_alc:
            problems.append(f"Habits.kt ALCOHOL_G: {c} has no alcohol in foods.csv")
    daos = (src / "Daos.kt").read_text(encoding="utf-8")
    for name in ("MILK_DRINKS", "SOUPS"):
        m = re.search(name + r" = listOf\((.*?)\)", daos, re.S)
        if not m:
            problems.append(f"Daos.kt: {name} not found")
            continue
        for c in re.findall(r'"(\w+)"', m.group(1)):
            if c not in codes:
                problems.append(f"Daos.kt {name}: unknown code {c}")
    print(f"{len(data)} foods checked")
    for p in problems:
        print("PROBLEM:", p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
