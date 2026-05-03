$ErrorActionPreference = 'Stop'

function Check-Last($name) {
    if ($LASTEXITCODE -ne 0) {
        throw "$name failed with exit $LASTEXITCODE"
    }
}

$root = (Resolve-Path '.').Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Users\zuoqirun\AppData\Local\Android\Sdk' }
$buildTools = Join-Path $sdk 'build-tools\34.0.0'
$androidJar = Join-Path $sdk 'platforms\android-31\android.jar'

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

& "$buildTools\aapt.exe" package -f -m -J build\gen -M app\src\main\AndroidManifest.xml -S app\src\main\res -I $androidJar
Check-Last 'aapt generate R'

$sources = @()
$sources += Get-ChildItem -Recurse -File app\src\main\java -Filter *.java | ForEach-Object { $_.FullName.Substring($root.Length + 1) }
$sources += Get-ChildItem -Recurse -File build\gen -Filter *.java | ForEach-Object { $_.FullName.Substring($root.Length + 1) }
[System.IO.File]::WriteAllLines((Join-Path $root 'build\sources.txt'), [string[]]$sources, (New-Object System.Text.UTF8Encoding($false)))

javac -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d build\classes '@build\sources.txt'
Check-Last 'javac'

$classFiles = Get-ChildItem -Recurse -File build\classes -Filter *.class | ForEach-Object { $_.FullName }
& "$buildTools\d8.bat" --lib $androidJar --min-api 23 --output build\dex $classFiles
Check-Last 'd8'

& "$buildTools\aapt.exe" package -f -M app\src\main\AndroidManifest.xml -S app\src\main\res -I $androidJar -F build\amap_companion_unsigned.apk build\dex
Check-Last 'aapt package'

& "$buildTools\zipalign.exe" -f 4 build\amap_companion_unsigned.apk build\amap_companion_aligned.apk
Check-Last 'zipalign'

& "$buildTools\apksigner.bat" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out amap_companion_signed.apk build\amap_companion_aligned.apk
Check-Last 'apksigner sign'

& "$buildTools\apksigner.bat" verify --verbose amap_companion_signed.apk
Check-Last 'apksigner verify'
