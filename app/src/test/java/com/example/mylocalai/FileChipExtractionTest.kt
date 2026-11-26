package com.example.mylocalai

import com.example.mylocalai.ui.components.extractMentionedFilePaths
import org.junit.Assert.assertEquals
import org.junit.Test

class FileChipExtractionTest {

    @Test
    fun extractsDistinctAbsolutePathsFromProse() {
        val answer = "Done. `/home/snow/Desktop/mysite/index.html` and " +
            "/home/snow/Desktop/mysite/style.css were created. " +
            "Also updated /home/snow/Desktop/mysite/index.html again."
        assertEquals(
            listOf(
                "/home/snow/Desktop/mysite/index.html",
                "/home/snow/Desktop/mysite/style.css"
            ),
            extractMentionedFilePaths(answer)
        )
    }

    @Test
    fun stripsTrailingPunctuationAndIgnoresBareFilenames() {
        val answer = "Wrote /home/snow/notes.txt, then `file.py` and (see /tmp/build.log)."
        assertEquals(
            listOf("/home/snow/notes.txt", "/tmp/build.log"),
            extractMentionedFilePaths(answer)
        )
    }

    @Test
    fun returnsEmptyForPlainChat() {
        assertEquals(emptyList<String>(), extractMentionedFilePaths("Tailscale is a VPN."))
    }
}
