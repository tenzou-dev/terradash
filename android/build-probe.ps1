param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$proj    = Join-Path $root 'app'
$probe   = Join-Path $root 'probe'
$build   = Join-Path $root 'build-probe'
$dist    = Join-Path $root 'dist'
$tc      = Join-Path (Split-Path -Parent (Split-Path -Parent $root)) '.toolchain'
$keystore = Join-Path $root 'keystore\terradash.jks'

$jdkDir = Get-ChildItem (Join-Path $tc 'jdk') -Directory -ErrorAction Stop | Select-Object -First 1
if (-not $jdkDir) { throw "JDK tidak ditemukan di $tc\jdk" }
$jdk = $jdkDir.FullName

$btDir = Get-ChildItem (Join-Path $tc 'sdk') -Directory -ErrorAction Stop |
         Where-Object { Test-Path (Join-Path $_.FullName 'aapt2.exe') } |
         Select-Object -First 1
if (-not $btDir) { throw "build-tools tidak ditemukan di $tc\sdk" }
$bt = $btDir.FullName

$platformDir = Get-ChildItem (Join-Path $tc 'sdk') -Directory -ErrorAction Stop |
               Where-Object { Test-Path (Join-Path $_.FullName 'android.jar') } |
               Select-Object -First 1
if (-not $platformDir) { throw "android.jar tidak ditemukan di $tc\sdk" }
$androidJar = Join-Path $platformDir.FullName 'android.jar'

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$bt;$env:PATH"

$javac    = Join-Path $jdk 'bin\javac.exe'
$jar      = Join-Path $jdk 'bin\jar.exe'
$aapt2    = Join-Path $bt 'aapt2.exe'
$d8       = Join-Path $bt 'd8.bat'
$zipalign = Join-Path $bt 'zipalign.exe'
$apksigner= Join-Path $bt 'apksigner.bat'

if ($Clean -and (Test-Path $build)) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $build, $dist | Out-Null

Write-Host "== 1/6  Kompilasi resource" -ForegroundColor Cyan
$resZip = Join-Path $build 'res.zip'
& $aapt2 compile --dir (Join-Path $proj 'res') -o $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile gagal" }

Write-Host "== 2/6  Link resource + manifest" -ForegroundColor Cyan
$baseApk = Join-Path $build 'base.apk'
& $aapt2 link -o $baseApk `
    -I $androidJar `
    --manifest (Join-Path $probe 'AndroidManifest.xml') `
    --java (Join-Path $build 'gen') `
    --min-sdk-version 24 --target-sdk-version 34 `
    $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 link gagal" }

Write-Host "== 3/6  Compile Java" -ForegroundColor Cyan
$classes = Join-Path $build 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = Get-ChildItem (Join-Path $probe 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $androidJar -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac gagal" }

Write-Host "== 4/6  Dex (d8)" -ForegroundColor Cyan
$classesJar = Join-Path $build 'classes.jar'
& $jar cf $classesJar -C $classes .
$dexDir = Join-Path $build 'dex'
New-Item -ItemType Directory -Force -Path $dexDir | Out-Null
& $d8 --release --min-api 24 --lib $androidJar --output $dexDir $classesJar
if ($LASTEXITCODE -ne 0) { throw "d8 gagal" }

Write-Host "== 5/6  Masukkan classes.dex + zipalign" -ForegroundColor Cyan
$unsigned = Join-Path $build 'unsigned.apk'
& python (Join-Path $root 'tools\add-dex.py') $baseApk (Join-Path $dexDir 'classes.dex') $unsigned
if ($LASTEXITCODE -ne 0) { throw "gagal menambahkan classes.dex" }
$aligned = Join-Path $build 'aligned.apk'
& $zipalign -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign gagal" }

Write-Host "== 6/6  Tanda tangan" -ForegroundColor Cyan
if (-not (Test-Path $keystore)) { throw "keystore tidak ada: $keystore" }
$apk = Join-Path $dist 'TerraDash-Probe-1.0.0.apk'
if (Test-Path $apk) { Remove-Item $apk -Force }
& $apksigner sign `
    --ks $keystore --ks-key-alias terradash `
    --ks-pass pass:terradash --key-pass pass:terradash `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --min-sdk-version 24 --out $apk $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner gagal" }

Write-Host ""
Write-Host "Probe APK siap: $apk" -ForegroundColor Green
