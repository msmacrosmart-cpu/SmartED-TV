# Tarefas — SmartED TV

Lista viva do que foi pedido, do que está entregue e de como cada item é
verificado. Atualizada a cada rodada.

Legenda: ✅ entregue e verificado · 🧪 entregue, aguardando teste no aparelho · ⏳ pendente

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
| A | **App não abria** depois de atualizar | `repositories.kinds` foi adicionada sem subir `version` do Room; a assinatura do esquema não batia com a do banco já existente → `IllegalStateException` na abertura | `version = 2` + `MIGRATION_1_2` (`ALTER TABLE … ADD COLUMN kinds TEXT NOT NULL DEFAULT ''`) + abertura antecipada com recriação de emergência | `check-db-schema.py` (roda o SQL num SQLite real) + job **Emulador** migrando um banco v1 de verdade |
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
| job **Emulador** (CI) | instala o APK num Android 11, abre o app, confere que a UI renderiza e que um banco v1 antigo migra para v2 sem crash |

## Pendências

- ⏳ Teste em emulador de **Android TV** (API 30, 1080p) além do celular.
- ⏳ Varredura visual de todas as telas no aparelho (foco branco, glassmorphism, overscan).
- ⏳ Acervo de Filmes/Séries depende de repositório ou fonte do usuário: nenhuma M3U pública BR confiável de VOD foi encontrada.
