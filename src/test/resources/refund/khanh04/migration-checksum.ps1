# Git converts LF/CRLF on checkout. Accept only that equivalent representation;
# any other file edit changes the hash and still fails the immutable-history gate.
function Get-Khanh04MigrationHashes([string]$Path){
 $resolved=(Resolve-Path -LiteralPath $Path).Path
 $text=[IO.File]::ReadAllText($resolved).Replace("`r`n","`n")
 $encoding=[Text.UTF8Encoding]::new($false);$sha=[Security.Cryptography.SHA256]::Create()
 try{
  foreach($bytes in @([IO.File]::ReadAllBytes($resolved),$encoding.GetBytes($text),$encoding.GetBytes($text.Replace("`n","`r`n")))){
   [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-','').ToLowerInvariant()
  }
 }finally{$sha.Dispose()}
}
