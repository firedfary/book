$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "D:\book\android-sdk"
$sdkManager = "D:\book\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat"

Write-Host ">>> Accepting Android SDK Licenses..."
cmd.exe /c "echo y | `"$sdkManager`" --sdk_root=`"D:\book\android-sdk`" --licenses"

Write-Host ">>> Installing platform-tools, platforms;android-34, build-tools;34.0.0..."
cmd.exe /c "echo y | `"$sdkManager`" --sdk_root=`"D:\book\android-sdk`" `"platform-tools`" `"platforms;android-34`" `"build-tools;34.0.0`""

Write-Host ">>> Android SDK components installed successfully!"
