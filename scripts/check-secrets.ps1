$ErrorActionPreference = "Stop"

$TrackedFiles = & git ls-files
if ($LASTEXITCODE -ne 0) {
    throw "Unable to list tracked files."
}

$SensitiveFiles = $TrackedFiles | Where-Object {
    $_ -match '(^|/)(secrets\.env|local\.properties)$' -or
    $_ -match '\.(jks|keystore|p12)$'
}
if ($SensitiveFiles) {
    Write-Error "Tracked sensitive files are not allowed:`n$($SensitiveFiles -join "`n")"
}

$Pattern = '(github_pat_[A-Za-z0-9_]{40,}|ghp_[A-Za-z0-9]{36,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)'
$Matches = & git grep -n -E -e $Pattern -- ':!scripts/check-secrets.ps1'
if ($LASTEXITCODE -eq 0) {
    Write-Error "Possible credential material detected:`n$($Matches -join "`n")"
}
if ($LASTEXITCODE -ne 1) {
    throw "Credential scan failed with exit code $LASTEXITCODE."
}

Write-Host "No tracked release keys or high-confidence token patterns detected."
