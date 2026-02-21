@echo off
echo Building Fat JAR...
call gradlew.bat :desktop:fatJar

echo.
echo Cleaning up old builds...
rmdir /S /Q custom-jre 2>nul
rmdir /S /Q release 2>nul

echo.
echo Creating minimal JRE with jlink...
REM Ensure we include the standard modules LibGDX requires 
jlink ^
  --add-modules java.base,java.desktop,jdk.unsupported ^
  --output custom-jre ^
  --strip-debug ^
  --compress=2 ^
  --no-header-files ^
  --no-man-pages

echo.
echo Packaging Windows Executable with jpackage...
jpackage ^
  --type msi ^
  --name "Typing Toucan" ^
  --input desktop/build/libs ^
  --main-jar desktop-1.0-SNAPSHOT-all.jar ^
  --main-class com.typingtoucan.DesktopLauncherKt ^
  --runtime-image custom-jre ^
  --win-console ^
  --win-shortcut ^
  --win-menu ^
  --win-dir-chooser ^
  --dest release

echo.
echo Packaging complete! The executable is located in the 'release/' folder.
pause
