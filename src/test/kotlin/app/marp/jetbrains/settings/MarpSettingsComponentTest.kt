package app.marp.jetbrains.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarpSettingsComponentTest {

    private val defaults = MarpSettings.State()

    private fun modified(
        state: MarpSettings.State = defaults,
        nodeJsPath: String = state.nodeJsPath.orEmpty(),
        marpCliPath: String = state.marpCliPath.orEmpty(),
        marpCliVersion: String = state.marpCliVersion,
        refreshDelay: Int = state.previewRefreshDelayMs,
        autoOpen: Boolean = state.autoOpenPreview,
        allowLocalFiles: Boolean = state.allowLocalFiles,
    ): Boolean = MarpSettingsComponent.computeIsModified(
        state = state,
        nodeJsPathRaw = nodeJsPath,
        marpCliPathRaw = marpCliPath,
        marpCliVersionRaw = marpCliVersion,
        refreshDelay = refreshDelay,
        autoOpen = autoOpen,
        allowLocalFiles = allowLocalFiles,
    )

    @Test
    fun `defaults match defaults`() {
        assertFalse(modified())
    }

    @Test
    fun `nodeJsPath - blank field matches null state`() {
        assertFalse(modified(nodeJsPath = ""))
        assertFalse(modified(nodeJsPath = "   "))
    }

    @Test
    fun `nodeJsPath - non-blank when state is null is modified`() {
        assertTrue(modified(nodeJsPath = "/usr/bin/node"))
    }

    @Test
    fun `nodeJsPath - blank when state has value is modified`() {
        val state = defaults.copy(nodeJsPath = "/usr/bin/node")
        assertTrue(modified(state = state, nodeJsPath = ""))
    }

    @Test
    fun `marpCliVersion - blank field equals latest state`() {
        // Default state.marpCliVersion is "latest"; blank field should mean latest.
        assertFalse(modified(marpCliVersion = ""))
        assertFalse(modified(marpCliVersion = "   "))
    }

    @Test
    fun `marpCliVersion - explicit latest equals latest state`() {
        assertFalse(modified(marpCliVersion = "latest"))
    }

    @Test
    fun `marpCliVersion - new version vs latest is modified`() {
        assertTrue(modified(marpCliVersion = "1.2.3"))
    }

    @Test
    fun `marpCliVersion - blank field vs pinned state is modified`() {
        val state = defaults.copy(marpCliVersion = "1.2.3")
        // Blank means "apply will set to latest", which differs from 1.2.3.
        assertTrue(modified(state = state, marpCliVersion = ""))
    }

    @Test
    fun `marpCliVersion - same pinned version is not modified`() {
        val state = defaults.copy(marpCliVersion = "1.2.3")
        assertFalse(modified(state = state, marpCliVersion = "1.2.3"))
    }

    @Test
    fun `marpCliVersion - trimming applied`() {
        val state = defaults.copy(marpCliVersion = "1.2.3")
        assertFalse(modified(state = state, marpCliVersion = "  1.2.3  "))
    }

    @Test
    fun `refreshDelay - different value is modified`() {
        assertTrue(modified(refreshDelay = 500))
    }

    @Test
    fun `refreshDelay - same value is not modified`() {
        assertFalse(modified(refreshDelay = defaults.previewRefreshDelayMs))
    }

    @Test
    fun `autoOpen - toggled is modified`() {
        assertTrue(modified(autoOpen = !defaults.autoOpenPreview))
    }

    @Test
    fun `allowLocalFiles - toggled is modified`() {
        assertTrue(modified(allowLocalFiles = !defaults.allowLocalFiles))
    }

    @Test
    fun `multiple unrelated changes are all reflected`() {
        val state = defaults.copy(marpCliVersion = "1.0.0", previewRefreshDelayMs = 500)
        // Identical apart from one toggled checkbox.
        assertTrue(modified(state = state, marpCliVersion = "1.0.0", refreshDelay = 500,
            allowLocalFiles = !state.allowLocalFiles))
    }
}
