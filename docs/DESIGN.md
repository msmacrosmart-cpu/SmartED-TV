# SmartED TV — especificação visual e funcional (BR-PT)

Este documento registra o pedido detalhado e como ele foi traduzido para o app nativo Android TV.
As referências abaixo são usadas como **modelo visual/organizacional**, não como fonte de catálogos, listas IPTV, plugins ou código de reprodução.

## Referências

| Referência | O que foi aproveitado | O que não foi importado |
|---|---|---|
| [TNT-Likely/xplayer](https://github.com/TNT-Likely/xplayer) | Cartões escuros com logo/arte, metadados, favoritos e busca; adaptação para vidro fumê | Fontes IPTV embutidas, lógica M3U, player Flutter |
| [HTWMedia/HTV](https://github.com/HTWMedia/HTV) | Menu de canais por categorias, favoritos, lista com programa atual/próximo e pré-visualização | Listas fixas, sources/EPG externos do app |
| [vantaflow/ultra-tv](https://github.com/vantaflow/ultra-tv) | Desenho da tela inicial com data/hora e navegação lateral | Xtream/M3U, perfis, Trakt, sincronização e catálogo do app |
| [Stremio](https://github.com/Stremio/stremio-web) | Organização das configurações: Geral, Interface, Player, Streaming, Atalhos; gestão de addons | Conta, servidor de streaming/torrent, addons oficiais embutidos |

## Telas

### Inicial

- Data e hora no canto superior direito, em `pt-BR`.
- Cartões **Canais**, **Filmes** e **Séries** centralizados verticalmente no lado esquerdo.
- **Configurações** perto da borda inferior esquerda.
- Mesmo vidro fumê dos cartões de conteúdo; foco branco puro para D-pad.

### Canais

Três colunas:

1. Categorias + Favoritos, com busca de categorias no topo.
2. Canais com logo, programa atual e próximo programa (EPG do addon).
3. Player/pré-visualização + programação abaixo, com ação de favoritar e tela cheia.

A busca de conteúdo pesquisa somente canais. A paginação continuaverticalmente e para quando o addon repetir a página ou encerrar o catálogo.

### Filmes e Séries

Duas colunas:

1. Categorias + Favoritos, com busca de categorias.
2. Grade vertical com carregamento infinito de cima para baixo.

Busca de conteúdo isolada por aba. Ordenações expostas:

- De A a Z;
- Últimos adicionados;
- Mais votados;
- Ano;
- Crescente/Decrescente.

**Limite honesto do protocolo:** o manifesto Stremio não padroniza contagem de votos nem ordenação global do servidor. “Mais votados” usa a nota IMDb dos títulos carregados como aproximação, e a UI informa isso. “Últimos adicionados” usa data de lançamento/ordem do addon quando não há timestamp de adição.

### Detalhes

Ao abrir um título, a busca de conteúdo desaparece e dá lugar a hero + sinopse, metadados, favorito e assistir.
Séries mostram todas as temporadas (incluindo especiais quando `season=0`) e episódios da temporada selecionada.

### Configurações

Menu lateral no modelo Stremio, com gestão de addons junto das configurações:

- **Addons:** até 3 manifestos; URL HTTP/JSON; escolha explícita de Canais, Filmes ou Séries; validar, adicionar, atualizar, alterar escopo e remover.
- **Geral:** idioma BR-PT, armazenamento local, apagar histórico, restaurar preferências.
- **Interface:** tema fumê, foco branco, layout por aba.
- **Player:** áudio/legendas, tamanho de legenda, retomar reprodução, próximo episódio, saltos.
- **Streaming:** compatibilidade HTTP/HLS/DASH e limites de torrent/DRM.
- **Atalhos:** D-pad, OK, Voltar e busca.
- **Sobre:** versão e referências.

Não há controle falso de conta Stremio ou servidor de torrent: o app é um cliente nativo de addons HTTP.

## Validação

- Kotlin com `allWarningsAsErrors=true`.
- Testes JVM de protocolo, paginação, escopo, favoritos, preferências e reprodução.
- Lint Android com relatório texto/XML.
- Testes instrumentados Android TV para D-pad, busca por escopo, detalhes, temporadas, canais e configurações.
- APK debug publicado como artefato para teste direto; APK assinado final só após as etapas de qualidade.
