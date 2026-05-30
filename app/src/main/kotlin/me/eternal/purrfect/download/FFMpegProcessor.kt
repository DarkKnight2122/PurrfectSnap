package me.eternal.purrfect.download

import android.media.AudioFormat
import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.suspendCancellableCoroutine
import me.eternal.purrfect.LogManager
import me.eternal.purrfect.RemoteSideContext
import me.eternal.purrfect.common.config.impl.DownloaderConfig
import me.eternal.purrfect.common.data.download.AudioStreamFormat
import me.eternal.purrfect.common.logger.LogLevel
import me.eternal.purrfect.task.PendingTask
import java.io.File
import java.util.concurrent.Executors


class ArgumentList  {
    private val arguments = mutableListOf<Pair<String, String>>()

    operator fun plusAssign(stringPair: Pair<String, String>) {
        arguments += stringPair
    }

    operator fun plusAssign(key: String) {
        arguments += key to ""
    }

    operator fun minusAssign(key: String) {
        arguments.removeIf { it.first == key }
    }

    operator fun get(key: String) = arguments.find { it.first == key }?.second

    fun forEach(action: (Pair<String, String>) -> Unit) {
        arguments.forEach(action)
    }

    fun clear() {
        arguments.clear()
    }
}


class FFMpegProcessor(
    private val logManager: LogManager,
    private val ffmpegOptions: DownloaderConfig.FFMpegOptions,
    private val onStatistics: (Statistics) -> Unit = {}
) {
    companion object {
        private const val TAG = "ffmpeg-processor"

        fun newFFMpegProcessor(context: RemoteSideContext, pendingTask: PendingTask) = FFMpegProcessor(
            logManager = context.log,
            ffmpegOptions = context.config.root.downloader.ffmpegOptions,
            onStatistics = {
                pendingTask.updateProgress("Processing (frames=${it.videoFrameNumber}, fps=${it.videoFps}, time=${it.time}, bitrate=${it.bitrate}, speed=${it.speed})")
            }
        )

        fun newFFMpegProcessor(context: RemoteSideContext, onStatistics: (Statistics) -> Unit = {}) = FFMpegProcessor(
            logManager = context.log,
            ffmpegOptions = context.config.root.downloader.ffmpegOptions,
            onStatistics = onStatistics
        )
    }
    enum class Action {
        DOWNLOAD_DASH,
        MERGE_OVERLAY,
        CONVERSION,
        MERGE_MEDIA,
        DOWNLOAD_AUDIO_STREAM,
        MERGE_AUDIO_STREAMS,
    }

    data class Request(
        val action: Action,
        val inputs: List<String>,
        val output: File,
        val overlay: File? = null, //only for MERGE_OVERLAY
        val startTime: Long? = null, //only for DOWNLOAD_DASH
        val duration: Long? = null, //only for DOWNLOAD_DASH
        val audioStreamFormat: AudioStreamFormat? = null, //only for DOWNLOAD_AUDIO_STREAM
        val inputDelayOffsets: Map<String, Long>? = null, // only for MERGE_AUDIO_STREAMS

        var videoCodec: String? = null,
        var audioCodec: String? = null,
    )


    private val sharedExecutor = Executors.newSingleThreadExecutor()

    protected fun finalize() {
        runCatching { sharedExecutor.shutdown() }
    }

    private suspend fun newFFMpegTask(globalArguments: ArgumentList, inputArguments: ArgumentList, outputArguments: ArgumentList) = suspendCancellableCoroutine<FFmpegSession> {
        val stringBuilder = StringBuilder()
        arrayOf(globalArguments, inputArguments, outputArguments).forEach { argumentList ->
            argumentList.forEach { (key, value) ->
                stringBuilder.append("$key ${value.takeIf { it.isNotEmpty() }?.plus(" ") ?: ""}")
            }
        }

        logManager.debug("arguments: $stringBuilder", "FFMpegProcessor")

        FFmpegKit.executeAsync(stringBuilder.toString(),
            { session ->
                it.resumeWith(
                    if (session.returnCode.isValueSuccess) {
                        Result.success(session)
                    } else {
                        val output = session.output
                        val errorMsg = when {
                            output.isNullOrBlank() -> "FFmpeg failed (exit code: ${session.returnCode})"
                            else -> {
                                val lines = output.lines().filter { line ->
                                    line.isNotBlank() && !line.startsWith("ffmpeg version", ignoreCase = true) && !line.contains("Copyright")
                                }
                                lines.lastOrNull()?.take(400)
                                    ?: "FFmpeg failed. Try changing video codec in FFmpeg options (e.g. libx264)"
                            }
                        }
                        Result.failure(Exception(errorMsg))
                    }
                )
            }, logFunction@{ log ->
                logManager.internalLog(TAG, when (log.level) {
                    Level.AV_LOG_ERROR, Level.AV_LOG_FATAL -> LogLevel.ERROR
                    Level.AV_LOG_WARNING -> LogLevel.WARN
                    Level.AV_LOG_VERBOSE -> LogLevel.VERBOSE
                    else -> return@logFunction
                }, log.message)
            }, { onStatistics(it) }, sharedExecutor)
    }

    suspend fun execute(args: Request) {
        // load ffmpeg native sync to avoid native crash
        synchronized(this) { FFmpegKit.listSessions() }
        val globalArguments = ArgumentList().apply {
            this += "-y"
            this += "-threads" to ffmpegOptions.threads.get().toString()
        }

        val inputArguments = ArgumentList().apply {
            args.inputs.forEach { path ->
                this += "-i" to "\"$path\""
            }
        }

        val outputArguments = ArgumentList().apply {
            this += "-c:a" to (ffmpegOptions.customAudioCodec.get().takeIf { it.isNotEmpty() }?.lowercase() ?: "copy")
            this += "-b:a" to ffmpegOptions.audioBitrate.get().toString() + "K"
        }

        fun applyVideoArguments() {
            outputArguments += "-preset" to (ffmpegOptions.preset.getNullable() ?: "ultrafast")
            outputArguments += "-c:v" to (ffmpegOptions.customVideoCodec.get().takeIf { it.isNotEmpty() }?.lowercase() ?: "libx264")
            outputArguments += "-crf" to ffmpegOptions.constantRateFactor.get().let { "\"$it\"" }
            outputArguments += "-b:v" to ffmpegOptions.videoBitrate.get().toString() + "K"
        }

        when (args.action) {
            Action.DOWNLOAD_DASH -> {
                applyVideoArguments()
                outputArguments += "-ss" to "'${args.startTime}ms'"
                if (args.duration != null) {
                    outputArguments += "-t" to "'${args.duration}ms'"
                }
            }
            Action.MERGE_OVERLAY -> {
                applyVideoArguments()
                inputArguments += "-i" to "\"${args.overlay!!.absolutePath}\""

                val isVideo = runCatching {
                    MediaMetadataRetriever().use { mmr ->
                        val file = File(args.inputs[0])
                        file.inputStream().use { fis -> mmr.setDataSource(fis.fd, 0, file.length()) }
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
                    }
                }.getOrElse {
                    logManager.error("MediaMetadataRetriever failed to read inputs[0]: ${args.inputs[0]}", it)
                    val ext = args.inputs[0].substringAfterLast('.').lowercase()
                    ext == "mp4" || ext == "mkv" || ext == "mov" || ext == "avi" || ext == "webm" || ext == "gif"
                }

                if (isVideo) {
                    // [1][0]scale2ref: scale overlay (1) to match source (0). format=rgba: preserve text transparency.
                    outputArguments += "-filter_complex" to "\"[1][0]scale2ref[img][vid];[img]format=rgba,setsar=1[img];[vid][img]overlay=(W-w)/2:(H-h)/2,scale=2*trunc(iw*sar/2):2*trunc(ih/2)\""
                } else {
                    outputArguments += "-filter_complex" to "\"[1][0]scale2ref[img][src];[img]format=rgba,setsar=1[img];[src][img]overlay=(W-w)/2:(H-h)/2,scale=2*trunc(iw*sar/2):2*trunc(ih/2)\""
                }
            }
            Action.CONVERSION -> {
                if (ffmpegOptions.customAudioCodec.isEmpty()) {
                    outputArguments -= "-c:a"
                }
                args.videoCodec?.let {
                    applyVideoArguments()
                    outputArguments -= "-c:v"
                    outputArguments += "-c:v" to it
                } ?: run {
                    outputArguments += "-vn"
                }
                args.audioCodec?.let {
                    outputArguments -= "-c:a"
                    outputArguments += "-c:a" to it
                }
            }
            Action.MERGE_MEDIA -> {
                applyVideoArguments()
                inputArguments.clear()
                val filesInfo = args.inputs.mapNotNull { file ->
                    runCatching {
                        val f = File(file)
                        MediaMetadataRetriever().apply { 
                            f.inputStream().use { fis -> setDataSource(fis.fd, 0, f.length()) }
                        }
                    }.getOrNull()?.let { file to it }
                }

                try {
                    val (maxWidth, maxHeight) = filesInfo.maxByOrNull { (_, r) ->
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    }?.let { (_, r) ->
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() to
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    } ?: throw Exception("Failed to get video size")

                    val filterFirstPart = StringBuilder()
                    val filterSecondPart = StringBuilder()
                    var containsNoSound = false

                    filesInfo.forEachIndexed { index, (file, retriever) ->
                        filterFirstPart.append("[$index:v]scale=$maxWidth:$maxHeight,setsar=1[v$index];")
                        if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") {
                            filterSecondPart.append("[v$index][$index:a]")
                        } else {
                            containsNoSound = true
                            filterSecondPart.append("[v$index][${filesInfo.size}:a]")
                        }
                        inputArguments += "-i" to "\"$file\""
                    }

                    if (containsNoSound) {
                        inputArguments += "-f" to "lavfi"
                        inputArguments += "-t" to "0.1"
                        inputArguments += "-i" to "anullsrc=channel_layout=stereo:sample_rate=44100"
                    }

                    if (outputArguments["-c:a"] == "copy") {
                        outputArguments -= "-c:a"
                    }

                    outputArguments += "-fps_mode" to "vfr"

                    outputArguments += "-filter_complex" to "\"$filterFirstPart ${filterSecondPart}concat=n=${filesInfo.size}:v=1:a=1[vout][aout]\""
                    outputArguments += "-map" to "\"[aout]\""
                    outputArguments += "-map" to "\"[vout]\""
                } finally {
                    filesInfo.forEach { it.second.close() }
                }
            }
            Action.DOWNLOAD_AUDIO_STREAM -> {
                outputArguments.clear()
                globalArguments += "-f" to when (args.audioStreamFormat!!.encoding) {
                    AudioFormat.ENCODING_PCM_8BIT -> "u8"
                    AudioFormat.ENCODING_PCM_16BIT -> "s16le"
                    AudioFormat.ENCODING_PCM_FLOAT -> "f32le"
                    AudioFormat.ENCODING_PCM_32BIT -> "s32le"
                    else -> throw IllegalArgumentException("Unsupported audio encoding")
                }
                globalArguments += "-ar" to args.audioStreamFormat.sampleRate.toString()
                globalArguments += "-ac" to args.audioStreamFormat.channels.toString()
            }
            Action.MERGE_AUDIO_STREAMS -> {
                inputArguments.clear()
                outputArguments.clear()
                val filterParts = StringBuilder()
                args.inputs.forEachIndexed { index, input ->
                    inputArguments += "-i" to "\"$input\""
                    val offset = args.inputDelayOffsets?.get(input) ?: 0L
                    if (offset > 0) {
                        filterParts.append("[$index:a]adelay=$offset|$offset[a$index];")
                    } else {
                        filterParts.append("[$index:a]acopy[a$index];")
                    }
                }
                args.inputs.indices.forEach { index ->
                    filterParts.append("[a$index]")
                }
                filterParts.append("amix=inputs=${args.inputs.size}:duration=longest:normalize=0[aout]")
                outputArguments += "-filter_complex" to "\"$filterParts\""
                outputArguments += "-map" to "\"[aout]\""
            }
        }
        outputArguments += "\"${args.output.absolutePath}\"" to ""
        newFFMpegTask(globalArguments, inputArguments, outputArguments)
    }
}
