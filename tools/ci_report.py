#!/usr/bin/env python3
"""Expose actionable CI errors even when raw log blob downloads are unavailable."""
from pathlib import Path
import os, re, sys
text = Path(sys.argv[1]).read_text(errors='replace') if Path(sys.argv[1]).exists() else ''
lines = text.splitlines()
errors = [line for line in lines if re.search(r'^[ew]: |^w:|FAILED|FAILURE:|Caused by:|What went wrong|> Task .*FAILED|Error:|error:|Unresolved reference', line)]
failures = []
for i, line in enumerate(lines):
    if ' FAILED' in line or 'AssertionError' in line or 'ComparisonFailure' in line:
        failures.extend(lines[i:min(i + 9, len(lines))])
selected = ((failures + errors)[:60] or lines[-30:])
for line in selected:
    clean = line.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print('::error title=SmartED validation::' + clean[:1800])
summary = '\n'.join(selected + ['\n--- Final log lines ---'] + lines[-50:])
if 'GITHUB_STEP_SUMMARY' in os.environ:
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as out:
        out.write('## Validation diagnostics\n\n```text\n' + summary[:55000] + '\n```\n')
print(summary[-16000:])
