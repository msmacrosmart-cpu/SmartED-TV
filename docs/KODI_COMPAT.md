# Compatibilidade com o ecossistema Kodi

Inventário honesto do que o SmartED-TV suporta hoje e do que **não** é
suportado. Nada aqui é simulado: cada linha corresponde a implementação real
no código (spec §8 — "documentar incompatibilidades").

## Suportado (implementado e testado)

| Capacidade | Estado | Onde |
| --- | --- | --- |
| Índice de repositório em JSON (formato próprio) | ✅ instalação, navegação e sincronização | `RepoPackages`, `IptvContentRepository` |
| Instalação de pacote ZIP por URL | ✅ download + validação (deve conter `.json`) | `RepoPackages.download` |
| Navegação do conteúdo do ZIP (Gestor de Arquivos) | ✅ lista real de pastas/arquivos | `RepoPackages.entries` |
| Instalação de sub-índices com escolha de tipo | ✅ Vídeo/PVR/Rádio/Programas/… gravados no índice | `RepoBrowserDialog` |
| Reconhecimento de `addon.xml` | ✅ **somente metadados**: id, nome, versão, tipo (extension point), dependências | `RepoPackages.parseAddonXml` |
| Grupos oficiais por tipo de add-on | ✅ Vídeo, Programas, Música, Interface, Serviços, Metadados, Legendas, Repositório, PVR, Imagens | `addonGroupLabel` |
| Segurança de extração | ✅ Zip Slip/Path Traversal bloqueado; nomes saneados; URLs restritas a http(s)/content/file | `RepoPackages.isSafeEntryPath`, `requireValidUrl` |
| Playlists | ✅ M3U/M3U8 + painéis Xtream | `M3uParser`, `SourceImporters` |
| Streaming | ✅ HLS e MPEG-DASH (Media3/ExoPlayer 1.5), HTTP(S) | `libs.versions.toml` |
| Reprodução de arquivos locais/URLs de vídeo comuns | ✅ delegada ao Media3 (MP4, MKV, WebM, TS conforme o dispositivo) | player do app |

## Não suportado (deliberadamente — não execute)

| Capacidade | Motivo |
| --- | --- |
| **Runtime de add-ons Kodi (Python)** | O app **não executa código de fontes desconhecidas** (spec §7). `addon.xml` é lido apenas como metadados; nenhum script/plugin é carregado. |
| **API/skin/ serviçs Kodi completos** | Sem camada de compatibilidade por adaptadores ainda — não presumimos compatibilidade automática (spec §5). |
| **Pacotes TAR, GZ, 7Z, RAR** | Sem biblioteca disponível no projeto; apenas ZIP (spec §3 — "conforme bibliotecas disponíveis"). |
| **Playlists PLS, XSPF, ASX, WPL** | Parser apenas de M3U/M3U8 por enquanto. |
| **Metadados NFO/STRM/XML de pastas** | Ainda não interpretados. |
| **DRM** | Nunca contornado (spec §7). |
| **Gerenciador de dependências automático** | Dependências de `addon.xml` são EXIBIDAS, nunca baixadas/executadas automaticamente. |
| **Credenciais em logs** | Proibido por política; senhas nunca são impressas. |

## Regras de segurança vigentes

1. Extração de ZIP: caminhos com `..`, absoluto (`/`) ou barra invertida são rejeitados (`isSafeEntryPath`).
2. URLs de fonte: apenas `http://`, `https://`, `content://`, `file://` (`requireValidUrl`).
3. Nenhum código de terceiros é avaliado — nem XML, nem scripts.
4. Add-on sem tipo declarado **não é instalado** até o usuário escolher a categoria (estilo Kodi).

## Próximos incrementos (spec §2 e §6)

- Descoberta de fontes por manifestos além do JSON atual.
- Identificação de formatos de arquivo (extensões de vídeo/áudio).
- Navegador com busca, ordenação, favoritos e histórico.
- Adaptadores de compatibilidade por capacidade real (em vez de presumir API Kodi).
