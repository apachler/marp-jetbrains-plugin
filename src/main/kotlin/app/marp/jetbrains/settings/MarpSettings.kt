package app.marp.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "MarpSettings", storages = [Storage("marp.xml")])
@Service(Service.Level.APP)
class MarpSettings : PersistentStateComponent<MarpSettings.State> {

    data class State(
        var nodeJsPath: String? = null,
        var marpCliPath: String? = null,
        var marpCliVersion: String = "latest",
        var previewRefreshDelayMs: Int = 300,
        var autoOpenPreview: Boolean = true,
        var allowLocalFiles: Boolean = false,
    )

    var state: State = State()
        private set

    override fun getState(): State = state

    override fun loadState(s: State) {
        state = s
    }

    fun update(block: (State) -> Unit) {
        val copy = state.copy()
        block(copy)
        state = copy
    }

    companion object {
        fun getInstance(): MarpSettings = service()
    }
}
