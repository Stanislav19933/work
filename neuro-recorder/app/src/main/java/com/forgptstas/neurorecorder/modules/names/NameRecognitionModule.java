package com.forgptstas.neurorecorder.modules.names;

import com.forgptstas.neurorecorder.Utterance;

import java.util.List;
import java.util.Map;

/** Replaceable boundary for context-based speaker name recognition. */
public interface NameRecognitionModule {
    Map<String, String> detectSpeakerNames(List<Utterance> utterances) throws Exception;
}
