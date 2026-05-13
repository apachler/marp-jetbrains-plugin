package app.marp.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic

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
        /**
         * Application-bus topic fired after the settings dialog applies a
         * change. Listeners receive the pre- and post-state snapshots so they
         * can decide which side effect (if any) to perform.
         */
        @JvmField
        val TOPIC: Topic<MarpSettingsListener> =
            Topic.create("Marp settings", MarpSettingsListener::class.java)

        fun getInstance(): MarpSettings = service()
    }
}

interface MarpSettingsListener {
    fun onSettingsChanged(old: MarpSettings.State, new: MarpSettings.State)
}
