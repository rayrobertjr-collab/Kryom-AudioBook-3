package ca.raymond.livresaudio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Recherche de couvertures sur Internet (Apple Books / iTunes et Google Livres, sans clé). */
object CoverSearch {

    suspend fun search(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val out = LinkedHashSet<String>()

        for (media in listOf("audiobook", "ebook")) {
            try {
                val json = JSONObject(get("https://itunes.apple.com/search?term=$q&media=$media&country=CA&limit=15"))
                val arr = json.optJSONArray("results") ?: continue
                for (i in 0 until arr.length()) {
                    val art = arr.getJSONObject(i).optString("artworkUrl100", "")
                    if (art.isNotBlank()) out += art.replace("100x100bb", "600x600bb")
                }
            } catch (_: Exception) {
            }
        }

        try {
            val json = JSONObject(get("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=20"))
            val arr = json.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val links = arr.getJSONObject(i).optJSONObject("volumeInfo")?.optJSONObject("imageLinks") ?: continue
                    val t = links.optString("thumbnail", "")
                    if (t.isNotBlank()) out += t.replace("http://", "https://").replace("&edge=curl", "")
                }
            }
        } catch (_: Exception) {
        }

        out.toList()
    }

    suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "LivresAudio/1.0")
            c.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "LivresAudio/1.0")
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
