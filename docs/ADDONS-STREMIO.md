# Addons de manifesto (estilo Stremio)

O SmartED TV tem **duas arquiteturas de addon**, que coexistem:

| Arquitetura | Como o addon se descreve | Onde vive |
|---|---|---|
| **Kodi** (já existente) | `addons.xml` do repositório + pasta de cada addon | `core/addons/AddonManager.kt`, `KodiRepo.kt`, `AddonBrowser.kt` |
| **Stremio** (esta camada) | `manifest.json` em uma URL HTTP/JSON | `core/addons/stremio/` |

---

## 1. Contrato do protocolo

Um addon Stremio é **um servidor HTTP com JSON**. O app só faz duas leituras:

```
GET {base}/manifest.json
GET {base}/catalog/{type}/{catalogId}.json          -> { "metas": [ … ] }
GET {base}/catalog/{type}/{catalogId}/skip=100.json  (quando o catálogo pagina)
GET {base}/catalog/{type}/{catalogId}/search=foo.json (quando aceita busca)
```

O manifesto declara os catálogos:

```json
{
  "id": "org.exemplo.catalogo",
  "version": "1.2.0",
  "name": "Catálogo Exemplo",
  "description": "Filmes e séries em pt-BR",
  "logo": "https://exemplo.org/logo.png",
  "types": ["movie", "series"],
  "catalogs": [
    { "type": "movie",  "id": "lancamentos", "name": "Lançamentos",
      "extra": [{ "name": "skip" }, { "name": "genre", "options": ["Ação", "Drama"] }] },
    { "type": "series", "id": "novelas",     "name": "Novelas" },
    { "type": "channel","id": "abertos",     "name": "Canais abertos" }
  ]
}
```

### Escopo

O `type` de cada catálogo é o **escopo** dele (`AddonScope`):

| `type` do manifesto | Escopo | Aba do app |
|---|---|---|
| `movie`, `vod`, `filme` | `MOVIE` | Filmes |
| `series`, `tvshow`, `anime` | `SERIES` | Séries |
| `channel`, `live`, `tv` | `LIVE` | Canais |

`StremioScopePolicy` garante que o vínculo nunca é impossível (um catálogo de
filmes não entra na aba Canais), mas **quem decide é o usuário**: cada catálogo
tem um `CatalogBinding` que ele liga/desliga.

---

## 2. Arquivos

| Arquivo | Responsabilidade |
|---|---|
| `StremioManifest.kt` | Modelos (`StremioManifest`, `StremioCatalog`, `StremioExtra`, `StremioPreview`, `StremioAddonSource`, `CatalogBinding`), `AddonScope`, `StremioParser` (JSON → modelos, sem `@Serializable` — o núcleo é verificado com stubs no CI), `StremioUrls` (normalização + montagem de URL) e `StremioScopePolicy`. |
| `StremioClient.kt` | `manifest(url)` valida e devolve o addon instalável; `catalog(addon, catalog, skip, search)` lê uma página e converte para `MediaItem`. Usa o `Http` único do app (cache de 48h). |
| `StremioRegistry.kt` | Estado observável (`StremioState`), instalação, remoção, vínculo por aba e persistência em `SharedPreferences` (texto original do manifesto + linhas de vínculo). |
| `ui/screens/StremioScreen.kt` | Tela: cola a URL, vê os catálogos declarados e vincula cada um à sua aba. Chega por **Addons → Addons Stremio**. |

### Conversão para o modelo do app

`StremioPreview.toMediaItem(addonId)`:

| Stremio | `MediaItem` |
|---|---|
| `id` | `id` (+ `addonId` = id do addon) |
| `type` | `kind` (`movie`→`MOVIE`, `series`→`SERIES`, `channel`→`LIVE`) |
| `name` | `title` |
| `poster` / `background` | `poster` / `backdrop` |
| `description` | `plot` |
| `releaseInfo` (`2013–2019`) | `year` (`2013`) |
| `imdbRating` (`"7.8"`) | `rating` (`7.8`) |
| `runtime` (`128 min`, `2h 08min`) | `durationSec` |
| `genres` | `genres` (base da classificação indicativa em `MediaMeta`) |

---

## 3. Persistência

Tudo em `SharedPreferences("smarted_stremio_addons")`, sem rede no start:

```
addons.urls            -> uma URL base por linha
addons.manifest.<url>  -> texto original do manifest.json
addons.bindings        -> linhas "url \t catalogId \t type \t scope \t addon \t catálogo"
```

O manifesto é re-parseado na leitura; se um addon mudou de formato, ele é
descartado silenciosamente (a tela continua funcionando).

---

## 4. Como consumir na Home (próximo passo)

`StremioState.shelvesFor(kind)` já devolve, na ordem dos vínculos, os pares
`(addon, catálogo)` de uma aba, e `StremioRegistry.items(binding, skip, search)`
devolve `MediaItem`s prontos — o mesmo modelo usado pelo palco e pelos cartões.

O encaixe natural é um `StremioAddon : Addon` (a interface do núcleo Kodi) que
implementa:

```kotlin
override suspend fun browse(request: BrowseRequest): CatalogPage {
    val shelves = registry.state.value.shelvesFor(request.kind)
    val items = shelves.flatMap { (addon, catalog) ->
        client.catalog(addon, catalog, skip = (request.page - 1) * PAGE)
    }
    return CatalogPage(items = items, hasMore = true, page = request.page)
}
```

Com isso os catálogos vinculados entram automaticamente em Canais/Filmes/Séries
e nas trilhas da Home, sem nenhuma tela nova.

---

## 5. O que esta camada **não** faz

- Não procura torrentes, não faz scraping e não executa código de addon.
- Não instala addons que não publiquem catálogo no manifesto.
- Não vincula nada sem confirmação explícita do usuário (a instalação vincula
  por padrão ao escopo declarado; a tela permite desligar).
