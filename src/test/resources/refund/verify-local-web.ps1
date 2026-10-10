$ErrorActionPreference='Stop'
$base='http://127.0.0.1:18081/ticketscenter'
$fixture=@{}
Get-Content target/khanh03-web-fixture.properties | ForEach-Object{if($_ -match '^([^#=]+)=(.*)$'){$fixture[$Matches[1]]=$Matches[2]}}
$results=[Collections.Generic.List[object]]::new()
function Check($name,$response,$expected){if([int]$response.StatusCode -ne $expected){throw "$name expected $expected, got $($response.StatusCode): $($response.Content)"};$results.Add([pscustomobject]@{check=$name;status=[int]$response.StatusCode})}
function Login($user){
 $session=[Microsoft.PowerShell.Commands.WebRequestSession]::new();$csrf=Invoke-RestMethod "$base/api/auth/csrf" -WebSession $session
 $body=@{identifier=$user;password='K03Fixture!25'} | ConvertTo-Json -Compress
 $login=Invoke-WebRequest "$base/api/auth/login" -Method Post -ContentType application/json -Body $body -WebSession $session -Headers @{'X-CSRF-Token'=$csrf.data.token} -SkipHttpErrorCheck
 Check 'fixture login' $login 200;return $session
}
Check 'anonymous denied' (Invoke-WebRequest "$base/api/me/refund-requests" -SkipHttpErrorCheck) 401
$buyer=Login $fixture.buyer;$other=Login $fixture.other
$orgRoute="$base/api/organizations/$($fixture.order)"
Check 'organization reports anonymous denied' (Invoke-WebRequest "$orgRoute/reports" -SkipHttpErrorCheck) 401
Check 'organization reports reach original filter validation' (Invoke-WebRequest "$orgRoute/reports" -WebSession $buyer -SkipHttpErrorCheck) 400
Check 'other organization routes retain event routing' (Invoke-WebRequest "$orgRoute/events" -WebSession $buyer -SkipHttpErrorCheck) 404
Check 'owner tickets' (Invoke-WebRequest "$base/api/orders/$($fixture.order)/refundable-tickets" -WebSession $buyer -SkipHttpErrorCheck) 200
Check 'other owner denied' (Invoke-WebRequest "$base/api/orders/$($fixture.order)/refundable-tickets" -WebSession $other -SkipHttpErrorCheck) 403
$page=Invoke-WebRequest "$base/refund-requests?orderId=$($fixture.order)" -WebSession $buyer -SkipHttpErrorCheck
Check 'request JSP compiled' $page 200;if(!$page.Content.Contains('Yêu cầu hoàn vé')){throw 'Wrong request page'}
Check 'existing navigation resolves real page' (Invoke-WebRequest "$base/refund-request?orderId=$($fixture.order)" -WebSession $buyer -SkipHttpErrorCheck) 200
$csrf=Invoke-RestMethod "$base/api/auth/csrf" -WebSession $buyer
$key=[guid]::NewGuid().ToString();$headers=@{'X-CSRF-Token'=$csrf.data.token;'Idempotency-Key'=$key}
$body=@{orderId=$fixture.order;ticketIds=@($fixture.tickets.Split(','));reason='Không thể tham dự sự kiện.'} | ConvertTo-Json -Compress
Check 'bad csrf denied' (Invoke-WebRequest "$base/api/refund-requests" -Method Post -ContentType application/json -Body $body -WebSession $buyer -Headers @{'Idempotency-Key'=$key} -SkipHttpErrorCheck) 403
$created=Invoke-WebRequest "$base/api/refund-requests" -Method Post -ContentType application/json -Body $body -WebSession $buyer -Headers $headers -SkipHttpErrorCheck
Check 'valid request created' $created 201;$data=($created.Content | ConvertFrom-Json).data
if($data.totalRefundAmount -ne '320002' -or $data.status -ne 'REQUESTED' -or $data.ticketCount -ne 2){throw 'Incorrect server refund amount or state'}
$replay=Invoke-WebRequest "$base/api/refund-requests" -Method Post -ContentType application/json -Body $body -WebSession $buyer -Headers $headers -SkipHttpErrorCheck
Check 'same key replay' $replay 201;if($created.Content -ne $replay.Content){throw 'Replay changed response'}
$headers['Idempotency-Key']=[guid]::NewGuid().ToString()
Check 'another key cannot duplicate frozen tickets' (Invoke-WebRequest "$base/api/refund-requests" -Method Post -ContentType application/json -Body $body -WebSession $buyer -Headers $headers -SkipHttpErrorCheck) 409
Check 'owner detail' (Invoke-WebRequest "$base/api/refund-requests/$($data.refundId)" -WebSession $buyer -SkipHttpErrorCheck) 200
Check 'other owner cannot read detail' (Invoke-WebRequest "$base/api/refund-requests/$($data.refundId)" -WebSession $other -SkipHttpErrorCheck) 403
Check 'owner attempts' (Invoke-WebRequest "$base/api/refund-requests/$($data.refundId)/attempts" -WebSession $buyer -SkipHttpErrorCheck) 200
Check 'my refund list' (Invoke-WebRequest "$base/api/me/refund-requests" -WebSession $buyer -SkipHttpErrorCheck) 200
Check 'tracking JSP compiled' (Invoke-WebRequest "$base/my-refunds" -WebSession $buyer -SkipHttpErrorCheck) 200
$results | ConvertTo-Json | Set-Content target/khanh03-web-acceptance.json
$results | Format-Table
