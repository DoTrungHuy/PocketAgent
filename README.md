# PocketAgent

PocketAgent is an open-source Android phone document agent. It is built for the moment when the user remembers what a file says, but not where the file is.

Instead of asking the user to upload a document manually, PocketAgent detects document-search requests in chat, asks for an Android system file or folder authorization, builds a local index over that authorized range, and then uses the model to explain the best matches.

## Current release

- Version: `v0.2.4-alpha.1`
- Repository: `https://github.com/DoTrungHuy/PocketAgent`
- APK: [PocketAgent-v0.2.4-alpha.1.apk](https://github.com/DoTrungHuy/PocketAgent/releases/download/v0.2.4-alpha.1/PocketAgent-v0.2.4-alpha.1.apk)
- Release notes: [v0.2.4-alpha.1](https://github.com/DoTrungHuy/PocketAgent/releases/tag/v0.2.4-alpha.1)

The Android package id remains `com.agentpad.app` for upgrade compatibility with earlier signed builds. The app name, release files, docs, and user-visible UI are now `PocketAgent`.
This alpha APK is generated from the release variant. In this local environment it is signed with the Android Debug certificate because no production release keystore is configured.

## What changed in v0.2.4

- Mobile-first chat interface with a left history drawer on phones and a persistent history sidebar on wider screens.
- New document-search flow: ask by content, authorize a file/folder range, index recent documents, rank candidates, and answer with file names and match reasons.
- Android Storage Access Framework support for multiple files and folders via system pickers.
- Local document index tables for search ranges and extracted document text.
- Text extraction for `txt`, `md`, `json`, `xml`, `html`, `docx`, and text-layer `pdf` files.
- Provider prompt updated so the agent requests authorization when it lacks phone content instead of pretending it can read the device.

## Why this is different from ordinary AI chat

Ordinary AI chat waits for the user to find and upload a file. PocketAgent turns that around:

1. The user asks from memory, for example: `Find the document that mentions the reimbursement amount`.
2. PocketAgent recognizes that this needs phone document access.
3. Android shows a system permission picker for files or folders.
4. PocketAgent searches only the authorized range, starting from recent documents.
5. The model explains the likely matches and suggests expanding the range if nothing strong is found.

PocketAgent does not silently scan the whole phone. It can only read files and folders that the user authorizes through Android system UI.

## Local development

```powershell
cd D:\Projects\AgentPad\android-app
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

If Gradle cannot find the Android SDK, create `android-app/local.properties` with:

```properties
sdk.dir=D\:\\Android\\Sdk
```

Run the secret check from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-secrets.ps1
```

## Browser preview

The `web-preview` folder is a static mock for checking the desktop/mobile UI direction. It cannot call Android file pickers or read real phone files. Use the APK for real permission and document search behavior.
