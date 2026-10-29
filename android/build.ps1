param(
    [switch]$Clean,
    [string]$OutDir = "",
    [string]$Toolchain = "",
    [string]$Keystore = "",
    [string]$StorePass = "terradash",
    [string]$KeyAlias = "terradash"
)

$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$proj    = Join-Path $root 'app'
$build   = Join-Path $root 'build'
$dist    = if ($OutDir) { $OutDir } else { Join-Path $root 'dist' }
$tc      = if ($Toolchain) { $Toolchain } else { Join-Path (Split-Path -Parent (Split-Path -Parent $root)) '.toolchain' }
$dash    = Join-Path (Split-Path -Parent $root) 'dashboard'
$keystore = if ($Keystore) { $Keystore } else { Join-Path $root 'keystore\terradash.jks' }

$jdkDir = Get-ChildItem (Join-Path $tc 'jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdkDir) {
    $jdk = $jdkDir.FullName
} elseif ($env:JAVA_HOME) {
    # dipakai di CI (GitHub Actions) yang JDK-nya sudah terpasang di runner
    $jdk = $env:JAVA_HOME
} else {
    throw "JDK tidak ditemukan di $tc\jdk dan JAVA_HOME kosong"
}

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
$keytool  = Join-Path $jdk 'bin\keytool.exe'
$jar      = Join-Path $jdk 'bin\jar.exe'
$aapt2    = Join-Path $bt 'aapt2.exe'
$d8       = Join-Path $bt 'd8.bat'
$zipalign = Join-Path $bt 'zipalign.exe'
$apksigner= Join-Path $bt 'apksigner.bat'

if ($Clean -and (Test-Path $build)) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $build, $dist | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $root 'keystore') | Out-Null

Write-Host "== 1/8  Sinkron aset dashboard" -ForegroundColor Cyan
$assets = Join-Path $proj 'assets'
if (Test-Path $assets) { Remove-Item $assets -Recurse -Force }
New-Item -ItemType Directory -Force -Path $assets | Out-Null
Copy-Item (Join-Path $dash 'index.html') $assets
Copy-Item (Join-Path $dash 'fonts') $assets -Recurse
Copy-Item (Join-Path $dash 'vendor') $assets -Recurse

Write-Host "== 2/8  Kompilasi resource" -ForegroundColor Cyan
$resZip = Join-Path $build 'res.zip'
& $aapt2 compile --dir (Join-Path $proj 'res') -o $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile gagal" }

Write-Host "== 3/8  Link resource + manifest" -ForegroundColor Cyan
$baseApk = Join-Path $build 'base.apk'
& $aapt2 link -o $baseApk `
    -I $androidJar `
    --manifest (Join-Path $proj 'AndroidManifest.xml') `
    -A $assets `
    --java (Join-Path $build 'gen') `
    --min-sdk-version 24 --target-sdk-version 34 `
    $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 link gagal" }

Write-Host "== 4/8  Compile Java" -ForegroundColor Cyan
$classes = Join-Path $build 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = Get-ChildItem (Join-Path $proj 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $androidJar -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac gagal" }

Write-Host "== 5/8  Dex (d8)" -ForegroundColor Cyan
$classesJar = Join-Path $build 'classes.jar'
& $jar cf $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar gagal" }
$dexDir = Join-Path $build 'dex'
New-Item -ItemType Directory -Force -Path $dexDir | Out-Null
& $d8 --release --min-api 24 --lib $androidJar --output $dexDir $classesJar
if ($LASTEXITCODE -ne 0) { throw "d8 gagal" }

Write-Host "== 6/8  Masukkan classes.dex" -ForegroundColor Cyan
$unsigned = Join-Path $build 'unsigned.apk'
& python (Join-Path $root 'tools\add-dex.py') $baseApk (Join-Path $dexDir 'classes.dex') $unsigned
if ($LASTEXITCODE -ne 0) { throw "gagal menambahkan classes.dex" }

Write-Host "== 7/8  Zipalign" -ForegroundColor Cyan
$aligned = Join-Path $build 'aligned.apk'
& $zipalign -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign gagal" }

Write-Host "== 8/8  Tanda tangan" -ForegroundColor Cyan
if (-not (Test-Path $keystore)) {
    Write-Host "   membuat keystore baru..." -ForegroundColor Yellow
    & $keytool -genkeypair -v `
        -keystore $keystore -alias $KeyAlias `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $StorePass -keypass $StorePass `
        -dname "CN=TerraDash, OU=TenZou.Dev, O=TenZou.Dev, L=Tuban, ST=Jawa Timur, C=ID" `
        -noprompt
    if ($LASTEXITCODE -ne 0) { throw "keytool gagal" }
}

$manifest = Join-Path $proj 'AndroidManifest.xml'
$verMatch = Select-String -Path $manifest -Pattern 'android:versionName="([^"]+)"'
$verName = if ($verMatch) { $verMatch.Matches[0].Groups[1].Value } else { 'dev' }

$apk = Join-Path $dist ("TerraDash-$verName.apk")
if (Test-Path $apk) { Remove-Item $apk -Force }
& $apksigner sign `
    --ks $keystore --ks-key-alias $KeyAlias `
    --ks-pass ("pass:" + $StorePass) --key-pass ("pass:" + $StorePass) `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --min-sdk-version 24 --out $apk $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner gagal" }

& $apksigner verify --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw "verifikasi APK gagal" }

$size = [math]::Round((Get-Item $apk).Length / 1MB, 2)
Write-Host ""
Write-Host "APK siap: $apk  ($size MB)" -ForegroundColor Green
