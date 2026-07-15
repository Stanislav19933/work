# NeuroRecorder architecture plan

NeuroRecorder is developed as a fully offline Android meeting recorder. The codebase is still a compact Android app module, but new work must follow replaceable module boundaries so UI code does not depend on a concrete ASR, diarization, summary, or export engine.

## Verified implementation snapshot

Checked in code, not comments:

- `RecorderService` records in a foreground microphone service and saves finished recordings through MediaStore.
- `RecordingRepository` reads, renames, and deletes recordings from `Music/NeuroRecorder`.
- `MainActivity` shows the recording button and archive cards with playback, transcription, rename, share, and delete actions.
- `WhisperEngine` and native JNI are present, but ASR is still tied to Whisper and must later be hidden behind an ASR module interface.

## Target module boundaries

- Recorder: microphone capture, foreground service lifecycle, PCM/WAV encoding.
- Storage: MediaStore access, metadata, transcript/result persistence.
- ASR: replaceable offline recognizer. Candidate engines must include sherpa-onnx models before a final choice.
- SpeakerDiarization: VAD, segmentation, embeddings, and clustering through sherpa-onnx rather than a custom clusterer.
- NameRecognition: context-based speaker naming; do not use simple regex-only replacement.
- Summary: local SLM summarization for short summary, tasks, owners, deadlines, open questions, numbers, and decisions.
- Export: TXT, DOCX, PDF, and Android share intents.
- UI: minimal screens that only call module interfaces.

## Current stage decision

Stage 3 starts with the Recorder boundary because high-quality ASR and diarization need predictable input audio. Recording now stores WAV with PCM 16 kHz mono audio, which matches the planned local ASR/VAD/diarization pipeline and avoids a lossy AAC preprocessing step.

## Offline processing rule

“Offline” means recordings, transcription, diarization, name recognition, summaries, and exports are processed on the device without cloud APIs. The app may use internet access only to download required local model files, cache them on the device, and then run processing locally. No user recordings, transcripts, summaries, or meeting metadata may be uploaded.

## Code module contracts

Stage 4 introduces Java interface contracts under `com.forgptstas.neurorecorder.modules.*`. Existing screens can keep working while implementations are moved behind these contracts one by one. This keeps Recorder, Storage, ASR, SpeakerDiarization, NameRecognition, Summary, Export, and UI replaceable without forcing a risky rewrite.

## Model files

Model downloads are centralized in `ModelFileManager` so ASR, diarization, and summary engines can share the same cache/download behavior instead of each module implementing its own networking code.
