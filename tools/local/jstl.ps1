#Requires -Version 7.4
param([Parameter(Mandatory=$true)][string]$LibDirectory)
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $LibDirectory -Force | Out-Null
# Maven Central JARs checked against their published SHA1 on 2026-10-11;
# pin the stronger SHA512 here rather than depending on absent .sha512 endpoints.
$artifacts=@(
 @{path='jakarta/servlet/jsp/jstl/jakarta.servlet.jsp.jstl-api/3.0.1';name='jakarta.servlet.jsp.jstl-api-3.0.1.jar';sha512='1ff94640398941e0f9de590d45fcaf836c07a308ffb8e2dbd73cfe2d82a5d09e44eaf39a6743d7973c644cbe3e17686ec9ee6e8efc88ebc4b23527812cf192c0'},
 @{path='org/glassfish/web/jakarta.servlet.jsp.jstl/3.0.1';name='jakarta.servlet.jsp.jstl-3.0.1.jar';sha512='8a34c92752c26d00470027115357ab8c34f9d6007beeaf8e61e81feac8a1cb4c0e1e24906396eda24093be35e78f30ebe24fbf9cb220596b331be6f4fb6aa0f9'}
)
foreach($artifact in $artifacts) {
 $destination=Join-Path $LibDirectory $artifact.name
 if(-not(Test-Path -LiteralPath $destination)) {
  $temporary=Join-Path $LibDirectory ([guid]::NewGuid().ToString('N')+'.part')
  try {
   Invoke-WebRequest ('https://repo.maven.apache.org/maven2/'+$artifact.path+'/'+$artifact.name) -OutFile $temporary
   if((Get-FileHash -LiteralPath $temporary -Algorithm SHA512).Hash -ine $artifact.sha512) {throw 'JSTL checksum mismatch.'}
   Move-Item -LiteralPath $temporary -Destination $destination
  } finally {if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
 }
 if((Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash -ine $artifact.sha512) {throw 'JSTL checksum mismatch.'}
 Write-Output $destination
}
