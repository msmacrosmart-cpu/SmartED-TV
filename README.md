# SmartED TV

Media center modular para **Android TV** e **mobile**, escrito 100% em **Kotlin + Jetpack Compose**,
com arquitetura de **addons no estilo Kodi** e o **Brazuca Play** integrado nativamente.

> Orientação travada em paisagem (`sensorLandscape`), interface D-pad-first com suporte a toque.

---

## ✨ O que está implementado

| Requisito | Implementação |
|---|---|
| Clonagem visual da referência (Extreme-InfiniTV) | Tokens OKLCH convertidos 1:1 (fundo slate `#0B0F14`, acento fúcsia `#EC92E5`, 12 paletas), rail lateral de 76dp, hero full-bleed, grid de pôsteres 2:3, anel de foco de 3dp e lift de 4% |
| Arquitetura de addons estilo Kodi | `Addon`, `AddonManifest`, `AddonManager`, `KodiRepoParser` (addons.xml + índice HTML) e persistência em Room |
| Brazuca Play por padrão | `BrazucaPlayAddon` nativo + `BrazucaCatalog` com snapshot em memória/disco; repositório `https://vkodi.net/repo` carregado no boot |
| Canais / Filmes / Séries | Abas dedicadas com sidebar de categorias, contagem por grupo e EPG "agora" nos canais |
| EPG com fallback XMLTV | `EpgService` em cascata (addon → fontes configuradas → `https://epg.best/brazil.xml`), parser XMLTV por streaming |
| Indexador + manutenção de links | `Indexer` (varredura paralela, saúde por fonte, probe de links, lista de bloqueio) |
| Atualização remota via nuvem | `SourceRepository.syncFromCloud()` lê catálogo JSON remoto e faz merge |
| Metadados TMDB | `TmdbClient`: nota, sinopse, gêneros, elenco em faixa horizontal contínua, temporadas/episódios |
| Trailer YouTube sem anúncios | `TrailerFinder` + `YouTubePlayer` (youtube-nocookie, bloqueio de hosts de anúncio, player nativo) |
| Trocar / Gerenciar Repositório | Tela dedicada, grava em SharedPreferences **e** Room; app abre pronto com o repositório ativo |
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

### Secrets (opcionais, recomendados)

Em *Settings → Secrets and variables → Actions*:

| Secret | Para quê |
|---|---|
| `KEYSTORE_BASE64` | keystore de release em base64 — **sem isso o APK usa o debug keystore** e não pode ser atualizado por cima de uma versão assinada antes |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | alias da chave |
| `KEY_PASSWORD` | senha da chave |
| `TMDB_API_KEY` | habilita notas, elenco e trailers na ficha |

Gerar o keystore e o base64:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias smarted -storetype JKS
base64 -w0 release.jks > release.jks.b64   # cole o conteúdo em KEYSTORE_BASE64
```

> ⚠️ Guarde o `release.jks` e as senhas. Perder a chave impede atualizar o app
> instalado — só é possível desinstalar e reinstalar (perdendo os dados).

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

./tools/verify-core.sh           # 67 asserções reais (M3U, XMLTV, addons.xml, textos)
./tools/verify-logic.sh          # typecheck de 28 arquivos (rede, TMDB, EPG, DB, VMs)
python3 tools/kt-check.mjs       # sintaxe de todos os .kt
python3 tools/check-imports.py   # imports de Compose por arquivo
python3 tools/check-compose.py   # lint de padrões Compose
python3 tools/check-symbols.py   # símbolo usado → import presente
```

Todos esses passos rodam no CI antes do build do APK.

---

## 📄 Fontes de conteúdo

O app **não hospeda mídia**. Ele indexa fontes públicas e o repositório configurado
pelo usuário. O catálogo embutido usa listas públicas (iptv-org) e o EPG
`epg.best/brazil.xml` como fallback. Respeite os termos de cada provedor.
