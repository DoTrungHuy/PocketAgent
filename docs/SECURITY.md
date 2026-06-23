# PocketAgent Security Model

PocketAgent reads phone content only after the user grants access through Android system UI.

## Boundaries

- No full-phone silent scan.
- No accessibility automation in v0.2.4.
- No background indexing of unauthorized locations.
- No API keys in diagnostics, logs, or exported crash reports.
- Document content is treated as untrusted context and must not override local safety policy.

## Document search permissions

PocketAgent can search:

- Files selected with the Android file picker.
- Folders selected with the Android folder picker.
- Previously authorized ranges until the user removes them or Android revokes the URI permission.

The Android package id remains `com.agentpad.app` for compatibility, but the product name is PocketAgent.