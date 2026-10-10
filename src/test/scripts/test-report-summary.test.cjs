'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const script = path.join(__dirname, 'test-report-summary.cjs');

function run(xml, flags = []) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tc-report-probe-'));
  try {
    if (xml) fs.writeFileSync(path.join(root, 'TEST-Probe.xml'), xml);
    return spawnSync(process.execPath, [script, root, ...flags], { encoding: 'utf8' });
  } finally {
    const resolved = path.resolve(root);
    assert.equal(path.dirname(resolved), path.resolve(os.tmpdir()));
    assert.ok(path.basename(resolved).startsWith('tc-report-probe-'));
    fs.rmSync(resolved, { recursive: true });
  }
}
test('missing and zero-execution results cannot produce success', () => {
  assert.equal(run(null).status, 1);
  assert.equal(run('<testsuite tests="1" failures="0" errors="0" skipped="1"></testsuite>').status, 1);
});
test('opt-in job fails on skipped tests while safe default reports them', () => {
  const xml = '<testsuite tests="2" failures="0" errors="0" skipped="1"><testcase classname="Probe" name="external"><skipped/></testcase></testsuite>';
  assert.equal(run(xml).status, 0);
  assert.equal(run(xml, ['--require-no-skips']).status, 1);
});
test('failure results never print a PASS claim', () => {
  const result = run('<testsuite tests="1" failures="1" errors="0" skipped="0"></testsuite>');
  assert.equal(result.status, 1);
  assert.match(result.stdout, /failures: 1/);
  assert.doesNotMatch(result.stdout, /PASS/);
});
test('report does not echo XML properties or captured secrets', () => {
  const result = run('<testsuite tests="1" failures="0" errors="0" skipped="0"><properties>PRIVATE_PROBE_VALUE</properties><system-out>PRIVATE_PROBE_VALUE</system-out></testsuite>');
  assert.equal(result.status, 0);
  assert.doesNotMatch(result.stdout, /PRIVATE_PROBE_VALUE/);
});
test('self-closing pure testcase cannot inherit next case skip', () => {
  const result = run('<testsuite tests="2" failures="0" errors="0" skipped="1"><testcase classname="Probe" name="pure"/><testcase classname="Probe" name="external"><skipped/></testcase></testsuite>');
  assert.match(result.stdout, /Skipped external case: Probe.external/);
  assert.doesNotMatch(result.stdout, /Skipped external case: Probe.pure/);
});
test('missing required suite fails even when another suite executed', () => {
  const result = run('<testsuite tests="1" failures="0" errors="0" skipped="0"></testsuite>', ['--require-suite=MissingTest']);
  assert.equal(result.status, 1);
});
