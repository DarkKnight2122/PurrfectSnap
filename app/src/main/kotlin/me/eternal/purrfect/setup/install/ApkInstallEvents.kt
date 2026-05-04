package me.eternal.purrfect.setup.install

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ApkInstallEvent(
    val packageName: String?,
    val status: Int,
    val message: String,
    val isFailure: Boolean
)

object ApkInstallEvents {
    private val _events = MutableSharedFlow<ApkInstallEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun publish(event: ApkInstallEvent) {
        _events.tryEmit(event)
    }
}
