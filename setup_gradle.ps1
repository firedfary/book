$ErrorActionPreference = "Stop"

$gradleZip = "D:\book\gradle-8.7-bin.zip"
$gradleUrl = "https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip"
$gradleDir = "D:\book\gradle-8.7"

if (-not (Test-Path "$gradleDir\bin\gradle.bat")) {
    Write-Host ">>> Downloading Gradle 8.7 from high-speed mirror..."
    curl.exe -L -o $gradleZip $gradleUrl
    Write-Host ">>> Extracting Gradle 8.7..."
    Expand-Archive -Path $gradleZip -DestinationPath "D:\book" -Force
    Remove-Item -Force $gradleZip
    Write-Host ">>> Gradle 8.7 extracted successfully to $gradleDir"
} else {
    Write-Host ">>> Gradle 8.7 already exists at $gradleDir"
}
