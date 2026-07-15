package com.forgptstas.neurorecorder.modules.diarization;

import com.forgptstas.neurorecorder.Utterance;

import java.util.List;

/** Replaceable boundary for VAD, speaker segmentation, embeddings, and clustering. */
public interface SpeakerDiarizationModule {
    List<Utterance> assignSpeakers(float[] monoPcm16Khz, List<Utterance> utterances) throws Exception;
}
