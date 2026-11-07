# Prévia visual da interface

Esta pasta **não** faz parte do APK. É uma maquete HTML autocontida para revisar
design, hierarquia e navegação por D-pad sem precisar de emulador Android.

```bash
cd docs/preview && python3 -m http.server 8099 --bind 0.0.0.0
# abra http://localhost:8099
```

- `index.html` — o **novo layout** da Home: palco cinematográfico full-bleed
  (backdrop real, três véus, metadados e ações em branco puro), doca flutuante
  de 96dp com ícone + rótulo, trilhas de cartões 16:10 com o título e os
  metadados dentro da arte, selo de posição do Top 10, barra de progresso
  branca e pílulas de gênero. O cartão 1 e o botão "Assistir" aparecem no
  estado de foco do D-pad (anel branco puro).
- `legacy-layout.html` — maquete do layout anterior (rail sólida de 76dp, acento
  fúcsia), mantida apenas como histórico de comparação.
- `art/` — backdrops, pôsteres, logos de canal e avatares gerados
  procedimentalmente (sem material de terceiros), para que a maquete não
  dependa da rede.

As cores, raios, espaçamentos e tempos de animação vêm dos mesmos tokens do
`EdTheme.kt` (`#07080B` de fundo, branco puro `#FFFFFF` em foco/seleção/anel,
cartão de 236×148 com raio 14dp, anel de foco de 2dp, lift de 5%, doca de 96dp).
