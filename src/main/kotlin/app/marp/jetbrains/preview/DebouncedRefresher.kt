package app.marp.jetbrains.preview

import com.intellij.openapi.Disposable
import com.intellij.util.Alarm

class DebouncedRefresher(
    parent: Disposable,
    private val defaultDelayMs: Int = 300,
    private val action: () -> Unit,
) : Disposable {

    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parent)

    fun schedule(delayMs: Int = defaultDelayMs) {
        alarm.cancelAllRequests()
        alarm.addRequest(action, delayMs.coerceAtLeast(0))
    }

    fun cancel() {
        alarm.cancelAllRequests()
    }

    override fun dispose() {
        alarm.cancelAllRequests()
    }
}
