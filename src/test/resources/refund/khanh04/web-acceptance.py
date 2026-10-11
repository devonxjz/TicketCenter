"""Full packaged WAR, real login/JSP/HTTP, fixed isolated SQL database and restricted ADMIN login."""
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
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
    url = os.environ['TC_SCAN_TEST_URL']
    if not url.startswith('jdbc:sqlserver://127.0.0.1:') or 'databaseName=TicketCenter_Khanh02_Test;' not in url:
        raise RuntimeError('Dedicated loopback SQL database required')
    target = Path('target').resolve()
    runtime = target / ('khanh04-web-' + uuid.uuid4().hex)
    runtime.mkdir()
    home_setting = os.environ.get('TC_ADMIN_REFUND_TOMCAT_HOME')
    if home_setting:
        home = Path(home_setting).resolve()
        if target not in home.parents or not (home / 'bin/bootstrap.jar').is_file():
            raise RuntimeError('Local Tomcat must be inside the workspace target directory')
    else:
        archive_url = 'https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.25/bin/apache-tomcat-11.0.25.zip'
        with urllib.request.urlopen(archive_url, timeout=60) as response:
            archive = response.read()
        with urllib.request.urlopen(archive_url + '.sha512', timeout=30) as response:
            checksum = response.read().decode('ascii').split()[0].lower()
        if hashlib.sha512(archive).hexdigest() != checksum:
            raise RuntimeError('Tomcat archive checksum mismatch')
        archive_path = runtime / 'tomcat.zip'
        archive_path.write_bytes(archive)
        with zipfile.ZipFile(archive_path) as package:
            if any(runtime not in (runtime / entry.filename).resolve().parents for entry in package.infolist()):
                raise RuntimeError('Archive escaped runtime directory')
            package.extractall(runtime)
        home = runtime / 'apache-tomcat-11.0.25'
    base = runtime / 'instance'
    shutil.copytree(home / 'conf', base / 'conf')
    for folder in ('logs', 'temp', 'work', 'webapps'):
        (base / folder).mkdir()
    server_path = base / 'conf/server.xml'
    server = ET.parse(server_path)
    server.getroot().set('port', '-1')
    for connector in server.findall('./Service/Connector'):
        if connector.get('protocol') == 'HTTP/1.1':
            connector.set('port', '18084')
            connector.set('address', '127.0.0.1')
    server.write(server_path, encoding='utf-8', xml_declaration=True)
    deployment = base / 'webapps/ticketscenter'
    with zipfile.ZipFile(target / 'ticketscenter.war') as war:
        if any(deployment not in (deployment / entry.filename).resolve().parents for entry in war.infolist()):
            raise RuntimeError('WAR escaped deployment directory')
        war.extractall(deployment)
    env = os.environ.copy()
    env.update(DB_HOST='127.0.0.1', DB_PORT='1433', DB_NAME='TicketCenter_Khanh02_Test', DB_URL=url,
               DB_TRUSTSERVERCERTIFICATE='true', ADMIN_SEED_ENABLED='false', MAIL_SMTP_HOST='127.0.0.1', MAIL_SMTP_PORT='18825')
    for role in ('', '_BUYER', '_MANAGER', '_CHECK_IN', '_AUTH', '_WORKER'):
        env['DB' + role + '_USER'] = env['TC_SCAN_OWNER_USER']
        env['DB' + role + '_PASSWORD'] = env['TC_SCAN_OWNER_PASSWORD']
    env['DB_ADMIN_USER'] = env['TC_ADMIN_REFUND_DB_USER']
    env['DB_ADMIN_PASSWORD'] = env['TC_ADMIN_REFUND_DB_PASSWORD']
    env.update({'HIKARI_MAXIMUM-POOL-SIZE': '3', 'HIKARI_MINIMUM-IDLE': '0', 'HIKARI_POOL-BUDGET': '24', 'HIKARI_CONNECTION-TIMEOUT': '5000'})
    java = str(Path(env['JAVA_HOME']) / ('bin/java.exe' if os.name == 'nt' else 'bin/java'))
    classpath = os.pathsep.join((str(target / 'test-classes'), str(target / 'classes'), (target / 'khanh04-classpath.txt').read_text().strip()))
    subprocess.run([java, '-cp', classpath, 'vn.ticketscenter.database.AdminRefundWebFixture'], check=True, env=env)
    args = [java, '-Dcatalina.home=' + str(home), '-Dcatalina.base=' + str(base), '-Djava.io.tmpdir=' + str(base / 'temp'),
            '-cp', os.pathsep.join((str(home / 'bin/bootstrap.jar'), str(home / 'bin/tomcat-juli.jar'))), 'org.apache.catalina.startup.Bootstrap', 'start']
    root = 'http://127.0.0.1:18084/ticketscenter'
    with (target / 'khanh04-tomcat.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(args, stdout=log, stderr=subprocess.STDOUT, env=env)
        try:
            for _ in range(90):
                if process.poll() is not None:
                    raise RuntimeError('Tomcat exited before readiness; inspect target/khanh04-tomcat.log')
                try:
                    with urllib.request.urlopen(root + '/api/auth/csrf', timeout=3) as response:
                        if response.status == 200:
                            break
                except (urllib.error.URLError, TimeoutError, socket.timeout):
                    pass
                time.sleep(1)
            else:
                raise RuntimeError('Full WAR did not become ready')
            fixture = dict(line.split('=', 1) for line in (target / 'khanh04-web-fixture.properties').read_text().splitlines() if line and not line.startswith('#'))
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
                    # Response contains no submitted password; never print request headers or credentials.
                    raise AssertionError(f'{name}: expected {expected}, got {response.code}: {payload[:500]}')
                checks.append({'check': name, 'status': response.code})
                return json.loads(payload) if payload.startswith('{') else payload

            def login(role):
                client = session()
                token = call(client, role + ' csrf', '/api/auth/csrf')['data']['token']
                call(client, role + ' real login', '/api/auth/login', body={'identifier': fixture[role], 'password': env['TC_SCAN_FIXTURE_PASSWORD']}, headers={'Content-Type': 'application/json', 'X-CSRF-Token': token})
                token = call(client, role + ' fresh csrf', '/api/auth/csrf')['data']['token']
                return client, token

            anonymous = session()
            call(anonymous, 'anonymous list denied', '/api/admin/refund-requests', 401)
            anonymous_token = call(anonymous, 'anonymous csrf boundary', '/api/auth/csrf')['data']['token']
            admin, token = login('admin')
            buyer, buyer_token = login('buyer')
            for client, name, csrf in ((buyer, 'customer', buyer_token), (anonymous, 'anonymous', anonymous_token)):
                expected = 403 if name == 'customer' else 401
                for path in ('', '/' + fixture['approve'], '/' + fixture['approve'] + '/decision', '/' + fixture['retry'] + '/retry'):
                    mutation = path.endswith(('/decision', '/retry'))
                    call(client, name + ' denied ' + path, '/api/admin/refund-requests' + path, expected,
                         body=({'decision': 'APPROVE'} if path.endswith('/decision') else {}) if mutation else None,
                         headers={'Content-Type': 'application/json', 'X-CSRF-Token': csrf, 'Idempotency-Key': str(uuid.uuid4())})
            listed = call(admin, 'filtered paginated list', '/api/admin/refund-requests?status=REQUESTED&page=1&pageSize=1')['data']
            assert len(listed['items']) == 1 and listed['pageSize'] == 1 and listed['items'][0]['status'] == 'REQUESTED'
            call(admin, 'invalid paging', '/api/admin/refund-requests?page=0', 400)
            call(admin, 'invalid UUID', '/api/admin/refund-requests/not-an-id', 400)
            assert 'Yêu cầu hoàn tiền' in call(admin, 'list JSP compiled', '/admin/refund-requests')
            assert 'Xử lý yêu cầu hoàn tiền' in call(admin, 'review JSP compiled', '/admin/refund-requests/' + fixture['approve'])
            assert 'Yêu cầu hoàn tiền' in call(admin, 'existing admin navigation', '/admin/refunds/approval')
            assert 'crypto.randomUUID' in call(admin, 'production JS asset', '/assets/refund/admin-refunds.js')
            path = '/api/admin/refund-requests/' + fixture['approve'] + '/decision'
            key = str(uuid.uuid4())
            headers = {'Content-Type': 'application/json', 'X-CSRF-Token': token, 'Idempotency-Key': key}
            body = {'decision': 'APPROVE', 'note': 'HTTP kiểm thử'}
            call(admin, 'csrf required', path, 403, body, {'Content-Type': 'application/json', 'Idempotency-Key': key})
            call(admin, 'UUID key required', path, 400, body, {'Content-Type': 'application/json', 'X-CSRF-Token': token, 'Idempotency-Key': 'short'})
            call(admin, 'JSON type required', path, 415, body, {'Content-Type': 'text/plain', 'X-CSRF-Token': token, 'Idempotency-Key': key})
            first = call(admin, 'DEC-01 approve', path, body=body, headers=headers)['data']
            assert first['status'] == 'APPROVED' and first['canonicalStatus'] == 'PROCESSING'
            assert first == call(admin, 'approve replay', path, body=body, headers=headers)['data']
            call(admin, 'changed payload same key', path, 409, {'decision': 'REJECT', 'rejectReason': 'changed'}, headers)
            detail = call(admin, 'detail after approve', '/api/admin/refund-requests/' + fixture['approve'])['data']
            assert detail['adminNote'] == 'HTTP kiểm thử' and detail['tickets'][0]['status'] == 'REFUND_PENDING' and len(detail['attempts']) == 1
            headers['Idempotency-Key'] = str(uuid.uuid4())
            call(admin, 'DEC-05 processing cannot retry', '/api/admin/refund-requests/' + fixture['approve'] + '/retry', 409, {}, headers)
            reject_path = '/api/admin/refund-requests/' + fixture['reject'] + '/decision'
            call(admin, 'DEC-03 reject needs reason', reject_path, 400, {'decision': 'REJECT', 'rejectReason': ' '}, headers)
            rejected = call(admin, 'DEC-02 reject', reject_path, body={'decision': 'REJECT', 'rejectReason': 'Đã kiểm tra'}, headers=headers)['data']
            assert rejected['status'] == 'REJECTED'
            rejected_detail = call(admin, 'ticket restored', '/api/admin/refund-requests/' + fixture['reject'])['data']
            assert rejected_detail['tickets'][0]['status'] == 'ACTIVE'
            headers['Idempotency-Key'] = str(uuid.uuid4())
            failed_detail = call(admin, 'failed attempt eligible', '/api/admin/refund-requests/' + fixture['retry'])['data']
            retried = call(admin, 'explicit retry', '/api/admin/refund-requests/' + fixture['retry'] + '/retry', body={}, headers=headers)['data']
            assert retried['refundId'] == fixture['retry'] and retried['attemptId'] != failed_detail['currentAttemptId']
            assert retried == call(admin, 'retry replay', '/api/admin/refund-requests/' + fixture['retry'] + '/retry', body={}, headers=headers)['data']
            headers['Idempotency-Key'] = str(uuid.uuid4())
            empty_retry = call(admin, 'explicit retry with empty POST body', '/api/admin/refund-requests/' + fixture['retryEmpty'] + '/retry', headers=headers, method='POST')['data']
            assert empty_retry['refundId'] == fixture['retryEmpty'] and empty_retry['canonicalStatus'] == 'PROCESSING'
            headers['Idempotency-Key'] = str(uuid.uuid4())
            call(admin, 'UNKNOWN cannot retry', '/api/admin/refund-requests/' + fixture['unknown'] + '/retry', 409, {}, headers)
            call(admin, 'internal decision URL blocked', '/api/admin/refund-decision-internal', 404, body, headers)
            (target / 'khanh04-web-acceptance.json').write_text(json.dumps(checks, ensure_ascii=False, indent=2), encoding='utf-8')
            print(f'Full WAR HTTP/JSP acceptance: {len(checks)} checks passed')
            browser_script = os.environ.get('TC_ADMIN_REFUND_BROWSER_SCRIPT')
            if browser_script:
                subprocess.run([env.get('TC_ADMIN_REFUND_NODE', 'node'), browser_script], check=True, env=env, timeout=180)
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)


if __name__ == '__main__':
    main()
