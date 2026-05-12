package app.marp.jetbrains.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PathUtilTest {

    @Test
    fun `encodes empty string as empty`() {
        assertEquals("", PathUtil.encodePathSegments(""))
    }

    @Test
    fun `passes through simple ascii unchanged`() {
        assertEquals("slides/intro.md", PathUtil.encodePathSegments("slides/intro.md"))
    }

    @Test
    fun `encodes spaces as percent-20 not plus`() {
        assertEquals("My%20Slides/Hello%20World.md", PathUtil.encodePathSegments("My Slides/Hello World.md"))
    }

    @Test
    fun `encodes non-ascii utf-8 sequences`() {
        // 'é' (U+00E9) → C3 A9 in UTF-8
        assertEquals(
            "r%C3%A9sum%C3%A9.md",
            PathUtil.encodePathSegments("résumé.md"),
        )
    }

    @Test
    fun `encodes characters that would otherwise change url semantics`() {
        // '?' and '#' would terminate the path otherwise
        val encoded = PathUtil.encodePathSegments("what?.md")
        assertEquals("what%3F.md", encoded)

        val withHash = PathUtil.encodePathSegments("a#b.md")
        assertEquals("a%23b.md", withHash)
    }

    @Test
    fun `preserves slashes between segments`() {
        assertEquals(
            "a/b%20c/d%20e.md",
            PathUtil.encodePathSegments("a/b c/d e.md"),
        )
    }

    @Test
    fun `keeps empty segments where present`() {
        // Defensive: a leading/trailing slash should not gain an extra encoded char.
        assertEquals("/foo", PathUtil.encodePathSegments("/foo"))
        assertEquals("foo/", PathUtil.encodePathSegments("foo/"))
    }

    @Test
    fun `encodes plus sign so it round-trips literally`() {
        // '+' must be percent-encoded, otherwise it would be interpreted as space
        // by some decoders.
        assertEquals("a%2Bb.md", PathUtil.encodePathSegments("a+b.md"))
    }
}
