@echo off
setlocal enabledelayedexpansion
title ACN Store - Build & Install Android APK

echo ======================================================================
echo   ACN STORE - COMPILATION ANDROID NATIVE (KOTLIN / JETPACK COMPOSE)
echo ======================================================================
echo.

set "PROJECT_ROOT=%~dp0"
set "ADB_EXE=C:\Users\luccl\AppData\Local\Android\Sdk\platform-tools\adb.exe"

if not exist "!ADB_EXE!" (
    if defined ANDROID_HOME (
        set "ADB_EXE=%ANDROID_HOME%\platform-tools\adb.exe"
    ) else (
        set "ADB_EXE=adb"
    )
)

echo [1/3] Detection de l'environnement Java / JDK 21...
set "JDK21_PATH=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
if exist "%JDK21_PATH%\bin\java.exe" (
    set "JAVA_HOME=%JDK21_PATH%"
    set "PATH=%JDK21_PATH%\bin;%PATH%"
    echo [OK] JDK 21 configure : %JDK21_PATH%
) else (
    echo [ATTENTION] JDK 21 specifique non trouve, utilisation du JAVA_HOME par defaut.
)

echo.
echo [2/3] Compilation de l'APK ACN Store avec Gradle...
cd /d "%PROJECT_ROOT%"
call gradlew.bat assembleDebug
if !errorlevel! neq 0 (
    echo [ERREUR] La compilation Gradle a echoue.
    pause
    exit /b !errorlevel!
)

set "SOURCE_APK=%PROJECT_ROOT%app\build\outputs\apk\debug\app-debug.apk"
set "TARGET_APK=%PROJECT_ROOT%ACN-Store.apk"

if exist "%SOURCE_APK%" (
    copy /y "%SOURCE_APK%" "%TARGET_APK%" >nul
    echo [OK] APK ACN Store pret : "%TARGET_APK%"
) else (
    echo [ERREUR] Impossible de trouver l'APK genere dans "%SOURCE_APK%".
    pause
    exit /b 1
)

echo.
echo [3/3] Verification de l'appareil Android connecte...
"!ADB_EXE!" devices > "%TEMP%\adb_store_devices.txt"
findstr /R /C:"[a-zA-Z0-9]	device" "%TEMP%\adb_store_devices.txt" >nul
if !errorlevel! equ 0 (
    echo [OK] Smartphone Android detecte ! Installation de ACN Store...
    "!ADB_EXE!" install -r "%TARGET_APK%"
    if !errorlevel! equ 0 (
        echo [OK] ACN Store installe avec succes sur votre telephone !
        echo Lancement de ACN Store...
        "!ADB_EXE!" shell am start -n com.acn.store/com.acn.store.MainActivity
    )
) else (
    echo [INFO] Aucun smartphone connecte en debogage USB. L'APK est disponible dans : "%TARGET_APK%"
)

echo.
echo ======================================================================
echo   OPERATION TERMINEE AVEC SUCCES !
echo ======================================================================
pause
