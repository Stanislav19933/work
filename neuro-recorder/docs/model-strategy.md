# Local model strategy

This project must run meeting processing on the Android device. Internet access is allowed only to download model files that are then cached locally.

## Selected production path

- Recorder: Android `AudioRecord`, PCM 16 kHz mono WAV.
- Primary ASR: `sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19` through `SherpaGigaAmAsrModule`.
- VAD: sherpa-onnx Silero VAD through `SileroVadModule`, executed before ASR to remove silence.
- Diarization: official sherpa-onnx speaker diarization through `SherpaSpeakerDiarizationModule`, using pyannote segmentation, speaker embeddings, and clustering.
- Current summary: Gemma 3 1B int4 through MediaPipe LLM Inference behind `SummaryModule`; the older extractive summarizer remains only as a testable lightweight module.
- Export: local TXT/DOCX/PDF generation behind `ExportModule`.

## Why GigaAM CTC v2 is selected now

The app is Russian-first, meeting-recorder-first, and Android-first. The selected model is a Russian NeMo CTC model converted by sherpa-onnx from GigaAM, has an int8 ONNX file, uses only `model.int8.onnx` plus `tokens.txt`, and is documented by upstream sherpa-onnx for offline file decoding and microphone recognition. This makes it the most practical production default right now: fewer model files than transducer models, a dedicated Russian acoustic model, and a direct Android-compatible sherpa-onnx runtime path.

## Model download behavior

The app downloads the selected ASR model files on first transcription:

- `model.int8.onnx` from Hugging Face model storage.
- `tokens.txt` from the same model repository.

After download, transcription runs locally on the device. Meeting audio and transcripts are not sent to cloud APIs.

## Sherpa-ONNX integration baseline

The Android dependency is `com.k2fsa.sherpa.onnx:sherpa-onnx-android:1.13.4`. ASR must use real sherpa-onnx APIs; fake ASR/VAD/diarization adapters are not allowed.

## Current diarization text strategy

`SpeakerTextAligner` attaches recognized text to diarized speaker intervals in chronological order. This gives the UI and exports speaker-labelled text now. When the selected ASR path exposes reliable word or segment timings, the aligner should be upgraded to timestamp-accurate assignment.

## Current summary model

The selected summary model is Gemma 3 1B IT int4 in a MediaPipe/LiteRT task bundle. It is small enough for a high-end Android device compared with larger 2B/4B models, supports summarization/reasoning prompts, and runs through the Android `tasks-genai` runtime without sending transcript text to a server. The app downloads the model on first summary generation and reuses the cached file afterwards.

## Remaining model quality work

- Upgrade speaker text alignment to use ASR word/segment timings when available.
- Measure Gemma summary speed and memory on a real Pixel 7 Pro with long meetings.
