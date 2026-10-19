# -*- coding: utf-8 -*-
"""Junta os achados dos subagentes num patch para o sakuga_agent.py, somando ao que já existe (sem apagar nada).
Uso: python3 juntar_achados.py <pasta_baixada> <pasta_achados> <saida_patch.json> [patch_extra.json]
Cada arquivo <pasta_achados>/*.json é uma lista de itens:
  episódio:  {"id": "<id do catálogo, se souber>", "anime": "<nome>", "ep": 3, "v": "<id do vídeo YouTube>", "ch": "<canal>", "k": "dub|leg|ja|en|orig", "reg": "" ou "JP", "note": "grátis até 23/10/2026" | "vídeo único com os eps 1-26"}
  (a data "até dd/mm/aaaa" vira "ate" e o vídeo some sozinho depois; "vídeo único com os eps X-Y" vira "x")
  plataforma:{"id": "...", "anime": "<nome>", "p": "pluto|bilibili|...", "u": "<url>", "reg": "BR|LATAM|GLOBAL|JP|CN|KR|US|ASIA", "lang": "...", "eps": "1-12", "note": "..."}
  título novo (opcional, junto de qualquer um acima): "novo": {"id": "m<MAL>"|"n-<slug>", "t": "...", "tp": "TV|MOVIE|ONA", "st": "...", "y": 2026, "al": ..., "kt": ..., "mal": ..., "g": [...]}
patch_extra.json (opcional): patch do próprio agente (horários, dublagens, sinopses...), que é mesclado junto."""
import json, glob, os, re, sys, unicodedata

src_dir, ach_dir, outp = sys.argv[1], sys.argv[2], sys.argv[3]
extra = json.load(open(sys.argv[4], encoding="utf-8")) if len(sys.argv) > 4 and os.path.exists(sys.argv[4]) else {"items": [], "remove": [], "feed": []}


def read_doc(path):
    d = json.load(open(path, encoding="utf-8"))
    return d["data"] if isinstance(d, dict) and isinstance(d.get("data"), dict) else d


items = {}
for f in glob.glob(os.path.join(src_dir, "catalog", "*.json")):
    for it in read_doc(f).get("items", []):
        items[it["id"]] = it


def norm(s):
    s = unicodedata.normalize("NFKD", str(s or "")).encode("ascii", "ignore").decode().lower()
    s = re.sub(r"\((dublado|legendado|dub|sub)\)", "", s)
    return re.sub(r"[^a-z0-9]+", "", s)


names = {}
for it in items.values():
    for n in (it.get("t"), it.get("en"), it.get("r")):
        if n:
            names.setdefault(norm(n), it["id"])

# começa pelo patch do agente
out = {}
for p in extra.get("items", []):
    if p.get("id"):
        out[p["id"]] = json.loads(json.dumps(p))


def cur_of(iid):
    base = items.get(iid, {})
    o = out.setdefault(iid, {"id": iid})
    if "yt" not in o:
        o["yt"] = json.loads(json.dumps(base.get("yt") or {}))
    if "src" not in o:
        o["src"] = json.loads(json.dumps(base.get("src") or []))
    return o


skipped, n_yt, n_src, n_new, feed_eps = [], 0, 0, 0, {}
for f in sorted(glob.glob(os.path.join(ach_dir, "*.json"))):
    try:
        lst = json.load(open(f, encoding="utf-8"))
    except Exception as e:
        print("arquivo ignorado:", f, e, file=sys.stderr)
        continue
    if isinstance(lst, dict):
        lst = lst.get("items") or []
    for a in lst:
        if not isinstance(a, dict):
            continue
        iid = a.get("id") if a.get("id") in items or a.get("id") in out else None
        if not iid:
            iid = names.get(norm(a.get("anime")))
        if not iid and isinstance(a.get("novo"), dict) and all(k in a["novo"] for k in ("id", "t", "tp", "st")):
            nv = a["novo"]
            iid = nv["id"]
            if iid not in items and iid not in out:
                out[iid] = dict(nv)
                n_new += 1
            names.setdefault(norm(nv["t"]), iid)
        if not iid:
            skipped.append(a.get("anime"))
            continue
        o = cur_of(iid)
        note = str(a.get("note") or "")
        mm = re.search(r"até\s*(\d{1,2})/(\d{1,2})/(\d{4})", note)
        ate = f"{mm.group(3)}-{int(mm.group(2)):02d}-{int(mm.group(1)):02d}" if mm else None
        mr = re.search(r"eps?\.?\s*(\d+)\s*[–-]\s*(\d+)", note) if "vídeo único" in note else None
        if a.get("v") and (a.get("reg") or "").upper() in ("JP", "ASIA", "US", "CN", "KR"):
            a = dict(a, u="https://www.youtube.com/watch?v=" + a["v"], p="yt", eps=str(a.get("ep") or ""), note="Canal oficial " + (a.get("ch") or "") + (" · " + note if note else ""))
            a.pop("v")
        if a.get("v"):
            try:
                ep = str(int(a.get("ep") or 1))
            except Exception:
                continue
            arr = o["yt"].setdefault(ep, [])
            if any(x.get("v") == a["v"] for x in arr):
                continue
            e = {"v": a["v"], "ch": a.get("ch") or "Canal oficial", "k": a.get("k") or "leg"}
            if ate: e["ate"] = ate
            if mr and mr.group(1) != mr.group(2): e["x"] = f"{mr.group(1)}–{mr.group(2)}"
            arr.append(e)
            n_yt += 1
            feed_eps.setdefault(iid, set()).add(int(ep))
        elif a.get("u"):
            if any(x.get("u") == a["u"] for x in o["src"]):
                continue
            s = {"p": a.get("p") or "other", "u": a["u"], "reg": a.get("reg") or "", "lang": a.get("lang") or ""}
            if a.get("eps"): s["eps"] = a["eps"]
            if a.get("note"): s["note"] = a["note"]
            if ate: s["ate"] = ate
            o["src"].append(s)
            n_src += 1

# limpa campos vazios que não mudaram
patch_items = []
for iid, o in out.items():
    base = items.get(iid, {})
    if o.get("yt") == (base.get("yt") or {}) and "yt" in o and iid in items:
        o.pop("yt")
    if o.get("src") == (base.get("src") or []) and "src" in o and iid in items:
        o.pop("src")
    if not o.get("yt") and "yt" in o:
        o.pop("yt")
    if not o.get("src") and "src" in o:
        o.pop("src")
    if len(o) > 1:
        patch_items.append(o)

feed = list(extra.get("feed", []))
for iid, eps in feed_eps.items():
    e = sorted(eps)
    txt = (f"Episódio {e[0]}" if len(e) == 1 else "Episódios " + ", ".join(map(str, e[:-1])) + " e " + str(e[-1])) + " grátis no Sakuga."
    feed.append({"k": "gratis", "id": iid, "txt": txt})
log = extra.get("log") or ""
log = (log + " " if log else "") + f"Buscas em paralelo: +{n_yt} episódios grátis no player, +{n_src} plataformas grátis, {n_new} títulos novos."
json.dump({"items": patch_items, "remove": extra.get("remove", []), "feed": feed, "log": log}, open(outp, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(json.dumps({"itens": len(patch_items), "episodios": n_yt, "plataformas": n_src, "novos": n_new, "sem_par": sorted(set(x for x in skipped if x))[:40]}, ensure_ascii=False))
