"""Complete unchanged WAR, native restricted WORKER login, timeout and real Tomcat restart.

All database writes are guarded by the named loopback fixture. A fresh base never touches 18081.
"""
import hashlib
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
    env.pop('TC_CONFIG_FILE', None)  # Never read the operator's private project configuration.
    if not env['TC_SCAN_TEST_URL'].startswith('jdbc:sqlserver://127.0.0.1:') or 'databaseName=TicketCenter_Khanh02_Test;' not in env['TC_SCAN_TEST_URL']:
        raise RuntimeError('Dedicated loopback fixture required')
    with socket.socket() as check:
        if check.connect_ex(('127.0.0.1', 18085)) == 0:
            raise RuntimeError('Acceptance port already in use')
    target = Path('target').resolve()
    runtime = target / ('khanh05-web-' + uuid.uuid4().hex)
    runtime.mkdir()
    home = Path(env.get('TC_KHANH05_TOMCAT_HOME', runtime / 'apache-tomcat-11.0.25')).resolve()
    if not home.exists():
        url = 'https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.25/bin/apache-tomcat-11.0.25.zip'
        with urllib.request.urlopen(url, timeout=60) as response:
            archive = response.read()
        with urllib.request.urlopen(url + '.sha512', timeout=30) as response:
            expected = response.read().decode('ascii').split()[0].lower()
        if hashlib.sha512(archive).hexdigest() != expected:
            raise RuntimeError('Tomcat checksum mismatch')
        zip_path = runtime / 'tomcat.zip'
        zip_path.write_bytes(archive)
        with zipfile.ZipFile(zip_path) as package:
            for entry in package.infolist():
                if not (runtime / entry.filename).resolve().is_relative_to(runtime):
                    raise RuntimeError('Archive escaped test runtime')
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
            connector.set('port', '18085')
            connector.set('address', '127.0.0.1')
    tree.write(config, encoding='utf-8', xml_declaration=True)
    deployment = base / 'webapps/ticketscenter'
    with zipfile.ZipFile(target / 'ticketscenter.war') as package:
        for entry in package.infolist():
            if not (deployment / entry.filename).resolve().is_relative_to(deployment):
                raise RuntimeError('WAR escaped test deployment')
        package.extractall(deployment)
    java = str(Path(env['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
    classpath = os.pathsep.join((str(target / 'test-classes'), str(target / 'classes'), (target / 'khanh05-classpath.txt').read_text().strip()))

    def probe(mode, capture=False):
        return subprocess.run([java, '-cp', classpath, 'vn.ticketscenter.database.RefundWorkerWebFixture', mode], env=env, timeout=25, capture_output=capture, text=True)

    probe('prepare').check_returncode()
    env['TC_KHANH05_WORKER_USER'] = 'khanh05_worker_' + uuid.uuid4().hex
    env['TC_KHANH05_WORKER_PASSWORD'] = 'K05!' + secrets.token_hex(24)
    local_login = os.name == 'nt' and env.get('GITHUB_ACTIONS') != 'true'
    login_created = False
    process = None
    try:
        if local_login:
            subprocess.run(['pwsh', '-NoProfile', '-File', 'src/test/resources/refund/khanh05/local-worker-login.ps1'], env=env, check=True, timeout=30)
        else:
            print('::add-mask::' + env['TC_KHANH05_WORKER_PASSWORD'])
            subprocess.run([java, '-cp', classpath, 'vn.ticketscenter.database.RefundWorkerWebLogin'], env=env, check=True, timeout=30)
        login_created = True
        env.update(DB_HOST='127.0.0.1', DB_PORT='1433', DB_NAME='TicketCenter_Khanh02_Test', DB_URL=env['TC_SCAN_TEST_URL'], DB_TRUSTSERVERCERTIFICATE='true', ADMIN_SEED_ENABLED='false', MAIL_SMTP_HOST='127.0.0.1', MAIL_SMTP_PORT='18825')
        env['DB_USER'] = env['TC_SCAN_OWNER_USER']
        env['DB_PASSWORD'] = env['TC_SCAN_OWNER_PASSWORD']
        for role in ('BUYER', 'MANAGER', 'CHECK_IN', 'ADMIN', 'AUTH', 'WORKER'):
            env['DB_' + role + '_USER'] = env['TC_KHANH05_WORKER_USER'] if role == 'WORKER' else env['TC_SCAN_OWNER_USER']
            env['DB_' + role + '_PASSWORD'] = env['TC_KHANH05_WORKER_PASSWORD'] if role == 'WORKER' else env['TC_SCAN_OWNER_PASSWORD']
        env.update({'HIKARI_MAXIMUM-POOL-SIZE': '3', 'HIKARI_MINIMUM-IDLE': '0', 'HIKARI_POOL-BUDGET': '24', 'HIKARI_CONNECTION-TIMEOUT': '5000'})
        env['REFUND_SIMULATED_STATE_DIRECTORY'] = str(runtime / 'receipts')
        env['REFUND_SIMULATED_OUTCOME'] = 'SUCCEEDED'
        env['REFUND_SIMULATED_TIMEOUTAFTERSIDEEFFECT'] = 'true'
        args = [java, '-Dcatalina.home=' + str(home), '-Dcatalina.base=' + str(base), '-Djava.io.tmpdir=' + str(base / 'temp'), '-cp', os.pathsep.join((str(home / 'bin/bootstrap.jar'), str(home / 'bin/tomcat-juli.jar'))), 'org.apache.catalina.startup.Bootstrap', 'start']
        kwargs = {'creationflags': subprocess.CREATE_NO_WINDOW} if os.name == 'nt' else {}

        def stop():
            nonlocal process
            if process is not None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
                process = None

        for phase in ('unknown', 'complete'):
            if phase == 'complete':
                probe('due').check_returncode()  # Advance the test-only reconciliation clock.
                env['REFUND_SIMULATED_OUTCOME'] = 'FAILED'  # Persisted SUCCEEDED receipt must win.
                env['REFUND_SIMULATED_TIMEOUTAFTERSIDEEFFECT'] = 'false'
            with (target / ('khanh05-tomcat-' + phase + '.log')).open('w', encoding='utf-8') as log:
                process = subprocess.Popen(args, env=env, stdout=log, stderr=subprocess.STDOUT, **kwargs)
                try:
                    for _ in range(60):
                        if process.poll() is not None:
                            raise RuntimeError('Tomcat exited; inspect acceptance log')
                        try:
                            with urllib.request.urlopen('http://127.0.0.1:18085/ticketscenter/api/auth/csrf', timeout=2) as response:
                                if response.status == 200:
                                    break
                        except (urllib.error.URLError, TimeoutError, socket.timeout):
                            pass
                        time.sleep(2)
                    else:
                        raise RuntimeError('Complete WAR did not start')
                    for _ in range(30):
                        checked = probe(phase, capture=True)
                        if checked.returncode == 0:
                            print(checked.stdout.strip())
                            break
                        if checked.returncode != 3:
                            raise RuntimeError('Fixture verification failed; inspect database acceptance')
                        time.sleep(2)
                    else:
                        raise RuntimeError('Scheduled refund worker did not reach expected state')
                finally:
                    stop()
        (target / 'khanh05-web-acceptance.json').write_text(json.dumps({'completeWar': True, 'restrictedWorkerLogin': True, 'timeoutAfterTransfer': 'NEEDS_RECONCILIATION', 'tomcatRestart': 'COMPLETED', 'durableAttempts': 1, 'inventoryReturnedOnce': True}), encoding='utf-8')
        print('KHANH-05 complete WAR and real Tomcat restart acceptance passed.')
    finally:
        if process is not None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        if login_created and local_login:
            subprocess.run(['pwsh', '-NoProfile', '-File', 'src/test/resources/refund/khanh05/local-worker-login.ps1', '-Cleanup'], env=env, check=True, timeout=30)


if __name__ == '__main__':
    main()
