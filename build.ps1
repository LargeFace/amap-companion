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

$buildDir = Join-Path $root 'build'
$genDir = Join-Path $buildDir 'gen'
$classesDir = Join-Path $buildDir 'classes'
$dexDir = Join-Path $buildDir 'dex'
$sourcesFile = Join-Path $buildDir 'sources.txt'
$manifestSource = Join-Path $root 'app/src/main/AndroidManifest.xml'
$manifestBuild = Join-Path $buildDir 'AndroidManifest.xml'
$buildDirFull = [System.IO.Path]::GetFullPath($buildDir).TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar
)
$pathComparison = if ($isWindowsHost) {
    [System.StringComparison]::OrdinalIgnoreCase
} else {
    [System.StringComparison]::Ordinal
}

foreach ($full in @($genDir, $classesDir, $dexDir)) {
    $full = [System.IO.Path]::GetFullPath($full).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )
    if (!$full.StartsWith($buildDirFull + [System.IO.Path]::DirectorySeparatorChar, $pathComparison)) {
        throw "Refusing to clean $full"
    }
    if (Test-Path -LiteralPath $full) {
        Remove-Item -LiteralPath $full -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $full | Out-Null
}

$manifestText = [System.IO.File]::ReadAllText($manifestSource)
if ($env:APP_VERSION_CODE) {
    $manifestText = $manifestText -replace 'android:versionCode="[^"]*"', ('android:versionCode="' + $env:APP_VERSION_CODE + '"')
}
if ($env:APP_VERSION_NAME) {
    $manifestText = $manifestText -replace 'android:versionName="[^"]*"', ('android:versionName="' + $env:APP_VERSION_NAME + '"')
}
[System.IO.File]::WriteAllText($manifestBuild, $manifestText, (New-Object System.Text.UTF8Encoding($false)))

& $aapt package -f -m -J $genDir -M $manifestBuild -S app/src/main/res -I $androidJar
Check-Last 'aapt generate R'

$sources = @()
$sources += Get-ChildItem -Recurse -File app\src\main\java -Filter *.java | ForEach-Object { $_.FullName.Substring($root.Length + 1) }
$sources += Get-ChildItem -Recurse -File $genDir -Filter *.java | ForEach-Object { $_.FullName.Substring($root.Length + 1) }
[System.IO.File]::WriteAllLines($sourcesFile, [string[]]$sources, (New-Object System.Text.UTF8Encoding($false)))

javac -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d $classesDir "@$sourcesFile"
Check-Last 'javac'

$classFiles = Get-ChildItem -Recurse -File $classesDir -Filter *.class | ForEach-Object { $_.FullName }
& $d8 --lib $androidJar --min-api 23 --output $dexDir $classFiles
Check-Last 'd8'

$unsignedApk = Join-Path $buildDir 'amap_companion_unsigned.apk'
$alignedApk = Join-Path $buildDir 'amap_companion_aligned.apk'
& $aapt package -f -M $manifestBuild -S app/src/main/res -I $androidJar -F $unsignedApk $dexDir
Check-Last 'aapt package'

& $zipalign -f 4 $unsignedApk $alignedApk
Check-Last 'zipalign'

& $apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out amap_companion_signed.apk $alignedApk
Check-Last 'apksigner sign'

& $apksigner verify --verbose amap_companion_signed.apk
Check-Last 'apksigner verify'
