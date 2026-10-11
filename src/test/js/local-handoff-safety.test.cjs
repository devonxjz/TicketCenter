'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
const {spawnSync} = require('node:child_process');
const net = require('node:net');
const powershell = process.env.TC_TEST_POWERSHELL;
const enabled = process.platform === 'win32' && powershell;
function run(script, args) {
  const result = spawnSync(powershell, ['-NoProfile', '-File', script, ...args], {
    encoding: 'utf8', windowsHide: true, timeout: 10000
  });
  assert.ifError(result.error);
  assert.notEqual(result.status, 0);
  return result.stderr;
}
test('local setup refuses non-local database names before SQL or config writes', {skip: !enabled}, async t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tc-handoff-'));
  try {
    for (const database of ['TicketCenterticke', 'master', 'TC_Vuong_Test_Shared', 'TC_Vuong_Test_Local_x;DROP', 'TC_Vuong_Test_Local_']) {
      await t.test(database, () => {
        const config = path.join(root, 'database.json');
        assert.match(run('tools/local/setup.ps1', ['-Database', database, '-ConfigPath', config]), /Dedicated local database name required/);
        assert.equal(fs.existsSync(config), false);
      });
    }
  } finally {fs.rmSync(root, {recursive: true, force: true});}
});
test('local setup preserves an existing credentials file before contacting SQL', {skip: !enabled}, () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tc-handoff-'));
  try {
    const config = path.join(root, 'database.json'); fs.writeFileSync(config, 'preserve-me');
    assert.match(run('tools/local/setup.ps1', ['-Database', 'TC_Vuong_Test_Local_Guard', '-ConfigPath', config]), /Configuration already exists/);
    assert.equal(fs.readFileSync(config, 'utf8'), 'preserve-me');
  } finally {fs.rmSync(root, {recursive: true, force: true});}
});
test('local runner rejects a missing JDK before build, download or SQL startup', {skip: !enabled}, () => {
  assert.match(run('tools/local/run.ps1', ['-JavaHome', 'Z:/tc-does-not-exist', '-TomcatHome', 'Z:/tc-does-not-exist']), /JDK 25 is required/);
});
test('local setup will not write credentials to an unignored checkout path', {skip: !enabled}, () => {
  const config = path.resolve('tools/local/not-ignored-credentials.json');
  assert.equal(fs.existsSync(config), false);
  assert.match(run('tools/local/setup.ps1', ['-Database', 'TC_Vuong_Test_Local_Guard', '-ConfigPath', config]), /must be git-ignored/);
  assert.equal(fs.existsSync(config), false);
});
test('JSTL cache corruption is rejected before reuse or downloads', {skip: !enabled}, () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tc-jstl-'));
  try {
    const file = path.join(root, 'jakarta.servlet.jsp.jstl-api-3.0.1.jar');
    fs.writeFileSync(file, 'not-a-trusted-jar');
    assert.match(run('tools/local/jstl.ps1', ['-LibDirectory', root]), /JSTL checksum mismatch/);
    assert.equal(fs.readFileSync(file, 'utf8'), 'not-a-trusted-jar');
    assert.deepEqual(fs.readdirSync(root), [path.basename(file)]);
  } finally {fs.rmSync(root, {recursive: true, force: true});}
});

test('local build clears inherited Maven settings and forces safe tests', {
  skip: !(enabled && process.env.TC_TEST_JAVA_HOME && process.env.TC_TEST_TOMCAT_HOME)
}, async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tc-maven-probe-'));
  try {
    const listener = net.createServer();
    await new Promise((resolve, reject) => {listener.once('error', reject); listener.listen(0, '127.0.0.1', resolve);});
    const port = listener.address().port;
    await new Promise((resolve, reject) => listener.close(error => error ? reject(error) : resolve()));
    const config = path.join(root, 'database.json'), probe = path.join(root, 'probe.txt');
    fs.writeFileSync(config, '{}');
    fs.writeFileSync(path.join(root, 'mvn.cmd'), '@echo off\r\n>"%HANDOFF_BUILD_PROBE%" echo args=[%MAVEN_ARGS%]\r\n>>"%HANDOFF_BUILD_PROBE%" echo opts=[%MAVEN_OPTS%]\r\n>>"%HANDOFF_BUILD_PROBE%" echo cli=%*\r\nexit /b 42\r\n');
    const result = spawnSync(powershell, ['-NoProfile', '-File', 'tools/local/run.ps1',
      '-JavaHome', process.env.TC_TEST_JAVA_HOME, '-TomcatHome', process.env.TC_TEST_TOMCAT_HOME,
      '-ConfigPath', config, '-Port', String(port), '-Smoke'], {
      encoding: 'utf8', windowsHide: true, timeout: 20000,
      env: {...process.env, PATH: root + path.delimiter + process.env.PATH,
        HANDOFF_BUILD_PROBE: probe, MAVEN_ARGS: '-DskipTests -Dtc.tests.db=true',
        MAVEN_OPTS: '-Dtc.tests.smtp=true'}
    });
    assert.ifError(result.error);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Build or tests failed/);
    const captured = fs.readFileSync(probe, 'utf8');
    assert.match(captured, /args=\[\]/);
    assert.match(captured, /opts=\[\]/);
    for (const flag of ['tc.tests.db', 'tc.tests.smtp', 'tc.tests.redis', 'skipTests', 'maven.test.skip', 'skipITs']) {
      assert.ok(captured.includes('-D' + flag + '=false'), flag);
    }
  } finally {fs.rmSync(root, {recursive: true, force: true});}
});
