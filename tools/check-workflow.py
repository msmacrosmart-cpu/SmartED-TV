#!/usr/bin/env python3
"""
check-workflow.py — valida o workflow do GitHub Actions.

Verificações (todas pegas por bugs reais neste projeto):

1. YAML válido e jobs/etapas presentes.
2. Referência `steps.<id>.outputs.<nome>` só aponta para etapa existente *e*
   que realmente emite aquele output via `echo "nome=..." >> $GITHUB_OUTPUT`.
   Um `versionCode` saiu vazio na release porque o corpo referenciava
   `version_name` quando o passo emitia `name`.
3. `needs:` aponta para jobs existentes.
4. Todo bloco `run:` passa em `bash -n` (aspas/crases desbalanceadas). Um bloco
   com crase solta quebrava o job inteiro.
5. Ações usadas têm versão fixada (`@vN`), nunca `@main`.
6. `permissions:` inclui `contents: write` quando há publicação em Release.

Uso:  python3 tools/check-workflow.py [arquivo]
Saída: 0 se tudo certo; 1 se houver problema.
"""
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT = os.path.join(ROOT, ".github", "workflows", "build.yml")


def load(path):
    try:
        import yaml
    except ImportError:
        print("PyYAML não instalado (pip install pyyaml)")
        sys.exit(2)
    with open(path, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def emitted_outputs(step):
    run = step.get("run") or ""
    found = set(re.findall(r'echo\s+"?([A-Za-z_][A-Za-z0-9_]*)=', run))
    # outputs via JSON em arquivo também contam
    found |= set(re.findall(r'"([A-Za-z_][A-Za-z0-9_]*)"\s*:', run)) if "GITHUB_OUTPUT" in run else set()
    return found


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
    doc = load(path)
    raw = open(path, encoding="utf-8").read()
    problems = []

    if True not in doc and "on" not in doc:
        problems.append("sem chave 'on:' (gatilhos)")

    jobs = doc.get("jobs") or {}
    if not jobs:
        problems.append("nenhum job definido")

    # --- 2) outputs referenciados vs. emitidos -------------------------------
    by_job = {}
    for job_name, cfg in jobs.items():
        steps = cfg.get("steps") or []
        by_job[job_name] = {s["id"]: emitted_outputs(s) for s in steps if s.get("id")}

    for sid, out in sorted(set(re.findall(r"steps\.([A-Za-z0-9_-]+)\.outputs\.([A-Za-z0-9_]+)", raw))):
        owners = [(job, outs) for job, mapping in by_job.items() if sid in mapping for outs in [mapping[sid]]]
        if not owners:
            problems.append(f"steps.{sid}.outputs.{out}: etapa '{sid}' não existe")
            continue
        for job, outs in owners:
            if out not in outs:
                problems.append(
                    f"steps.{sid}.outputs.{out}: etapa não emite '{out}' "
                    f"(emite: {', '.join(sorted(outs)) or 'nada'})"
                )

    # --- 3) needs -----------------------------------------------------------
    for job_name, cfg in jobs.items():
        needs = cfg.get("needs") or []
        if isinstance(needs, str):
            needs = [needs]          # YAML aceita 'needs: verify'
        for need in needs:
            if need not in jobs:
                problems.append(f"job '{job_name}': needs aponta para '{need}', que não existe")

    # --- 4) sintaxe dos blocos run -----------------------------------------
    for job_name, cfg in jobs.items():
        for index, step in enumerate(cfg.get("steps") or []):
            script = step.get("run")
            if not script:
                continue
            with tempfile.NamedTemporaryFile("w", suffix=".sh", delete=False, encoding="utf-8") as handle:
                handle.write(script)
                tmp = handle.name
            result = subprocess.run(["bash", "-n", tmp], capture_output=True, text=True)
            os.unlink(tmp)
            if result.returncode != 0:
                detail = (result.stderr or "").strip().splitlines()
                label = step.get("name") or f"etapa #{index}"
                problems.append(f"{job_name} / {label}: sintaxe inválida — {detail[-1] if detail else '?'}")

    # --- 5) ações sem versão fixa ------------------------------------------
    for use in re.findall(r"uses:\s*([^\s#]+)", raw):
        if "@" not in use:
            problems.append(f"uses: {use} sem versão")
        elif use.endswith("@main") or use.endswith("@master"):
            problems.append(f"uses: {use} aponta para branch móvel")

    # --- 6) permissões para publicar release -------------------------------
    publishes = "action-gh-release" in raw
    if publishes:
        permissions = doc.get("permissions") or {}
        if permissions.get("contents") not in ("write", True):
            problems.append("publica Release mas permissions.contents não é 'write'")

    print(f"{len(jobs)} job(s), {sum(len(c.get('steps') or []) for c in jobs.values())} etapa(s) analisada(s)")
    if problems:
        print(f"\n{len(problems)} problema(s):\n")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print("OK — workflow consistente.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
