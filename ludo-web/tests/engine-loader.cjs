const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const cache = new Map();

// Compile the production modules in memory; no separate test implementation or emitted files.
function loadEngine(name) {
  return load(path.resolve(__dirname, '../src/engine', `${name}.ts`));
}
function load(file) {
  if (cache.has(file)) return cache.get(file).exports;
  const module = { exports: {} };
  cache.set(file, module);
  const result = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  vm.runInThisContext(`(function(require,module,exports){${result.outputText}\n})`, { filename: file })(
    relative => load(path.resolve(path.dirname(file), `${relative}.ts`)), module, module.exports,
  );
  return module.exports;
}
module.exports = { loadEngine };
