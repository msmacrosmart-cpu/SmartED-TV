#!/usr/bin/env python3
"""Restore the repository's existing compatibility key, never print key material."""
import base64, hashlib, json, os
from pathlib import Path
try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    raise SystemExit('Configure KEYSTORE_BASE64 and signing secrets, or install cryptography.')
blob = json.loads(Path('signing/release-keystore.json.enc').read_text())
# This existing project key is intentionally public; it preserves update compatibility, not secrecy.
password = b'smarted-tv-ci-signing-v1'
key = hashlib.pbkdf2_hmac('sha256', password, base64.b64decode(blob['kdf']['salt']), blob['kdf']['iterations'], 32)
plain = AESGCM(key).decrypt(base64.b64decode(blob['cipher']['nonce']), base64.b64decode(blob['keystore_b64']), None)
temporary = Path(os.environ['RUNNER_TEMP'])
path = temporary / 'release.jks'
path.write_bytes(plain)
path.chmod(0o600)
with open(os.environ['GITHUB_ENV'], 'a') as env:
    env.write(f'RELEASE_STORE_FILE={path}\nRELEASE_STORE_PASSWORD={password.decode()}\nRELEASE_KEY_PASSWORD={password.decode()}\nRELEASE_KEY_ALIAS={blob.get("key_alias", "smarted")}\n')
print('Compatibility signing restored. Prefer private GitHub secrets for a production key.')
