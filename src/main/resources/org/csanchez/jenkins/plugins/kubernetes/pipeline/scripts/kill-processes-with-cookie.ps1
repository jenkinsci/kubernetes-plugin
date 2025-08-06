param(
    [string]$cookie,
	[string]$csFile
)

Add-Type -Path $csFile

$failed = $false
Get-Process | ForEach-Object {
        $id = $_.Id
        try {
    		$envBlock = [ProcessEnvironmentReader]::ReadEnvironmentBlock($id)
            if ($envBlock.Contains("JENKINS_SERVER_COOKIE=$($cookie)")) {
            	Write-Host "Killing $($_.ProcessName) (ID: $($_.Id)) - JENKINS_SERVER_COOKIE matches"
            	try {
            		Stop-Process -Id $id -Force
                	Write-Host "Killed."
            	} catch {
                	Write-Host "Failed to kill process: $id"
                	$failed = $true
            	}
        	}
    	} catch {
    		Write-Error "Failed to read environment variables for $id"
		}

}
if ($failed) {
	exit 1
} else {
	exit 0
}