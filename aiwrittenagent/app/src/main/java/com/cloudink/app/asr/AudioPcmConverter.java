package com.cloudink.app.asr;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 将 m4a/mp3/amr 等转为 16kHz 单声道 PCM，供百度语音识别使用。
 */
public final class AudioPcmConverter {

    private static final String TAG = "AudioPcmConverter";
    private static final int TARGET_RATE = 16000;

    private AudioPcmConverter() {}

    public static byte[] toPcm16Mono16k(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pcm")) {
            return readPcmFile(file);
        }
        if (name.endsWith(".wav")) {
            return extractWavPcm(file);
        }
        return decodeWithMediaExtractor(file);
    }

    private static byte[] readPcmFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            return in.readAllBytes();
        }
    }

    /** 简单解析 16bit PCM WAV */
    private static byte[] extractWavPcm(File file) throws IOException {
        byte[] data = readPcmFile(file);
        if (data.length < 44) {
            throw new IOException("WAV 文件过短");
        }
        int dataOffset = 44;
        for (int i = 12; i + 8 < data.length; i++) {
            String chunk = new String(data, i, 4);
            int size = ByteBuffer.wrap(data, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if ("data".equals(chunk)) {
                dataOffset = i + 8;
                int len = Math.min(size, data.length - dataOffset);
                byte[] pcm = new byte[len];
                System.arraycopy(data, dataOffset, pcm, 0, len);
                return pcm;
            }
            i += 8 + size;
        }
        byte[] pcm = new byte[data.length - dataOffset];
        System.arraycopy(data, dataOffset, pcm, 0, pcm.length);
        return pcm;
    }

    private static byte[] decodeWithMediaExtractor(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    break;
                }
            }
            if (track < 0) {
                throw new IOException("文件中没有音频轨道");
            }
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                throw new IOException("未知音频格式");
            }

            MediaCodec codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;

            while (true) {
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                        if (inBuf != null) {
                            int sampleSize = extractor.readSampleData(inBuf, 0);
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize,
                                    extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (inputDone) break;
                } else if (outIndex >= 0) {
                    ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                    if (outBuf != null && info.size > 0) {
                        byte[] chunk = new byte[info.size];
                        outBuf.position(info.offset);
                        outBuf.get(chunk);
                        out.write(chunk);
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }

            codec.stop();
            codec.release();
            byte[] raw = out.toByteArray();
            if (raw.length == 0) {
                throw new IOException("解码后音频为空");
            }

            // 读取原始采样率和声道数，进行重采样与混音到 16kHz 单声道
            MediaFormat outputFormat = extractor.getTrackFormat(0);
            // 重新拿一次已选 track 的 format（extractor 已 release 前）
            int srcRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : TARGET_RATE;
            int srcChannels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;

            Log.i(TAG, "decoded " + file.getName()
                + " -> " + raw.length + " bytes, srcRate=" + srcRate
                + " channels=" + srcChannels);

            byte[] result = resampleToMono16k(raw, srcRate, srcChannels);
            Log.i(TAG, "resampled -> " + result.length + " bytes at 16kHz mono");
            return result;
        } finally {
            extractor.release();
        }
    }

    /**
     * 将 PCM-16bit 数据从任意采样率/声道数重采样至 16kHz 单声道。
     * 采用线性插值下采样，适合语音识别（非音乐级精度）。
     */
    static byte[] resampleToMono16k(byte[] src, int srcRate, int srcChannels) {
        if (srcRate <= 0 || srcChannels <= 0) return src;
        // 先做声道混音（多声道→单声道）
        short[] mono = toMono(src, srcChannels);
        if (srcRate == TARGET_RATE) {
            return shortsToBytes(mono);
        }
        // 线性插值重采样
        double ratio = (double) srcRate / TARGET_RATE;
        int outLen = (int) (mono.length / ratio);
        short[] out = new short[outLen];
        for (int i = 0; i < outLen; i++) {
            double pos = i * ratio;
            int idx = (int) pos;
            double frac = pos - idx;
            short s0 = mono[Math.min(idx, mono.length - 1)];
            short s1 = mono[Math.min(idx + 1, mono.length - 1)];
            out[i] = (short) (s0 + frac * (s1 - s0));
        }
        return shortsToBytes(out);
    }

    private static short[] toMono(byte[] src, int channels) {
        int samples = src.length / 2;
        int monoLen = samples / channels;
        short[] mono = new short[monoLen];
        for (int i = 0; i < monoLen; i++) {
            long sum = 0;
            for (int c = 0; c < channels; c++) {
                int byteIdx = (i * channels + c) * 2;
                if (byteIdx + 1 >= src.length) break;
                short s = (short) ((src[byteIdx + 1] << 8) | (src[byteIdx] & 0xFF));
                sum += s;
            }
            mono[i] = (short) (sum / channels);
        }
        return mono;
    }

    private static byte[] shortsToBytes(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            bytes[i * 2]     = (byte) (shorts[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) (shorts[i] >> 8);
        }
        return bytes;
    }

}
