// 使用刻意破坏的契约验证检查工具确实会拒绝不合规输入。
const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');

const original = JSON.parse(fs.readFileSync(path.join(__dirname, 'control-mileage.json'), 'utf8'));
const cli = path.join(__dirname, 'node_modules/@stoplight/spectral-cli/dist/index.js');
const route = '/api/control/statistics/mileage';

// OAS 3.1 允许省略 type；本项目的已描述模型仍须显式声明，防止生成器丢失类型后校验形同虚设。
function assertSchemaTypes(schema, location) {
  if (!schema || typeof schema !== 'object' || Object.keys(schema).length === 0) return;
  assert.ok(schema.type || schema.$ref || schema.oneOf || schema.anyOf || schema.allOf,
    `${location}: 已描述的 Schema 缺少类型或模型引用`);
  for (const [name, property] of Object.entries(schema.properties || {})) {
    assertSchemaTypes(property, `${location}.properties.${name}`);
  }
  for (const name of ['items', 'additionalProperties']) assertSchemaTypes(schema[name], `${location}.${name}`);
  for (const name of ['oneOf', 'anyOf', 'allOf']) {
    (schema[name] || []).forEach((child, index) => assertSchemaTypes(child, `${location}.${name}[${index}]`));
  }
}

function assertDocumentTypes(document) {
  function visit(node, location) {
    if (!node || typeof node !== 'object') return;
    for (const [key, value] of Object.entries(node)) {
      if (key === 'schema') assertSchemaTypes(value, `${location}.schema`);
      else visit(value, `${location}.${key}`);
    }
  }
  visit(document.paths, 'paths');
  for (const [name, schema] of Object.entries(document.components?.schemas || {})) {
    assertSchemaTypes(schema, `components.schemas.${name}`);
  }
}

test('全部版本化契约保留实际可验证的字段类型', () => {
  for (const name of ['control-mileage', 'media-files', 'control-files', 'media-service', 'control-service', 'bff']) {
    assertDocumentTypes(JSON.parse(fs.readFileSync(path.join(__dirname, `${name}.json`), 'utf8')));
  }
});

test('嵌套 Schema 遗失类型时拒绝放行，开放扩展值仍合法', () => {
  assert.doesNotThrow(() => assertSchemaTypes({ type: 'object', additionalProperties: {} }, 'dynamic'));
  const schema = { type: 'object', properties: { items: { description: '列表', items: { type: 'string' } } } };
  assert.throws(() => assertSchemaTypes(schema, 'test'), /缺少类型/);
  assert.throws(() => assertSchemaTypes({ description: '数值' }, 'test'), /缺少类型/);
});

for (const [name, mutate] of [
  ['正常入口缺少成功响应', doc => { delete doc.paths[route].get.responses['200']; }],
  ['缺少操作标识', doc => { delete doc.paths[route].get.operationId; }],
  ['操作标识重复', doc => { doc.paths['/duplicate'] = structuredClone(doc.paths[route]); }],
  ['无效模型引用', doc => { doc.paths[route].get.responses['200'].content['application/json'].schema = { $ref: '#/components/schemas/Missing' }; }],
  ['非法字段类型', doc => { doc.components.schemas.MileageSummaryResponse.properties.totalMeters.type = 'invalid-type'; }],
  ['缺少字段说明', doc => { delete doc.components.schemas.MileageSummaryResponse.properties.timezone.description; }],
]) {
  test(name, () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'openapi-negative-'));
    try {
      const file = path.join(directory, 'invalid.json');
      const document = structuredClone(original);
      mutate(document);
      fs.writeFileSync(file, JSON.stringify(document));
      const result = spawnSync(process.execPath, [cli, 'lint', '--ruleset', path.join(__dirname, '.spectral.yaml'),
        '--fail-severity', 'warn', '--format', 'json', file], { encoding: 'utf8' });
      assert.ifError(result.error);
      assert.equal(result.status, 1, result.stderr);
      assert.ok(JSON.parse(result.stdout).length > 0, '必须存在明确的规则诊断');
    } finally {
      fs.rmSync(directory, { recursive: true, force: true });
    }
  });
}
