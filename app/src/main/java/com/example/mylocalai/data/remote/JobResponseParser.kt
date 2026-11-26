package com.example.mylocalai.data.remote

import com.example.mylocalai.model.JobDto
import com.google.gson.Gson
import org.json.JSONObject

object JobResponseParser {
    private val gson = Gson()

    /** Accept both the POST envelope ({"job": {...}}) and a direct GET job object. */
    fun parse(raw: String): JobDto? = runCatching {
        val root = JSONObject(raw)
        val job = root.optJSONObject("job") ?: root
        gson.fromJson(job.toString(), JobDto::class.java)
    }.getOrNull()?.takeIf { it.job_id.isNotBlank() && it.status.isNotBlank() }
}
