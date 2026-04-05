package me.eternal.purrfectsnap.common.config.impl

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import me.eternal.purrfectsnap.common.Constants
import me.eternal.purrfectsnap.common.config.*
import me.eternal.purrfectsnap.common.logger.AbstractLogger

class Camera : ConfigContainer() {
    private val customFrameRates = arrayOf("60", "120", "240")

    private val _overrideFrontResolution = string("override_front_resolution") { addNotices(FeatureNotice.UNSTABLE); inputCheck = { it.matches(Regex("\\d+x\\d+")) } }
    private val _overrideBackResolution = string("override_back_resolution") { addNotices(FeatureNotice.UNSTABLE); inputCheck = { it.matches(Regex("\\d+x\\d+")) } }

    val disableCameras = multiple("disable_cameras", "front", "back") { requireRestart() }
    val blackPhotos = boolean("black_photos")
    val frontCustomFrameRate = unique("front_custom_frame_rate", *customFrameRates) { requireRestart(); addFlags(ConfigFlag.NO_TRANSLATE) }
    val backCustomFrameRate = unique("back_custom_frame_rate", *customFrameRates) { requireRestart(); addFlags(ConfigFlag.NO_TRANSLATE) }
    val hevcRecording = boolean("hevc_recording") { requireRestart() }
    val forceCameraSourceEncoding = boolean("force_camera_source_encoding")
    val startupDefaultCamera = unique("startup_default_camera", "front", "back") { requireRestart() }
    val overrideFrontResolution get() = _overrideFrontResolution
    val overrideBackResolution get() = _overrideBackResolution
    val videoRecordTimer = boolean("video_record_timer")

    val unlockZoomLimit = boolean("unlock_zoom_limit") { requireRestart(); addNotices(FeatureNotice.UNSTABLE) }
    val maxZoomOverride = float("max_zoom_override", 120f) {
        requireRestart()
        addFlags(ConfigFlag.NO_TRANSLATE)
        inputCheck = { (it.toFloatOrNull() ?: 0f) in 1f..500f }
    }

    val audioVideoOptimizations = boolean("audio_video", defaultValue = true) { requireRestart() }
    val cameraOptimizations = boolean("camera_tweaks", defaultValue = false) { addNotices(FeatureNotice.UNSTABLE); requireRestart() }

    val customResolution = string("custom_resolution") { addNotices(FeatureNotice.UNSTABLE); inputCheck = { it.matches(Regex("\\d+x\\d+")) } }
}
