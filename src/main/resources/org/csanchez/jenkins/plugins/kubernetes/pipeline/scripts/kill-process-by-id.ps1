param(
    [string]$processId
)

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class CtrlC {
    [DllImport("kernel32.dll")]
    public static extern bool AttachConsole(int dwProcessId);
    [DllImport("kernel32.dll")]
    public static extern bool SetConsoleCtrlHandler(ConsoleCtrlDelegate HandlerRoutine, bool Add);
    [DllImport("kernel32.dll")]
    public static extern bool GenerateConsoleCtrlEvent(uint dwCtrlEvent, uint dwProcessGroupId);
    public delegate bool ConsoleCtrlDelegate(int CtrlType);
    [DllImport("kernel32.dll")]
    public static extern bool FreeConsole();
}
"@
    try {
        if (Get-Process -Id $processId) {
            [CtrlC]::FreeConsole()
            [CtrlC]::SetConsoleCtrlHandler($null, $true)
            $attached = [CtrlC]::AttachConsole($processId)
            [CtrlC]::GenerateConsoleCtrlEvent([int]0, [int]0)
            if (Get-Process -Id $processId) {
                Write-Host "Still alive"
                #To kill also confirmation dialogs
                Start-Sleep -Seconds 5
            }
            if (Get-Process -Id $processId) {
                Write-Host "Still alive"
                #To kill also confirmation dialogs
                Start-Sleep -Seconds 5
            }
            if (Get-Process -Id $processId) {
                Write-Host "Still alive"
                #To kill also confirmation dialogs
                Start-Sleep -Seconds 5
            }
            if (Get-Process -Id $processId) {
                Write-Output "Process with PID $processId is still running."
            }
        } else {
            Write-Output "Process with PID $processId is NOT running."
        }
    } catch {
        Write-Host "Caught exception: $($_.Exception.Message)"
        Write-Host "Full Exception: $_"
        Write-Host "Stack Trace: $($_.Exception.StackTrace)"
    }
