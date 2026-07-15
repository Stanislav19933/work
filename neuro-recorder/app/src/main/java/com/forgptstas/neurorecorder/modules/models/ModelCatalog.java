package com.forgptstas.neurorecorder.modules.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Central list of local model choices. The default is selected for this product, not benchmarked at runtime. */
public final class ModelCatalog {
    private final List<ModelCandidate> candidates;

    public ModelCatalog() {
        ArrayList<ModelCandidate> list = new ArrayList<>();
        list.add(new ModelCandidate(
                "whisper-base-int8-current",
                ModelCandidate.Task.ASR,
                "Current Whisper base int8 JNI path",
                "ru",
                "whisper.cpp",
                true
        ));
        list.add(new ModelCandidate(
                "sherpa-onnx-nemo-transducer-giga-am-russian-2024-10-24",
                ModelCandidate.Task.ASR,
                "GigaAM NeMo transducer Russian",
                "ru",
                "sherpa-onnx",
                true
        ));
        list.add(new ModelCandidate(
                "sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19",
                ModelCandidate.Task.ASR,
                "GigaAM CTC v2 Russian",
                "ru",
                "sherpa-onnx",
                true
        ));
        list.add(new ModelCandidate(
                "sherpa-onnx-zipformer-ru-2024-09-18",
                ModelCandidate.Task.ASR,
                "Zipformer Russian",
                "ru",
                "sherpa-onnx",
                true
        ));
        list.add(new ModelCandidate(
                "sherpa-onnx-silero-vad",
                ModelCandidate.Task.VAD,
                "Silero VAD",
                "multi",
                "sherpa-onnx",
                true
        ));
        list.add(new ModelCandidate(
                "sherpa-onnx-speaker-diarization",
                ModelCandidate.Task.DIARIZATION,
                "Official sherpa-onnx speaker diarization",
                "multi",
                "sherpa-onnx",
                true
        ));
        list.add(new ModelCandidate(
                "gemma3-1b-it-int4-summary",
                ModelCandidate.Task.SUMMARY,
                "Gemma 3 1B IT int4 LiteRT summary",
                "multi",
                "MediaPipe LLM Inference",
                true
        ));
        candidates = Collections.unmodifiableList(list);
    }

    public List<ModelCandidate> all() {
        return candidates;
    }

    public List<ModelCandidate> forTask(ModelCandidate.Task task) {
        ArrayList<ModelCandidate> result = new ArrayList<>();
        for (ModelCandidate candidate : candidates) {
            if (candidate.getTask() == task) {
                result.add(candidate);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public ModelCandidate defaultAsr() {
        for (ModelCandidate candidate : candidates) {
            if ("sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19".equals(candidate.getId())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Default ASR model is missing from catalog.");
    }
    public ModelCandidate defaultSummary() {
        for (ModelCandidate candidate : candidates) {
            if ("gemma3-1b-it-int4-summary".equals(candidate.getId())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Default summary model is missing from catalog.");
    }
}
