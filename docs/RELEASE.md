# AgentPad Release Process

## Release identity

The Android release keystore is the long-term upgrade identity for AgentPad.
Losing it prevents future APKs from upgrading existing installations. Leaking
it lets an attacker sign malicious updates.

The keystore must never be committed. Keep at least two encrypted offline
backups, and keep the recovery credentials separately.

## Required GitHub Actions secrets

- `AGENTPAD_RELEASE_KEYSTORE`: Base64-encoded JKS bytes.
- `AGENTPAD_KEYSTORE_PASSWORD`: Keystore password.
- `AGENTPAD_KEY_ALIAS`: Signing key alias.
- `AGENTPAD_KEY_PASSWORD`: Private-key password.

## Publishing

1. Ensure `main` CI is green.
   Run the manual `Android Instrumentation` workflow when hosted-emulator
   diagnostics are needed; signed alpha acceptance is performed on the target
   tablets and ARM64 phone.
2. Confirm `versionName` equals the intended tag without the leading `v`.
3. Create and push an annotated tag, for example `v0.2.1-alpha.1`.
4. The read-only build job tests, lints, signs, verifies, creates the CycloneDX
   SBOM, and uploads a temporary release bundle.
5. A separate publish job receives `contents: write` and creates the GitHub
   pre-release from that verified bundle.
6. Download the APK and verify its SHA256 and signing certificate before
   installing it on test devices.

The workflow publishes prereleases only. Stable release promotion is a separate
manual decision after real-device acceptance.
