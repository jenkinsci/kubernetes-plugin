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
        Write-Host "Attempting graceful termination of process $processId"
        $gracefulShutdown = $false
        
        # Method 1: Try Ctrl+C via console API (most reliable in containers)
        Write-Host "Attempting Ctrl+C termination"
        [ProcessControl]::FreeConsole() | Out-Null
        if ([ProcessControl]::AttachConsole($processId)) {
            # Don't disable our handler
            [ProcessControl]::GenerateConsoleCtrlEvent(0, 0) | Out-Null
            
            # Wait for process to handle signal
            if ($process.WaitForExit(5000)) {
                Write-Host "Process $processId terminated via Ctrl+C"
                $gracefulShutdown = $true
            }
            [ProcessControl]::FreeConsole() | Out-Null
        }
        
        # Method 2: Try Stop-Process with -Force:$false (more graceful)
        if (-not $gracefulShutdown) {
            Write-Host "Attempting Stop-Process termination"
            try {
                Stop-Process -Id $processId -Force:$false -ErrorAction SilentlyContinue
                if ($process.WaitForExit(5000)) {
                    Write-Host "Process $processId terminated via Stop-Process"
                    $gracefulShutdown = $true
                }
            } catch {
                Write-Host "Stop-Process failed: $_"
            }
        }
        
        # Method 3: Last resort - force kill
        if (-not $gracefulShutdown) {
            Write-Host "Graceful termination attempts failed, forcing termination of process $processId"
            try {
                $process.Kill()
                $process.WaitForExit(2000)
                Write-Host "Process $processId forcefully terminated"
            } catch {
                Write-Host "Force termination failed: $_"
            }
        }
    } else {
        Write-Host "Process with ID $processId is not running"
    }
    
    return 0
} catch {
    Write-Host "Error: $($_.Exception.Message)"
    Write-Host "Full Exception: $_"
    Write-Host "Stack Trace: $($_.Exception.StackTrace)"
    return 1
}