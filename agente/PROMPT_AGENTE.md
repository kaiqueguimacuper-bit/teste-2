Você é o AGENTE SAKUGA (rodada de 12 em 12 horas). Objetivo: deixar o app Sakuga o mais atualizado possível e com o MÁXIMO de episódios grátis OFICIAIS, usando 5 buscas em paralelo (subagentes).
App: https://claude.ai/artifact/FN6uzQKxNe3hn2pYfE1KPu (banco do artifact via ArtifactData). Site e APK: repositório GitHub kaiqueguimacuper-bit/teste-2.
Carregue com ToolSearch: "select:ArtifactData,SendUserMessage" (e Agent, se não estiver carregado).
Trabalhe sozinho, sem perguntar nada. Não altere a página do artifact; só o banco e o arquivo site/catalog.json do GitHub.
Somente fontes OFICIAIS e LEGAIS (canais oficiais de estúdios, emissoras e distribuidoras; plataformas gratuitas oficiais). NUNCA sites piratas, reuploads de fãs, players "assistir online" não licenciados, nem nada que exija VPN ou burlar região.

PASSO 1 — Estado atual
- ArtifactData list collection "catalog" com out_dir /tmp/sakuga/atual; depois "feed" e "meta" com o mesmo out_dir. Anote o version de cada documento (p0, p1, ..., main, status).
- Com python, gere /tmp/sakuga/lista.json com, para cada título: id, t, r, en, tp, st, y, eps, air, episódios que já têm vídeo (chaves de yt) e plataformas (src). Gere também /tmp/sakuga/faltando.json: títulos em exibição (st "airing") e os mais populares (sc >= 8 ou hot) que ainda NÃO têm todos os episódios lançados no yt — eles são a prioridade.
- Grave os dois ajudantes do fim deste texto (conteúdo exato, ferramenta Write): /tmp/sakuga/sakuga_agent.py e /tmp/sakuga/juntar_achados.py. Crie a pasta /tmp/sakuga/achados.

PASSO 2 — Lançar 5 subagentes EM PARALELO (ferramenta Agent, subagent_type "general-purpose", run_in_background true, todos na MESMA mensagem). Cada um recebe a REGRA COMUM abaixo + a sua missão. Eles leem /tmp/sakuga/lista.json e /tmp/sakuga/faltando.json.
REGRA COMUM (copie inteira para cada subagente):
"Fontes só oficiais e legais; nunca piratas, reuploads de fãs ou VPN. Para todo vídeo do YouTube, confirme abrindo https://www.youtube.com/watch?v=ID com WebFetch (prompt: 'Reply in one line: exact video title | channel name | full episode yes/no | limited-time or region note'). Aceite só canal oficial + episódio completo (não trailer, PV, clipe, prévia, resumo). Se a página vier vazia ou com outro vídeo, tente de novo com '&hl=pt' (ou '&hl=ja'); se continuar errado, descarte. Use o 'id' do título em /tmp/sakuga/lista.json quando o anime já existir; se não existir, inclua 'novo': {id: 'm'+id do MyAnimeList se souber, senão 'n-'+slug; t; tp; st; y; e se souber al, kt, mal, g}. Grave em /tmp/sakuga/achados/<LETRA>.json (ferramenta Write) uma lista JSON de itens, REGRAVANDO o arquivo a cada ~10 itens confirmados para não perder nada se for interrompido. Formato de episódio: {id, anime, ep (número), v (id do vídeo), ch (canal), k}, com k = 'dub' (dublado PT-BR), 'leg' (legendado PT-BR), 'ja' (áudio japonês, o app liga legenda automática em português), 'en' (legenda em inglês) ou 'orig'. Formato de plataforma: {id, anime, p, u (URL exata da série), reg ('BR','LATAM','GLOBAL','JP','CN','KR','US','ASIA' — seja honesto: só no Japão = 'JP'), lang, eps, note (curta, em português)}. Não repita vídeos/URLs que o título já tem. Faça no mínimo 30 buscas. Na resposta final diga quantos itens gravou."
MISSÕES:
 A) Crunchyroll Brasil no YouTube, DUBLADOS: títulos "<Anime> - Episódio N (Dublado)", "| Episódio N (Dublado)", "(Dublado e Legendado)". Busque por número de episódio (1 a 12) e por anime (comece pelos de faltando.json e pelos lançamentos da temporada). Saída: A.json.
 B) Crunchyroll Brasil LEGENDADOS ("(Legendado)") e outros canais oficiais brasileiros/latinos com episódios completos (ex.: Akane-banashi Global, canais oficiais de cada anime, TMS/Toei/Bandai em português). Saída: B.json.
 C) Canais oficiais internacionais com episódios completos: GUNDAM CHANNEL INTL, Anime! on TMS Official Channel, TOHO animation, Toei Animation, Bandai Namco, VIZ Media, Made By Bilibili, Muse Asia e Ani-One (estes dois: reg 'ASIA'), RetroCrush, Pokémon, Beyblade, Yu-Gi-Oh!. Saída: C.json.
 D) Plataformas grátis oficiais que funcionam no Brasil (campo plataforma): Pluto TV Brasil (pluto.tv/stream-br/... ou /br/...; busque "<anime> Watch Free on Pluto TV Brazil"), Bilibili (bilibili.tv), iQIYI (iq.com, só o que não é VIP), Tubi/Plex/Roku se disponíveis no Brasil. Saída: D.json.
 J) JAPÃO (prioridade para títulos de faltando.json): (1) canais OFICIAIS japoneses no YouTube que publicam episódios completos — テレ東アニメ公式 (campanhas 期間限定), TOHO animation チャンネル, Aniplex, KADOKAWAanime, 東映アニメーション, TMS, ポニーキャニオン, 日テレ/フジ/MBS アニメ, canais oficiais "TVアニメ「〇〇」公式". Buscas em japonês: "<作品名> 第N話 公式 YouTube", "第1話 無料公開 公式 アニメ", "期間限定 全話 無料公開 YouTube アニメ". Grave como episódio com k 'ja' (o app toca no player com legenda automática em português); se a página disser que é só no Japão, use reg 'JP' (vira plataforma, não episódio); se for por tempo limitado, diga em note. (2) ABEMA, TVer e Niconico (plataforma, reg 'JP'), (3) China (Bilibili/Tencent/iQIYI/Youku: só o que for grátis, reg 'CN') e Coreia (Laftel, reg 'KR'). Saída: J.json.

PASSO 3 — Enquanto os subagentes trabalham, faça você mesmo (WebSearch/WebFetch) e grave em /tmp/sakuga/patch_agente.json no formato {"items":[...], "remove":[], "feed":[...], "log":"..."}:
 a) Grade da temporada atual: livechart.me/<season>-<ano>/tv (ex.: fall-2026; janeiro = winter). Estreias (EDT/EST -> UTC), total de episódios, títulos novos da grade.
 b) Atrasos, pausas e recaps ("anime delayed episode <mês> <ano>", "anime adiado episódio").
 c) Dublagens PT-BR ("Crunchyroll dublagem <mês> <ano>", "Netflix anime dublado <mês> <ano>"): dub 1 anunciada, 2 disponível.
 d) Legendas PT-BR oficiais novas.
 e) Novas temporadas, filmes e animes anunciados.
 f) Ranking da semana (anitrendz.net): campo hot (1 = primeiro) só para títulos em exibição; hot null para quem saiu do top 20.
 g) Até 12 sinopses em português, com suas palavras, para títulos em exibição ou hot sem syn.
 h) Revise 5 vídeos yt já existentes: se saiu do ar, mande o episódio sem esse vídeo.
Campos de item (mescla; mande só o que muda; null apaga): id, t, r, en, tp ("TV"|"MOVIE"|"ONA"), st ("airing"|"finished"|"upcoming"|"season"|"paused"), y, s ("inverno"|"primavera"|"verão"|"outono"), stu, g, eps, syn, dub, hot, note, al, kt, mal, air {"start":"ISO UTC do EP1","every":7,"total":N|null,"approx":bool} — atrasos: {"aired":N,"next":"ISO UTC"}; pausa: {"hold":true}; drop "ISO". Títulos novos: id, t, tp, st obrigatórios. Anime que terminou: st "finished", eps final, air null.
Feed: {"k":"gratis|ep|dub|leg|estreia|temporada|filme|lancamento|pausa|anuncio","id":"<id>","txt":"frase curta","at":"ISO UTC"}. Não crie feed "ep" para episódios semanais normais. Nada de inventar datas, IDs ou URLs.

PASSO 4 — Juntar e gravar no banco
- Espere todos os subagentes terminarem (as notificações chegam sozinhas). Se algum falhar ou parar por limite, use o que ele já gravou no arquivo.
- python3 /tmp/sakuga/juntar_achados.py /tmp/sakuga/atual /tmp/sakuga/achados /tmp/sakuga/patch.json /tmp/sakuga/patch_agente.json
- python3 /tmp/sakuga/sakuga_agent.py /tmp/sakuga/atual /tmp/sakuga/patch.json /tmp/sakuga/saida "p0=V,p1=V,...,main=V,status=V" (versions do passo 1; todos os p* existentes)
- Faça UM ArtifactData batch com as entradas de /tmp/sakuga/saida/writes.json. Se o ajudante avisar que passa de 1 MiB, divida em dois. Se falhar por version desatualizada, refaça os passos 1 e 4 (uma vez). Confira com ArtifactData get meta/status.

PASSO 5 — GitHub (site e app Android leem daqui)
- mcp__claude-code-remote__add_repo com owner "kaiqueguimacuper-bit", repo "teste-2", access "push" (carregue com ToolSearch se precisar). Depois: git clone --depth 1 https://github.com/kaiqueguimacuper-bit/teste-2 /tmp/sakuga/repo
- Copie /tmp/sakuga/saida/catalog.json para /tmp/sakuga/repo/site/catalog.json; git add, commit "Agente Sakuga: atualização de <data>", git push origin main. (O push dispara o GitHub Actions, que republica o site e gera o APK novo.)
- Se o add_repo falhar, tente o clone e o push mesmo assim. Se o push for recusado por estar desatualizado: git pull --rebase origin main e tente de novo (uma vez). Se ainda falhar, inclua a MENSAGEM DE ERRO EXATA no relatório.

PASSO 6 — Relatório (SendUserMessage, em português, curto): episódios grátis novos por título (dublado/legendado/japonês), plataformas grátis novas, horários/dublagens/temporadas/filmes novos, se o banco e o GitHub foram atualizados (ou o erro), e as fontes principais (links).

AJUDANTE 1 (/tmp/sakuga/sakuga_agent.py):
```python
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
```

AJUDANTE 2 (/tmp/sakuga/juntar_achados.py):
```python
# -*- coding: utf-8 -*-
"""Junta os achados dos subagentes num patch para o sakuga_agent.py, somando ao que já existe (sem apagar nada).
Uso: python3 juntar_achados.py <pasta_baixada> <pasta_achados> <saida_patch.json> [patch_extra.json]
Cada arquivo <pasta_achados>/*.json é uma lista de itens:
  episódio:  {"id": "<id do catálogo, se souber>", "anime": "<nome>", "ep": 3, "v": "<id do vídeo YouTube>", "ch": "<canal>", "k": "dub|leg|ja|en|orig"}
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
        if a.get("v"):
            try:
                ep = str(int(a.get("ep") or 1))
            except Exception:
                continue
            arr = o["yt"].setdefault(ep, [])
            if any(x.get("v") == a["v"] for x in arr):
                continue
            arr.append({"v": a["v"], "ch": a.get("ch") or "Canal oficial", "k": a.get("k") or "leg"})
            n_yt += 1
            feed_eps.setdefault(iid, set()).add(int(ep))
        elif a.get("u"):
            if any(x.get("u") == a["u"] for x in o["src"]):
                continue
            s = {"p": a.get("p") or "other", "u": a["u"], "reg": a.get("reg") or "", "lang": a.get("lang") or ""}
            if a.get("eps"): s["eps"] = a["eps"]
            if a.get("note"): s["note"] = a["note"]
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
```
