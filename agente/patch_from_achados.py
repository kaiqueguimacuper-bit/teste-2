# -*- coding: utf-8 -*-
"""Gera um patch (formato do agente) somando achados/merged.json ao catálogo atual do banco.
Uso: python3 patch_from_achados.py <pasta_db_atual> <saida_patch.json>"""
import json, glob, os, re, sys, unicodedata, hashlib

B = os.path.dirname(os.path.abspath(__file__))
dbdir, outp = sys.argv[1], sys.argv[2]
sys.path.insert(0, B)
import curadoria as C

items = {}
for f in glob.glob(os.path.join(dbdir, "catalog", "*.json")):
    d = json.load(open(f, encoding="utf-8")); d = d.get("data", d)
    for it in d["items"]:
        items[it["id"]] = it
m = json.load(open(os.path.join(B, "achados", "merged.json"), encoding="utf-8"))
aod = json.load(open(os.path.join(B, "data", "aod.json"), encoding="utf-8"))["data"]


def norm(s):
    s = unicodedata.normalize("NFKD", str(s or "")).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "", s)


by_r, by_name = {}, {}
for it in items.values():
    by_r.setdefault(it.get("r"), it)
    for n in (it.get("t"), it.get("en"), it.get("r")):
        if n:
            by_name.setdefault(norm(n), it)
aod_by_title = {}
for a in aod:
    aod_by_title.setdefault(a["title"], []).append(a)


def src_id(a, host):
    for u in a["sources"]:
        if host in u:
            mm = re.search(r"/(\d+)$", u)
            return int(mm.group(1)) if mm else u.rsplit("/", 1)[-1]
    return None


def new_item(romaji):
    cands = aod_by_title.get(romaji) or []
    if not cands:
        return None
    a = max(cands, key=lambda x: len(x["sources"]))
    mal = src_id(a, "myanimelist.net")
    iid = f"m{mal}" if mal else "h" + hashlib.md5(romaji.encode()).hexdigest()[:10]
    if iid in items:
        return items[iid]
    syn = [s for s in a.get("synonyms", []) if s.isascii() and len(s) < 70]
    slug = src_id(a, "anime-planet.com")
    en = None
    if isinstance(slug, str):
        ns = norm(slug)
        en = next((s for s in syn if norm(s) == ns), None)
    gmap = C.GENRES
    g = []
    for t in a.get("tags", []):
        x = gmap.get(t.lower())
        if x and x not in g:
            g.append(x)
    st = {"FINISHED": "finished", "ONGOING": "airing", "UPCOMING": "upcoming"}.get(a["status"], "finished")
    sc = (a.get("score") or {}).get("median")
    dur = (a.get("duration") or {}).get("value")
    sp = {"WINTER": "inverno", "SPRING": "primavera", "SUMMER": "verão", "FALL": "outono"}
    it = {"id": iid, "t": en or a["title"], "r": a["title"], "tp": a["type"] if a["type"] in ("TV", "MOVIE", "ONA") else "TV",
          "st": "finished" if st == "airing" else st, "eps": a["episodes"] or None, "y": a["animeSeason"].get("year"),
          "s": sp.get(a["animeSeason"].get("season"), ""), "stu": (a.get("studios") or [""])[0].title(), "g": g[:6],
          "sc": round(sc, 1) if sc and len(a["sources"]) >= 7 else None, "dur": round(dur / 60) if dur else None,
          "pic": a.get("picture") if "no_pic" not in (a.get("picture") or "") else "",
          "mal": mal, "al": src_id(a, "anilist.co"), "kt": src_id(a, "kitsu.app"), "lc": src_id(a, "livechart.me"),
          "fr": iid, "_new": True}
    if en and en != it["t"]:
        it["en"] = en
    return it


def locate(romaji):
    it = by_r.get(romaji)
    if it:
        return it
    cands = aod_by_title.get(romaji) or []
    if cands:  # o título exato da base manda: casa pelo id do MAL, senão cria o título certo
        a = max(cands, key=lambda x: len(x["sources"]))
        mal = src_id(a, "myanimelist.net")
        if mal and f"m{mal}" in items:
            return items[f"m{mal}"]
        if mal:
            al = src_id(a, "anilist.co")
            for it in items.values():
                if it.get("mal") == mal or (al and it.get("al") == al):
                    return it
            it = new_item(romaji)
            if it:
                items[it["id"]] = it
                by_r[romaji] = it
                return it
    for a in cands:
        for n in [a["title"]] + a.get("synonyms", []):
            hit = by_name.get(norm(n))
            if hit:
                return hit
    it = new_item(romaji)
    if it:
        items[it["id"]] = it
        by_r[romaji] = it
    return it


touched = {}
added_yt, added_src = {}, {}
for row in m["yt"]:
    romaji, ep, v, ch, k = row[:5]
    ex = row[5] if len(row) > 5 else {}
    it = locate(romaji)
    if not it:
        continue
    cur = touched.setdefault(it["id"], {"id": it["id"]})
    yt = json.loads(json.dumps(it.get("yt") or {}))
    yt.update(cur.get("yt", {}))
    arr = yt.setdefault(str(ep), [])
    if any(x.get("v") == v for x in arr):
        continue
    arr.append({"v": v, "ch": ch, "k": k, **ex})
    cur["yt"] = {**cur.get("yt", {}), str(ep): arr}
    added_yt.setdefault(it["id"], set()).add(int(ep))
for row in m["src"]:
    romaji, p, u, reg, lang, eps, note = row[:7]
    ex = row[7] if len(row) > 7 else {}
    it = locate(romaji)
    if not it:
        continue
    cur = touched.setdefault(it["id"], {"id": it["id"]})
    lst = cur.get("src") or json.loads(json.dumps(it.get("src") or []))
    if any(x.get("u") == u for x in lst):
        continue
    s = {"p": p, "u": u, "reg": reg, "lang": lang}
    if eps: s["eps"] = eps
    if note: s["note"] = note
    s.update(ex)
    lst.append(s)
    cur["src"] = lst
    added_src.setdefault(it["id"], 0)
    added_src[it["id"]] += 1

patch_items, feed = [], []
for iid, ch in touched.items():
    it = items[iid]
    if it.get("_new"):
        base = {k: v for k, v in it.items() if k != "_new" and v is not None}
        base.update(ch)
        patch_items.append(base)
    else:
        patch_items.append(ch)
    if iid in added_yt:
        eps = sorted(added_yt[iid])
        txt = (f"Episódio {eps[0]}" if len(eps) == 1 else "Episódios " + ", ".join(map(str, eps[:-1])) + " e " + str(eps[-1])) + (" grátis no Sakuga." if len(eps) == 1 else " grátis no Sakuga.")
        feed.append({"k": "gratis", "id": iid, "txt": txt})
    elif iid in added_src and any(s.get("reg") in ("BR", "LATAM", "GLOBAL") for s in ch.get("src", [])):
        feed.append({"k": "gratis", "id": iid, "txt": "Disponível grátis em plataforma oficial."})
n_yt = sum(len(v) for v in added_yt.values())
n_src = sum(added_src.values())
n_new = sum(1 for i in touched if items[i].get("_new"))
log = f"Busca no Japão (áudio japonês + legenda automática em português): +{n_yt} episódios grátis no player, +{n_src} plataformas grátis oficiais (Niconico, ABEMA, TVer, Bilibili, canais oficiais), {n_new} títulos novos."
json.dump({"items": patch_items, "remove": [], "feed": feed, "log": log}, open(outp, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(json.dumps({"itens": len(patch_items), "eps_yt": n_yt, "src": n_src, "novos": n_new, "feed": len(feed)}, ensure_ascii=False))
