$ErrorActionPreference = 'Stop'

function Check-Last($name) {
    if ($LASTEXITCODE -ne 0) {
        throw "$name failed with exit $LASTEXITCODE"
    }
}

$root = (Resolve-Path '.').Path
$sdk = if ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    'C:\Users\zuoqirun\AppData\Local\Android\Sdk'
}

function Get-LatestAndroidDir($parent, $prefix) {
    if (!(Test-Path -LiteralPath $parent)) {
        throw "Android SDK directory not found: $parent"
    }
    $dirs = Get-ChildItem -LiteralPath $parent -Directory |
        Where-Object { $_.Name -like "$prefix*" } |
        Sort-Object {
            $versionText = $_.Name.Substring($prefix.Length)
            $versionText = $versionText -replace '[^\d\.].*$', ''
            try { [version]$versionText } catch { [version]'0.0.0' }
        } -Descending
    if (!$dirs) {
        throw "No Android SDK component found in $parent"
    }
    $dirs[0].FullName
}

$buildTools = if ($env:ANDROID_BUILD_TOOLS_VERSION) {
    Join-Path (Join-Path $sdk 'build-tools') $env:ANDROID_BUILD_TOOLS_VERSION
} else {
    Get-LatestAndroidDir (Join-Path $sdk 'build-tools') ''
}

$platformName = if ($env:ANDROID_PLATFORM) {
    $env:ANDROID_PLATFORM
} elseif ($env:ANDROID_PLATFORM_VERSION) {
    "android-$env:ANDROID_PLATFORM_VERSION"
} else {
    $null
}
$platformDir = if ($platformName) {
    Join-Path (Join-Path $sdk 'platforms') $platformName
} else {
    Get-LatestAndroidDir (Join-Path $sdk 'platforms') 'android-'
}
$androidJar = Join-Path $platformDir 'android.jar'

$isWindowsHost = $PSVersionTable.Platform -eq 'Win32NT' -or $env:OS -eq 'Windows_NT'
$exeSuffix = if ($isWindowsHost) { '.exe' } else { '' }
$scriptSuffix = if ($isWindowsHost) { '.bat' } else { '' }

$aapt = Join-Path $buildTools "aapt$exeSuffix"
$d8 = Join-Path $buildTools "d8$scriptSuffix"
$zipalign = Join-Path $buildTools "zipalign$exeSuffix"
$apksigner = Join-Path $buildTools "apksigner$scriptSuffix"

foreach ($tool in @($aapt, $d8, $zipalign, $apksigner, $androidJar)) {
    if (!(Test-Path -LiteralPath $tool)) {
        throw "Required build input not found: $tool"
    }
}

foreach ($target in @('build\gen', 'build\classes', 'build\dex')) {
    $full = Join-Path $root $target
    if ($full -notlike "$root\build\*") {
        throw "Refusing to clean $full"
    }
    if (Test-Path -LiteralPath $full) {
        Remove-Item -LiteralPath $full -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $full | Out-Null
}

& $aapt package -f -m -J build\gen -M app\src\main\AndroidManifest.xml -S app\src\main\res -I $androidJar
Check-Last 'aapt generate R'

$sources = @()
$sources += Get-ChildItem -Recurse -File app\src\main\java -Filter *.java | ForEach-Object { $_.FullName.Substring($root.Length + 1) }
$sources += Get-ChildItem -Recurse -File build\gen -Filter *.java | ForEach-Object { $_.FullName.Substring($root.Length + 1) }
[System.IO.File]::WriteAllLines((Join-Path $root 'build\sources.txt'), [string[]]$sources, (New-Object System.Text.UTF8Encoding($false)))

javac -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d build\classes '@build\sources.txt'
Check-Last 'javac'

$classFiles = Get-ChildItem -Recurse -File build\classes -Filter *.class | ForEach-Object { $_.FullName }
& $d8 --lib $androidJar --min-api 23 --output build\dex $classFiles
Check-Last 'd8'

& $aapt package -f -M app\src\main\AndroidManifest.xml -S app\src\main\res -I $androidJar -F build\amap_companion_unsigned.apk build\dex
Check-Last 'aapt package'

& $zipalign -f 4 build\amap_companion_unsigned.apk build\amap_companion_aligned.apk
Check-Last 'zipalign'

& $apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out amap_companion_signed.apk build\amap_companion_aligned.apk
Check-Last 'apksigner sign'

& $apksigner verify --verbose amap_companion_signed.apk
Check-Last 'apksigner verify'
