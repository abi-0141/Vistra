package com.vistra.camera.rtsp

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

class H264Encoder(
    private val width: Int = 1280,
    private val height: Int = 720,
    private val fps: Int = 15,
    private val bitrate: Int = 1_000_000
) {
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private val running = AtomicBoolean(false)

    companion object {
        private const val COLOR_FORMAT_SURFACE = 0x7F000789
    }

    fun start(): Surface {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        )

        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            COLOR_FORMAT_SURFACE
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec!!.configure(
            format,
            null,
            null,
            MediaCodec.CONFIGURE_FLAG_ENCODE
        )

        inputSurface = codec!!.createInputSurface()
        codec!!.start()
        running.set(true)

        return inputSurface!!
    }

    fun drain(onFrame: (ByteArray, Long, Boolean) -> Unit) {
        val encoder = codec ?: return
        val info = MediaCodec.BufferInfo()

        while (running.get()) {
            val index = encoder.dequeueOutputBuffer(info, 10_000)

            if (index >= 0) {
                encoder.getOutputBuffer(index)?.let { buffer ->
                    val data = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    buffer.get(data)

                    val keyFrame =
                        (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                    onFrame(data, info.presentationTimeUs, keyFrame)
                }

                encoder.releaseOutputBuffer(index, false)
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return

        try {
            codec?.stop()
        } catch (_: Exception) {
        }

        try {
            codec?.release()
        } catch (_: Exception) {
        }

        inputSurface?.release()
        inputSurface = null
        codec = null
    }
}
