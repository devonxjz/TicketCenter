"""Complete production WAR, isolated Tomcat, native ADMIN/WORKER and real restart.
Only guarded loopback fixtures are mutated. No private operator config is read.
"""
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile


def main():
    env = os.environ.copy()
    env.pop('TC_CONFIG_FILE', None)
    canonical = env.get('TC_CANCELLATION_CANONICAL_TEST') == 'true' and 'databaseName=TC_Khanh06_Test_Canonical;' in env['TC_SCAN_TEST_URL']
    if not env['TC_SCAN_TEST_URL'].startswith('jdbc:sqlserver://127.0.0.1:') or ('databaseName=TicketCenter_Khanh02_Test;' not in env['TC_SCAN_TEST_URL'] and not canonical):
        raise RuntimeError('Dedicated loopback fixture required')
    with socket.socket() as check:
        if check.connect_ex(('127.0.0.1', 18086)) == 0:
            raise RuntimeError('Acceptance port already occupied')
    target = Path('target').resolve()
    runtime = target / ('khanh06-web-' + uuid.uuid4().hex)
    runtime.mkdir()
    home = Path(env.get('TC_KHANH06_TOMCAT_HOME', runtime / 'apache-tomcat-11.0.25')).resolve()
    if not home.exists():
        url = 'https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.25/bin/apache-tomcat-11.0.25.zip'
        with urllib.request.urlopen(url, timeout=60) as response:
            archive = response.read()
        with urllib.request.urlopen(url + '.sha512', timeout=30) as response:
            expected = response.read().decode('ascii').split()[0].lower()
        if hashlib.sha512(archive).hexdigest() != expected:
            raise RuntimeError('Tomcat checksum mismatch')
        archive_path = runtime / 'tomcat.zip'
        archive_path.write_bytes(archive)
        with zipfile.ZipFile(archive_path) as package:
            for entry in package.infolist():
                if not (runtime / entry.filename).resolve().is_relative_to(runtime):
                    raise RuntimeError('Archive path escaped fixture')
            package.extractall(runtime)
    base = runtime / 'base'
    shutil.copytree(home / 'conf', base / 'conf')
    for folder in ('logs', 'temp', 'work', 'webapps'):
        (base / folder).mkdir()
    config = base / 'conf/server.xml'
    tree = ET.parse(config)
    tree.getroot().set('port', '-1')
    for connector in tree.findall('./Service/Connector'):
        if connector.get('protocol') == 'HTTP/1.1':
            connector.set('port', '18086')
            connector.set('address', '127.0.0.1')
    tree.write(config, encoding='utf-8', xml_declaration=True)
    deployment = base / 'webapps/ticketscenter'
    with zipfile.ZipFile(target / 'ticketscenter.war') as package:
        for entry in package.infolist():
            if not (deployment / entry.filename).resolve().is_relative_to(deployment):
                raise RuntimeError('WAR path escaped fixture')
        package.extractall(deployment)
    java = str(Path(env['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
    cp = os.pathsep.join((str(target / 'test-classes'), str(target / 'classes'), (target / 'khanh06-classpath.txt').read_text().strip()))

    def probe(mode, capture=False):
        return subprocess.run([java, '-cp', cp, 'vn.ticketscenter.database.EventCancellationWebFixture', mode], env=env, timeout=35, capture_output=capture, text=True)

    probe('prepare').check_returncode()
    for role in ('ADMIN', 'WORKER'):
        env['TC_KHANH06_' + role + '_USER'] = 'khanh06_' + role.lower() + '_' + uuid.uuid4().hex
        env['TC_KHANH06_' + role + '_PASSWORD'] = 'K06!' + secrets.token_hex(24)
    local = os.name == 'nt' and env.get('GITHUB_ACTIONS') != 'true'
    process = None
    logins = False
    try:
        if local:
            subprocess.run(['pwsh', '-NoProfile', '-File', 'src/test/resources/cancellation/local-runtime-logins.ps1'], env=env, check=True, timeout=30)
        else:
            for role in ('ADMIN', 'WORKER'):
                print('::add-mask::' + env['TC_KHANH06_' + role + '_PASSWORD'])
            subprocess.run([java, '-cp', cp, 'vn.ticketscenter.database.EventCancellationWebLogin'], env=env, check=True, timeout=30)
        logins = True
        env.update(DB_HOST='127.0.0.1', DB_PORT='1433', DB_NAME='TC_Khanh06_Test_Canonical' if canonical else 'TicketCenter_Khanh02_Test', DB_URL=env['TC_SCAN_TEST_URL'], DB_TRUSTSERVERCERTIFICATE='true', ADMIN_SEED_ENABLED='false', MAIL_SMTP_HOST='127.0.0.1', MAIL_SMTP_PORT='18825', OUTBOX_WORKER_ENABLED='false')
        env['DB_USER'], env['DB_PASSWORD'] = env['TC_SCAN_OWNER_USER'], env['TC_SCAN_OWNER_PASSWORD']
        for role in ('BUYER', 'MANAGER', 'CHECK_IN', 'ADMIN', 'AUTH', 'WORKER'):
            env['DB_' + role + '_USER'] = env['TC_KHANH06_' + role + '_USER'] if role in ('ADMIN', 'WORKER') else env['TC_SCAN_OWNER_USER']
            env['DB_' + role + '_PASSWORD'] = env['TC_KHANH06_' + role + '_PASSWORD'] if role in ('ADMIN', 'WORKER') else env['TC_SCAN_OWNER_PASSWORD']
        env.update({'HIKARI_MAXIMUM-POOL-SIZE': '3', 'HIKARI_MINIMUM-IDLE': '0', 'HIKARI_POOL-BUDGET': '24', 'HIKARI_CONNECTION-TIMEOUT': '5000'})
        env.update(REFUND_SIMULATED_STATE_DIRECTORY=str(runtime / 'receipts'), REFUND_SIMULATED_OUTCOME='SUCCEEDED', REFUND_SIMULATED_TIMEOUTAFTERSIDEEFFECT='false')
        args = [java, '-Dcatalina.home=' + str(home), '-Dcatalina.base=' + str(base), '-Djava.io.tmpdir=' + str(base / 'temp'), '-cp', os.pathsep.join((str(home / 'bin/bootstrap.jar'), str(home / 'bin/tomcat-juli.jar'))), 'org.apache.catalina.startup.Bootstrap', 'start']
        kwargs = {'creationflags': subprocess.CREATE_NO_WINDOW} if os.name == 'nt' else {}
        root = 'http://127.0.0.1:18086/ticketscenter'
        fixture = dict(line.split('=', 1) for line in (target / 'khanh06-web-fixture.properties').read_text().splitlines() if line and not line.startswith('#'))
        checks = []

        def session():
            return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

        def call(client, name, path, expected=200, body=None, headers=None, method=None):
            request = urllib.request.Request(root + path, data=None if body is None else json.dumps(body).encode('utf-8'), headers=headers or {}, method=method)
            try:
                response = client.open(request, timeout=25)
            except urllib.error.HTTPError as error:
                response = error
            payload = response.read().decode('utf-8')
            if response.code != expected:
                raise AssertionError(f'{name}: expected {expected}, got {response.code}: {payload[:500]}')
            checks.append({'check': name, 'status': response.code})
            return json.loads(payload) if payload.startswith('{') else payload

        def login(role):
            client = session()
            token = call(client, role + ' csrf', '/api/auth/csrf')['data']['token']
            call(client, role + ' login', '/api/auth/login', body={'identifier': fixture[role], 'password': env['TC_SCAN_FIXTURE_PASSWORD']}, headers={'Content-Type': 'application/json', 'X-CSRF-Token': token})
            return client, call(client, role + ' session csrf', '/api/auth/csrf')['data']['token']

        def stop():
            nonlocal process
            if process:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
                process = None

        for phase in ('first', 'restart'):
            with (target / ('khanh06-tomcat-' + phase + '.log')).open('w', encoding='utf-8') as log:
                process = subprocess.Popen(args, env=env, stdout=log, stderr=subprocess.STDOUT, **kwargs)
                try:
                    for _ in range(60):
                        if process.poll() is not None:
                            raise RuntimeError('Tomcat exited; inspect fixture log')
                        try:
                            with urllib.request.urlopen(root + '/api/auth/csrf', timeout=2) as r:
                                if r.status == 200:
                                    break
                        except (urllib.error.URLError, TimeoutError, socket.timeout):
                            pass
                        time.sleep(2)
                    else:
                        raise RuntimeError('Complete WAR did not start')
                    for _ in range(30):
                        checked = probe('verify', True)
                        if checked.returncode == 0:
                            print(checked.stdout.strip())
                            break
                        if checked.returncode != 3:
                            raise RuntimeError('Cancellation fixture verification failed')
                        time.sleep(2)
                    else:
                        raise RuntimeError('Cancellation worker did not resume')
                    if phase == 'first':
                        anonymous = session()
                        call(anonymous, 'anonymous progress denied', '/api/admin/events/' + fixture['restartEvent'] + '/cancellation-progress', 401)
                        admin, token = login('admin')
                        buyer, buyer_token = login('buyer')
                        path = '/api/admin/events/' + fixture['httpEvent'] + '/cancel'
                        headers = {'Content-Type': 'application/json', 'X-CSRF-Token': token, 'Idempotency-Key': str(uuid.uuid4())}
                        call(buyer, 'customer cancel denied', path, 403, {'reason': 'Thời tiết nguy hiểm'}, {**headers, 'X-CSRF-Token': buyer_token})
                        call(buyer, 'customer progress denied', '/api/admin/events/' + fixture['restartEvent'] + '/cancellation-progress', 403)
                        call(admin, 'missing CSRF', path, 403, {'reason': 'Thời tiết nguy hiểm'}, {'Content-Type': 'application/json', 'Idempotency-Key': headers['Idempotency-Key']})
                        call(admin, 'invalid UUID key', path, 400, {'reason': 'Thời tiết nguy hiểm'}, {**headers, 'Idempotency-Key': '1-1-1-1-1'})
                        call(admin, 'JSON required', path, 415, {'reason': 'Thời tiết nguy hiểm'}, {**headers, 'Content-Type': 'text/plain'})
                        call(admin, 'reason required', path, 400, {}, headers)
                        call(admin, 'unknown fields rejected', path, 400, {'reason': 'Thời tiết nguy hiểm', 'actorId': fixture['admin']}, headers)
                        call(admin, 'after start denied', '/api/admin/events/' + fixture['expiredEvent'] + '/cancel', 400, {'reason': 'Thời tiết nguy hiểm'}, headers)
                        first = call(admin, 'CAN01 HTTP cancel', path, body={'reason': 'Thời tiết nguy hiểm'}, headers=headers)['data']
                        assert first['status'] == 'CANCELLED' and first['totalOrdersToRefund'] == 1
                        assert first == call(admin, 'same intent replay', path, body={'reason': 'Thời tiết nguy hiểm'}, headers=headers)['data']
                        call(admin, 'same key changed payload', path, 409, {'reason': 'Một lý do hoàn toàn khác'}, headers)
                        call(admin, 'another cancellation conflicts', path, 409, {'reason': 'Thời tiết nguy hiểm'}, {**headers, 'Idempotency-Key': str(uuid.uuid4())})
                        call(admin, 'internal mutation URL blocked', '/api/admin/event-cancel-internal', 404, {'reason': 'Thời tiết nguy hiểm'}, headers)
                        call(admin, 'wrong method rejected', path, 405)
                        progress = call(admin, 'durable progress', '/api/admin/events/' + fixture['restartEvent'] + '/cancellation-progress')['data']
                        assert progress['processedOrders'] == progress['totalOrders'] == 2 and progress['successfulRefunds'] == 2
                        page = call(admin, 'UI20 JSP compiles', '/events/review')
                        assert 'cancellation-dialog' in page and 'event-cancellation.js' in page
                        if env.get('TC_KHANH06_BROWSER_SCRIPT'):
                            subprocess.run([env.get('TC_KHANH06_NODE', 'node'), env['TC_KHANH06_BROWSER_SCRIPT']], env=env, check=True, timeout=150)
                finally:
                    stop()
        (target / 'khanh06-web-acceptance.json').write_text(json.dumps({'checks': checks, 'partialBatchResumed': True, 'realTomcatRestart': True, 'restrictedAdminAndWorkerLogins': True, 'transferSimulator': True}, ensure_ascii=False, indent=2), encoding='utf-8')
        print(f'KHANH-06 complete WAR: {len(checks)} HTTP/JSP checks, partial batch and real Tomcat restart passed.')
    finally:
        if process:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        if logins and local:
            subprocess.run(['pwsh', '-NoProfile', '-File', 'src/test/resources/cancellation/local-runtime-logins.ps1', '-Cleanup'], env=env, check=True, timeout=30)


if __name__ == '__main__':
    main()
