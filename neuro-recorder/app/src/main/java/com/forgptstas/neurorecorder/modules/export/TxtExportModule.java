package com.forgptstas.neurorecorder.modules.export;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.forgptstas.neurorecorder.Utterance;
import com.forgptstas.neurorecorder.modules.summary.MeetingSummary;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Local exporter for TXT, DOCX, and PDF meeting artifacts. */
public final class TxtExportModule implements ExportModule {
    @Override
    public Uri exportTxt(Context context, String title, List<Utterance> utterances, MeetingSummary summary) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append("\n\n");
        if (summary != null) {
            builder.append("Краткое содержание:\n")
                    .append(summary.getShortSummary()).append("\n\n");
        }
        if (utterances != null) {
            for (Utterance utterance : utterances) {
                builder.append(utterance.getSpeakerLabel()).append(": ")
                        .append(utterance.getText()).append("\n");
            }
        }
        return exportTranscriptTxt(context, title, builder.toString());
    }

    public Uri exportTranscriptTxt(Context context, String title, String transcript) throws Exception {
        File file = exportFile(context, title, ".txt");
        Files.writeString(file.toPath(), transcript == null ? "" : transcript, StandardCharsets.UTF_8);
        return uriFor(context, file);
    }

    public Uri exportTranscriptDocx(Context context, String title, String transcript) throws Exception {
        File file = exportFile(context, title, ".docx");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            addZipEntry(zip, "[Content_Types].xml", ""
                    + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>");
            addZipEntry(zip, "_rels/.rels", ""
                    + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>");
            addZipEntry(zip, "word/document.xml", buildDocumentXml(title, transcript));
        }
        return uriFor(context, file);
    }

    public Uri exportTranscriptPdf(Context context, String title, String transcript) throws Exception {
        File file = exportFile(context, title, ".pdf");
        PdfDocument document = new PdfDocument();
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(18f);
        titlePaint.setFakeBoldText(true);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(12f);

        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 40;
        int y = margin;
        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create());
        Canvas canvas = page.getCanvas();
        canvas.drawText(title == null ? "Расшифровка" : title, margin, y, titlePaint);
        y += 30;
        int pageNumber = 1;
        for (String line : wrapText(transcript == null ? "" : transcript, 82)) {
            if (y > pageHeight - margin) {
                document.finishPage(page);
                pageNumber++;
                page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                canvas = page.getCanvas();
                y = margin;
            }
            canvas.drawText(line, margin, y, textPaint);
            y += 18;
        }
        document.finishPage(page);
        try (FileOutputStream output = new FileOutputStream(file)) {
            document.writeTo(output);
        } finally {
            document.close();
        }
        return uriFor(context, file);
    }

    @Override
    public Uri exportDocx(Context context, String title, List<Utterance> utterances, MeetingSummary summary) throws Exception {
        return exportTranscriptDocx(context, title, buildPlainText(title, utterances, summary));
    }

    @Override
    public Uri exportPdf(Context context, String title, List<Utterance> utterances, MeetingSummary summary) throws Exception {
        return exportTranscriptPdf(context, title, buildPlainText(title, utterances, summary));
    }

    private static String buildPlainText(String title, List<Utterance> utterances, MeetingSummary summary) {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append("\n\n");
        if (summary != null) {
            builder.append("Краткое содержание:\n").append(summary.getShortSummary()).append("\n\n");
        }
        if (utterances != null) {
            for (Utterance utterance : utterances) {
                builder.append(utterance.getSpeakerLabel()).append(": ")
                        .append(utterance.getText()).append("\n");
            }
        }
        return builder.toString();
    }

    private static String safeTitle(String title) {
        String safeTitle = title == null || title.isBlank() ? "meeting" : title.replaceAll("[\\/:*?\"<>|]", "_");
        if (safeTitle.toLowerCase().endsWith(".wav")) {
            safeTitle = safeTitle.substring(0, safeTitle.length() - 4);
        }
        return safeTitle;
    }

    private File exportFile(Context context, String title, String extension) {
        File directory = new File(context.getCacheDir(), "exports");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Не удалось создать папку экспорта.");
        }
        String safeTitle = safeTitle(title);
        return new File(directory, safeTitle + extension);
    }

    private Uri uriFor(Context context, File file) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
    }

    private static void addZipEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String buildDocumentXml(String title, String transcript) {
        StringBuilder body = new StringBuilder();
        body.append(paragraph(title == null ? "Расшифровка" : title));
        String safeTranscript = transcript == null ? "" : transcript;
        for (String line : safeTranscript.split("\\R", -1)) {
            body.append(paragraph(line));
        }
        return ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + body
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>"
                + "</w:body></w:document>";
    }

    private static String paragraph(String text) {
        return "<w:p><w:r><w:t>" + escapeXml(text) + "</w:t></w:r></w:p>";
    }

    private static String escapeXml(String text) {
        return (text == null ? "" : text)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static List<String> wrapText(String text, int maxCharacters) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            String remaining = paragraph;
            while (remaining.length() > maxCharacters) {
                int cut = remaining.lastIndexOf(' ', maxCharacters);
                if (cut <= 0) {
                    cut = maxCharacters;
                }
                lines.add(remaining.substring(0, cut));
                remaining = remaining.substring(cut).trim();
            }
            lines.add(remaining);
        }
        return lines;
    }
}
