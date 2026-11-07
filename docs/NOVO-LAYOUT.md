# Layout cinematográfico (palco + trilhas + doca)

Este documento fixa a **nova estrutura visual** do SmartED TV e as regras que
qualquer tela nova deve seguir. Ele substitui o layout anterior (rail sólida de
76dp + grid de pôsteres 2:3 + hero em cartão arredondado).

Prévia estática do resultado: [`docs/preview/index.html`](preview/index.html).

---

## 1. Princípios

| Regra | Como é aplicada |
|---|---|
| **Dark glassmorphism** | Fundo `#07080B`; todo painel é uma camada translúcida (`glass`, `glassStrong`, `glassDark`) com contorno de 1dp branco a 10–20%. Nada de sombra colorida. |
| **Branco puro é o único acento** | `EdColors.Accent = #FFFFFF`. Anéis de foco, botão primário, barra de progresso, ponto de "ao vivo" e cabeçalho de seção ativa usam `#FFFFFF`. Não existe matiz de acento em nenhuma tela. |
| **Full-bleed** | A arte do palco sangra até as bordas (inclusive **atrás** da doca). Só o texto respeita o recuo. |
| **Metadados antes da reprodução** | Título, classificação indicativa, ano, duração, gêneros, sinopse e nota aparecem no palco e no cartão, sem precisar entrar na ficha. |
| **D-pad primeiro** | Todo elemento clicável nasce de `edInteraction()` (interactionSource + clickable sem indicação). O estado de foco vem dessa fonte, nunca de `onFocusChanged` solto. |
| **Zero anúncios** | Não existe SDK, placeholder ou "espaço reservado" de anúncio no código. |

---

## 2. Estrutura da Home

```
┌──────────────────────────────────────────────────────────────────┐
│  PALCO (StageHero) — 68% da altura, arte full-bleed               │
│  véus: topo / esquerda / base                                     │
│                                        [Atualizado hoje, 2:13]   │
│                                                                   │
│  • FILME EM DESTAQUE                                              │
│  O Último Horizonte                                               │
│  16 · 2013 · 2h30 · Ação, Suspense          ★ 8.2                 │
│  sinopse (3 linhas, 56% da largura)                               │
│  [ ▶ Assistir ]  [ ⓘ Detalhes ]                                   │
├──────────────────────────────────────────────────────────────────┤
│  Em destaque para você                                            │
│  Top 10 em alta                             mais votados  Ver tudo│
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐                        │
│  │ 01 │ │ 02 │ │ 03 │ │ 04 │ │ 05 │ │ 06 │  cartões 16:10        │
│  └────┘ └────┘ └────┘ └────┘ └────┘ └────┘                        │
│  Continuar assistindo … (com barra de progresso branca)           │
│  Explorar por gênero … (pílulas)                                  │
└──────────────────────────────────────────────────────────────────┘
   ▲ doca flutuante (96dp) desenhada POR CIMA de tudo
```

### Componentes

| Componente | Arquivo | Papel |
|---|---|---|
| `SideRail` (doca) | `ui/components/SideRail.kt` | Coluna de 96dp sobre um véu em degradê. Item = ícone + rótulo 8.5sp em caixa alta. Selecionado: vidro 8% + ponto branco. Focado: vidro 14% + anel branco de 2dp. Em telas de 720p os rótulos somem e a doca entra em modo compacto. |
| `StageHero` | `ui/components/Stage.kt` | Palco. Backdrop rotativo (crossfade de 750ms + Ken Burns de 3%), três véus, selo de atualização, título, metadados, sinopse e as duas ações. `StageAction(primary = true)` é a pílula branca; o foco desenha um anel branco **separado por 3dp**, então o destaque aparece mesmo sobre um botão branco. |
| `FeatureCard` | `ui/components/FeatureCard.kt` | Cartão 236×148 (16:10) com a arte, véu inferior, **título e metadados dentro do cartão**, selo de posição (`01`…`10`), barra de progresso branca e anel de foco + `scale(1.05)`. |
| `LiveChannelCard` | `ui/components/FeatureCard.kt` | Mesmo vocabulário para canais: logo centralizado, selo AO VIVO, nome e "agora" dentro da arte. |
| `ShelfHeader` / `GenrePill` | `ui/components/Stage.kt`, `FeatureCard.kt` | Cabeçalhos de trilha (21sp para o bloco "Em destaque", 15sp para as trilhas) e pílulas de gênero. |
| `GlassPanel` / `GlassPill` | `ui/components/PlayerGlass.kt` | HUD do player: painéis de vidro escuro flutuantes + pílulas brancas. |

### Métricas

`EdDimens` centraliza as medidas novas:

```kotlin
dockWidth = 96.dp     // largura da doca
dockInset = 84.dp     // recuo do conteúdo sob a doca
stageMin  = 300.dp    // altura mínima do palco
stageMax  = 620.dp    // altura máxima (o palco ocupa 68% da tela)
```

O conteúdo da Home começa em `dockInset + 20dp`; o **palco não** — ele começa em
`x = 0`. É essa diferença que cria a sensação de "cinema" do layout.

---

## 3. Navegação por D-pad

- A doca é a **primeira** parada do foco (`navFocus`); a Home entrega o foco
  inicial ao botão "Assistir" do palco (`heroFocusRequester`).
- Cada trilha é um `LazyRow`: ◀ ▶ percorrem os cartões e o foco rola a lista
  automaticamente.
- Atalhos numéricos continuam valendo: `1` Início, `2` Canais, `3` Filmes,
  `4` Séries, `5` Guia, `6` Buscar, `7` Addons, `8` Ajustes.
- `VOLTAR` fecha, nesta ordem: trailer → player → ficha → sub-tela → volta para
  a Home.
- O anel de foco é sempre `2dp` (cartões) ou `3dp` (ações do palco), branco
  puro, com `EdEasing` (`cubic-bezier(0.16, 1, 0.3, 1)`).

---

## 4. Player em tela cheia absoluta

- `PlayerScreen` é desenhado **fora** da casca com overscan e doca, numa camada
  com `zIndex(10)` ocupando `fillMaxSize()`.
- Vídeo em `MATCH_PARENT`/`Fill Bounds`: `SurfaceView` do libVLC recebe
  `setWindowSize` no `onLayoutChange` (sem isso o vout desenha num viewport
  minúsculo) e o `PlayerView` do ExoPlayer usa `RESIZE_MODE_FILL`.
- Modo imersivo reaplicado a cada abertura (Fire TV e boxes chineses trazem as
  barras de volta quando a janela ganha foco).
- O HUD são **painéis flutuantes** (`GlassPanel`) no topo e na base, com véu
  leve nas bordas; todos os controles são `GlassPill` — texto e ícones branco
  puro, foco com anel branco. O play/pause é a pílula branca.

---

## 5. Checklist para telas novas

1. Fundo `palette.bg`; painéis com `glass`/`surface` + contorno `glassBorder`.
2. Nenhuma cor literal `Color(0xFF…)` fora de `ui/theme` (o gate
   `tools/check-compose.py` reprova).
3. Ícones sempre via `EdIcons` (nada de `Icons.Outlined.*` direto).
4. Foco sempre via `edInteraction`; anel branco puro, nunca colorido.
5. Texto em `strings.xml` (gate `tools/check-ptbr.py`).
6. Cartões de mídia: `FeatureCard` (trilha) ou `PosterCard` (grade densa).
7. Medidas da doca: `Ed.dimen.dockWidth` / `Ed.dimen.dockInset`.
