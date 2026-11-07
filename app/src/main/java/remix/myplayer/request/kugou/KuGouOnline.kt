package remix.myplayer.request.kugou

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 酷狗在线音乐（搜索/榜单/歌单/取流），协议来自 kg_lite.py。
 * 只走 mobilecdn 的免签接口，不需要账号。
 * 注意：要和项目里已有的 KuGouClient（歌词用）区分开，这里是独立的在线播放源。
 */
object KuGouOnline {

    private const val TAG = "KuGouOnline"
    private const val UA = "Android712-AndroidPhone-11410-18-0-playtype-0-0-0-0-0-0-0"

    data class KgSong(
        val hash: String,
        val name: String,
        val singer: String,
        val albumId: String = "",
        val albumName: String = "",
        val durationMs: Int = 0,
        val imgUrl: String = ""
    )

    private fun get(base: String, params: Map<String, String>): String? {
        return try {
            val query = params.entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            val url = base + (if (base.contains("?")) "&" else "?") + query
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 25000
                setRequestProperty("User-Agent", UA)
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
              // 接口响应都在 1MB 以内，超了说明被劫持/拿到错误页
              val buf = ByteArray(256 * 1024)
              val out = java.io.ByteArrayOutputStream()
              var total = 0
              while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > 4 * 1024 * 1024) return null
                out.write(buf, 0, n)
              }
              out.toString("UTF-8")
            }
        } catch (e: Exception) {
            Log.w(TAG, "get $base: $e")
            null
        }
    }

    private fun clean(s: String?) = s.orEmpty().replace("<em>", "").replace("</em>", "")

    private fun parse(o: JSONObject?): KgSong? {
        if (o == null) return null
        val hash = o.optString("hash").ifBlank { o.optString("FileHash") }
        if (hash.isBlank()) return null
        val name = clean(
            o.optString("songname").ifBlank {
                o.optString("SongName").ifBlank { o.optString("filename") }
            }
        )
        val singer = clean(o.optString("singername").ifBlank { o.optString("SingerName") })
        val dur = o.optString("duration").toIntOrNull() ?: o.optInt("Duration", 0)
        return KgSong(
            hash = hash,
            name = name.ifBlank { "未知歌曲" },
            singer = singer,
            albumId = o.optString("album_id").ifBlank { o.optString("AlbumID") },
            albumName = clean(o.optString("album_name")),
            // 搜索接口给的是秒（歌再长也就几千秒，不会到 10 万），
            // 榜单接口偶尔直接给毫秒；用 600 秒作为分界，避免长歌被误判。
            durationMs = if (dur in 1..600) dur * 1000 else dur,
            imgUrl = o.optString("imgUrl").ifBlank { o.optString("Image") }
        )
    }

    /** 搜索歌曲 */
    fun search(keyword: String, pageSize: Int = 30): List<KgSong> {
        if (keyword.isBlank()) return emptyList()
        val raw = get(
            "http://mobilecdn.kugou.com/api/v3/search/song",
            mapOf(
                "format" to "json", "keyword" to keyword,
                "page" to "1", "pagesize" to pageSize.toString(), "showtype" to "1"
            )
        ) ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONObject("data")?.optJSONArray("info")
                ?: return emptyList()
            (0 until arr.length()).mapNotNull { parse(arr.optJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 排行榜 */
    fun ranks(): List<Pair<Int, String>> {
        val raw = get(
            "http://mobilecdn.kugou.com/api/v3/rank/list",
            mapOf(
                "version" to "1", "plat" to "0", "pagesize" to "20",
                "page" to "1", "withsong" to "0", "parentid" to "0"
            )
        ) ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONObject("data")?.optJSONArray("info")
                ?: return emptyList()
            (0 until arr.length()).mapNotNull {
                val o = arr.optJSONObject(it) ?: return@mapNotNull null
                val id = o.optInt("rankid")
                if (id <= 0) null else id to clean(o.optString("rankname"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 榜单歌曲 */
    fun rankSongs(rankId: Int, pageSize: Int = 50): List<KgSong> {
        val raw = get(
            "http://mobilecdn.kugou.com/api/v3/rank/song",
            mapOf(
                "rankid" to rankId.toString(), "ranktype" to "1", "plat" to "0",
                "pagesize" to pageSize.toString(), "page" to "1", "version" to "8000"
            )
        ) ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONObject("data")?.optJSONArray("info")
                ?: return emptyList()
            (0 until arr.length()).mapNotNull { parse(arr.optJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 歌单歌曲 */
    fun playlistSongs(specialId: String, pageSize: Int = 100): List<KgSong> {
        val raw = get(
            "http://mobilecdn.kugou.com/api/v3/special/song",
            mapOf(
                "specialid" to specialId, "plat" to "0",
                "pagesize" to pageSize.toString(), "page" to "1", "version" to "8000"
            )
        ) ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONObject("data")?.optJSONArray("info")
                ?: return emptyList()
            (0 until arr.length()).mapNotNull { parse(arr.optJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 取播放直链（未登录时仅部分歌有 url） */
    fun playUrl(hash: String): String? {
        val raw = get(
            "http://m.kugou.com/app/i/getSongInfo.php",
            mapOf("cmd" to "playInfo", "hash" to hash, "from" to "mkugou")
        ) ?: return null
        return try {
            val o = JSONObject(raw)
            val url = o.optString("url")
            if (url.isNotBlank()) url else {
                val arr = o.optJSONArray("backup_url")
                if (arr != null && arr.length() > 0) arr.optString(0).takeIf { it.isNotBlank() } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
