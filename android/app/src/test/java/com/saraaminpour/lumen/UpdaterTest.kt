package com.saraaminpour.lumen

import com.saraaminpour.lumen.net.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun versions() {
        assertTrue(Updater.isNewer("1.3.0", "1.2.0"))
        assertTrue(Updater.isNewer("v1.10.0", "1.9.9"))
        assertTrue(Updater.isNewer("2.0", "1.9.9"))
        assertFalse(Updater.isNewer("1.2.0", "1.2.0"))
        assertFalse(Updater.isNewer("1.1.9", "1.2.0"))
    }

    @Test
    fun notes() {
        val body = "Intro line\n\n## What's new\n\n- **Built-in updates:** a daily check\n- Paste a [YouTube](https://youtube.com) link with `Ctrl+V`\n* *Continue watching* titles\n"
        assertEquals(
            "•  Built-in updates: a daily check\n•  Paste a YouTube link with Ctrl+V\n•  Continue watching titles",
            Updater.shortNotes(body),
        )
    }
}
