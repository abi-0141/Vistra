package com.vistra.camera.rtsp

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors

class RtspServer(
    private val port: Int = 8554
) {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return

        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true

                while (isRunning) {
                    val client = serverSocket?.accept()
                    if (client != null) {
                        executor.execute {
                            handleClient(client)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    e.printStackTrace()
                }
            } finally {
                isRunning = false
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->

            try {
                val reader =
                    BufferedReader(
                        InputStreamReader(client.getInputStream())
                    )

                val output = client.getOutputStream()

                val session = UUID.randomUUID().toString()

                while (true) {

                    val requestLine = reader.readLine()
                        ?: break

                    if (requestLine.isBlank()) {
                        continue
                    }

                    val headers = mutableMapOf<String, String>()

                    while (true) {
                        val line = reader.readLine() ?: break

                        if (line.isEmpty()) {
                            break
                        }

                        val separator = line.indexOf(':')

                        if (separator > 0) {
                            val key =
                                line.substring(0, separator).trim()

                            val value =
                                line.substring(separator + 1).trim()

                            headers[key] = value
                        }
                    }

                    val parts = requestLine.split(" ")

                    if (parts.size < 2) {
                        continue
                    }

                    val method = parts[0]
                    val cseq = headers["CSeq"] ?: "1"

                    when (method) {

                        "OPTIONS" -> {

                            send(
                                output,
                                "RTSP/1.0 200 OK\r\n" +
                                "CSeq: $cseq\r\n" +
                                "Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN\r\n" +
                                "\r\n"
                            )
                        }

                        "DESCRIBE" -> {

                            val sdp =
                                "v=0\r\n" +
                                "o=- 0 0 IN IP4 0.0.0.0\r\n" +
                                "s=VISTRA Network Camera\r\n" +
                                "c=IN IP4 0.0.0.0\r\n" +
                                "t=0 0\r\n" +
                                "a=control:*\r\n" +
                                "m=video 0 RTP/AVP/TCP 96\r\n" +
                                "a=rtpmap:96 H264/90000\r\n" +
                                "a=fmtp:96 packetization-mode=1;profile-level-id=42E01F;sprop-parameter-sets=Z0LgHtoCgPaE,aM4xUg==\r\n" +
                                "a=control:trackID=0\r\n"

                            send(
                                output,
                                "RTSP/1.0 200 OK\r\n" +
                                "CSeq: $cseq\r\n" +
                                "Content-Base: rtsp://0.0.0.0:$port/live/0/\r\n" +
                                "Content-Type: application/sdp\r\n" +
                                "Content-Length: ${sdp.toByteArray().size}\r\n" +
                                "\r\n" +
                                sdp
                            )
                        }

                        "SETUP" -> {

                            send(
                                output,
                                "RTSP/1.0 200 OK\r\n" +
                                "CSeq: $cseq\r\n" +
                                "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n" +
                                "Session: $session\r\n" +
                                "\r\n"
                            )
                        }

                        "PLAY" -> {

                            send(
                                output,
                                "RTSP/1.0 200 OK\r\n" +
                                "CSeq: $cseq\r\n" +
                                "Session: $session\r\n" +
                                "Range: npt=0.000-\r\n" +
                                "RTP-Info: url=rtsp://0.0.0.0:$port/live/0/trackID=0;seq=0;rtptime=0\r\n" +
                                "\r\n"
                            )

                            println("RTSP PLAY received")
                        }

                        "TEARDOWN" -> {

                            send(
                                output,
                                "RTSP/1.0 200 OK\r\n" +
                                "CSeq: $cseq\r\n" +
                                "Session: $session\r\n" +
                                "\r\n"
                            )

                            break
                        }

                        else -> {

                            send(
                                output,
                                "RTSP/1.0 501 Not Implemented\r\n" +
                                "CSeq: $cseq\r\n" +
                                "\r\n"
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun send(
        output: java.io.OutputStream,
        response: String
    ) {
        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()

        println("RTSP RESPONSE:")
        println(response)
    }

    fun stop() {
        isRunning = false

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        serverSocket = null
    }
}
