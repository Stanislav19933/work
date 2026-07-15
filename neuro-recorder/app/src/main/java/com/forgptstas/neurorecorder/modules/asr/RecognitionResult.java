package com.forgptstas.neurorecorder.modules.asr;

import com.forgptstas.neurorecorder.Utterance;

import java.util.List;

public final class RecognitionResult {
    private final String plainText;
    private final List<Utterance> utterances;

    public RecognitionResult(String plainText, List<Utterance> utterances) {
        this.plainText = plainText;
        this.utterances = utterances;
    }

    public String getPlainText() {
        return plainText;
    }

    public List<Utterance> getUtterances() {
        return utterances;
    }
}
