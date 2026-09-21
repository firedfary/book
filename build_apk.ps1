$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "D:\book\android-sdk"
$env:PATH = "D:\book\gradle-8.7\bin;C:\Program Files\Java\jdk-17\bin;$env:PATH"

Write-Host "=========================================="
Write-Host "  FlowLedger Android APK 自动化编译与打包  "
Write-Host "=========================================="
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"

Set-Location "D:\book"

Write-Host ">>> 正在执行单元测试..."
gradle.bat test --info

Write-Host ">>> 正在执行 APK 编译 (assembleDebug)..."
gradle.bat assembleDebug --stacktrace

$debugApk = "D:\book\app\build\outputs\apk\debug\app-debug.apk"
$finalApk = "D:\book\FlowLedger-v1.0.0.apk"

if (Test-Path $debugApk) {
    Copy-Item $debugApk -Destination $finalApk -Force
    $apkItem = Get-Item $finalApk
    $apkHash = (Get-FileHash -Path $finalApk -Algorithm SHA256).Hash
    Write-Host "=========================================="
    Write-Host ">>> 编译成功！安装包已就绪！"
    Write-Host "APK 路径: $finalApk"
    Write-Host "APK 大小: $([math]::Round($apkItem.Length / 1MB, 2)) MB"
    Write-Host "SHA256: $apkHash"
    Write-Host "=========================================="
} else {
    Write-Error "未能找到编译生成的 APK 文件: $debugApk"
}
