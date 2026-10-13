'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const shell = process.env.TC_TEST_POWERSHELL;
const enabled = process.platform === 'win32' && Boolean(shell);
function check(contents) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'tc-azure-config-'));
  try {
    const file = path.join(directory, 'application.properties');
    fs.writeFileSync(file, contents);
    const result = spawnSync(shell, ['-NoProfile', '-File', 'tools/run-web.ps1', '-ConfigPath', file, '-CheckConfig'], {
      encoding: 'utf8', windowsHide: true, timeout: 10000
    });
    assert.ifError(result.error);
    return result;
  } finally { fs.rmSync(directory, { recursive: true, force: true }); }
}
const target = 'db.host=ticketscenter.database.windows.net\ndb.name=TicketsCenter\n';
test('one main application.properties is enough for the default configuration path', {skip: !enabled}, () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'tc-one-config-'));
  try {
    fs.mkdirSync(path.join(directory, 'tools'), {recursive: true});
    fs.mkdirSync(path.join(directory, 'src/main/resources'), {recursive: true});
    fs.copyFileSync('tools/run-web.ps1', path.join(directory, 'tools/run-web.ps1'));
    fs.writeFileSync(path.join(directory, 'src/main/resources/application.properties'), target + 'db.user=test\ndb.password=private-config-test\n');
    const result = spawnSync(shell, ['-NoProfile', '-File', path.join(directory, 'tools/run-web.ps1'), '-CheckConfig'], {
      encoding: 'utf8', windowsHide: true, timeout: 10000
    });
    assert.ifError(result.error);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /AZURE_CONFIG_VALID/);
    assert.equal(fs.existsSync(path.join(directory, '.local/azure.properties')), false);
  } finally { fs.rmSync(directory, {recursive: true, force: true}); }
});
test('Azure config check accepts the team target without launching or printing credentials', {skip: !enabled}, () => {
  const result = check(target + 'db.user=configuration-test\ndb.password=do-not-print-this-value\n');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /AZURE_CONFIG_VALID/);
  assert.doesNotMatch(result.stdout + result.stderr, /do-not-print-this-value/);
});
test('Azure runner rejects a different target before starting or building', {skip: !enabled}, () => {
  const result = check('db.host=localhost\ndb.name=TicketsCenter\ndb.user=test\ndb.password=test\n');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Unexpected Azure target/);
});
test('blank template cannot fall back to credentials in the WAR', {skip: !enabled}, () => {
  const result = check(target + 'db.user=\ndb.password=\n');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Azure credentials required/);
});
