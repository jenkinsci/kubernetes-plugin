param(
    [string]$cookie,
	[string]$csFile,
	[string]$killScript
)

Add-Type -Path $csFile

$failed = $false
$matchedProcessIds = @()
Get-Process | ForEach-Object {
        $id = $_.Id
		$name = $_.ProcessName
		Write-Host "Trying process $id with name $name"
        try {
    		$envBlock = [ProcessEnvironmentReader]::ReadEnvironmentBlock($id)
            if (($envBlock.Contains("JENKINS_SERVER_COOKIE=$($cookie)")) -and ($id -ne $PID)) {
				Write-Host "Added process $name with id: $id to the list of processes to kill"
				$matchedProcessIds += $id

        	}
    	} catch {
    		# Do nothing
		}

}
foreach ($processId in $matchedProcessIds) {
	& $killScript -processId $processId
}
if ($failed) {
	exit 1
} else {
	exit 0
}