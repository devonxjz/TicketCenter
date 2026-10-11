"""Run the packaged production WAR against the disposable CI database only."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


def main():
    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise RuntimeError("This runner is for the disposable GitHub Actions environment")
    url = os.environ["TC_SCAN_TEST_URL"]
    if not url.startswith("jdbc:sqlserver://127.0.0.1:") or "databaseName=TicketCenter_Khanh02_Test;" not in url:
        raise RuntimeError("Dedicated loopback database required")
    target = Path("target").resolve()
    runtime = target / "checkin-ci-web"
    runtime.mkdir()  # Refuse an existing deployment instead of overwriting it.
    archive_url = "https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.25/bin/apache-tomcat-11.0.25.zip"
    with urllib.request.urlopen(archive_url, timeout=60) as response:
        archive = response.read()
    with urllib.request.urlopen(archive_url + ".sha512", timeout=30) as response:
        checksum = response.read().decode("ascii").split()[0].lower()
    if hashlib.sha512(archive).hexdigest() != checksum:
        raise RuntimeError("Tomcat SHA-512 mismatch")
    archive_path = runtime / "tomcat.zip"
    archive_path.write_bytes(archive)
    with zipfile.ZipFile(archive_path) as package:
        for entry in package.infolist():
            if not (runtime / entry.filename).resolve().is_relative_to(runtime):
                raise RuntimeError("Archive path escaped isolated runtime")
        package.extractall(runtime)
    home = runtime / "apache-tomcat-11.0.25"
    base = runtime / "instance"
    shutil.copytree(home / "conf", base / "conf")
    for folder in ("logs", "temp", "work", "webapps"):
        (base / folder).mkdir()
    server_path = base / "conf/server.xml"
    server = ET.parse(server_path)
    server.getroot().set("port", "-1")
    for connector in server.findall("./Service/Connector"):
        if connector.get("protocol") == "HTTP/1.1":
            connector.set("port", "18081")
            connector.set("address", "127.0.0.1")
    server.write(server_path, encoding="utf-8", xml_declaration=True)
    with zipfile.ZipFile(target / "ticketscenter.war") as war:
        deployment = base / "webapps/ticketscenter"
        for entry in war.infolist():
            if not (deployment / entry.filename).resolve().is_relative_to(deployment):
                raise RuntimeError("WAR path escaped isolated deployment")
        war.extractall(deployment)  # No descriptor or application code overlays.
    env = os.environ.copy()
    env.update(DB_HOST="127.0.0.1", DB_PORT="1433", DB_NAME="TicketCenter_Khanh02_Test",
               DB_URL=url, DB_TRUSTSERVERCERTIFICATE="true", ADMIN_SEED_ENABLED="false",
               MAIL_SMTP_HOST="127.0.0.1", MAIL_SMTP_PORT="18825")
    env["DB_USER"] = env["TC_SCAN_OWNER_USER"]
    env["DB_PASSWORD"] = env["TC_SCAN_OWNER_PASSWORD"]
    for role in ("BUYER", "MANAGER", "CHECK_IN", "ADMIN", "AUTH", "WORKER"):
        fixture_role = "CHECKIN" if role == "CHECK_IN" else "OWNER"
        for setting in ("USER", "PASSWORD"):
            env[f"DB_{role}_{setting}"] = env[f"TC_SCAN_{fixture_role}_{setting}"]
    env.update({"HIKARI_MAXIMUM-POOL-SIZE": "3", "HIKARI_MINIMUM-IDLE": "0",
                "HIKARI_POOL-BUDGET": "24", "HIKARI_CONNECTION-TIMEOUT": "5000"})
    java = str(Path(env["JAVA_HOME"]) / "bin/java")
    classpath = os.pathsep.join((str(target / "test-classes"), str(target / "classes"),
                                (target / "khanh02-classpath.txt").read_text().strip()))
    subprocess.run([java, "-cp", classpath,
                    "vn.ticketscenter.fulfillment.checkin.integration.ScanWebFixture"], check=True, env=env)
    args = [java, f"-Dcatalina.home={home}", f"-Dcatalina.base={base}", f"-Djava.io.tmpdir={base / 'temp'}",
            "-cp", os.pathsep.join((str(home / "bin/bootstrap.jar"), str(home / "bin/tomcat-juli.jar"))),
            "org.apache.catalina.startup.Bootstrap", "start"]
    with (target / "khanh02-ci-tomcat.log").open("w") as log:
        process = subprocess.Popen(args, stdout=log, stderr=subprocess.STDOUT, env=env)
        try:
            for _ in range(60):
                if process.poll() is not None:
                    raise RuntimeError("Tomcat exited before application readiness; inspect the CI log")
                try:
                    with urllib.request.urlopen("http://127.0.0.1:18081/ticketscenter/api/auth/csrf", timeout=3) as response:
                        if response.status == 200:
                            break
                except (urllib.error.URLError, TimeoutError):
                    pass
                time.sleep(2)
            else:
                raise RuntimeError("Full production WAR did not become ready")
            subprocess.run(["python3", "src/test/resources/checkin/khanh02/web-acceptance.py"],
                           check=True, env=env, timeout=120)
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)


if __name__ == "__main__":
    main()
