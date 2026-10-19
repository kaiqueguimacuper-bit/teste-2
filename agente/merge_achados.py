# -*- coding: utf-8 -*-
"""Junta os achados das buscas (achados/*.json) em achados/merged.json, casando cada anime com a base.
Saída: {"yt": [[romaji, ep, v, ch, k], ...], "src": [[romaji, p, u, reg, lang, eps, note], ...], "sem_par": [...]}"""
import json, glob, os, re, sys, unicodedata

B = os.path.dirname(os.path.abspath(__file__))
data = json.load(open(os.path.join(B, "data/aod.json"), encoding="utf-8"))["data"]
TYPES = {"TV", "MOVIE", "ONA", "OVA", "SPECIAL"}


def norm(s):
    s = unicodedata.normalize("NFKD", str(s)).encode("ascii", "ignore").decode().lower()
    s = re.sub(r"\(.*?\)", " ", s)
    s = re.sub(r"\b(season|temporada|part|parte|cour)\b.*$", " ", s) if False else s
    return re.sub(r"[^a-z0-9]+", "", s)


index = {}
for a in data:
    if a["type"] not in TYPES:
        continue
    for n in [a["title"]] + a.get("synonyms", []):
        k = norm(n)
        if len(k) >= 3:
            index.setdefault(k, []).append(a)


def season_hint(name):
    m = re.search(r"(\d+)(?:st|nd|rd|th)?\s*(?:season|temporada)|season\s*(\d+)|temporada\s*(\d+)|\b(II|III|IV)\b", name, re.I)
    if not m:
        return None
    g = next(x for x in m.groups() if x)
    return {"II": 2, "III": 3, "IV": 4}.get(g, None) or int(g)


ORD = {1: "1st", 2: "2nd", 3: "3rd"}


def variants(name):
    out = [name]
    m = re.search(r"\s*(?:Temporada|Season)\s*(\d+)", name, re.I)
    if m:
        n = int(m.group(1)); base = name[:m.start()].strip()
        out += [f"{base} Season {n}", f"{base} {ORD.get(n, str(n) + 'th')} Season", f"{base} {n}", base]
    return out


OVERRIDE = {
    "Magic Knight Rayearth": ("Magic Knight Rayearth", 1994),
    "Magic Knight Rayearth (2026)": ("Magic Knight Rayearth (2026)", None),
    "Keroro Gunsou (2026)": ("Keroro Gunsou ☆", None),
    "Pocket Monsters (2023)": ("Pokemon (2023)", None),
    "Chibi Maruko-chan": ("Chibi Maruko-chan (1995)", None),
    "Juushin Enbu Hero Densetsu Iron Leaguer": ("Shippuu! Iron Leaguer", None),
}


def find(name):
    if name in OVERRIDE:
        t, y = OVERRIDE[name]
        c = [a for a in data if a["title"] == t and (y is None or a["animeSeason"].get("year") == y)]
        if c:
            return max(c, key=lambda a: len(a["sources"]))["title"]
    exact = [a for a in data if a["title"] == name and a["type"] in TYPES]
    if exact:
        return max(exact, key=lambda a: len(a["sources"]))["title"]
    cands = []
    for v in variants(name):
        cands = index.get(norm(v), [])
        if cands:
            break
    if not cands:
        base = re.split(r"\s+[-|:]\s+|\s+\(", name)[0]
        cands = index.get(norm(base), [])
    if not cands:
        return None
    sh = season_hint(name)
    def score(a):
        s = len(a["sources"]) * 10
        t = a["title"].lower()
        if sh and (str(sh) in t or {2: "ii", 3: "iii", 4: "iv"}.get(sh, "#") in t.split()):
            s += 100
        if not sh and re.search(r"season|2nd|3rd|\bii\b|movie", t):
            s -= 20
        if a["type"] == "TV":
            s += 5
        return s
    return max(cands, key=score)["title"]


def extra_of(it):
    note = str(it.get("note") or "")
    e = {}
    mm = re.search(r"até\s*(\d{1,2})/(\d{1,2})/(\d{4})", note)
    if mm: e["ate"] = f"{mm.group(3)}-{int(mm.group(2)):02d}-{int(mm.group(1)):02d}"
    mr = re.search(r"eps?\.?\s*(\d+)\s*[–-]\s*(\d+)", note) if "vídeo único" in note else None
    if mr and mr.group(1) != mr.group(2): e["x"] = f"{mr.group(1)}–{mr.group(2)}"
    return e


yt, src, miss, seen = [], [], [], set()
for f in sorted(glob.glob(os.path.join(B, "achados", "[A-Z].json"))):
    try:
        items = json.load(open(f, encoding="utf-8"))
    except Exception as e:
        print("ignorado", f, e, file=sys.stderr)
        continue
    if isinstance(items, dict):
        items = items.get("items") or items.get("data") or []
    for it in items:
        name = it.get("anime") or it.get("title") or ""
        r = find(name)
        if not r:
            miss.append(name)
            continue
        if it.get("v") and (it.get("reg") or "").upper() in ("ASIA", "US", "JP", "CN", "KR"):
            key = ("src", it["v"])
            if key in seen:
                continue
            seen.add(key)
            src.append([r, "yt", "https://www.youtube.com/watch?v=" + it["v"], it["reg"].upper(), it.get("k") or "", str(it.get("ep") or ""), "Canal oficial " + (it.get("ch") or "") + (" · " + it["note"] if it.get("note") else ""), extra_of(it)])
        elif it.get("v"):
            key = ("yt", it["v"])
            if key in seen:
                continue
            seen.add(key)
            try:
                ep = int(it.get("ep") or 1)
            except Exception:
                ep = 1
            yt.append([r, ep, it["v"], it.get("ch") or "Canal oficial", it.get("k") or "leg", extra_of(it)])
        elif it.get("u"):
            key = ("src", it["u"])
            if key in seen:
                continue
            seen.add(key)
            src.append([r, it.get("p") or "other", it["u"], it.get("reg") or "", it.get("lang") or "", it.get("eps") or "", it.get("note") or "", extra_of(it)])
json.dump({"yt": yt, "src": src, "sem_par": sorted(set(miss))}, open(os.path.join(B, "achados", "merged.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(json.dumps({"yt": len(yt), "src": len(src), "sem_par": len(set(miss))}, ensure_ascii=False))
