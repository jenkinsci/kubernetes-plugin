param(
    [string]$cookie
)

Add-Type -Language CSharp -TypeDefinition @"
using System;
using System.Text;
using System.Diagnostics;
using System.ComponentModel;
using System.Runtime.InteropServices;

public class ProcessEnvironmentReader
{
    [StructLayout(LayoutKind.Sequential)]
    public struct PROCESS_BASIC_INFORMATION
    {
        public IntPtr Reserved1;
        public IntPtr PebBaseAddress;
        public IntPtr Reserved2_0;
        public IntPtr Reserved2_1;
        public IntPtr UniqueProcessId;
        public IntPtr Reserved3;
    }

    [DllImport("ntdll.dll")]
    private static extern int NtQueryInformationProcess(
        IntPtr ProcessHandle,
        int ProcessInformationClass,
        ref PROCESS_BASIC_INFORMATION ProcessInformation,
        uint ProcessInformationLength,
        out uint ReturnLength);

    [DllImport("kernel32.dll")]
    private static extern IntPtr OpenProcess(
        uint dwDesiredAccess,
        bool bInheritHandle,
        int dwProcessId);

    [DllImport("kernel32.dll")]
    private static extern bool ReadProcessMemory(
        IntPtr hProcess,
        IntPtr lpBaseAddress,
        byte[] lpBuffer,
        int dwSize,
        out IntPtr lpNumberOfBytesRead);

    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool CloseHandle(IntPtr hHandle);

    private const uint PROCESS_QUERY_INFORMATION = 0x0400;
    private const uint PROCESS_VM_READ = 0x0010;
    private const int ProcessBasicInformation = 0;

    public static string ReadEnvironmentBlock(int pid)
    {
        IntPtr hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, pid);
        if (hProcess == IntPtr.Zero)
            throw new Win32Exception(Marshal.GetLastWin32Error());

        try
        {
            PROCESS_BASIC_INFORMATION pbi = new PROCESS_BASIC_INFORMATION();
            uint tmp;
            int status = NtQueryInformationProcess(hProcess, ProcessBasicInformation, ref pbi, (uint)Marshal.SizeOf(pbi), out tmp);
            if (status != 0)
                throw new Win32Exception("NtQueryInformationProcess failed");

            // Offsets for Environment variables are different on 32/64 bit
            // The following offsets are for Windows x64 - for x86 some offsets would need adjusting!
            // PEB is at pbi.PebBaseAddress
            // In PEB, offset 0x20 (Win10 x64, might differ!) is ProcessParameters
            byte[] procParamsPtr = new byte[IntPtr.Size];
            IntPtr bytesRead;
            IntPtr processParametersAddr;

            // Offset to ProcessParameters
            int offsetProcessParameters = 0x20;
            if (!ReadProcessMemory(hProcess, pbi.PebBaseAddress + offsetProcessParameters, procParamsPtr, procParamsPtr.Length, out bytesRead))
                throw new Win32Exception("ReadProcessMemory (ProcessParameters) failed");

            processParametersAddr = (IntPtr)BitConverter.ToInt64(procParamsPtr, 0);

            // Offset in RTL_USER_PROCESS_PARAMETERS for Environment = 0x80 (x64)!
            int offsetEnvironment = 0x80;
            byte[] environmentPtr = new byte[IntPtr.Size];

            if (!ReadProcessMemory(hProcess, processParametersAddr + offsetEnvironment, environmentPtr, environmentPtr.Length, out bytesRead))
                throw new Win32Exception("ReadProcessMemory (Environment) failed");

            IntPtr environmentAddr = (IntPtr)BitConverter.ToInt64(environmentPtr, 0);

            // Read an arbitrary chunk (say, 32 KB) where env block should fit
            int envSize = 0x8000;
            byte[] envData = new byte[envSize];
            if (!ReadProcessMemory(hProcess, environmentAddr, envData, envData.Length, out bytesRead))
                throw new Win32Exception("ReadProcessMemory (Environment data) failed");

            // Environment block is Unicode, ends with two 0 chars.
            string env = Encoding.Unicode.GetString(envData);
            int end = env.IndexOf("\0\0");

            if (end > -1)
                env = env.Substring(0, end);

            return env.Replace('\0', '\n');
        }
        finally
        {
            CloseHandle(hProcess);
        }
    }
}
"@

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
        	} else {
        		Write-Host "Cookie does not match"
        	}
    	} catch {
    		Write-Error "Failed to read environment variables: $id"
		}

}
if ($failed) {
	exit 1
} else {
	exit 0
}