$toolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
$zipPath = "D:\book\android-sdk\cmdline-tools.zip"
$targetDir = "D:\book\android-sdk\cmdline-tools\latest"

if (-not (Test-Path "$targetDir\bin\sdkmanager.bat")) {
    Write-Host "Downloading commandlinetools from $toolsUrl ..."
    Invoke-WebRequest -Uri $toolsUrl -OutFile $zipPath
    Write-Host "Extracting commandlinetools..."
    Expand-Archive -Path $zipPath -DestinationPath "D:\book\android-sdk\temp_cmdline" -Force
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    Copy-Item -Path "D:\book\android-sdk\temp_cmdline\cmdline-tools\*" -Destination $targetDir -Recurse -Force
    Remove-Item -Recurse -Force "D:\book\android-sdk\temp_cmdline", $zipPath
    Write-Host "Commandlinetools successfully installed to $targetDir"
} else {
    Write-Host "sdkmanager already exists at $targetDir"
}
