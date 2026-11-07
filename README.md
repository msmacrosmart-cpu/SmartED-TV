# SmartED TV

Cliente nativo **Android TV** em Kotlin + Jetpack Compose para addons no protocolo **Stremio** sobre HTTP/JSON.
Interface em **português do Brasil**, dark glassmorphism fumê e foco branco para controle remoto.

> Não há catálogo fixo, lista IPTV embutida, Kodi/Elementum ou execução de plugins. O conteúdo vem dos manifestos que você instalar.

## Telas

- **Inicial:** data/hora, navegação lateral esquerda (Canais, Filmes, Séries) e Configurações na borda inferior.
- **Canais:** 3 colunas — categorias/favoritos, canais com logo e EPG, player com próximos programas.
- **Filmes/Séries:** 2 colunas — categorias/favoritos e grade vertical infinita; busca isolada por aba; A–Z, últimos adicionados, mais votados e ordem crescente/decrescente.
- **Detalhes:** hero + sinopse; séries com temporadas e episódios.
- **Configurações:** organização no modelo Stremio, com até **3 manifestos** e escolha explícita de escopo por addon.

Detalhes do pedido e das referências: [`docs/DESIGN.md`](docs/DESIGN.md).

## Build

Requisitos: JDK 17, Android SDK 35, Gradle wrapper 8.9.

```bash
./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

CI em [`.github/workflows/build.yml`](.github/workflows/build.yml): compilação → testes JVM → APK debug → lint → release → testes Android TV → APK assinado.
Publicação de release é manual: `workflow_dispatch` com `publish=true`.

### Assinatura

Configure `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
ou os secrets equivalentes no GitHub. Sem chave, o release não é assinado; o APK debug continua disponível para teste.

## Limites do protocolo

- “Mais votados”: o protocolo não fornece contagem universal de votos; a UI usa nota IMDb dos títulos carregados e informa a aproximação.
- EPG e logos dependem do addon; quando ausentes, o app mostra estado vazio sem inventar programação.
- Torrent/P2P e DRM não são executados; links externos só abrem após confirmação.
