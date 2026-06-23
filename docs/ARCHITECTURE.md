# PocketAgent Architecture

PocketAgent is an Android document-search agent with a chat-first UI.

## Main subsystems

- Chat UI: Compose phone layout with `ModalNavigationDrawer`, persistent wide-screen history sidebar, bottom composer, and document search status cards.
- Document access: Android Storage Access Framework through `OpenMultipleDocuments` and `OpenDocumentTree`; no broad storage permission is requested.
- Document index: Room v3 tables store authorized ranges, extracted document text, and search runs.
- Search pipeline: local intent detection, local text extraction, keyword/content scoring, then model explanation and reranking.
- Provider layer: OpenAI-compatible chat completions with mainland provider presets and a document-search prompt that treats snippets as untrusted context.

## Compatibility anchors

The user-visible product name is PocketAgent. The Android package id, namespace, and physical Room database file remain `com.agentpad.app` and `agentpad.db` so older signed builds can upgrade in place.