package com.example.mylocalai

import com.example.mylocalai.util.AgentAnswerSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAnswerSanitizerTest {

    @Test
    fun stripsToolNarrationButKeepsTheRealAnswer() {
        val raw = """
            **Remote Execution Summary:**
            - `start_software_task(Create a website)`

            The local model returned an empty response after one retry.

            ## Tool output
            ### start_software_task
            ```text
            {"task": {"task_id": "958bcb43"}}
            ```

            [Task delegated] This request is now handled by a software task. See the Software tasks section to follow progress and verification.

            Done — the files are in ~/Desktop/website.
        """.trimIndent()

        val clean = AgentAnswerSanitizer.sanitize(raw)

        assertFalse(clean.contains("Remote Execution Summary"))
        assertFalse(clean.contains("Tool output"))
        assertFalse(clean.contains("958bcb43"))
        assertFalse(clean.contains("Task delegated"))
        assertTrue(clean.contains("The local model returned an empty response"))
        assertTrue(clean.contains("Done — the files are in"))
    }

    @Test
    fun stripsPlainTextToolOutputFences() {
        val raw = """
            **Remote Execution Summary:**
            - `write_file(/home/snow/Desktop/index.html)`

            Updated the page.

            ## Tool output
            ### write_file
            ```text
            Successfully wrote 1529 chars to /home/snow/Desktop/index.html
            ```
        """.trimIndent()

        val clean = AgentAnswerSanitizer.sanitize(raw)

        assertFalse(clean.contains("Successfully wrote"))
        assertFalse(clean.contains("write_file"))
        assertTrue(clean.contains("Updated the page."))
    }

    @Test
    fun replacesMaxIterationsTextWithFriendlyMessage() {
        val raw = """
            Reached max tool iterations.

            ### run_command
            ```text
            EXIT 0
            ```
        """.trimIndent()

        val clean = AgentAnswerSanitizer.sanitize(raw)

        assertFalse(clean.contains("Reached max tool iterations"))
        assertFalse(clean.contains("EXIT 0"))
        assertTrue(clean.contains("turn limit"))
    }

    @Test
    fun keepsRealCodeBlocks() {
        val raw = """
            Here is a script:

            ```python
            print("hello")
            ```
        """.trimIndent()

        val clean = AgentAnswerSanitizer.sanitize(raw)

        assertTrue(clean.contains("```python"))
        assertTrue(clean.contains("print(\"hello\")"))
    }

    @Test
    fun keepsBulletPointsStartingWithInlineCodeInRealAnswer() {
        val raw = """
            **Remote Execution Summary:**
            - `write_file(/home/snow/Desktop/index.html)`
            - `write_file(/home/snow/Desktop/style.css)`

            I created the website with these files:
            - `index.html` - main page with portfolio layout
            - `style.css` - responsive styling and themes
        """.trimIndent()

        val clean = AgentAnswerSanitizer.sanitize(raw)

        assertFalse(clean.contains("Remote Execution Summary"))
        assertFalse(clean.contains("write_file"))
        assertTrue(clean.contains("I created the website with these files:"))
        assertTrue(clean.contains("- `index.html` - main page with portfolio layout"))
        assertTrue(clean.contains("- `style.css` - responsive styling and themes"))
    }

    @Test
    fun handlesEmptyAndWhitespaceOnlyText() {
        assertEquals("", AgentAnswerSanitizer.sanitize(""))
        assertEquals("   ", AgentAnswerSanitizer.sanitize("   "))
    }
}
