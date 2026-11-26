package com.example.mylocalai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTest {

    private fun keys(path: String): Map<String, String> {
        val candidates = listOf(
            File(path),
            File("app", path),
            File(System.getProperty("user.dir"), path),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("Missing resource file: $path (cwd=${System.getProperty("user.dir")})")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
            result[name] = node.textContent.trim()
        }
        return result
    }

    @Test
    fun hebrewAndArabicCoverAllKeys() {
        val base = keys("src/main/res/values/strings.xml")
        val he = keys("src/main/res/values-he/strings.xml")
        val ar = keys("src/main/res/values-ar/strings.xml")
        assertTrue(base.isNotEmpty())
        val missingHe = base.keys - he.keys
        val missingAr = base.keys - ar.keys
        assertTrue("Missing Hebrew keys: $missingHe", missingHe.isEmpty())
        assertTrue("Missing Arabic keys: $missingAr", missingAr.isEmpty())
        assertTrue("Empty Hebrew value", he.values.all { it.isNotBlank() })
        assertTrue("Empty Arabic value", ar.values.all { it.isNotBlank() })
        assertEquals("New chat", base["nav_new_chat"])
        assertTrue(he["nav_new_chat"]!!.contains("שיחה"))
        assertTrue(ar["nav_new_chat"]!!.contains("محادثة"))
    }

    @Test
    fun rtlLanguagesAreDetected() {
        fun isRtl(lang: String) = lang.equals("he", true) || lang.equals("ar", true)
        assertTrue(isRtl("he"))
        assertTrue(isRtl("ar"))
        assertTrue(!isRtl("en"))
        assertTrue(!isRtl("system"))
    }

    @Test
    fun manifestEnablesRtl() {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File(System.getProperty("user.dir"), "app/src/main/AndroidManifest.xml"),
        )
        val manifest = candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("AndroidManifest.xml not found")
        val text = manifest.readText()
        assertTrue("supportsRtl must be enabled", text.contains("supportsRtl") &&
            text.substringAfter("supportsRtl").substringBefore("\n").contains("true"))
    }

    @Test
    fun placeholderFormatsSurviveTranslation() {
        val he = keys("src/main/res/values-he/strings.xml")
        val ar = keys("src/main/res/values-ar/strings.xml")
        for (key in listOf("continuing", "chip_queued", "banner_downloading", "banner_saved")) {
            assertTrue("$key missing %1\$s/%1\$d in Hebrew", he[key]!!.contains("%1\$"))
            assertTrue("$key missing %1\$s/%1\$d in Arabic", ar[key]!!.contains("%1\$"))
        }
    }
}
