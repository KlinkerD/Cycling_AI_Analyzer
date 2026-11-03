param(
    [Parameter(Mandatory = $true)]
    [string]$ApiKey,

    [string[]]$AthleteIds = @("i547322", "547322"),

    [string]$Oldest = "2026-01-01",

    [string]$Newest = (Get-Date -Format "yyyy-MM-dd"),

    [int]$TimeoutSec = 30
)

$ErrorActionPreference = "Stop"
$ApiKey = $ApiKey.Trim()

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    throw "ApiKey darf nicht leer sein."
}

$normalizedAthleteIds = @()
foreach ($entry in $AthleteIds) {
    foreach ($part in ($entry -split ",")) {
        $candidate = $part.Trim()
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            $normalizedAthleteIds += $candidate
        }
    }
}

if ($normalizedAthleteIds.Count -eq 0) {
    throw "Mindestens eine Athlete-ID muss gesetzt sein."
}

$authVariants = @(
    @{
        Name = "key-colon"
        Value = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${ApiKey}:"))
    },
    @{
        Name = "API-colon"
        Value = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("API:${ApiKey}"))
    }
)

$results = New-Object System.Collections.Generic.List[object]

foreach ($id in $normalizedAthleteIds) {
    foreach ($auth in $authVariants) {
        $url = "https://intervals.icu/api/v1/athlete/$id/activities?oldest=$Oldest&newest=$Newest"

        try {
            $response = Invoke-WebRequest -Method Get -Uri $url -Headers @{ Authorization = $auth.Value } -UseBasicParsing -TimeoutSec $TimeoutSec
            $results.Add([pscustomobject]@{
                    athleteId = $id
                    authMode  = $auth.Name
                    status    = [int]$response.StatusCode
                    ok        = $true
                    detail    = "OK"
                })
        }
        catch {
            $statusCode = $null
            $detail = $_.Exception.Message

            if ($_.Exception.Response) {
                try {
                    $statusCode = [int]$_.Exception.Response.StatusCode
                }
                catch {
                    $statusCode = -1
                }

                try {
                    $stream = $_.Exception.Response.GetResponseStream()
                    if ($stream) {
                        $reader = New-Object System.IO.StreamReader($stream)
                        $body = $reader.ReadToEnd()
                        if (-not [string]::IsNullOrWhiteSpace($body)) {
                            $detail = $body
                        }
                    }
                }
                catch {
                    # Fallback: belasse die Exception-Message als Detail.
                }
            }

            $results.Add([pscustomobject]@{
                    athleteId = $id
                    authMode  = $auth.Name
                    status    = $statusCode
                    ok        = $false
                    detail    = $detail
                })
        }
    }
}

$results | Format-Table athleteId, authMode, status, ok -AutoSize

$success = $results | Where-Object { $_.ok -eq $true }
if ($success.Count -gt 0) {
    Write-Host "`nMindestens eine Kombination war erfolgreich." -ForegroundColor Green
    exit 0
}

Write-Host "`nKeine Kombination war erfolgreich. Bitte API-Key und Athlete-ID im intervals.icu-Account pruefen." -ForegroundColor Yellow
$results | Select-Object athleteId, authMode, status, detail | Format-List
exit 1

