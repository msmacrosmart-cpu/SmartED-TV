#!/usr/bin/env python3
"""Cifra um keystore PKCS12 para ser usado pelo CI (ver signing/README.md).

Uso:  python3 signing/seal.py caminho/release.jks
"""
import base64
import hashlib
import json
import os
import sys

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

PASSPHRASE = "smarted-tv-ci-signing-v1"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "release-keystore.json.enc")


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    source = sys.argv[1]
    iterations = 200_000
    salt = os.urandom(16)
    key = hashlib.pbkdf2_hmac("sha256", PASSPHRASE.encode(), salt, iterations, 32)

    nonce = os.urandom(12)
    ciphertext = AESGCM(key).encrypt(nonce, open(source, "rb").read(), None)

    blob = {
        "v": 1,
        "kdf": {"algo": "pbkdf2-sha256", "iterations": iterations,
                "salt": base64.b64encode(salt).decode()},
        "cipher": {"algo": "aes-256-gcm", "nonce": base64.b64encode(nonce).decode()},
        "store_type": "PKCS12",
        "key_alias": "smarted",
        "keystore_b64": base64.b64encode(ciphertext).decode(),
    }
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(blob, handle, indent=2)
    print(f"gravado {OUT} ({os.path.getsize(OUT)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
