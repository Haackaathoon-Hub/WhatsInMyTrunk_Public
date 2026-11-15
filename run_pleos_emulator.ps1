# ============================================================================
#   HMG Pleos Connect v2.1.0 Official Automotive Emulator Launcher (PowerShell)
#   Auto-Sizing: Dynamically adapts to user's monitor resolution
# ============================================================================

$Host.UI.RawUI.WindowTitle = "HMG Pleos Connect Emulator Launcher"
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host "  HMG Pleos Connect v2.1.0 Official Automotive Emulator Launcher" -ForegroundColor Cyan
Write-Host "  Auto Monitor Screen Sizing & Dynamic Scaling" -ForegroundColor Cyan
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host ""

$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$Adb = "$SdkRoot\platform-tools\adb.exe"
$Emulator = "$SdkRoot\emulator\emulator.exe"
$AvdName = "Pleos_Connect_AVD"
$AvdDir = "$env:USERPROFILE\.android\avd\$AvdName.avd"

# Win32 Window Manager definition for reliable window resizing & centering
Add-Type -TypeDefinition @"
using System;
using System.Text;
using System.Runtime.InteropServices;

public class WindowManager {
    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("user32.dll")]
    public static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);

    public static IntPtr FindEmulatorWindow() {
        IntPtr foundHwnd = IntPtr.Zero;
        EnumWindows((hWnd, lParam) => {
            if (IsWindowVisible(hWnd)) {
                uint pid;
                GetWindowThreadProcessId(hWnd, out pid);
                try {
                    var proc = System.Diagnostics.Process.GetProcessById((int)pid);
                    string name = proc.ProcessName.ToLower();
                    if (name.Contains("qemu") || name.Contains("emulator")) {
                        StringBuilder sb = new StringBuilder(256);
                        GetWindowText(hWnd, sb, 256);
                        string title = sb.ToString();
                        if (!string.IsNullOrEmpty(title) && !title.Contains("Launcher")) {
                            foundHwnd = hWnd;
                            return false;
                        }
                    }
                } catch {}
            }
            return true;
        }, IntPtr.Zero);
        return foundHwnd;
    }

    public static bool ResizeAndCenter(IntPtr hWnd, int x, int y, int width, int height) {
        if (hWnd == IntPtr.Zero) return false;
        // SWP_NOZORDER (0x0004) | SWP_SHOWWINDOW (0x0040)
        return SetWindowPos(hWnd, IntPtr.Zero, x, y, width, height, 0x0044);
    }
}
"@

# Step 1: Detect User Monitor Resolution and Compute Auto Scale
Write-Host "[1/6] Detecting monitor size and calculating auto scale..." -ForegroundColor Yellow
Add-Type -AssemblyName System.Windows.Forms
$screen = [System.Windows.Forms.Screen]::PrimaryScreen
$workW = if ($screen.WorkingArea.Width -gt 0) { $screen.WorkingArea.Width } else { 1536 }
$workH = if ($screen.WorkingArea.Height -gt 0) { $screen.WorkingArea.Height } else { 816 }

# Target: comfortably occupy ~80% width and ~75% height of working area
$scaleW = ($workW * 0.80) / 2560.0
$scaleH = ($workH * 0.75) / 1440.0
$scale = [Math]::Min($scaleW, $scaleH)
$scale = [Math]::Round($scale, 3)

$winW = [int](2560.0 * $scale)
$winH = [int](1440.0 * $scale)
$posX = [int]([Math]::Max(10, ($workW - $winW) / 2))
$posY = [int]([Math]::Max(10, ($workH - $winH) / 2))

Write-Host "      Detected Monitor Working Area: ${workW}x${workH}" -ForegroundColor Gray
Write-Host "      Auto Scale Factor: $scale" -ForegroundColor Gray
Write-Host "      Target Window Geometry: ${winW}x${winH} at (${posX}, ${posY})" -ForegroundColor Green

# Step 2: Clean previous processes and stale locks
Write-Host "[2/6] Cleaning previous emulator processes and lock files..." -ForegroundColor Yellow
Stop-Process -Name qemu-system-x86_64,emulator -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 800
Remove-Item -Path "$AvdDir\*.lock" -Recurse -Force -ErrorAction SilentlyContinue

# Write initial coordinates and scale into emulator-user.ini
$scaleStr = $scale.ToString("0.000000", [System.Globalization.CultureInfo]::InvariantCulture)
@"
window.x = $posX
window.y = $posY
window.scale = $scaleStr
resizable.config.id = -1
posture = 0
"@ | Out-File -FilePath "$AvdDir\emulator-user.ini" -Encoding ascii -Force
Write-Host "      [OK] Pre-configured emulator-user.ini with auto scale." -ForegroundColor Green

# Step 3: Start Emulator GUI with host OpenGL (Intel UHD compatibility)
Write-Host "[3/6] Starting Pleos Connect Automotive Emulator GUI..." -ForegroundColor Yellow
Write-Host "      Renderer: Host Intel UHD Graphics (OpenGL ES 3.0 Translator)" -ForegroundColor Gray
$EmulatorArgs = @(
    "-avd", $AvdName,
    "-gpu", "host",
    "-feature", "-Vulkan",
    "-memory", "3072",
    "-no-snapshot-load"
)
Start-Process -FilePath $Emulator -ArgumentList $EmulatorArgs

# Step 4: Window Watchdog - verify and enforce auto-sizing on user's monitor
Write-Host "      Adjusting window to fit monitor screen perfectly..." -ForegroundColor Gray
$watchLimit = 25
$watchCount = 0
$windowAdjusted = $false
while ($watchCount -lt $watchLimit -and -not $windowAdjusted) {
    Start-Sleep -Seconds 1
    $watchCount++
    $hwnd = [WindowManager]::FindEmulatorWindow()
    if ($hwnd -ne [IntPtr]::Zero) {
        [WindowManager]::ResizeAndCenter($hwnd, $posX, $posY, $winW, $winH) | Out-Null
        $windowAdjusted = $true
        Write-Host "      [OK] Emulator window dynamically fitted: ${winW}x${winH} at (${posX}, ${posY})!" -ForegroundColor Green
    }
}

# Step 5: Wait for ADB device and boot completion
Write-Host "[4/6] Waiting for Pleos Automotive OS to finish booting..." -ForegroundColor Yellow
Write-Host "      (Initial boot usually takes 30~50 seconds. Please wait...)" -ForegroundColor Gray

& $Adb start-server | Out-Null
& $Adb wait-for-device

$BootCompleted = ""
$MaxWaitSec = 120
$Elapsed = 0

while ($BootCompleted -ne "1" -and $Elapsed -lt $MaxWaitSec) {
    Start-Sleep -Seconds 2
    $Elapsed += 2
    $BootCompleted = (& $Adb shell getprop sys.boot_completed 2>$null).Trim()
    Write-Host -NoNewline "."
}
Write-Host ""

if ($BootCompleted -eq "1") {
    Write-Host "      [OK] Pleos Automotive OS is fully booted and operational!" -ForegroundColor Green
} else {
    Write-Host "      [WARNING] Boot check timed out, but proceeding..." -ForegroundColor Yellow
}

# Step 6: Install and launch What's in My Trunk app
Write-Host "[5/6] Installing and launching 'What's in My Trunk' application..." -ForegroundColor Yellow
$ApkPath = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $ApkPath) {
    & $Adb install -r -g $ApkPath | Out-Null
    
    $CurrentUser = (& $Adb shell am get-current-user 2>$null).Trim()
    if ([string]::IsNullOrWhiteSpace($CurrentUser)) { $CurrentUser = "10" }

    & $Adb shell appops set --user $CurrentUser com.pleos.vehiclecontext POST_NOTIFICATION allow 2>$null
    & $Adb shell am start --user $CurrentUser -n "com.pleos.vehiclecontext/.MainActivity" | Out-Null
    Write-Host "      [OK] Application installed and launched in foreground (User $CurrentUser)." -ForegroundColor Green
} else {
    Write-Host "      [NOTICE] APK not found at $ApkPath. Please build project first." -ForegroundColor Red
}

# Step 7: Port forwarding for Pleos Virtual Console
Write-Host "[6/6] Connecting Official Pleos Virtual Console (Port 8088)..." -ForegroundColor Yellow
& $Adb forward tcp:8088 tcp:8088
Start-Sleep -Seconds 1
Start-Process "http://127.0.0.1:8088/"
Write-Host "      [OK] Virtual Console connected and opened in web browser!" -ForegroundColor Green

Write-Host ""
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host "  [ALL READY] Pleos Connect Simulation Environment is Live!" -ForegroundColor Cyan
Write-Host ""
Write-Host "  1. Emulator Display: Pleos Connect IVI screen (${winW}x${winH}) is running." -ForegroundColor White
Write-Host "  2. Virtual Console (Web): http://127.0.0.1:8088/" -ForegroundColor White
Write-Host "     - Convenience Tab: Tailgate (Trunk Open/Close), Doors, Windows" -ForegroundColor Gray
Write-Host "     - Motion Tab: Gear Shift (P / D / R / N)" -ForegroundColor Gray
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host ""
Read-Host "Press Enter to exit this launcher window (Emulator will keep running)"
