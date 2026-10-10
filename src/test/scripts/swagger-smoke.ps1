param(
    [Parameter(Mandatory = $true)][string]$TomcatHome,
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$Port = 0,
    [switch]$FinanceFixture
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$tomcatRoot = (Resolve-Path -LiteralPath $TomcatHome).Path
if (-not $JavaHome) { throw 'Choose JDK 25 using JAVA_HOME or -JavaHome.' }
$javaExe = Join-Path $JavaHome 'bin/java.exe'
$warPath = Join-Path $repoRoot 'target/ticketscenter.war'
foreach ($required in @($javaExe, $warPath, (Join-Path $tomcatRoot 'bin/bootstrap.jar'))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required file is missing: $required" }
}
if ($Port -eq 0) {
    $reservation = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $reservation.Start()
    $Port = $reservation.LocalEndpoint.Port
    $reservation.Stop()
}
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Use an unprivileged TCP port.' }

# Each run gets its own directory. Never overwrite an installed Tomcat or another run.
$runRoot = Join-Path $repoRoot ('target/swagger-smoke/' + [guid]::NewGuid().ToString('N'))
foreach ($directory in @('conf', 'logs', 'temp', 'webapps', 'work')) {
    New-Item -ItemType Directory -Path (Join-Path $runRoot $directory) -Force | Out-Null
}
# Do not inherit Host/Context/Listener/Resource configuration from the installed server.
# Only the Tomcat binaries are reused; all deployed applications belong to this run.
$serverXml = @"
<?xml version="1.0" encoding="UTF-8"?>
<Server port="-1">
  <Service name="Catalina">
    <Connector port="$Port" address="127.0.0.1" protocol="org.apache.coyote.http11.Http11Nio2Protocol" connectionTimeout="5000" />
    <Engine name="Catalina" defaultHost="localhost">
      <Host name="localhost" appBase="webapps" autoDeploy="false" deployOnStartup="true" unpackWARs="false" />
    </Engine>
  </Service>
</Server>
"@
[System.IO.File]::WriteAllText((Join-Path $runRoot 'conf/server.xml'), $serverXml)
[System.IO.File]::WriteAllText((Join-Path $runRoot 'conf/context.xml'), '<Context />')
[System.IO.File]::WriteAllText((Join-Path $runRoot 'conf/logging.properties'), "handlers=java.util.logging.ConsoleHandler`n.level=INFO`njava.util.logging.ConsoleHandler.level=INFO`n")
$defaultWebXml = @'
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.1">
  <servlet><servlet-name>default</servlet-name><servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class><load-on-startup>1</load-on-startup></servlet>
  <servlet-mapping><servlet-name>default</servlet-name><url-pattern>/</url-pattern></servlet-mapping>
  <mime-mapping><extension>html</extension><mime-type>text/html</mime-type></mime-mapping>
  <mime-mapping><extension>js</extension><mime-type>application/javascript</mime-type></mime-mapping>
  <mime-mapping><extension>css</extension><mime-type>text/css</mime-type></mime-mapping>
  <mime-mapping><extension>json</extension><mime-type>application/json</mime-type></mime-mapping>
</web-app>
'@
[System.IO.File]::WriteAllText((Join-Path $runRoot 'conf/web.xml'), $defaultWebXml)

# WAR resources/classes are real. Only lifecycle wiring is restricted for this HTTP boundary fixture.
# No persistence/seeder/worker/SMTP listener is started and no production configuration is changed.
$webXml = @'
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.1" metadata-complete="true">
  <servlet><servlet-name>OpenApi</servlet-name><servlet-class>vn.ticketscenter.config.web.OpenApiServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>OpenApi</servlet-name><url-pattern>/swagger</url-pattern><url-pattern>/swagger/</url-pattern><url-pattern>/api/openapi.json</url-pattern><url-pattern>/swagger/openapi.json</url-pattern></servlet-mapping>
  <servlet><servlet-name>Health</servlet-name><servlet-class>vn.ticketscenter.config.web.HealthServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>Health</servlet-name><url-pattern>/health/live</url-pattern></servlet-mapping>
  <servlet><servlet-name>Ready</servlet-name><servlet-class>vn.ticketscenter.config.web.ReadinessServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>Ready</servlet-name><url-pattern>/health/ready</url-pattern></servlet-mapping>
  <servlet><servlet-name>Csrf</servlet-name><servlet-class>vn.ticketscenter.identity.controller.CsrfServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>Csrf</servlet-name><url-pattern>/api/auth/csrf</url-pattern></servlet-mapping>
  <servlet><servlet-name>Login</servlet-name><servlet-class>vn.ticketscenter.identity.controller.LoginServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>Login</servlet-name><url-pattern>/api/auth/login</url-pattern></servlet-mapping>
  <servlet><servlet-name>Logout</servlet-name><servlet-class>vn.ticketscenter.identity.controller.LogoutServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>Logout</servlet-name><url-pattern>/api/auth/logout</url-pattern></servlet-mapping>
  <servlet><servlet-name>Me</servlet-name><servlet-class>vn.ticketscenter.identity.controller.MeServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>Me</servlet-name><url-pattern>/api/me</url-pattern></servlet-mapping>
  <filter><filter-name>Validation</filter-name><filter-class>vn.ticketscenter.config.web.RequestValidationFilter</filter-class></filter>
  <filter-mapping><filter-name>Validation</filter-name><url-pattern>/api/*</url-pattern></filter-mapping>
  <filter><filter-name>Csrf</filter-name><filter-class>vn.ticketscenter.identity.security.CsrfFilter</filter-class></filter>
  <filter-mapping><filter-name>Csrf</filter-name><url-pattern>/api/*</url-pattern></filter-mapping>
  <filter><filter-name>Session</filter-name><filter-class>vn.ticketscenter.identity.security.SessionValidationFilter</filter-class></filter>
  <filter-mapping><filter-name>Session</filter-name><url-pattern>/api/*</url-pattern></filter-mapping>
  <session-config><cookie-config><http-only>true</http-only></cookie-config></session-config>
</web-app>
'@
if ($FinanceFixture) {
    if (-not $env:TC_FINANCE_HTTP_FIXTURE_TOKEN -or $env:TC_FINANCE_HTTP_FIXTURE_TOKEN.Length -lt 32 -or -not $env:TC_FINANCE_HTTP_IDS -or $env:DB_NAME -notmatch '^TC_Vuong_Test_[A-Za-z0-9_]+$') {
        throw 'Finance HTTP requires an explicit isolated fixture, actors and private control token.'
    }
    $financeWiring = @'
  <servlet><servlet-name>FixtureSession</servlet-name><servlet-class>vn.ticketscenter.settlement.http.FixtureSessionServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>FixtureSession</servlet-name><url-pattern>/__fixture/session</url-pattern></servlet-mapping>
  <servlet><servlet-name>Finance</servlet-name><servlet-class>vn.ticketscenter.settlement.controller.AdminFinanceServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>Finance</servlet-name><url-pattern>/api/admin/organizations/*</url-pattern><url-pattern>/api/admin/commission-rules/*</url-pattern><url-pattern>/api/admin/commission-rules</url-pattern><url-pattern>/api/admin/events/*</url-pattern><url-pattern>/api/admin/settlements/*</url-pattern></servlet-mapping>
  <servlet><servlet-name>Report</servlet-name><servlet-class>vn.ticketscenter.report.controller.ReportServlet</servlet-class></servlet><servlet-mapping><servlet-name>Report</servlet-name><url-pattern>/api/organizations/*</url-pattern></servlet-mapping><servlet-mapping><servlet-name>Report</servlet-name><url-pattern>/api/admin/reports</url-pattern></servlet-mapping><servlet-mapping><servlet-name>Report</servlet-name><url-pattern>/api/reports/export</url-pattern></servlet-mapping><servlet-mapping><servlet-name>Report</servlet-name><url-pattern>/api/reports/options</url-pattern></servlet-mapping><servlet><servlet-name>ReportPage</servlet-name><servlet-class>vn.ticketscenter.report.controller.FinanceReadsidePageServlet</servlet-class></servlet><servlet-mapping><servlet-name>ReportPage</servlet-name><url-pattern>/finance/reports</url-pattern></servlet-mapping><servlet><servlet-name>FinancePage</servlet-name><servlet-class>vn.ticketscenter.settlement.controller.FinancePageServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>FinancePage</servlet-name><url-pattern>/finance/commission</url-pattern><url-pattern>/finance/settlements</url-pattern></servlet-mapping>
  <servlet><servlet-name>FinanceAudit</servlet-name><servlet-class>vn.ticketscenter.audit.controller.AuditServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>FinanceAudit</servlet-name><url-pattern>/api/admin/audit-logs</url-pattern><url-pattern>/api/admin/audit-logs/*</url-pattern></servlet-mapping>
  <servlet><servlet-name>FinanceOverview</servlet-name><servlet-class>vn.ticketscenter.dashboard.controller.DashboardServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>FinanceOverview</servlet-name><url-pattern>/api/admin/overview</url-pattern></servlet-mapping>
  <servlet><servlet-name>FinanceAdminReadsidePage</servlet-name><servlet-class>vn.ticketscenter.dashboard.controller.AdminReadsidePageServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>FinanceAdminReadsidePage</servlet-name><url-pattern>/finance/audit</url-pattern><url-pattern>/finance/overview</url-pattern></servlet-mapping>
  <servlet><servlet-name>jsp</servlet-name><servlet-class>org.apache.jasper.servlet.JspServlet</servlet-class></servlet>
  <servlet-mapping><servlet-name>jsp</servlet-name><url-pattern>*.jsp</url-pattern></servlet-mapping>
'@
    $webXml=$webXml.Replace('</web-app>', $financeWiring + '</web-app>')
}
foreach ($context in @('ROOT', 'ticketscenter')) {
    $applicationPath = Join-Path $runRoot ('webapps/' + $context)
    [System.IO.Compression.ZipFile]::ExtractToDirectory($warPath, $applicationPath)
    [System.IO.File]::WriteAllText((Join-Path $applicationPath 'WEB-INF/web.xml'), $webXml, [System.Text.UTF8Encoding]::new($false))
    if ($FinanceFixture) {
        $fixtureSource=Join-Path $repoRoot 'target/test-classes/vn/ticketscenter/settlement/http/FixtureSessionServlet.class'
        $fixtureTarget=Join-Path $applicationPath 'WEB-INF/classes/vn/ticketscenter/settlement/http/FixtureSessionServlet.class'
        New-Item -ItemType Directory -Path (Split-Path -Parent $fixtureTarget) -Force | Out-Null
        Copy-Item -LiteralPath $fixtureSource -Destination $fixtureTarget
    }
}

$processInfo = [System.Diagnostics.ProcessStartInfo]::new()
$processInfo.FileName = $javaExe
$processInfo.WorkingDirectory = $runRoot
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true
$processInfo.RedirectStandardOutput = $true
$processInfo.RedirectStandardError = $true
foreach ($argument in @(
    "-Dcatalina.home=$tomcatRoot", "-Dcatalina.base=$runRoot",
    "-Djava.io.tmpdir=$(Join-Path $runRoot 'temp')",
    '-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',
    "-Djava.util.logging.config.file=$(Join-Path $runRoot 'conf/logging.properties')",
    '-classpath', ((Join-Path $tomcatRoot 'bin/bootstrap.jar') + ';' + (Join-Path $tomcatRoot 'bin/tomcat-juli.jar')),
    'org.apache.catalina.startup.Bootstrap', 'start'
)) { $processInfo.ArgumentList.Add($argument) }

$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $processInfo
$started = $false
$previousOrigin = $env:TC_SWAGGER_SMOKE_ORIGIN
$previousFinanceOrigin = $env:TC_FINANCE_SMOKE_ORIGIN
try {
    $started = $process.Start()
    if (-not $started) { throw 'Unable to launch the isolated Tomcat.' }
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $origin = "http://127.0.0.1:$Port"
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline -and -not $process.HasExited) {
        try {
            $response = Invoke-WebRequest "$origin/ticketscenter/health/live" -TimeoutSec 2
            if ($response.StatusCode -eq 200) { $ready = $true; break }
        } catch { Start-Sleep -Milliseconds 250 }
    }
    if (-not $ready) { throw "Tomcat did not become ready; see logs in $runRoot" }
    $env:TC_SWAGGER_SMOKE_ORIGIN = $origin
    if ($FinanceFixture) {
        $env:TC_FINANCE_SMOKE_ORIGIN=$origin
        & node --test (Join-Path $repoRoot 'src/test/js/finance-http-smoke.test.cjs')
        if ($LASTEXITCODE -ne 0) { throw 'Finance HTTP fixture tests failed.' }
        Write-Output 'Finance HTTP fixture smoke passed; real finance JDBC and JSP with test-only session bridge; no owner auth/worker/SMTP startup.'
    } else {
        & node --test (Join-Path $repoRoot 'src/test/js/swagger-http-smoke.test.cjs')
        if ($LASTEXITCODE -ne 0) { throw 'Swagger HTTP smoke tests failed.' }
        Write-Output 'Swagger HTTP boundary smoke passed in ROOT and /ticketscenter; DB/SMTP workflows were not initialized.'
    }
} finally {
    $env:TC_SWAGGER_SMOKE_ORIGIN = $previousOrigin
    $env:TC_FINANCE_SMOKE_ORIGIN = $previousFinanceOrigin
    if ($started -and -not $process.HasExited) { $process.Kill($true); $process.WaitForExit(5000) | Out-Null }
    if ($stdout) { [System.IO.File]::WriteAllText((Join-Path $runRoot 'stdout.log'), $stdout.GetAwaiter().GetResult()) }
    if ($stderr) { [System.IO.File]::WriteAllText((Join-Path $runRoot 'stderr.log'), $stderr.GetAwaiter().GetResult()) }
    $process.Dispose()
}
