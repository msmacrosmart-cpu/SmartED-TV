# Prompt 07 — Reprodutor/emplação do Kodi na aba Navegar (PLANO B — só se “use o prompt 07”)

> Salvo de mensagem do usuário (2026-11-09). Usar APENAS quando ele disser
> “use o prompt 07”. Enquanto isso, vale apenas a mudança anterior (menu Kodi
> com ícones sincronizados).

---

Antes de continuar de onde parou...

Se não tiver sucesso com isso, trabalhando dessa forma.. quero que adicione um
reprodutor de app android na barra navegar, esse reprodutor, deve reproduzir o
app original do kodi. Mas você deve trabalhar bem a otimização do aplicativo,
pra que ele não trave, como trava no meu celular o app kodi.

Agora de continuidade, se não quiser fazer e quiser testar da primeira forma,
me mande o release com a modificação do pedido anterior, e me avise aqui que se
não conseguirmos reproduzir as opções kodi direto no app, podemos então emular
o kodi no nosso app.

---

## Contexto de apoio para o uso futuro (não é pedido em vigor)

- Materiais: release IPTV-Native v1.0.0 (`files.zip`, `Kodi.for.Android.21.3.1.apk`,
  `LER.APENAS.KODI.zip` — este último veio só com pasta `files/` vazia, pedir
  re-upload se precisar). Host de assets bloqueado no sandbox (EOF) — usar o
  runner (workflows `kodi-tree.yml` / `read-asset.yml`).
- Instruções do usuário (Termux no aparelho dele, não no sandbox):
  `pkg update && pkg install apktool openjdk-17 apksigner aapt -y` + MT Manager
  para inspecionar o APK do Kodi — equivalente aqui: ícones/estrutura vem do
  repo fonte `xbmc/xbmc` (skin.estuary) via `gh api`.
- Ideia do plano B: reprodutor Android embutido na aba **Navegar** que executa o
  Kodi original, com foco em otimização (não travar como no celular do usuário).
  Alternativas técnicas a avaliar quando ativado: Activity do Kodi embutida/
  redirecionada, VM leve, ou streaming da UI — tudo preservando as regras do
  spec §7 (nunca executar código desconhecido sem validação; aqui seria o app
  oficial do usuário instalado).
- Aceitação em vigor do plano A (atual): add-on executado sem listagem de
  arquivos, categorias nas abas nativas, player abrindo o conteúdo; E2E real com
  credenciais do usuário (não há secrets no CI público).
