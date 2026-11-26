package com.example.mylocalai.data.remote

import com.example.mylocalai.model.SoftwareTaskDiff
import com.example.mylocalai.model.SoftwareTaskEvent
import com.example.mylocalai.model.SoftwareTaskEventsPage
import com.example.mylocalai.model.SoftwareTaskSummary
import org.json.JSONArray
import org.json.JSONObject

object SoftwareTaskParser {
    fun parseTask(raw: String, fallbackId: String = ""): SoftwareTaskSummary {
        val root = JSONObject(raw)
        val task = root.optJSONObject("task") ?: root.optJSONObject("data") ?: root
        val id = firstString(task, "task_id", "id").ifBlank { fallbackId }
        require(id.isNotBlank()) { "The server response did not include a task_id." }
        val step = task.optJSONObject("step")
        val progress = task.optJSONObject("progress")
        val worktree = task.optJSONObject("worktree")
        val stepsDone = firstInt(task, "steps_done", "step_count", "completed_steps", "current_step")
            ?: firstInt(progress, "completed", "done", "current")
        val stepsTotal = firstInt(task, "max_steps", "steps_total", "total_steps")
            ?: firstInt(progress, "total", "max")
        return SoftwareTaskSummary(
            taskId = id,
            objective = firstString(task, "objective", "title"),
            status = firstString(task, "status").ifBlank { "unknown" },
            phase = firstString(task, "phase", "current_phase").ifBlank { firstString(step, "phase", "name") },
            stepsDone = stepsDone,
            stepsTotal = stepsTotal,
            activeJobId = firstString(task, "active_job_id", "job_id").takeIf(String::isNotBlank),
            workspacePath = firstString(task, "workspace", "workspace_path").takeIf(String::isNotBlank),
            worktreePath = firstString(task, "worktree_path").takeIf(String::isNotBlank)
                ?: firstString(worktree, "path").takeIf(String::isNotBlank),
            statusUrl = firstString(task, "status_url").takeIf(String::isNotBlank),
            eventsUrl = firstString(task, "events_url").takeIf(String::isNotBlank),
            diffUrl = firstString(task, "diff_url").takeIf(String::isNotBlank),
            reused = task.optBoolean("reused", false),
            plan = task.opt("plan")?.takeUnless { it == JSONObject.NULL }?.displayValue(),
            error = firstString(task, "error", "errors", "error_detail").takeIf(String::isNotBlank),
            finalReport = task.opt("final_report")?.takeUnless { it == JSONObject.NULL }?.displayValue(),
            rawJson = task.toString(2),
        )
    }

    fun parseEvents(raw: String, cursor: Long): SoftwareTaskEventsPage {
        val root = JSONObject(raw)
        val array = root.optJSONArray("events") ?: root.optJSONArray("items") ?: JSONArray()
        val events = buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val id = value.optLong("id", value.optLong("event_id", cursor + index + 1))
                add(
                    SoftwareTaskEvent(
                        id = id,
                        eventType = firstString(value, "type", "event", "kind").ifBlank { "event" },
                        message = firstString(value, "message", "text", "detail", "content"),
                        rawJson = value.toString(2),
                    )
                )
            }
        }
        val next = root.optLong("next_after_id", events.maxOfOrNull { it.id } ?: cursor)
        return SoftwareTaskEventsPage(events, next.coerceAtLeast(cursor))
    }

    fun parseDiff(raw: String): SoftwareTaskDiff {
        val root = JSONObject(raw)
        val files = root.optJSONArray("untracked_files") ?: JSONArray()
        return SoftwareTaskDiff(
            baseCommit = firstString(root, "base_commit"),
            worktreePath = firstString(root, "worktree_path"),
            diff = firstString(root, "diff", "patch"),
            untrackedFiles = buildList { for (index in 0 until files.length()) add(files.optString(index)) },
            truncated = root.optBoolean("truncated", false),
        )
    }

    fun taskIdFromCreate(raw: String): String {
        val root = JSONObject(raw)
        val task = root.optJSONObject("task") ?: root.optJSONObject("data") ?: root
        return firstString(task, "task_id", "id")
    }

    private fun firstString(json: JSONObject?, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            json?.opt(key)?.takeUnless { it == JSONObject.NULL }?.let { value ->
                when (value) {
                    is String -> value.takeIf(String::isNotBlank)
                    is JSONObject, is JSONArray -> value.toString()
                    else -> value.toString().takeIf(String::isNotBlank)
                }
            }
        }.orEmpty()

    private fun firstInt(json: JSONObject?, vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
        json?.opt(key)?.let { value ->
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }
    }

    private fun Any.displayValue(): String = when (this) {
        is String -> this
        is JSONObject -> toString(2)
        is JSONArray -> toString(2)
        else -> toString()
    }
}
