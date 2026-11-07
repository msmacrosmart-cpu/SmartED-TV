# CI — Build & Release APK

`.github/workflows/build.yml` compila o app na nuvem a cada push, sem exigir
Android SDK na sua máquina.

## Fluxo

```
push / tag / PR / manual
        │
        ├─ job "Núcleo"  (rápido, ~40s)
        │    linters: kt-check · refs · imports · compose · symbols · workflow
        │    testes:  67 asserções dos parsers (kotlinc real)
        │    typecheck de 28 arquivos do núcleo
        │
        └─ job "APK de release"  (~4min)
             JDK 17 + Android SDK do runner
             ./gradlew :app:assembleRelease (-PversionName -PversionCode)
             aapt2 dump badging · apksigner verify · unzip -l (assets)
             Release no GitHub + SHA256SUMS.txt
```

## Versão

| Disparo | versionName | versionCode | Release |
|---|---|---|---|
| push em branch | `1.0.0.<run>` | `<run>` | rolante `latest-build` (sobrescrita) |
| tag `v1.2.3` | `1.2.3` | `<run>` | `v1.2.3`, marcada como latest |
| pull request | — | — | só artefato |

O `versionCode` é o número da execução: sempre cresce, então o Android aceita
atualizar por cima (`adb install -r`).

## Assinatura

Estável por padrão: `signing/release-keystore.json.enc` (PKCS12 com AES-256-GCM).
Secrets `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` têm
prioridade, para quem quiser chave própria. Ver [`signing/README.md`](../signing/README.md).

## Quando falha

O job publica os erros do Kotlin como **anotações** (`e: arquivo:linha ...`), visíveis
na aba *Checks* e via API. O log completo do Gradle também sobe como artefato
`gradle-build-log` e o resumo fica no *Summary* do run.

Para diagnosticar problemas de R8/minificação, rode manualmente com
`minify: false` (Actions → Build & Release APK → Run workflow).

## Validação local

```bash
python3 tools/check-workflow.py   # outputs, needs, sintaxe dos run:, versões de actions
```
