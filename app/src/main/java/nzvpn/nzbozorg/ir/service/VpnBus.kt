package nzvpn.nzbozorg.ir.service

import kotlinx.coroutines.flow.MutableStateFlow

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, STOPPING }

data class VpnStatus(
    val state: VpnState = VpnState.DISCONNECTED,
    val profileName: String = "",
    val profileId: String = "",
    val groupName: String = "",
    val server: String = "",
    val latencyMs: Long = -1,
    val upBytes: Long = 0,
    val downBytes: Long = 0,
    val message: String = "",
)

/**
 * وضعیت اتصال بین سرویس VPN و UI (هر دو در یک پروسه هستند).
 */
object VpnBus {
    val status = MutableStateFlow(VpnStatus())

    fun update(block: (VpnStatus) -> VpnStatus) {
        status.value = block(status.value)
    }

    fun reset() {
        status.value = VpnStatus()
    }
}
