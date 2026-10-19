# -*- coding: utf-8 -*-
"""Monta site/catalog.json a partir do banco baixado (ArtifactData list catalog/feed/meta com out_dir).
Uso: python3 db_para_site.py <pasta_baixada> <saida_catalog.json>"""
import json, glob, os, sys
src, outp = sys.argv[1], sys.argv[2]
def rd(p):
    d = json.load(open(p, encoding="utf-8")); return d["data"] if isinstance(d, dict) and isinstance(d.get("data"), dict) else d
parts = [rd(f) for f in sorted(glob.glob(os.path.join(src, "catalog", "*.json")))]
top = max((b.get("gen") or b.get("upd") or "") for b in parts)
use = [b for b in parts if (b.get("gen") or b.get("upd") or "") == top]
if use and use[0].get("parts") and len(use) < use[0]["parts"]: use = parts
items = {}
for b in sorted(use, key=lambda b: b.get("part", 0)):
    for it in b.get("items", []): items[it["id"]] = it
feed = rd(os.path.join(src, "feed", "main.json")).get("items", [])
status = rd(os.path.join(src, "meta", "status.json"))
lst = sorted(items.values(), key=lambda x: (x.get("st") != "airing", -(x.get("sc") or 0), x.get("t", "")))
json.dump({"items": lst, "feed": feed, "status": status}, open(outp, "w", encoding="utf-8"), ensure_ascii=False)
print(json.dumps({"titulos": len(lst), "gen": top, "lastRun": status.get("lastRun"), "partes": len(use)}, ensure_ascii=False))
