package com.forgptstas.neurorecorder.modules.export;

import android.content.Context;
import android.net.Uri;

import com.forgptstas.neurorecorder.Utterance;
import com.forgptstas.neurorecorder.modules.summary.MeetingSummary;

import java.util.List;

/** Replaceable boundary for TXT, DOCX, PDF, and shareable exports. */
public interface ExportModule {
    Uri exportTranscriptTxt(Context context, String title, String transcript) throws Exception;

    Uri exportTxt(Context context, String title, List<Utterance> utterances, MeetingSummary summary) throws Exception;

    Uri exportDocx(Context context, String title, List<Utterance> utterances, MeetingSummary summary) throws Exception;

    Uri exportPdf(Context context, String title, List<Utterance> utterances, MeetingSummary summary) throws Exception;
}
