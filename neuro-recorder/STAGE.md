# Current stage

Stage 3 starts the offline modular MVP path.

Completed in this stage:

- Recorder now captures microphone audio as PCM 16 kHz mono through `AudioRecord`.
- Finished recordings are saved to MediaStore as `.wav` files in `Music/NeuroRecorder`.
- Internet access is allowed only for downloading required local model files.
- Whisper model preparation downloads and caches the model on first use; transcription still runs on the device after the model is available.
- Architecture notes document the verified current implementation and the target replaceable modules.

Completed in this follow-up stage:

- Added explicit Java interfaces/packages for Recorder, Storage, ASR, SpeakerDiarization, NameRecognition, Summary, Export, and UI boundaries.
- Moved the existing MediaStore storage path behind `StorageModule`.
- Moved the existing Whisper transcription path behind `AsrModule` with `WhisperAsrModule`.
- Added local TXT, DOCX, and PDF transcript export implementations and UI actions.
- Added fully local extractive summary module, summary persistence, and archive UI action.
- Added context-based name recognition module that detects self-introductions without regex-only matching.
- Moved recorder commands behind `AndroidRecorderModule` implementing `RecorderModule`.
- Fixed repository-root GitHub Actions Android CI workflow so it builds debug APK, runs lint, and uploads the APK artifact.
- Extended TXT/DOCX/PDF exports to include saved meeting summary together with transcript.
- Added reusable `ModelFileManager` for downloading and caching on-device model files, and moved Whisper model download through it.
- Added recording details screen with Audio/Text/By speakers/Summary tabs and TXT/DOCX/PDF export actions.
- Added JVM unit tests for context name recognition and local extractive summary behavior.
- Selected sherpa-onnx GigaAM CTC v2 Russian as the primary ASR path and wired MainActivity transcription to it.
- Added sherpa-onnx Silero VAD module and wired transcription to remove silence before ASR.
- Added official sherpa-onnx speaker diarization module, persisted speaker segments, and connected the “By speakers” details tab to saved diarization output.
- Added transcript-to-speaker interval alignment, speaker-name application, and included speaker sections in TXT/DOCX/PDF exports.
- Added a local model status screen so a beginner can see which ASR/VAD/diarization files are already downloaded and which will be downloaded automatically.
- Added model-screen actions to download all local models immediately or clear cached models.
- Wired JVM unit tests into GitHub Actions so test coverage runs in CI, not only locally.
- Moved transcription/ASR/VAD/diarization/name-recognition persistence into a WorkManager worker and made MainActivity observe worker progress.
- Moved summary generation into a WorkManager worker so long summaries are not tied to the Activity lifecycle.
- Added MediaPipe LLM Inference with Gemma 3 1B int4 as the local SLM summary engine and connected model download progress to the UI.

Next stage:

- Run a final end-to-end device check on Android 14+/Pixel 7 Pro and collect performance/memory notes for long meetings.

Roadmap:

- See `docs/roadmap.md` for the beginner-friendly status table, remaining work, and the exact definition of “ready to use”.
