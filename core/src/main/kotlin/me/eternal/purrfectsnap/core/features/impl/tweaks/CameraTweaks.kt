package me.eternal.purrfectsnap.core.features.impl.tweaks

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.Key
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.util.Range
import me.eternal.purrfectsnap.core.features.Feature
import me.eternal.purrfectsnap.core.util.hook.HookStage
import me.eternal.purrfectsnap.core.util.hook.hook
import me.eternal.purrfectsnap.core.util.ktx.setObjectField
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder

class CameraTweaks : Feature("Camera Tweaks") {
    private fun parseResolution(resolution: String): IntArray? {
        return runCatching { resolution.split("x").map { it.toInt() }.toIntArray() }.getOrNull()
    }

    @SuppressLint("MissingPermission", "DiscouragedApi")
    override fun init() {
        val config = context.config.camera
        val devOptions = context.config.experimental.developerOptions

        // --- PART A: MediaRecorder (Video & Audio Data) ---

        if (config.audioVideoOptimizations.get()) {
            MediaRecorder::class.java.hook("setVideoEncodingBitRate", HookStage.BEFORE) { param ->
                val rate = if (devOptions.enhancedCameraQuality.get()) 30_000_000 else {
                    val currentRate = param.arg<Int>(0)
                    if (currentRate < 30_000_000) 30_000_000 else currentRate
                }
                param.setArg(0, rate)
            }

            MediaRecorder::class.java.hook("setAudioEncodingBitRate", HookStage.BEFORE) { param ->
                val rate = if (devOptions.enhancedCameraQuality.get()) 320_000 else 128_000
                param.setArg(0, rate)
            }

            if (devOptions.enhancedCameraQuality.get()) {
                MediaRecorder::class.java.hook("setAudioSamplingRate", HookStage.BEFORE) { param ->
                    param.setArg(0, 48_000)
                }
            }
        }

        // --- PART B: CaptureRequest (ISP & Optics) ---

        CaptureRequest.Builder::class.java.hook("set", HookStage.BEFORE) { param ->
            val key = param.arg<CaptureRequest.Key<*>>(0)
            val isExtreme = devOptions.enhancedCameraQuality.get()
            val isGeneral = config.cameraOptimizations.get()

            if (!isExtreme && !isGeneral) return@hook

            when (key) {
                CaptureRequest.EDGE_MODE -> param.setArg(1, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                CaptureRequest.NOISE_REDUCTION_MODE -> param.setArg(1, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)     
                CaptureRequest.HOT_PIXEL_MODE -> param.setArg(1, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY)
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE -> param.setArg(1, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
                CaptureRequest.COLOR_CORRECTION_MODE -> param.setArg(1, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)   
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE -> {
                    if (isExtreme) param.setArg(1, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
                }
                CaptureRequest.CONTROL_AF_MODE -> param.setArg(1, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                CaptureRequest.CONTROL_ENABLE_ZSL -> {
                    if (isExtreme) {
                        val flashMode = runCatching {
                            param.thisObject<CaptureRequest.Builder>().get(CaptureRequest.FLASH_MODE)
                        }.getOrNull()
                        if (flashMode == null || flashMode == CaptureRequest.FLASH_MODE_OFF) {
                            param.setArg(1, true)
                        }
                    }
                }
            }
        }

        val frontCameraId by lazy {
            runCatching { context.androidContext.getSystemService(CameraManager::class.java).run {
                cameraIdList.firstOrNull { getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
            } }.getOrNull()
        }

        val backCameraId by lazy {
            runCatching { context.androidContext.getSystemService(CameraManager::class.java).run {
                cameraIdList.firstOrNull { getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
            } }.getOrNull()
        }

        if (config.disableCameras.get().isNotEmpty() && frontCameraId != null) {
            ContextWrapper::class.java.hook("checkPermission", HookStage.BEFORE) { param ->
                val permission = param.arg<String>(0)
                if (permission == Manifest.permission.CAMERA) {
                    param.setResult(PackageManager.PERMISSION_GRANTED)
                }
            }
        }

        var isLastCameraFront = false
        var startupCameraApplied = false

        CameraManager::class.java.hook("openCamera", HookStage.BEFORE) { param ->
            val cameraManager = param.thisObject() as? CameraManager ?: return@hook
            var cameraId = param.arg<String>(0)
            val disabledCameras = config.disableCameras.get()
            val startupDefaultCamera = config.startupDefaultCamera.getNullable()

            if (startupDefaultCamera != null && !startupCameraApplied) {
                val preferredCameraId = if (startupDefaultCamera == "back") backCameraId else frontCameraId
                if (preferredCameraId != null) {
                    if (preferredCameraId != cameraId) {
                        param.setArg(0, preferredCameraId)
                        cameraId = preferredCameraId
                    }
                    startupCameraApplied = true
                }
            }

            if (disabledCameras.size >= 2) {
                param.setResult(null)
                return@hook
            }

            isLastCameraFront = cameraId == frontCameraId

            if (disabledCameras.size != 1) return@hook

            if ((disabledCameras.contains("front") && isLastCameraFront) || (disabledCameras.contains("back") && !isLastCameraFront)) {
                param.setArg(0, cameraManager.cameraIdList.filterNot { it == cameraId }.firstOrNull() ?: return@hook)
                isLastCameraFront = !isLastCameraFront
            }
        }

        ImageReader::class.java.hook("newInstance", HookStage.BEFORE) { param ->
            val captureResolutionConfig = config.customResolution.getNullable()?.takeIf { it.isNotEmpty() }?.let { parseResolution(it) }
                ?: (if (isLastCameraFront) config.overrideFrontResolution.getNullable() else config.overrideBackResolution.getNullable())?.let { parseResolution(it) } ?: return@hook
            param.setArg(0, captureResolutionConfig[0])
            param.setArg(1, captureResolutionConfig[1])
        }

        CameraCharacteristics::class.java.hook("get", HookStage.AFTER)  { param ->
            val key = param.argNullable<Key<*>>(0) ?: return@hook

            if (key == CameraCharacteristics.LENS_FACING) {
                val disabledCameras = config.disableCameras.get()
                if (disabledCameras.size == 1) {
                    val isFrontCamera = param.getResult() as? Int == CameraCharacteristics.LENS_FACING_FRONT
                    if ((disabledCameras.contains("front") && isFrontCamera) || (disabledCameras.contains("back") && !isFrontCamera)) {
                        param.setResult(if (isFrontCamera) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT)
                    }
                }
            }

            if (config.unlockZoomLimit.get()) {
                val maxZoom = config.maxZoomOverride.get().coerceAtLeast(1f)
                when {
                    key == CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM -> {
                        param.setResult(maxZoom)
                    }
                    key.name == "android.control.zoomRatioRange" -> {
                        param.setResult(Range(1f, maxZoom))
                    }
                }
            }

            if (key == CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) {
                val isFrontCamera = param.invokeOriginal(
                    arrayOf(CameraCharacteristics.LENS_FACING)
                ) == CameraCharacteristics.LENS_FACING_FRONT
                val customFrameRate = (if (isFrontCamera) config.frontCustomFrameRate.getNullable() else config.backCustomFrameRate.getNullable())?.toIntOrNull() ?: return@hook
                param.setResult(arrayOf(Range(customFrameRate, customFrameRate)))
            }
        }

        if (config.blackPhotos.get()) {
            findClass("android.media.ImageReader\$SurfaceImage").hook("getPlanes", HookStage.AFTER) { param ->
                val image = param.thisObject() as? Image ?: return@hook
                val planes = param.getResult() as? Array<*> ?: return@hook
                planes.filterNotNull().forEach { plane ->
                    val buffer = runCatching {
                        plane.javaClass.getMethod("getBuffer").invoke(plane) as? ByteBuffer
                    }.getOrNull() ?: return@forEach
                    val zeroes = ByteArray(buffer.capacity())
                    buffer.clear()
                    buffer.put(zeroes)
                    buffer.flip()
                }
            }
        }
    }
}
