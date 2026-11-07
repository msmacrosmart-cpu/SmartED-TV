#!/usr/bin/env python3
"""Guardas de arquitetura do SmartED TV (pivô 3.0: listas/repositórios locais + Elementum/Xtream).

Estas checagens NÃO substituem compilação/testes; elas travam regressões de identidade e
de camadas que o compilador não vê: foco D-pad sempre branco (nunca magenta/rosa), nenhum
vestigínio do Stremio, UI sem acesso direto a rede/banco e manifest TV correto.
"""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main'
errors = []

MAGENTA_HEXES = (
    '0xffe91e63', '0xffec407a', '0xfff06292', '0xffff00ff', '0xffff48d0',
    '0xffff69b4', '0xffff80ab', '0xffd500f9', '0xffaa00ff', '0xffe040fb', '0xffad1457',
)

# 1) Identidade: foco branco e proibição absoluta de magenta/rosa no código de UI.
ui_root = source / 'java/com/smarted/tv/ui'
theme = (ui_root / 'theme/Theme.kt').read_text()
if 'FocusRingColor' not in theme or not re.search(r'val\s+FocusRingColor\s*=\s*Color\.White|Color\.White\s*//.*focus', theme, re.I):
    if 'Color(0xFFFFFFFF.toLong())' not in theme and 'Color.White' not in theme:
        errors.append('Theme.kt: anel de foco deve ser branco puro (Color.White/#FFFFFF)')
if 'fun Modifier.whiteFocus' not in theme:
    errors.append('Theme.kt: whiteFocus(...) precisa existir como único modificador de foco')
if re.search(r'\bColor\.Magenta\b|Fuchsia', theme):
    errors.append('Theme.kt: cor magenta/fuchsia proibida')

for path in list(ui_root.rglob('*.kt')) + list((source / 'res').rglob('*.xml')):
    text = path.read_text().lower()
    for color in MAGENTA_HEXES:
        if color in text:
            errors.append(f'{path.relative_to(root)}: cor magenta/rosa proibida ({color})')
    if re.search(r'\bmagenta\b', text):
        errors.append(f'{path.relative_to(root)}: a palavra "magenta" é proibida em código de UI/recursos')

# Toda tela deve compor foco apenas via helpers do tema (whiteFocus / componentes).
for path in (ui_root / 'screens').glob('*.kt'):
    text = path.read_text()
    for match in re.finditer(r'\.border\([^\n]*', text):
        line = match.group()
        if 'Color.White' not in line and 'focus' not in line.lower():
            continue
        if 'Color.White' not in line:
            errors.append(f'{path.relative_to(root)}: borda de foco sem Color.White')
    if re.search(r'android\.net\.|okhttp3|IptvStore\(|CatalogRepository\(', text):
        errors.append(f'{path.relative_to(root)}: tela não pode tocar rede/banco diretamente — use o ViewModel')

# 2) Pivô de plataforma: nada de Stremio/addons no código-fonte do app.
for path in source.rglob('*.kt'):
    text = path.read_text()
    if re.search(r'\bstremio\b|AddonUrls|streaming\.url|kodi\.repository\.smarted', text, re.I):
        errors.append(f'{path.relative_to(root)}: vestígio de Stremio/addon proibido após o pivô 3.0')

# 3) Arquitetura por camadas: core puro (sem Android), data/elementum/xtream presentes.
core = source / 'java/com/smarted/tv/core'
for path in core.rglob('*.kt'):
    text = path.read_text()
    if re.search(r'^\s*import\s+(android|androidx)\.', text, re.M):
        errors.append(f'{path.relative_to(root)}: core precisa permanecer JVM puro (sem imports android.*)')
required = {
    'core/parse/M3uParser.kt': r'object M3uParser',
    'core/parse/XmltvParser.kt': r'object XmltvParser',
    'core/epg/EpgMatcher.kt': r'object EpgMatcher',
    'core/sources/FavKeys.kt': r'object FavKeys',
    'core/repo/RepoIndex.kt': r'fun computeRepoDiff|data class RepoIndex',
    'data/elementum/ElementumClient.kt': r'class ElementumClient',
    'data/import/SourceImporters.kt': r'class XtreamClient|object XtreamMapper',
    'data/import/RepoJson.kt': r'object RepoJson',
    'data/db/IptvStore.kt': r'class IptvStore',
    'ui/theme/Theme.kt': r'smokedGlass',
}
for rel, pattern in required.items():
    file = source / 'java/com/smarted/tv' / rel
    if not file.exists() or not re.search(pattern, file.read_text()):
        errors.append(f'arquitetura esperada ausente: {rel} ({pattern})')

# 4) Manifest Android TV + queries para o bridge do Kodi (Elementum roda lá).
manifest = (source / 'AndroidManifest.xml').read_text()
if 'android.software.leanback' not in manifest or 'LEANBACK_LAUNCHER' not in manifest:
    errors.append('manifest: feature/launcher Android TV ausentes')
if 'org.xbmc.kodi' not in manifest:
    errors.append('manifest: <queries> org.xbmc.kodi ausente (bridge Elementum→Kodi)')

# 5) Versão do pivô.
gradle = (root / 'app/build.gradle.kts').read_text()
if re.search(r'versionName\s*=.*"2\.', gradle):
    errors.append('app/build.gradle.kts: versionName padrão precisa refletir a 3.x (pivô)')

if errors:
    print('\n'.join(dict.fromkeys(errors)))
    sys.exit(1)
print('Architecture guard OK: foco branco, sem magenta, sem Stremio, camadas core/data/ui corretas.')
