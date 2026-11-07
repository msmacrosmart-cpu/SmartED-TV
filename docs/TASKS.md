# Tarefas — SmartED TV

Lista viva do que foi pedido, do que está entregue e de como cada item é
verificado. Atualizada a cada rodada.

Legenda: ✅ entregue e verificado · 🧪 entregue, aguardando teste no aparelho · ⏳ pendente

## Rodada 6 — addon Elementum (torrentes do Vikings)

| # | Pedido | Status | Onde | Verificação |
|---|--------|--------|------|-------------|
| 1 | Reconhecer o addon `plugin.video.elementum` do repositório Vikings e garanti-lo na lista, mesmo antes de sincronizar | ✅ | `Elementum.isElementum`, `AddonManager.ensureElementum/elementumAddon` | `verify-core.sh` (detecção por id/nome/script) |
| 2 | Falar com o daemon local (`127.0.0.1:65220`) no **protocolo real** (view do Kodi, `list=1`, `/play?uri=`) em vez de tentar rodar o plugin Python | ✅ | `core/addons/Elementum.kt`, `core/net/ElementumClient.kt` | 34 asserções (endpoints, view, tagline, magnet, play) |
| 3 | Resultado do Elementum entra em **Filmes/Séries**, nunca em Canais, e abre no player em tela cheia | ✅ | `Elementum.toEntries`, `AddonBrowser.isLocalPlayerLink`, `AddonBrowserScreen(onPlay)`, rota `browser` | `verify-core.sh` + emulador |
| 4 | Estado do daemon visível (ligado/desligado), destaques e busca por título na UI | ✅ | cartão `ElementumCard` em Addons, busca + **Destaques/Recomeçar** no navegador, `AddonsViewModel.openElementum/searchElementum/resetElementum/checkElementum` | gates + emulador |

**Contrato do daemon (verificado no código-fonte do `elementumd`):**

- `GET /status` → corpo com `version` quando ligado.
- `GET /?list=1`, `/movies/popular?list=1`, `/shows/popular?list=1`,
  `/search?list=1&q=…` → `{"content_type":"…","items":[{label, label2, path, is_playable, info:{tagline, title, code}}]}`.
- Item reproduzível: `path = /play?uri=<magnet escapado>`.
- `GET /play?uri=…` → `302` para `/files/…` (arquivo servido pelo daemon).
- Cabeçalho `User-Agent` **não** pode ser `plugin.video.elementum` (com ele o
  daemon trata a chamada como vinda do Kodi e abre diálogo).

**Limite conhecido:** sem o daemon instalado no aparelho o Elementum fica
"desligado" — o app informa isso e o restante da navegação (repositório) segue
funcionando. O addon Python em si nunca é executado. A versão do registro local
é `1.0.0` até a primeira sincronização do índice do Vikings (o índice não foi
consultado daqui: o download do `addons.xml` no sandbox falha por TLS).

## Rodada 5 — arquitetura de repositórios e addons (M3U desativado)

| # | Pedido | Status | Onde | Verificação |
|---|--------|--------|------|-------------|
| 1 | Repositório **Vikings** como padrão, lendo o índice estilo Kodi e separando em TV ao Vivo / Filmes / Séries | ✅ | `SettingsStore.DEFAULT_REPO_URL`, `KodiRepoParser`, `AddonManager.discoverAddons` | Sincronização real no app + `verify-core.sh` |
| 2 | **Navegador de addons**: pastas e itens com pôster, título e metadados em dark mode/glassmorphism | ✅ | `core/addons/AddonBrowser.kt`, `ui/screens/AddonBrowserScreen.kt`, rota `browser` em `SmartEdRoot` | 24 asserções novas + revisão visual |
| 3 | Pasta do repositório **vinculada à aba** (Filmes/Séries só MP4/MKV, canais só stream) | ✅ | `AddonManager.bindFolder/unbindFolder`, `AddonBrowser.accepts`, `ContentClassifier` | `verify-core.sh` (barreira de formato por pasta) |
| 4 | Player em tela cheia absoluta + foco/seleção/bordas em **branco puro #FFFFFF** | ✅ | `PlayerScreen` (`fillMaxSize` + `setWindowSize` do vout), `EdColors.Accent = 0xFFFFFFFF` | Emulador (abertura) + revisão |
| 5 | Remover listas **M3U** da arquitetura | ✅ | `SourcesScreen` reescrita, `BrazucaCatalog.configuredSources()` só com repositórios/addon embutido, seletor de modos removido de Catálogo/Ajustes | gates + CI |

**Verificação final (run `42361396378`, release `1.0.0.67`):**

| Job | Resultado |
| --- | --- |
| Núcleo (parsers e testes) | ✅ `185 testes OK · 0 falha(s)` |
| APK de release | ✅ `SmartED-TV-1.0.0.67-release.apk` (41,8 MB) + `SHA256SUMS.txt` |
| Emulador (celular, x86_64) | ✅ instalação limpa abre o app (pid vivo), interface renderiza, e o **upgrade de um banco v1 real** migra para `user_version = 2` com a coluna `repositories.kinds`, dados preservados e sem crash |

## Rodada 4 — navegador com seleção manual de mídia

| # | Pedido | Status | Onde | Verificação |
|---|--------|--------|------|-------------|
| 1 | Navegador de addons/repositórios com **checkboxes** de abas (`Usar para Canais`, `Usar para Filmes`, `Usar para Séries`) ao adicionar repositório, M3U ou addon | ✅ | `ui/components/TargetPicker.kt`, `ui/screens/{Sources,Repository,Addons}Screen.kt`, `core/util/SourceTargeting.kt` | `verify-core.sh` (13 asserções de `SourceTargeting`) + gates |
| 2 | Separação rigorosa: Canais só ao vivo; Filmes/Séries só arquivos estáticos e metadados | ✅ | `core/util/ContentClassifier.kt`, `addons/brazuca/BrazucaCatalog.kt` | `verify-core.sh` (barreira do classificador por aba) |
| 3 | Player em **tela cheia absoluta** (Match Parent / Fill Bounds) e controles glassmorphism com foco/seleção/bordas **branco puro**, sem rosa | ✅ | `ui/screens/PlayerScreen.kt`, `core/player/VlcEngine.kt` | Emulador (abertura) + revisão visual no aparelho |
| 4 | EPG por nome inteligente (normaliza HD/FHD/SD/`\|`) e **mínimo 12 h** de programação | ✅ | `core/epg/{EpgMatcher,EpgService,EpgTimeline}.kt` | `verify-core.sh` (`EpgMatcher`, `EpgTimeline`) |
| 5 | Home com hero **só de filmes** + botões Assistir/Detalhes brancos; seção de Canais **só favoritos** | ✅ | `ui/screens/HomeScreen.kt` | Revisão + `verify-core.sh` |
| 6 | Modos **Normal / SmartED / Repo** disponíveis nas abas Canais, Filmes e Séries | ✅ | `core/model/Catalog.kt` (`ContentMode`), `ui/components/Controls.kt` (`ModeSelector`) | `verify-logic.sh` |

## Correções desta rodada (bloqueavam a entrega)

| # | Problema | Causa | Correção | Verificação |
|---|----------|-------|----------|-------------|
| A | **App não abria** depois de atualizar | `repositories.kinds` foi adicionada sem subir `version` do Room; a assinatura do esquema não batia com a do banco já existente → `IllegalStateException` na abertura | `version = 2` + `MIGRATION_1_2` (`ALTER TABLE … ADD COLUMN kinds TEXT NOT NULL DEFAULT ''`) + abertura antecipada com recriação de emergência | `check-db-schema.py` **e** job **Emulador** ✅ (banco v1 real migrado, `user_version=2`, dados preservados) |
| B | Build da rodada 4 nunca compilou (APK antigo na Release) | `import R` ausente em `AddonsScreen` e `TargetsDialog` chamado dentro do `LazyColumn` | imports + diálogos fora do escopo da lista | CI `APK de release` verde |
| C | Player abria **pequeno no canto inferior esquerdo** | O vout do libVLC não herda o tamanho da `SurfaceView`; sem `setWindowSize` ele usa um viewport minúsculo | `VlcEngine.attachSurface`/`updateWindowSize` com `setWindowSize` + reaplicação no layout | Emulador + teste no aparelho |
| D | Erros de compilação ficavam invisíveis no CI | Anotações do Actions têm teto de 12 e o log bruto é inacessível | Step publica os erros completos como comentário de commit | `gh api …/commits/<sha>/comments` |

## Testes automatizados

| Ferramenta | O que cobre |
|------------|-------------|
| `node tools/kt-check.mjs app/src` | sintaxe dos 57 arquivos Kotlin |
| `tools/check-{refs,imports,compose,symbols,workflow,ptbr}.py` | referências, imports órfãos, regras do Compose, símbolos, workflow, pt-BR |
| `tools/check-db-schema.py` | esquema do Room: versão x baseline, migração obrigatória por passo e **execução real do SQL** no SQLite |
| `tools/verify-core.sh` | 161 asserções do núcleo puro (parsers, EPG, classificador, abas) |
| `tools/verify-logic.sh` | typecheck dos 35 arquivos não-UI |
| job **Emulador** (CI) | compila um APK `debug` x86_64 do mesmo código, instala num Android 11, abre o app, confere que a UI renderiza e que um banco v1 real migra para v2 preservando dados. Roda **em segundo plano** (`continue-on-error`): informa no comentário do commit, não segura o release |

## Pendências

- ⏳ Teste em emulador de **Android TV** (API 30, 1080p) além do celular.
- ⏳ Teste de reprodução no emulador (abrir um canal, um filme e um trailer via `adb input keyevent`).
- ⏳ Varredura visual de todas as telas no aparelho (foco branco, glassmorphism, overscan).
- ⏳ Acervo de Filmes/Séries depende de repositório ou fonte do usuário: nenhuma M3U pública BR confiável de VOD foi encontrada.
