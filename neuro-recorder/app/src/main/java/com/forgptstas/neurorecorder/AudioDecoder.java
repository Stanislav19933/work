package com.forgptstas.neurorecorder;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class AudioDecoder {
    private static final int TARGET_SAMPLE_RATE = 16000;
    private static final long TIMEOUT_US = 10_000;

    private AudioDecoder() {
    }

    public static float[] decodeToMono16Khz(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, uri, null);
            int trackIndex = findAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new IllegalArgumentException("В записи нет аудиодорожки.");
            }
            extractor.selectTrack(trackIndex);
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                throw new IllegalArgumentException("Неизвестный формат аудио.");
            }
            int sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inputIndex);
                        if (input == null) {
                            throw new IllegalStateException("Не удалось получить входной буфер декодера.");
                        }
                        int size = extractor.readSampleData(input, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outputIndex >= 0) {
                    ByteBuffer output = codec.getOutputBuffer(outputIndex);
                    if (output != null && info.size > 0) {
                        output.position(info.offset);
                        output.limit(info.offset + info.size);
                        byte[] chunk = new byte[info.size];
                        output.get(chunk);
                        pcm.write(chunk);
                    }
                    codec.releaseOutputBuffer(outputIndex, false);
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                }
            }

            return pcm16ToMonoResampled(pcm.toByteArray(), sourceRate, channels);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                codec.release();
            }
            extractor.release();
        }
    }

    private static int findAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private static float[] pcm16ToMonoResampled(byte[] bytes, int sourceRate, int channels) {
        if (sourceRate <= 0 || channels <= 0 || bytes.length < 2) {
            throw new IllegalArgumentException("Некорректные параметры PCM.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int frames = bytes.length / 2 / channels;
        float[] mono = new float[frames];
        for (int frame = 0; frame < frames; frame++) {
            float sum = 0f;
            for (int channel = 0; channel < channels; channel++) {
                sum += buffer.getShort() / 32768f;
            }
            mono[frame] = sum / channels;
        }
        if (sourceRate == TARGET_SAMPLE_RATE) {
            return mono;
        }
        int targetLength = Math.max(1, (int) Math.round(mono.length * (TARGET_SAMPLE_RATE / (double) sourceRate)));
        float[] resampled = new float[targetLength];
        double ratio = sourceRate / (double) TARGET_SAMPLE_RATE;
        for (int i = 0; i < targetLength; i++) {
            double position = i * ratio;
            int left = Math.min((int) position, mono.length - 1);
            int right = Math.min(left + 1, mono.length - 1);
            float fraction = (float) (position - left);
            resampled[i] = mono[left] + (mono[right] - mono[left]) * fraction;
        }
        return resampled;
    }
}
