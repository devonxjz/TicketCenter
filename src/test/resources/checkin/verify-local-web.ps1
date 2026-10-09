$ErrorActionPreference='Stop'
. ./src/test/resources/checkin/load-local.ps1
$base='http://127.0.0.1:18081/ticketscenter'
$results=[Collections.Generic.List[object]]::new()
function Check-Response($name,$response,$expected) {
 if([int]$response.StatusCode -ne $expected){throw "$name expected $expected, got $($response.StatusCode): $($response.Content)"}
 $results.Add([pscustomobject]@{check=$name;status=[int]$response.StatusCode})
}
function Login-Local($identifier) {
 $session=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
 $csrf=Invoke-RestMethod "$base/api/auth/csrf" -WebSession $session
 $body=@{identifier=$identifier;password=$env:TC_CHECKIN_FIXTURE_PASSWORD} | ConvertTo-Json -Compress
 $login=Invoke-WebRequest "$base/api/auth/login" -Method Post -ContentType application/json -Body $body -WebSession $session -Headers @{'X-CSRF-Token'=$csrf.data.token} -SkipHttpErrorCheck
 Check-Response "$identifier login" $login 200
 return $session
}
Check-Response 'anonymous list denied' (Invoke-WebRequest "$base/api/check-ins/events" -SkipHttpErrorCheck) 401
$staff=Login-Local 'khanh01-staff'
$list=Invoke-WebRequest "$base/api/check-ins/events?organizationId=$env:TC_CHECKIN_TEST_ORG_ID" -WebSession $staff -SkipHttpErrorCheck
Check-Response 'staff event list' $list 200
$data=($list.Content | ConvertFrom-Json).data
$open=$data | Where-Object {$_.eventId -eq $env:TC_CHECKIN_TEST_EVENT_ID}
if(!$open.isCheckInOpen -or $open.totalTicketsIssued -ne 3 -or $open.totalCheckedIn -ne 1){throw 'Open event or real ticket counters incorrect'}
foreach($token in @('paid_amount','unit_price','total_amount','passwordHash')){if($list.Content.Contains($token)){throw 'Sensitive field leaked'}}
Check-Response 'window open' (Invoke-WebRequest "$base/api/events/$env:TC_CHECKIN_TEST_EVENT_ID/check-in-window" -WebSession $staff -SkipHttpErrorCheck) 200
Check-Response 'cancelled event denied' (Invoke-WebRequest "$base/api/events/$env:TC_CHECKIN_TEST_CANCELLED_ID/check-in-window" -WebSession $staff -SkipHttpErrorCheck) 400
Check-Response 'foreign organization denied' (Invoke-WebRequest "$base/api/events/$env:TC_CHECKIN_TEST_FOREIGN_ID/check-in-window" -WebSession $staff -SkipHttpErrorCheck) 403
Check-Response 'unknown event' (Invoke-WebRequest "$base/api/events/11111111-1111-1111-1111-111111111111/check-in-window" -WebSession $staff -SkipHttpErrorCheck) 404
Check-Response 'invalid UUID' (Invoke-WebRequest "$base/api/events/invalid/check-in-window" -WebSession $staff -SkipHttpErrorCheck) 400
$page=Invoke-WebRequest "$base/check-ins" -WebSession $staff -SkipHttpErrorCheck
Check-Response 'UI-15 JSP compiled' $page 200
if(!$page.Content.Contains('Chọn sự kiện soát vé')){throw 'JSP content missing'}
$outside=Login-Local 'khanh01-outsider'
Check-Response 'outsider list denied' (Invoke-WebRequest "$base/api/check-ins/events?organizationId=$env:TC_CHECKIN_TEST_ORG_ID" -WebSession $outside -SkipHttpErrorCheck) 403
$manager=Login-Local 'khanh01-manager'
Check-Response 'manager allowed' (Invoke-WebRequest "$base/api/check-ins/events" -WebSession $manager -SkipHttpErrorCheck) 200
$results | ConvertTo-Json | Set-Content -LiteralPath target/khanh01-web-acceptance.json
$results | Format-Table
