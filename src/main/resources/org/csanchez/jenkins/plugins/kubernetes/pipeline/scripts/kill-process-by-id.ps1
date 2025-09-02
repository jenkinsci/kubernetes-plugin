param(
    [string]$processId
)

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
using System.Diagnostics;

public class ProcessControl {
    [DllImport("kernel32.dll")]
    public static extern bool AttachConsole(int dwProcessId);
    [DllImport("kernel32.dll")]
    public static extern bool SetConsoleCtrlHandler(ConsoleCtrlDelegate HandlerRoutine, bool Add);
    public delegate bool ConsoleCtrlDelegate(int CtrlType);
    [DllImport("kernel32.dll")]
    public static extern bool GenerateConsoleCtrlEvent(uint dwCtrlEvent, uint dwProcessGroupId);
    [DllImport("kernel32.dll")]
    public static extern bool FreeConsole();
}
"@

try {
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    
    if ($process) {
        $gracefulShutdown = $false
        
        # Method 1: Try Ctrl+C via console API (most reliable in containers)
        [ProcessControl]::FreeConsole() | Out-Null
        if ([ProcessControl]::AttachConsole($processId)) {
            # Don't disable our handler
            [ProcessControl]::GenerateConsoleCtrlEvent(0, 0) | Out-Null
            
            # Wait for process to handle signal
            if ($process.WaitForExit(5000)) {
                $gracefulShutdown = $true
            }
            [ProcessControl]::FreeConsole() | Out-Null
        }
        
        # Method 2: Try Stop-Process with -Force:$false (more graceful than plain Stop-Process)
        if (-not $gracefulShutdown) {
            try {
                Stop-Process -Id $processId -Force:$false -ErrorAction SilentlyContinue
                if ($process.WaitForExit(5000)) {
                    $gracefulShutdown = $true
                }
            } catch {
                # Ignore, this is best effort
            }
        }
        
        # Method 3: Last resort - force kill
        if (-not $gracefulShutdown) {
            try {
                $process.Kill()
                $process.WaitForExit(2000)
                Write-Host "Process $processId forcefully terminated"
            } catch {
                return 1 # Failed to stop the process
            }
        }
    }
    return 0
} catch {
    return 1 #Failed to even get the process
}