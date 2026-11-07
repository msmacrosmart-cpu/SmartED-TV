#!/usr/bin/env python3
"""Expose actionable CI errors even when raw log blob downloads are unavailable."""
from pathlib import Path
import os, re, sys
text = Path(sys.argv[1]).read_text(errors='replace') if Path(sys.argv[1]).exists() else ''
lines = text.splitlines()
errors = [line for line in lines if re.search(r'^[ew]: |^w:|FAILED|FAILURE:|Caused by:|What went wrong|> Task .*FAILED|Error:|error:|Unresolved reference', line)]
selected = (errors[:35] or lines[-30:])
for line in selected:
    clean = line.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print('::error title=SmartED validation::' + clean[:1800])
summary = '\n'.join(selected + ['\n--- Final log lines ---'] + lines[-50:])
if 'GITHUB_STEP_SUMMARY' in os.environ:
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as out:
        out.write('## Validation diagnostics\n\n```text\n' + summary[:55000] + '\n```\n')
print(summary[-16000:])
