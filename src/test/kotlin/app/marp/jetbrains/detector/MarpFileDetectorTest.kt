package app.marp.jetbrains.detector

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarpFileDetectorTest {

    @Test
    fun `recognises marp markdown by text`() {
        val text = """
            ---
            marp: true
            ---
            # Slide
        """.trimIndent()
        assertTrue(MarpFileDetector.isMarp(text))
    }

    @Test
    fun `rejects non marp markdown by text`() {
        val text = """
            ---
            title: Hello
            ---
            # Slide
        """.trimIndent()
        assertFalse(MarpFileDetector.isMarp(text))
    }

    @Test
    fun `rejects plain markdown without frontmatter`() {
        assertFalse(MarpFileDetector.isMarp("# Hello\nWorld"))
    }
}
