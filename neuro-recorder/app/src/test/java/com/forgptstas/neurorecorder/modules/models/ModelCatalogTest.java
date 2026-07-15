package com.forgptstas.neurorecorder.modules.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class ModelCatalogTest {
    @Test
    public void containsRussianAsrCandidates() {
        ModelCatalog catalog = new ModelCatalog();
        List<ModelCandidate> asr = catalog.forTask(ModelCandidate.Task.ASR);

        assertFalse(asr.isEmpty());
        assertTrue(containsId(asr, "whisper-base-int8-current"));
        assertTrue(containsId(asr, "sherpa-onnx-nemo-transducer-giga-am-russian-2024-10-24"));
        assertTrue(containsId(asr, "sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19"));
    }

    @Test
    public void selectsGigaAmCtcV2AsDefaultAsr() {
        ModelCatalog catalog = new ModelCatalog();

        assertTrue("sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19".equals(catalog.defaultAsr().getId()));
    }

    @Test
    public void containsVadAndDiarizationCandidates() {
        ModelCatalog catalog = new ModelCatalog();

        assertFalse(catalog.forTask(ModelCandidate.Task.VAD).isEmpty());
        assertFalse(catalog.forTask(ModelCandidate.Task.DIARIZATION).isEmpty());
    }

    @Test
    public void selectsGemmaAsDefaultSummaryModel() {
        ModelCatalog catalog = new ModelCatalog();

        assertFalse(catalog.forTask(ModelCandidate.Task.SUMMARY).isEmpty());
        assertTrue("gemma3-1b-it-int4-summary".equals(catalog.defaultSummary().getId()));
    }

    private static boolean containsId(List<ModelCandidate> candidates, String id) {
        for (ModelCandidate candidate : candidates) {
            if (candidate.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
