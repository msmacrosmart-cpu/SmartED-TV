# SmartED TV — especificação visual e funcional (BR-PT) — arquitetura 3.0

Documento do pivô: o app **deixou de ser um cliente de addons Stremio** e passou a ser o
repositório local definitivo (estilo Kodi SmartED + OnePlay), com Elementum como camada de
torrent/VOD via daemon HTTP do Kodi. As referências abaixo são modelos visuais/organizacionais,
não fontes de catálogo.

## Referências

| Referência | O que foi aproveitado | O que não foi importado |
|---|---|---|
| [TNT-Likely/xplayer](https://github.com/TNT-Likely/xplayer) | Cartões escuros com logo/arte, metadados, favoritos e busca | Lógica M3U crua, player Flutter |
| [HTV](https://github.com/HTWMedia/HTV) | Menu de categorias, lista com agora/próximo e pré-visualização | Listas fixas embutidas |
| [ultra-tv](https://github.com/vantaflow/ultra-tv) | Tela inicial com data/hora e navegação lateral | Xtream/perfis/Trakt |
| Stremio (UI) | Organização das Configurações em seções | Servidor de streaming, addons, conta |
| Elementum (Kodi) | Modelo de repositório: fontes, auto-update, daemon HTTP | Execução de torrent dentro do app |
| OnePlay | Índice JSON de repositório com diff de fontes filhas | — |

## Identidade

- Fundo fumê (`#0B0C0E`→gradientes), painéis `smokedGlass`, tipografia Material3 em pt-BR.
- **Foco/seleção por D-pad sempre branco puro `#FFFFFF`** (`whiteFocus`); proibido magenta/rosa em código, recursos e temas (gate `tools/check_architecture.py`).

## Telas

### Inicial
Data/hora no canto superior direito; rail lateral Início/Canais/Filmes/Séries + Configurações ao fim. Cartões: canais favoritos, continuar assistindo (progresso salvo por `fav_key`), últimos adicionados e mais votados.

### Canais (3 colunas — estritas)
1. Categorias + Favoritos, com **busca de categorias** própria.
2. Canais: número, logo, nome, programa atual com barra de progresso e próximo; coração por linha; paginação 80/80.
3. Pré-visualização mudada (toggle em Interface), ação **Assistir** (tela cheia) e grade de próximos programas do canal selecionado.

A busca de conteúdo da aba filtra **somente canais** (nome/grupo), nunca categorias do painel 1. O EPG vem do XMLTV da fonte casado por nome real (tiers do matcher) e, em painéis Xtream sem XMLTV, de `get_short_epg` — este último só em memória (nunca persistido como se fosse guia completo).

### Filmes/Séries (2 colunas)
Esquerda: busca de categorias + Favoritos + chips de categoria (com contagem). Direita: grade infinita com A–Z/Últimos/Mais votados/Ano e Crescente/Decrescente (colunas SQL whitelist + tiebreak por nome). Tocar num título: hero full-bleed (backdrop, pôster, sinopse, metadados, Favoritar, Assistir) substituindo a barra de busca; séries carregam temporadas/episódios (`get_series_info` no Xtream; Episódios ficam no banco).

### Player
media3/ExoPlayer nativo: HLS/DASH/progressivo, headers por stream, legendas (idioma preferido, tamanho), saltos ←/→ configuráveis, OK play/pause, Voltar fecha; progresso persistido a cada 10 s e no dispose; “Próximo episódio” é ação confirmada (sem autoplay silencioso).

### Configurações
- **Fontes:** adicionar M3U/Xtream/repositório, status por linha, Atualizar, Só EPG, pausar, intervalo de auto-update, URL de EPG, remover (cascata limpa canais/VOD/EPG da fonte).
- **Elementum:** host/porta, teste de conexão (health = `GET /torrents/list`), reload de provedores (`GET /reload`), contagem de torrents ativos.
- **Reprodução:** idiomas, legendas, retomar, saltos, tamanho de legenda.
- **Interface:** pré-visualização, janela do EPG (3–72 h), auto-update global.
- **Sistema:** atualizar tudo, reindexar EPG nome a nome, limpar índices, apagar histórico, restaurar preferências, contagens do repositório.

## Camadas

```
core/    JVM puro: parse M3U/XMLTV, normalizador, matcher EPG, timeline, RepoIndex/diff, FavKeys, StreamRules
data/    SQLite (IptvStore), importadores (M3U/Xtream/repo), ElementumClient, SettingsStore, SourceManager, CatalogRepository
ui/      Compose TV: AppViewModel + VMs por aba, componentes fumê, telas
```

O `core` não importa `android.*` (verificado no gate). A UI nunca toca rede/banco: só ViewModels.

## Validação

- `allWarningsAsErrors=true`; compilação debug+androidTest.
- Testes JVM: parsers, matcher/timeline, diffs de repositório, queries VOD, FavKeys, mappers Xtream e cliente Elementum (MockWebServer).
- Gate de arquitetura: foco branco obrigatório, cores proibidas, ausência de vestígios do antigo modo addons, manifest TV + `<queries> org.xbmc.kodi`.
- Instrumentados no emulador Android TV: adicionar fonte pela UI → EPG visível → busca só-canais → hero de filmes → player pronto sem erro (MP4 local do MockWebServer).
- Selftest local do core (`tools/corecheck`) para iteração rápida fora do Android.

## Limites honestos

- Sem contagem universal de votos: “Mais votados” usa a nota da fonte e a UI informa a aproximação.
- EPG ausente nunca é inventado; painéis sem XMLTV recebem apenas now/next volátil.
- Torrent não roda no app: integração é o daemon do Elementum (Kodi) via HTTP + intent de play.
