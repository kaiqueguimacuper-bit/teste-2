# Sakuga — streaming de anime

Site: https://kaiqueguimacuper-bit.github.io/teste-2/
APK Android: https://github.com/kaiqueguimacuper-bit/teste-2/releases/download/apk-latest/sakuga.apk

## Como funciona
- `site/` — o app web (index.html + catalog.json). É publicado na branch `gh-pages` pelo GitHub Actions.
- `site/catalog.json` — catálogo de animes, episódios, dublagens e novidades. O **Agente Sakuga** (tarefa agendada no Claude, 07:47 e 19:47 de Brasília) pesquisa as novidades e grava este arquivo aqui a cada 12 horas.
- `android/` — app Android (Kotlin + WebView). A cada abertura baixa do GitHub (`main/site/index.html` e `main/site/catalog.json`) a versão mais nova do app e do catálogo e guarda no aparelho, então as atualizações do agente chegam sozinhas, sem reinstalar; enquanto aberto, o app também busca o catálogo novo a cada 20 min. Sem internet, usa a última cópia guardada (ou a do APK). O seletor de arquivos e a tela cheia do Sakuga Player funcionam no app.
- `.github/workflows/publicar.yml` — a cada push no `main`: compila o APK assinado, atualiza a versão `apk-latest` em Releases e publica o site (com o `sakuga.apk`) no GitHub Pages.

## Instalar o APK
1. No celular, abra o link do APK acima (ou o botão "Baixar o app Android" no rodapé do site).
2. Permita "instalar apps desconhecidos" para o navegador quando o Android pedir.
3. Abra o Sakuga. As próximas versões instalam por cima, sem perder sua lista.

## Compilar no computador
Abra a pasta `android/` no Android Studio → Build → Build APK(s). Ou `cd android && ./gradlew assembleRelease`.

A chave de assinatura (`android/sakuga-release.jks`, senha `sakuga2026`) está no repositório para que todas as versões instalem por cima umas das outras. Como o repositório é público, não instale APKs do Sakuga vindos de outro lugar.
