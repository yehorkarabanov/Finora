<#
.SYNOPSIS
  End-to-end check that Finora data is excluded from cloud backup and device-to-device transfer
  (design section 8.4, item 3).

.DESCRIPTION
  Builds and installs a DEBUG build with -PbackupVerification=true (needed for run-as and so that
  allowBackup is true; the exclusion rules are what is under test). Sets the theme to Dark so a
  row exists in app_setting, backs the app up, uninstalls, reinstalls, and checks that nothing
  was restored.

  Requires an emulator or device on Android 12+ with Google APIs and the backup transports.
  If the D2D transport is unavailable the D2D leg is reported as NOT RUN, never as passed.
  A normal-mode PASS is only meaningful for a leg whose -PositiveControl run also succeeded on the
  same image. If the positive control cannot see the theme survive, the leg is INCONCLUSIVE.
  Exit codes: 0 all legs conclusive, 2 at least one leg NOT RUN or INCONCLUSIVE.

.PARAMETER PositiveControl
  Temporarily replaces data_extraction_rules.xml with empty sections, runs the same checks, and
  expects the theme to SURVIVE reinstall. This proves the script can detect a leak. The original
  file is always restored. Run once when the script is written or changed.

.EXAMPLE
  .\scripts\verify-backup-exclusion.ps1
  .\scripts\verify-backup-exclusion.ps1 -PositiveControl
#>
param(
    [switch]$PositiveControl,
    [string]$Serial
)

$ErrorActionPreference = 'Stop'
$pkg = 'com.yehor.finora'
$root = Split-Path -Parent $PSScriptRoot
$rules = Join-Path $root 'app/src/main/res/xml/data_extraction_rules.xml'

if (-not $env:JAVA_HOME) {
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
}
$sdk = $env:ANDROID_HOME
if (-not $sdk) {
    $line = Select-String -Path (Join-Path $root 'local.properties') -Pattern '^sdk.dir=' | Select-Object -First 1
    $sdk = $line.Line.Split('=', 2)[1].Replace('\\', '\').Replace('\:', ':')
}
$adbExe = Join-Path $sdk 'platform-tools/adb.exe'

function Adb {
    if ($Serial) { & $adbExe -s $Serial @args } else { & $adbExe @args }
}

function Adb-Out {
    # Capture stdout as text, joined with newlines.
    ((Adb @args 2>&1) | Out-String).Trim()
}

function Tap-Text([string]$text) {
    Adb shell uiautomator dump /sdcard/ui.xml | Out-Null
    $xmlPath = Join-Path $env:TEMP 'finora-ui.xml'
    Adb pull /sdcard/ui.xml $xmlPath | Out-Null
    $doc = [xml](Get-Content -Raw $xmlPath)
    $node = $doc.SelectNodes('//node') | Where-Object { $_.text -eq $text -or $_.'content-desc' -eq $text } | Select-Object -First 1
    if (-not $node) { throw "UI element '$text' not found" }
    if ($node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { throw "Bad bounds: $($node.bounds)" }
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Adb shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 800
}

function Test-ThemeRowPresent {
    # The row (key "theme_mode", value "dark") is stored adjacent in the record, either in the main
    # file or in the WAL, so search both for the byte sequence.
    $found = $false
    $serialArg = if ($Serial) { "-s $Serial" } else { '' }
    foreach ($file in 'finora.db', 'finora.db-wal') {
        $tmp = Join-Path $env:TEMP "finora-$file"
        Remove-Item $tmp -ErrorAction SilentlyContinue
        # cmd.exe does the redirect because Windows PowerShell 5.1 would re-encode binary output.
        cmd /c "`"$adbExe`" $serialArg exec-out run-as $pkg cat databases/$file > `"$tmp`" 2>nul"
        if ((Test-Path $tmp) -and (Get-Item $tmp).Length -gt 0) {
            $text = [System.Text.Encoding]::GetEncoding(28591).GetString([System.IO.File]::ReadAllBytes($tmp))
            if ($text.Contains('theme_modedark')) { $found = $true }
        }
    }
    return $found
}

function Install-Apks {
    param($paths)
    Adb install-multiple -t --user 0 @paths | Out-Null
}

function Run-Leg {
    param([string]$Name, [scriptblock]$SelectTransport, [scriptblock]$Cleanup)

    Write-Host "`n=== $Name ===" -ForegroundColor Cyan
    Adb logcat -c
    $ok = & $SelectTransport
    if (-not $ok) { return 'NOT RUN' }

    $result = Adb-Out shell bmgr backupnow $pkg
    Write-Host $result
    $pkgRe = [regex]::Escape($pkg)
    if ($result -match "Package $pkgRe with result: Success") {
        Write-Host "$Name : backup ran (data was offered to the transport)."
    } elseif (-not $PositiveControl -and $result -match "Package $pkgRe with result: Transport rejected package") {
        # Expected when every domain is excluded: the app has nothing to back up (PFTBT -1002).
        Write-Host "$Name : transport rejected the package because there is no data to back up."
    } else {
        & $Cleanup
        throw "$Name : unexpected backup result"
    }

    # Pull the installed APKs, uninstall, reinstall the same APKs.
    $local = @()
    $i = 0
    foreach ($line in (Adb-Out shell pm path $pkg) -split "`n") {
        $i++
        $dest = Join-Path $env:TEMP "finora-backup-$i.apk"
        Adb pull ($line.Trim() -replace '^package:', '') $dest | Out-Null
        $local += $dest
    }
    Adb shell pm uninstall --user 0 $pkg | Out-Null
    Install-Apks $local
    Adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds 5
    & $Cleanup

    $themePresent = Test-ThemeRowPresent
    $restoreLog = Adb-Out logcat -d -s BackupManagerService:V RestoreManager:V FullRestoreEngine:V
    Write-Host ($restoreLog -split "`n" | Select-String $pkg | Select-Object -First 10 | Out-String)

    if ($PositiveControl) {
        if ($themePresent) { Write-Host "$Name : theme survived reinstall, leak detected as expected." -ForegroundColor Green; return 'PASS (positive control)' }
        # The restore path of this transport did not fire even with no exclusions, so this leg cannot
        # tell a leak from an exclusion on this image. Report it as inconclusive, never as a pass.
        Write-Warning "$Name : positive control FAILED. Theme did not survive with rules removed, so this leg cannot detect a leak here."
        return 'INCONCLUSIVE (cannot detect leaks on this image)'
    }
    if ($themePresent) { throw "$Name : LEAK. Theme row was restored after reinstall." }
    Write-Host "$Name : no restored data." -ForegroundColor Green
    return 'PASS'
}

# ---- Prepare ---------------------------------------------------------------------------------
$backupFile = Join-Path $env:TEMP 'finora-data_extraction_rules.xml.bak'
try {
    if ($PositiveControl) {
        Copy-Item $rules $backupFile -Force
        Set-Content -Path $rules -Encoding utf8 -Value @'
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup />
    <device-transfer />
</data-extraction-rules>
'@
        Write-Host 'POSITIVE CONTROL: exclusion rules temporarily removed.' -ForegroundColor Yellow
    }

    Push-Location $root
    & .\gradlew.bat :app:assembleDebug -PbackupVerification=true --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    Pop-Location

    $apk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
    Adb uninstall $pkg 2>$null | Out-Null
    Adb install -t $apk | Out-Null

    # Open the app and change the theme to Dark so a row exists in app_setting.
    Adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds 4
    Tap-Text 'More'
    Tap-Text 'Settings'
    Tap-Text 'Dark'
    Start-Sleep -Seconds 1

    $dbs = Adb-Out shell run-as $pkg ls databases
    Write-Host "databases/: $dbs"
    if ($dbs -notmatch 'finora\.db') { throw 'Database file not found before backup' }
    if (-not (Test-ThemeRowPresent)) { throw 'Precondition failed: theme row not found before backup' }

    Adb shell bmgr enable true | Out-Null
    $results = [ordered]@{}

    # ---- Cloud backup via the local transport ------------------------------------------------
    $results['cloud backup'] = Run-Leg -Name 'Cloud backup (LocalTransport)' -SelectTransport {
        $out = Adb-Out shell bmgr transport com.android.localtransport/.LocalTransport
        if ($out -notmatch 'Selected transport') { Write-Warning "Local transport unavailable: $out"; return $false }
        Adb shell settings put secure backup_local_transport_parameters 'is_encrypted=true' | Out-Null
        return $true
    } -Cleanup {
        Adb shell bmgr transport com.google.android.gms/.backup.BackupTransportService | Out-Null
    }

    # Recreate the precondition for the second leg.
    Adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds 3
    Tap-Text 'More'
    Tap-Text 'Settings'
    Tap-Text 'Dark'
    Start-Sleep -Seconds 1

    # ---- Device-to-device transfer -----------------------------------------------------------
    $d2d = 'com.google.android.gms/.backup.migrate.service.D2dTransport'
    $results['device transfer'] = Run-Leg -Name 'Device transfer (D2dTransport)' -SelectTransport {
        Adb shell settings put secure backup_enable_d2d_test_mode 1 | Out-Null
        Adb shell bmgr transport $d2d | Out-Null
        Adb shell bmgr init $d2d | Out-Null
        $list = Adb-Out shell bmgr list transports
        if ($list -notmatch [regex]::Escape("* $d2d")) { Write-Warning 'D2dTransport not available on this image.'; return $false }
        return $true
    } -Cleanup {
        Adb shell bmgr init $d2d | Out-Null
        Adb shell settings put secure backup_enable_d2d_test_mode 0 | Out-Null
        Adb shell bmgr transport com.google.android.gms/.backup.BackupTransportService | Out-Null
    }

    Write-Host "`n=== Summary ===" -ForegroundColor Cyan
    $results.GetEnumerator() | ForEach-Object { Write-Host ("{0,-18} {1}" -f $_.Key, $_.Value) }
    if ($results.Values | Where-Object { $_ -like 'NOT RUN*' -or $_ -like 'INCONCLUSIVE*' }) {
        Write-Warning 'At least one leg was NOT RUN or INCONCLUSIVE. The result is incomplete, not a pass.'
        exit 2
    }
}
finally {
    if (Test-Path $backupFile) {
        Move-Item $backupFile $rules -Force
        Write-Host 'Restored data_extraction_rules.xml.' -ForegroundColor Yellow
    }
}
