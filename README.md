# Smarted TV

Media center modular para **Android TV** e **mobile**, escrito 100% em **Kotlin + Jetpack Compose**,
com arquitetura de **addons no estilo Kodi**, repositório **Vikings** como padrão e o
catálogo nativo do Brazuca Play integrado.

> Orientação travada em paisagem (`sensorLandscape`), interface D-pad-first com suporte a toque.

**[⬇️ Baixar o APK mais recente](https://github.com/msmacrosmart-cpu/SmartED-TV/releases/latest)** —
compilado e assinado automaticamente pelo GitHub Actions a cada push.

---

## ✨ O que está implementado

Interface **dark + glassmorphism**, foco de D-pad em **branco puro** e todo o
texto em **português do Brasil**.

| Requisito | Implementação |
|---|---|
| Identidade visual | Fundo `#07080B`, **acento branco puro `#FFFFFF`** em foco/seleção/ação, 3 camadas de vidro (`Glass.kt`), **doca flutuante de 96dp** com ícone + rótulo, anel de foco de 2–3dp e lift de 3–5% |
| Layout da Home | **Palco cinematográfico full-bleed** (68% da altura) com backdrop rotativo, classificação indicativa, ano, duração, gêneros, sinopse e as ações "Assistir"/"Detalhes"; abaixo, trilhas horizontais de **cartões 16:10 com o título e os metadados dentro da arte** (`Stage.kt`, `FeatureCard.kt`, `HomeScreen.kt`) — ver [`docs/NOVO-LAYOUT.md`](docs/NOVO-LAYOUT.md) e a [prévia estática](docs/preview/index.html) |
| App 100% pt-BR | `strings.xml` como fonte única, locale fixo em `MainActivity`, gate `tools/check-ptbr.py` no CI |
| Player VLC | `VlcEngine` sobre `libvlc-all` 3.5.1 (codecs + reconexão), fallback automático para ExoPlayer |
| Layouts por tipo de mídia | **Ao vivo**: zap ◀ ▶ / CH±, áudio, legendas, badge AO VIVO, resolução. **Filmes/Séries**: linha do tempo, capítulos, ±10s/+30s, áudio e legendas |
| Navegador de repositório (Kodi) | Tela dedicada que abre o repositório nas pastas **TV ao Vivo**, **Filmes** e **Séries**, com pôster, título e metadados de cada item (`AddonBrowser`, `AddonBrowserScreen`) |
| Pasta do repositório → aba do app | Cada pasta conecta na sua aba (`AddonManager.bindFolder`), gravando a escolha no repositório e nos addons; **Filmes/Séries só aceitam MP4/MKV**, canais só transmissão |
| Somente repositórios e addons | Listas M3U avulsas **desativadas** nesta versão: o conteúdo vem do repositório (padrão Vikings) e do addon embutido |
| Separação rigorosa de acervo | `ContentClassifier` recusa canal ao vivo em Filmes e exige MP4/MKV/VOD; Canais só recebem transmissão; Séries exigem estrutura de temporada |
| Séries com temporadas | `SeriesGrouper` transforma episódios soltos (`S01E02`, `1x02`) em séries com temporadas e episódios, persistidas no cache |
| EPG inteligente por nome | `EpgMatcher` normaliza o nome (remove HD/FHD/SD, `|`, `[tags]`) e cruza com o XMLTV por similaridade quando o `tvg-id` falta ou está errado |
| Filtro PT-BR | `LanguageFilter` em cascata (metadados → grupo → título → palavras funcionais), com contagem de descartados |
| EPG ≥ 12h contínuas | `EpgTimeline.pad` preenche lacunas; fontes XMLTV de fallback registradas no boot |
| Home por ranking | Top 10 em alta → Séries em alta → Continuar assistindo → Lançamentos → Séries para maratonar → Canais favoritos → Gêneros; **palco só com filme**, botões "Assistir" e "Detalhes" em branco puro |
| Home sem canal poluindo | A seção de canais mostra **apenas favoritos** — sem favoritos, a seção não aparece |
| Player em tela cheia absoluta · HUD de vidro | Vídeo em `MATCH_PARENT`/`FILL_BOUNDS`, fora do overscan e da rail; `IVLCVout.setWindowSize` informa o tamanho real da janela ao libVLC (sem isso o vout desenha num viewport minúsculo no canto); controles em **painéis de vidro flutuantes** (`GlassPanel`/`GlassPill`) com texto e foco branco puro |
| Arquitetura de addons estilo Kodi | `Addon`, `AddonManager`, `KodiRepoParser` (addons.xml + índice HTML) e persistência em Room |
| Addons de manifesto (estilo Stremio) | `core/addons/stremio/`: lê `manifest.json` por HTTP/JSON, **escopo declarado por catálogo** (Filmes/Séries/Canais) e vínculo manual por aba persistido; tela em **Addons → Addons Stremio**. Sem torrentes, sem scraping — ver [`docs/ADDONS-STREMIO.md`](docs/ADDONS-STREMIO.md) |
| Repositório de addons padrão | **Vikings** (`vikingskoditeam.github.io`), com addons BR/PT |
| Elementum (torrentes) | Addon `plugin.video.elementum` do Vikings reconhecido como provedor de **Filmes/Séries**; o app conversa com o **daemon local** (`127.0.0.1:65220`) em vez de tentar rodar o plugin Python — busca, pastas e reprodução pelo player |
| Canais / Filmes / Séries | Abas dedicadas com sidebar de categorias, contagem por grupo e EPG "agora" nos canais |
| Indexador + manutenção de links | `Indexer` (varredura paralela, saúde por fonte, probe de links, lista de bloqueio) |
| Atualização remota via nuvem | `SourceRepository.syncFromCloud()` lê catálogo JSON remoto e faz merge |
| Metadados TMDB | `TmdbClient`: nota, sinopse, gêneros, elenco, temporadas/episódios |
| Trailer YouTube sem anúncios | `TrailerFinder` + `YouTubePlayer` (youtube-nocookie, bloqueio de hosts de anúncio) |
| Trocar / Gerenciar Repositório | Tela dedicada, grava em SharedPreferences **e** Room |
| Paisagem travada | `android:screenOrientation="sensorLandscape"` |

---

## 🚀 Build

```bash
# requer JDK 17+ e Android SDK 35
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease   # requer signing config (ver abaixo)
```

### Chave do TMDB (opcional, mas recomendado)

```bash
# ~/.gradle/gradle.properties
TMDB_API_KEY=suachavev3
TMDB_LANGUAGE=pt-BR
```

Sem chave o app funciona; apenas as fichas ficam sem nota/elenco/trailer do TMDB.

### Assinatura do release (nunca commite)

```properties
# ~/.gradle/gradle.properties
RELEASE_STORE_FILE=/caminho/keystore.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

---

## ⚙️ Compilação automática (GitHub Actions)

O workflow [`.github/workflows/build.yml`](.github/workflows/build.yml) compila o
APK de release na nuvem. Você não precisa de Android SDK na sua máquina.

**Dispara em:** todo push (qualquer branch), tag `v*`, pull request para `main`
e execução manual em *Actions → Build & Release APK → Run workflow*.

**O que acontece:**

1. **Núcleo** — roda os linters e as 67 asserções dos parsers. Se falhar, o
   build caro nem começa.
2. **APK** — `./gradlew :app:assembleRelease` com JDK 17 + Android SDK 35,
   cache do Gradle entre execuções.
3. **Verificação** — `aapt2 dump badging` (pacote, SDKs) e `apksigner verify`
   quando há keystore.
4. **Publicação** — APK + `SHA256SUMS.txt` numa Release do repositório.

### De onde vem o APK

| Disparo | Tag da Release | Observação |
|---|---|---|
| push em branch | `latest-build` | release rolante, **sobrescrita** a cada push |
| tag `v1.2.3` | `v1.2.3` | versão nomeada, marcada como *latest* |
| pull request | — | só artefato, não publica |
| manual | conforme acima | campo `publish` permite desligar |

O `versionCode` usa o **número do run** do Actions, então é sempre crescente e o
Android aceita atualizar por cima (`adb install -r`). O `versionName` numa branch
é `1.0.0.<run>`; numa tag é o próprio número da tag.

### Assinatura: já vem configurada

O APK do CI **já sai assinado com chave estável**, sem você configurar nada:
`signing/release-keystore.json.enc` guarda um keystore PKCS12 cifrado
(AES-256-GCM, chave derivada por PBKDF2) e o workflow o decifra no runner.
Detalhes e como trocar a chave em [`signing/README.md`](signing/README.md).

Isso significa que toda atualização pode ser instalada por cima da anterior
(`adb install -r`).

> ⚠️ A passphrase está no repositório de propósito (ele é público): o objetivo é
> **estabilidade da assinatura**, não sigilo. Instale APKs apenas das Releases
> oficiais deste repositório.

### Secrets opcionais

Em *Settings → Secrets and variables → Actions*:

| Secret | Para quê |
|---|---|
| `TMDB_API_KEY` | habilita notas, elenco e trailers na ficha |
| `KEYSTORE_BASE64` | usar um keystore **próprio** em vez do versionado (base64) |
| `KEYSTORE_PASSWORD` | senha desse keystore |
| `KEY_ALIAS` | alias da chave |
| `KEY_PASSWORD` | senha da chave |

Se os quatro secrets de keystore existirem, eles têm prioridade sobre o arquivo
versionado.

### Baixar o APK

- Release rolante: <https://github.com/msmacrosmart-cpu/SmartED-TV/releases/tag/latest-build>
- Versões estáveis: <https://github.com/msmacrosmart-cpu/SmartED-TV/releases>

### Build local

```bash
./gradlew :app:assembleDebug        # debug
./gradlew :app:assembleRelease      # release (sem assinatura se não houver keystore)
./gradlew :app:assembleRelease -Pminify=false   # sem R8, para diagnosticar
```

---

## 🧱 Arquitetura

```
com.smarted.tv
├── addons/
│   └── brazuca/            BrazucaPlayAddon (nativo) + BrazucaCatalog
├── core/
│   ├── addons/             Addon, AddonManifest, AddonManager, KodiRepoParser
│   ├── data/               SettingsStore (SharedPreferences), SourceRepository, AssetReader
│   ├── data/db/            Room: repositórios, addons, favoritos, progresso, cache, EPG
│   ├── epg/                EpgService (fallback XMLTV em cascata)
│   ├── indexer/            Indexer (varredura + manutenção de links)
│   ├── model/              MediaItem, ItemDetails, EpgProgram, WatchProgress…
│   ├── net/                Http (OkHttp único, cache 96MB, retry)
│   ├── tmdb/               TmdbClient (match pt-BR, elenco, temporadas, trailers)
│   ├── trailer/            TrailerFinder (busca sem API key)
│   └── util/               TextUtils, M3uParser, XmltvParser, SourceDetector
└── ui/
    ├── components/         SideRail, cards, hero, controles, interações
    ├── screens/            Home, Catálogo, Detalhes, Player, Guia, Busca, Ajustes…
    ├── state/              ViewModels + contratos (AppServices)
    └── theme/              Tokens portados da referência (EdTheme, Focus)
```

### Fluxo de boot

1. `SettingsStore` lê a URL do repositório ativo de **SharedPreferences** (leitura síncrona, sem rede).
2. `AddonManager.bootstrap()` garante repositório padrão + addon nativo no Room.
3. `SourceRepository` faz seed do catálogo de fontes embutido (`assets/catalog/sources.json`).
4. `BrazucaCatalog.hydrate()` restaura o último snapshot → **home pronta em milissegundos**.
5. Em background: sync do repositório, download/parse das fontes, EPG e varredura do indexador.

---

## 🖼️ Prévia da interface (sem emulador)

`docs/preview/` traz uma maquete HTML autocontida das 9 telas principais com
navegação por D-pad, troca de acento e splash:

```bash
cd docs/preview && python3 -m http.server 8099
```

Ela serve para revisar design e fluxo; o app real é Compose.

---

## 🧪 Verificação (sem Android SDK)

Como o núcleo (parsers, addons, dados, view-models) não depende do Android, ele é
compilado e testado de verdade com o compilador Kotlin:

```bash
npm ci                           # web-tree-sitter + tree-sitter-wasms
npm i kotlin-compiler            # ou tenha `kotlinc` no PATH

./tools/verify-core.sh           # 297 asserções reais (EPG 12h, cruzamento de canais, separação de acervo, séries, navegador de repositório)
./tools/verify-logic.sh          # typecheck de 35 arquivos (rede, TMDB, EPG, DB, VMs)
node tools/kt-check.mjs app/src  # sintaxe de todos os .kt
python3 tools/check-imports.py   # imports de Compose por arquivo
python3 tools/check-compose.py   # lint de padrões Compose
python3 tools/check-symbols.py   # símbolo usado → import presente
python3 tools/check-workflow.py  # consistência do próprio CI
python3 tools/check-ptbr.py      # interface 100% pt-BR + R.string existentes
python3 tools/check-db-schema.py # Room: versão x baseline + SQL da migração executado no SQLite
```

Todos esses passos rodam no CI antes do build do APK. Depois do APK, o job
**Emulador** sobe um Android 11 de verdade, instala o app, confere que ele
abre e que um banco v1 antigo migra para v2 sem crash — o cenário exato do bug
de abertura que a versão anterior tinha.

O acompanhamento de tudo (pedidos, correções e pendências) fica em
[`docs/TASKS.md`](docs/TASKS.md).

---

## 📄 Fontes de conteúdo

O app **não hospeda mídia** e, nesta versão, **não aceita listas M3U avulsas**.
O modelo é o do Kodi: repositórios de addons.

### Repositório padrão: Vikings

`https://vikingskoditeam.github.io` já vem cadastrado e ativo
(`SettingsStore.DEFAULT_REPO_URL`). O app lê o índice do repositório
(`addons.xml` ou a listagem de diretórios) e classifica cada addon de conteúdo
em três pastas:

| Pasta | Aba do app | Regra |
| --- | --- | --- |
| TV ao Vivo | Canais | aceita apenas transmissão (HLS/TS/RTMP) e mostra EPG |
| Filmes | Filmes | aceita apenas arquivo estático (MP4, MKV…) |
| Séries | Séries | idem Filmes, com estrutura de temporada/episódio |

**Addons → Explorar** abre o navegador: primeiro as pastas, depois os itens com
pôster, título e metadados. Em qualquer nível dá para conectar/desconectar a
pasta da aba correspondente (`AddonManager.bindFolder` /
`unbindFolder`).

### Por que o addon embutido entrega os streams

Addons de repositório Kodi são plugins **Python** e não executam no Android.
O repositório entrega a estrutura e os metadados; os streams vêm do addon
embutido do app (`plugin.video.smarted`, sempre instalado). Itens de addon que
declaram link direto de vídeo viram cards reproduzíveis; os demais aparecem no
navegador marcados como **catálogo**.

### Elementum (torrentes)

O repositório Vikings publica o **Elementum** (`plugin.video.elementum`). Ele não
é um catálogo: é um cliente de torrentes com **daemon nativo**. No Android esse
daemon é um serviço que responde em `127.0.0.1:65220`, e é com ele que o
SmartED TV conversa — plugin Python continua não executando no app.

O daemon fala o mesmo dialeto do Kodi: respostas são uma *view* com `items[]`
(`label`, `path`, `is_playable`). O app pede sempre `list=1`, que é o que faz o
daemon devolver a lista em vez de abrir o seletor de arquivos do Kodi.

| Passo | O que acontece |
| --- | --- |
| **Addons → Elementum → Abrir** | o app garante o addon na lista, consulta `/status` e, se o daemon estiver no ar, mostra os destaques |
| Destaques | `/movies/popular` + `/shows/popular` → pastas **Filmes** e **Séries** |
| Digitar um título e **Buscar** | `GET /search?list=1&q=…`; a resposta é lida por `Elementum.parseItems` |
| Assistir | `/play?uri=<magnet>` responde `302` para `/files/…`, e o player toca essa URL |

Se o daemon estiver desligado, a tela diz exatamente isso em vez de dar erro: o
cartão em **Addons** mostra o estado real (`ligado`/`desligado`), o botão
**Verificar** refaz a consulta e a busca fica disponível para quando ele subir.
O vínculo entre pasta e aba continua valendo: um item do Elementum só entra em
Filmes/Séries se o link for um arquivo estático ou o `/play` de um serviço em
loopback (`AddonBrowser.isLocalPlayerLink`) — magnet cru é recusado.

### Navegação em árvore e “Minha lista”

O navegador anda **como no Kodi**: o daemon devolve pastas, o usuário entra em
“Lançamentos”, “Ação”, “Aventura”… e chega no título. Cada nível é uma resposta
do serviço (`/movies/genre/acao`) e o botão **Voltar pasta** sobe um nível.

No item, dois gestos:

| Botão | O que faz |
| --- | --- |
| **Assistir** | manda o `/play?uri=<magnet>` para o player em tela cheia |
| **Salvar** | abre a escolha **Canais / Filmes / Séries** e guarda o item na aba escolhida, com a pasta de origem |

A escolha da aba é a autoridade: é o usuário quem diz onde o conteúdo entra —
o tipo detectado no título apenas pré-seleciona o botão. A aba **Minha lista**
(em Addons) mostra as três abas com os itens agrupados pela pasta de onde
vieram (“Lançamentos 4”, “Ação 3”…), e é a playlist própria do usuário: nada é
adicionado sozinho, e remover é um botão.

### Filtro de português

Com **Somente conteúdo em português** ligado (padrão), todo item passa por
`LanguageFilter`, que aceita o conteúdo por evidência decrescente:

1. `tvg-language` / `tvg-country` explícitos no M3U;
2. `group-title` nacional (Brasil, Nacional, Dublado…);
3. marcadores no título (`[BR]`, `(Dublado)`, `PT-BR`…);
4. palavras funcionais do português no título, desempatando contra espanhol.

O rodapé da Home mostra quantos itens foram descartados pelo filtro.

### EPG

A grade vem de XMLTV e é sempre exibida com **no mínimo 12 horas contínuas**:
`EpgTimeline.pad` preenche buracos com blocos "Sem informação", então a régua de
horários nunca quebra. Fontes de fallback gratuitas ficam em
`app/src/assets/catalog/epg.json` e são registradas automaticamente.
