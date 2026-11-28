package com.example.mylocalai.util

/** Removes raw tool-call narration and JSON dumps from a model's final answer. */
object AgentAnswerSanitizer {
    fun sanitize(text: String): String {
        if (text.isBlank()) return text
        val kept = mutableListOf<String>()
        var fenceOpen: String? = null
        var fenceIsToolOutput = false
        var skipNextFence = false
        val fenceBuffer = mutableListOf<String>()
        var skipDelegatedSentence = false
        var inRemoteExecutionSummary = false
        for (raw in text.lines()) {
            val line = raw.trim()
            if (fenceOpen != null) {
                if (line.startsWith("```")) {
                    val isJsonPayload = fenceBuffer.firstOrNull()?.trim()?.startsWith("{") == true
                    if (!isJsonPayload && !fenceIsToolOutput) {
                        kept.add(fenceOpen!!)
                        kept.addAll(fenceBuffer)
                        kept.add("```")
                    }
                    fenceOpen = null
                    fenceIsToolOutput = false
                    fenceBuffer.clear()
                } else {
                    fenceBuffer.add(raw)
                }
                continue
            }
            if (line.startsWith("```")) {
                fenceOpen = line
                fenceIsToolOutput = skipNextFence
                skipNextFence = false
                continue
            }
            if (line == "**Remote Execution Summary:**") {
                inRemoteExecutionSummary = true
                continue
            }
            if (inRemoteExecutionSummary) {
                if (line.startsWith("- `") || line.startsWith("- ")) continue
                if (line.isBlank()) {
                    inRemoteExecutionSummary = false
                    continue
                }
                inRemoteExecutionSummary = false
            }
            if (line.equals("## Tool output", ignoreCase = true)) continue
            // Server tool dumps are formatted as "### tool" + ```text fence. The header
            // is dropped above; remember to drop the following fence as well so no raw
            // tool output leaks into the chat bubble.
            if (line.startsWith("### ")) {
                skipNextFence = true
                continue
            }
            if (line.startsWith("Reached max tool iterations")) {
                kept.add("The agent reached its turn limit before writing a final summary. The completed steps are listed in the action log; the changes are already on the PC.")
                continue
            }
            if (line.startsWith("[Task delegated]")) {
                skipDelegatedSentence = true
                continue
            }
            if (skipDelegatedSentence) {
                if (line.isBlank()) continue
                skipDelegatedSentence = false
            }
            kept.add(raw)
        }
        // A truncated answer can end inside a real code fence. Keep what we have
        // instead of silently dropping everything after the opening fence.
        if (fenceOpen != null && !fenceIsToolOutput) {
            kept.add(fenceOpen!!)
            kept.addAll(fenceBuffer)
        }
        return kept.joinToString("\n").trim()
    }
}
