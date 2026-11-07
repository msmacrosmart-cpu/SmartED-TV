# Prévia visual da interface

Esta pasta **não** faz parte do APK. É uma maquete HTML autocontida para revisar
design, hierarquia e navegação por D-pad sem precisar de emulador Android.

```bash
cd docs/preview && python3 -m http.server 8099 --bind 0.0.0.0
# abra http://localhost:8099
```

- `index.html` — telas Início, Canais, Filmes, Séries, Guia, Busca, Addons,
  Ajustes e Detalhes, com foco navegável por setas (mesma lógica do
  `edInteraction`/`FocusRequester` do app), troca de acento persistida em
  `localStorage` e splash animado.
- `art/` — pôsteres, backdrops, logos de canal e avatares gerados
  procedimentalmente (sem material de terceiros), para que a maquete não
  dependa da rede.

As cores, raios, espaçamentos e tempos de animação vêm dos mesmos tokens do
`EdTheme.kt` (fundo `#0B0F14`, acento fúcsia `#EC92E5`, raio de card 14dp,
anel de foco 3dp, lift 1.045).
