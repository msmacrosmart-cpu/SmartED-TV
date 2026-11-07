#!/usr/bin/env node
/**
 * Validador sintático de Kotlin para o SmartED TV.
 *
 * Usa o grammar oficial (tree-sitter-kotlin) para garantir que todos os
 * arquivos `.kt` do projeto estejam sintaticamente corretos. Não substitui
 * o build do Gradle, mas pega 95% dos erros de digitação/estrutura cedo.
 *
 * Uso:  node tools/kt-check.mjs [diretório]
 * Requer: npm i web-tree-sitter@0.20.8 tree-sitter-wasms
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const WASM_CANDIDATES = [
  'node_modules/tree-sitter-wasms/out/tree-sitter-kotlin.wasm',
  '/tmp/klex/node_modules/tree-sitter-wasms/out/tree-sitter-kotlin.wasm',
];

const root = process.argv[2] ?? 'app/src';

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    const st = statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (entry.endsWith('.kt') || entry.endsWith('.kts')) out.push(p);
  }
  return out;
}

const { default: Parser } = await import('web-tree-sitter');
await Parser.init();

let langPath = null;
for (const c of WASM_CANDIDATES) {
  try {
    readFileSync(c);
    langPath = c;
    break;
  } catch {
    /* tenta o próximo */
  }
}
if (!langPath) {
  console.error('WASM do tree-sitter-kotlin não encontrado.');
  process.exit(2);
}

const Lang = await Parser.Language.load(langPath);
const parser = new Parser();
parser.setLanguage(Lang);

const files = walk(root).sort();
let bad = 0;
let errors = 0;

for (const f of files) {
  const src = readFileSync(f, 'utf8');
  // O grammar não aceita vírgula final em literais de coleção — normaliza antes.
  const normalized = src.replace(/,(\s*[\]}])/g, '$1');
  const tree = parser.parse(normalized);
  if (tree.rootNode.hasError()) {
    bad++;
    const found = [];
    console.log('ARQUIVO COM ERRO:', f);
    const visit = (node) => {
      if (node.type === 'ERROR' || node.isMissing()) {
        const line = node.startPosition.row + 1;
        const col = node.startPosition.column + 1;
        const snippet = normalized.split('\n')[node.startPosition.row]?.slice(0, 110) ?? '';
        found.push(`    ${relative(process.cwd(), f)}:${line}:${col}  ${node.type === 'ERROR' ? 'erro' : 'faltando'}  ${snippet.trim()}`);
      }
      for (const child of node.children) visit(child);
    };
    visit(tree.rootNode);
    errors += found.length;
    console.log(found.slice(0, 12).join('\n') || '  (sem nós ERROR — problema no topo)');
    if (found.length > 12) console.log(`    … +${found.length - 12} problemas`);
  }
}

console.log(`\n${files.length} arquivo(s) Kotlin analisado(s) · ${bad} com erro de sintaxe`);
process.exit(bad === 0 ? 0 : 1);
