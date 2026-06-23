# PocketAgent Release Process

PocketAgent releases are published from the `main` branch of `DoTrungHuy/PocketAgent`.

## Version checklist

1. Update Gradle `versionName`, root project version, release notes, README links, and APK name.
2. Confirm `applicationId` remains `com.agentpad.app` unless a migration plan explicitly changes it.
3. Run local checks:

```powershell
cd android-app
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
cd ..
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-secrets.ps1
```

4. Build the signed release APK when release signing env vars are available. Preferred variables are `POCKETAGENT_KEYSTORE_PATH`, `POCKETAGENT_KEYSTORE_PASSWORD`, `POCKETAGENT_KEY_ALIAS`, and `POCKETAGENT_KEY_PASSWORD`; legacy `AGENTPAD_*` variables are accepted as fallbacks.
5. Create a GitHub release with tag `vX.Y.Z-alpha.N` and attach the APK plus checksums.
