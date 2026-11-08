# Assinatura de release do CI

O APK do GitHub Actions é assinado com um keystore **fixo e versionado** neste
diretório, para que as versões possam ser instaladas por cima umas das outras
(`adb install -r`). Um APK assinado com o debug keystore muda de chave a cada
runner e trava atualizações.

## Arquivos

| Arquivo | O que é |
|---|---|
| `release-keystore.json.enc` | keystore PKCS12 cifrado com AES-256-GCM |

## Parâmetros (documentados de propósito)

```
KDF        PBKDF2-HMAC-SHA256, 200.000 iterações, salt de 16 bytes (no JSON)
Cifra      AES-256-GCM, nonce de 12 bytes (no JSON)
Passphrase smarted-tv-ci-signing-v1
Alias      smarted
Formato    PKCS12
```

A passphrase está aqui porque o repositório é público e o objetivo é apenas
**estabilidade da assinatura**, não sigilo. Quem tem acesso ao repositório pode
assinar um APK que se passa pelo SmartED TV — trate as releases apenas das
origens oficiais.

## Trocar a chave

```bash
keytool -genkeypair -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias smarted -storetype PKCS12
python3 signing/seal.py release.jks      # regrava release-keystore.json.enc
```

⚠️ Trocar a chave impede atualizações por cima de instalações anteriores: será
preciso desinstalar (perdendo favoritos e progresso). Só faça isso se a chave
vazar.
