#!/usr/bin/env python3
"""Small architectural guards. These are NOT a substitute for Kotlin compilation/tests."""
from pathlib import Path
import re, sys
root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main'
errors = []
for path in source.rglob('*'):
    if not path.is_file() or path.suffix not in {'.kt','.xml','.json'}: continue
    text = path.read_text()
    if re.search(r'\b(Kodi|Elementum|Vikings|Brazuca|libVLC|VlcEngine|Xtream|M3uParser)\b', text, re.I):
        errors.append(f'Legacy dependency: {path.relative_to(root)}')
    if path.suffix == '.kt' and re.search(r'Color\(0x[Ff][Ff](?:[Ee][Cc]|[Ff][0-9]|[Ee][0-9]).*[0-9]', text):
        # Do not infer colors with a brittle regex; explicit focus modifier is checked below.
        pass
for path in (source/'java').rglob('*.kt'):
    text = path.read_text()
    for match in re.finditer(r'\.border\([^\n]*', text):
        if 'focused' in match.group() and 'TvColors.Focus' not in match.group() and 'Color.White' not in match.group():
            errors.append(f'Non-white focus: {path.relative_to(root)}')
if (source/'assets/catalog').exists(): errors.append('Fixed catalog assets are forbidden')
manifest = (source/'AndroidManifest.xml').read_text()
if 'android.software.leanback' not in manifest or 'LEANBACK_LAUNCHER' not in manifest:
    errors.append('Android TV launcher/feature missing')
if errors:
    print('\n'.join(errors)); sys.exit(1)
print('Architecture guard passed; compilation and tests run separately.')
