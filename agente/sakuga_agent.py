# -*- coding: utf-8 -*-
"""Ajudante do Agente Sakuga: aplica um patch de novidades ao catálogo baixado do banco e gera os arquivos para o batch.
Uso:
  python3 sakuga_agent.py <pasta_baixada> <patch.json> <pasta_saida> "p0=1,p1=1,p2=1,main=1,status=1"
(o 4º argumento traz o version de cada documento, como o ArtifactData list mostrou)
<pasta_baixada>: out_dir usado no ArtifactData list das coleções catalog, feed e meta (contém catalog/*.json, feed/main.json, meta/status.json)
patch.json: {"items": [{"id": "...", ...campos a mesclar...}], "remove": ["id"], "feed": [{"k": "ep|dub|leg|estreia|temporada|filme|lancamento|pausa|anuncio", "id": "...", "txt": "...", "at": "ISO"}], "log": "resumo"}
Saída: <pasta_saida>/catalog_pN.json, feed.json, status.json, catalog.json (site do GitHub) e writes.json (lista pronta para ArtifactData batch, com if_version)."""
import json, os, sys, glob, datetime as dt

src, patch_path, out = sys.argv[1], sys.argv[2], sys.argv[3]
VER = {}
if len(sys.argv) > 4 and sys.argv[4].strip():
    for kv in sys.argv[4].split(","):
        k, v = kv.split("=")
        VER[k.strip()] = int(v)
os.makedirs(out, exist_ok=True)
NOW = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def read_doc(path):
    d = json.load(open(path, encoding="utf-8"))
    # formato do out_dir: {"id","data","version"} ou o próprio corpo
    if isinstance(d, dict) and "data" in d and isinstance(d["data"], dict):
        return d["data"], d.get("version")
    return d, None


parts, versions = [], {}
for f in sorted(glob.glob(os.path.join(src, "catalog", "*.json"))):
    body, ver = read_doc(f)
    doc_id = os.path.splitext(os.path.basename(f))[0]
    versions["catalog/" + doc_id] = ver or VER.get(doc_id)
    parts.append((doc_id, body))
if not parts:
    sys.exit("nenhum documento em catalog/ — baixe com ArtifactData list collection=catalog out_dir=<pasta>")
top = max((b.get("gen") or b.get("upd") or "") for _, b in parts)
use = [b for _, b in parts if (b.get("gen") or b.get("upd") or "") == top]
if use and use[0].get("parts") and len(use) < use[0]["parts"]:
    use = [b for _, b in parts]
items = {}
for b in sorted(use, key=lambda b: b.get("part", 0)):
    for it in b.get("items", []):
        items[it["id"]] = it
before = len(items)

patch = json.load(open(patch_path, encoding="utf-8"))
changed = 0


def merge(dst, src_):
    for k, v in src_.items():
        if isinstance(v, dict) and isinstance(dst.get(k), dict):
            merge(dst[k], v)
        elif v is None:
            dst.pop(k, None)
        else:
            dst[k] = v


REQ = {"id", "t", "tp", "st"}
for p in patch.get("items", []):
    if "id" not in p:
        continue
    if p["id"] in items:
        merge(items[p["id"]], p)
    else:
        missing = REQ - set(p)
        if missing:
            print("ignorado (faltam campos " + ",".join(sorted(missing)) + "):", p.get("id"), file=sys.stderr)
            continue
        base = {"r": p.get("t"), "eps": None, "y": None, "s": "", "stu": "", "g": [], "sc": None, "dur": None, "syn": "", "pic": "", "dub": 0, "prov": [], "fr": p["id"]}
        base.update(p)
        items[p["id"]] = base
    items[p["id"]]["upd"] = NOW
    changed += 1
for rid in patch.get("remove", []):
    if items.pop(rid, None):
        changed += 1

# feed
fpath = os.path.join(src, "feed", "main.json")
feed, fver = (read_doc(fpath) if os.path.exists(fpath) else ({"items": []}, None))
fver = fver or VER.get("main")
fitems = feed.get("items", [])
keys = {(f.get("k"), f.get("id"), f.get("txt")) for f in fitems}
added = 0
for f in patch.get("feed", []):
    f.setdefault("at", NOW)
    if (f.get("k"), f.get("id"), f.get("txt")) not in keys:
        fitems.append(f)
        added += 1
fitems.sort(key=lambda f: f.get("at", ""), reverse=True)
feed = {"items": fitems[:300], "upd": NOW}

# status
spath = os.path.join(src, "meta", "status.json")
status, sver = (read_doc(spath) if os.path.exists(spath) else ({}, None))
sver = sver or VER.get("status")
missing_v = [k for k, v in list(versions.items()) + [("feed/main", fver if os.path.exists(fpath) else 1), ("meta/status", sver if os.path.exists(spath) else 1)] if not v]
if missing_v:
    sys.exit("faltam versions de: " + ", ".join(missing_v) + " (passe o 4º argumento)")
airing = sum(1 for i in items.values() if i.get("st") == "airing")
status.update({"lastRun": NOW, "every": 12, "titles": len(items),
               "log": patch.get("log") or f"Busca concluída: {changed} títulos atualizados, {added} novidades."})
status.pop("probe", None)

# reparticiona (≤180 KB por documento)
lst = sorted(items.values(), key=lambda x: (x.get("st") != "airing", -(x.get("sc") or 0), x.get("t", "")))
chunks, cur, size = [], [], 0
for it in lst:
    s = len(json.dumps(it, ensure_ascii=False).encode())
    if cur and size + s > 180_000:
        chunks.append(cur); cur, size = [], 0
    cur.append(it); size += s
if cur:
    chunks.append(cur)
status["parts"] = len(chunks)
writes = []
for n, ch in enumerate(chunks):
    path = os.path.join(out, f"catalog_p{n}.json")
    json.dump({"part": n, "parts": len(chunks), "gen": NOW, "upd": NOW, "items": ch}, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    w = {"op": "set", "collection": "catalog", "doc_id": f"p{n}", "file_path": os.path.abspath(path)}
    if versions.get(f"catalog/p{n}"):
        w["if_version"] = versions[f"catalog/p{n}"]
    writes.append(w)
# documentos antigos sobrando (se o catálogo encolheu) são apagados
for key, ver in versions.items():
    doc_id = key.split("/")[1]
    if doc_id.startswith("p") and doc_id[1:].isdigit() and int(doc_id[1:]) >= len(chunks):
        writes.append({"op": "delete", "collection": "catalog", "doc_id": doc_id, "if_version": ver})
json.dump(feed, open(os.path.join(out, "feed.json"), "w", encoding="utf-8"), ensure_ascii=False)
json.dump(status, open(os.path.join(out, "status.json"), "w", encoding="utf-8"), ensure_ascii=False)
w = {"op": "set", "collection": "feed", "doc_id": "main", "file_path": os.path.abspath(os.path.join(out, "feed.json"))}
if fver: w["if_version"] = fver
writes.append(w)
w = {"op": "set", "collection": "meta", "doc_id": "status", "file_path": os.path.abspath(os.path.join(out, "status.json"))}
if sver: w["if_version"] = sver
writes.append(w)
json.dump(writes, open(os.path.join(out, "writes.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
# catálogo completo para o site hospedado no GitHub (site/catalog.json)
json.dump({"items": lst, "feed": feed["items"], "status": status}, open(os.path.join(out, "catalog.json"), "w", encoding="utf-8"), ensure_ascii=False)
total = sum(os.path.getsize(x["file_path"]) for x in writes if x.get("file_path"))
print(json.dumps({"titulos_antes": before, "titulos_depois": len(items), "alterados": changed, "novidades": added,
                  "em_exibicao": airing, "documentos": len(writes), "bytes": total}, ensure_ascii=False))
if total > 1_000_000:
    print("ATENÇÃO: passa de 1 MiB; envie em dois batches (catálogo num, feed+status no outro)", file=sys.stderr)
