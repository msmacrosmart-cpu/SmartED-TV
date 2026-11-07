# SmartED TV

Cliente nativo **Android TV** em Kotlin + Jetpack Compose para **listas IPTV locais e repositórios de fontes** (modelo Kodi/Elementum/OnePlay), com **EPG sincronizado** e VOD completo (filmes e séries). Interface em **português do Brasil**, dark glassmorphism fumê e **foco branco puro** para D-pad — nunca magenta/rosa.

> Arquitetura 3.0 (pivô): o app **não** executa torrent nem plugins. Ele mantém um repositório local (SQLite) alimentado por suas fontes e integra-se ao **daemon HTTP do Elementum** instalado no Kodi para catálogos torrent/repositórios, com auto-update.

## Telas

- **Inicial:** data/hora no topo, navegação lateral esquerda (Início, Canais, Filmes, Séries) e Configurações perto da borda inferior; linhas de favoritos, continuar assistindo e últimas entradas.
- **Canais:** 3 colunas — categorias/favoritos (com busca de categorias), canais com logo e programa atual/próximo, pré-visualização + grade de EPG futuros. A busca de conteúdo filtra **somente canais**.
- **Filmes/Séries:** 2 colunas — categorias/favoritos + busca de categorias no topo e grade infinita; ordenações De A a Z, Últimos adicionados, Mais votados, Ano e Crescente/Decrescente. Tocar num título troca a busca pelo **hero** (sinopse + metadados); séries têm seletor de temporada/episódio.
- **Configurações:** menu lateral com Fontes (M3U, Xtream, repositórios JSON com auto-update), Elementum (host/porta/teste/reload), Reprodução (áudio/legendas/saltos), Interface, Sistema (limpezas e reindexação de EPG) e Sobre.

## Fontes de dados

| Fonte | O que faz |
|---|---|
| **M3U/M3U8** | Playlist com EXTINF/tvg-id/headers; grupos FILMES/SÉRIES viram catálogo VOD automaticamente. |
| **Xtream Codes** | `player_api.php`: categorias, streams, `get_vod_info`, `get_series_info`, `get_short_epg` (fallback now/next quando não há XMLTV). |
| **Repositório JSON** | Índice estilo OnePlay `{"name":…, "sources":[…]}`: cria fontes filhas e mantém sincronizado (diff add/update/remove). |
| **EPG XMLTV** | Janela deslizante (default 24 h) vinculada por nome real do canal: IDs, nomes de exibição, igualdade normalizada, cobertura de tokens — com auto-reparo do vínculo anterior; nunca inventa programação. |
| **Elementum (Kodi)** | HTTP local (`:65064`): status via `/torrents/list`, busca, links, `/reload` para atualização automática; play via intent do Kodi quando não há URL HTTP direta. |

## Build

Requisitos: JDK 17, Android SDK 35, Gradle wrapper 8.9.

```bash
./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
python3 tools/check_architecture.py   # gate: foco branco, sem magenta, camadas core/data/ui
java -jar <selftest-core>             # tools/corecheck → "CORE SELFTEST OK (52 checks)"
```

CI em [`.github/workflows/build.yml`](.github/workflows/build.yml): gate de arquitetura → compilação → testes JVM → APK debug → lint → release → testes Android TV (emulador) → APK assinado. Publicação de release é manual (`workflow_dispatch` com `publish=true`).

### Assinatura

Configure `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` ou os secrets equivalentes no GitHub. Sem chave, o release não é assinado; o APK debug continua disponível para teste.

## Limites honestos

- “Mais votados” usa a nota informada pela fonte (rating Xtream/IMDb quando existe); sem nota, a ordenação cai para o nome e a UI avisa.
- EPG nunca é fabricado: sem XMLTV nem `get_short_epg`, o painel mostra estado vazio e a reindexação em Configurações → Sistema.
- Torrent/P2P e DRM não são executados pelo app; o conteúdo do Elementum abre via daemon/intent do Kodi.

Detalhes do pedido e decisões: [`docs/DESIGN.md`](docs/DESIGN.md).
