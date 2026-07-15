package com.forgptstas.neurorecorder.modules.summary;

import com.forgptstas.neurorecorder.Utterance;

import java.util.List;

/** Replaceable boundary for fully local meeting summarization. */
public interface SummaryModule {
    MeetingSummary summarize(List<Utterance> utterances) throws Exception;
}
