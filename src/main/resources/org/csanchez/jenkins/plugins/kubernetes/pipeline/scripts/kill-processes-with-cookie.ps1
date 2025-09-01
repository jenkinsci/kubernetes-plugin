param(
    [string]$cookie,
    [string]$csFile,
    [string]$killScript
)

Add-Type -Path $csFile
$returnCode = 0
$matchedProcessIds = @()
Get-Process | ForEach-Object {
        $id = $_.Id
        try {
            $envBlock = [ProcessEnvironmentReader]::ReadEnvironmentBlock($id)
            if (($envBlock.Contains("JENKINS_SERVER_COOKIE=$($cookie)")) -and ($id -ne $PID)) {
                $matchedProcessIds += $id
            }
        } catch {
            # Do nothing this is best effort and we expect not to be able to read all processes
        }
}
foreach ($processId in $matchedProcessIds) {
    & $killScript -processId $processId
    $returnCode = $returnCode + $LASTEXITCODE
}
return $returnCode
