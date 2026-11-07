package remix.myplayer.request.netease

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import remix.myplayer.lyric.LrcParser
import remix.myplayer.lyric.decrypt.NetEaseEapiCrypt.eapiParamsEncrypt
import remix.myplayer.lyric.decrypt.NetEaseEapiCrypt.eapiResponseDecrypt
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import androidx.core.content.edit


/**
 * 新的网易云接口
 */
@Singleton
class NetEaseClient @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val okHttpClient: OkHttpClient
) {

  private val json = Json { ignoreUnknownKeys = true }
  private val apiBase = "https://interface.music.163.com"

  @Volatile
  private var inited = false

  @Volatile
  private var expireTs: Long = 0L
  private val cookies: LinkedHashMap<String, String> = LinkedHashMap()
  private var userId: Long = 0L

  private fun nowSeconds() = System.currentTimeMillis() / 1000

  private fun genMac(): String {
    return List(6) { "%02X".format(Random.Default.nextInt(0, 256)) }.joinToString(":")
  }

  private fun genRandomUpperLetters(n: Int): String {
    val sb = StringBuilder(n)
    repeat(n) {
      sb.append(('A' + Random.Default.nextInt(26)))
    }
    return sb.toString()
  }

  private fun genTokenHex(bytes: Int): String {
    val arr = ByteArray(bytes) { Random.Default.nextInt(0, 256).toByte() }
    return arr.joinToString("") { "%02x".format(it) }
  }

  private fun getDeviceId(): String {
    val sp = context.getSharedPreferences("netease_config", Context.MODE_PRIVATE)
    var deviceId = sp.getString("device_id", null)
    if (deviceId.isNullOrEmpty()) {
      deviceId = genTokenHex(16)
      sp.edit { putString("device_id", deviceId) }
    }
    return deviceId
  }

  private fun getAnonymousUsername(deviceId: String): String {
    val xorKey = "3go8&$8*3*3h0k(2)2"
    val xored = deviceId.mapIndexed { i, ch ->
      (ch.code xor xorKey[i % xorKey.length].code).toChar()
    }.joinToString("")
    val md5 = MessageDigest.getInstance("MD5").digest(xored.toByteArray(Charsets.UTF_8))
    val md5b64 = Base64.encodeToString(md5, Base64.NO_WRAP)
    val combined = "$deviceId $md5b64"
    return Base64.encodeToString(combined.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
  }

  private fun getParamsHeader(cookies: Map<String, String>): String {
    val obj = JSONObject()
    obj.put("clientSign", cookies["clientSign"])
    obj.put("os", cookies["os"])
    obj.put("appver", cookies["appver"])
    obj.put("deviceId", cookies["deviceId"])
    obj.put("requestId", 0)
    obj.put("osver", cookies["osver"])
    return obj.toString()
  }

  private fun buildCookieHeader(): String {
    val base = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    val mu = musicU
    return if (mu.isNotEmpty() && !base.contains("MUSIC_U")) "$base; MUSIC_U=$mu" else base
  }

  @Synchronized
  private fun initIfNeeded() {
    if (inited && expireTs > nowSeconds()) return

    val preCookies = LinkedHashMap<String, String>().apply {
      put("os", "pc")
      put("deviceId", getDeviceId())
      put("osver", "Microsoft-Windows-10--build-${Random.Default.nextInt(200, 300)}00-64bit")
      val clientSign = "${genMac()}@@@${genRandomUpperLetters(8)}@@@@@@${genTokenHex(32)}"
      put("clientSign", clientSign)
      put("channel", "netease")
      put(
        "mode",
        listOf(
          "MS-iCraft B760M WIFI",
          "ASUS ROG STRIX Z790",
          "MSI MAG B550 TOMAHAWK",
          "ASRock X670E Taichi"
        ).random()
      )
      put("appver", "3.1.3.203419")
    }

    val path = "/eapi/register/anonimous"
    val params = JSONObject().apply {
      put("username", getAnonymousUsername(preCookies["deviceId"]!!))
      put("e_r", true)
      put("header", getParamsHeader(preCookies))
    }
    val bodyStr = eapiParamsEncrypt(path.replace("eapi", "api"), params)
    val req = Request.Builder()
      .url(apiBase + path)
      .post(
        RequestBody.Companion.create(
          "application/x-www-form-urlencoded".toMediaType(),
          bodyStr
        )
      )
      .header("accept", "*/*")
      .header("content-type", "application/x-www-form-urlencoded")
      .header("Cookie", preCookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
      .header("mconfig-info", """{"IuRPVVmc3WWul9fT":{"version":733184,"appver":"3.1.3.203419"}}""")
      .header("origin", "orpheus://orpheus")
      .header(
        "user-agent",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.3.203419"
      )
      .header("sec-ch-ua", "\"Chromium\";v=\"91\"")
      .header("sec-ch-ua-mobile", "?0")
      .header("sec-fetch-site", "cross-site")
      .header("sec-fetch-mode", "cors")
      .header("sec-fetch-dest", "empty")
//      .header("accept-encoding", "gzip, deflate, br")
      .header("accept-language", "en-US,en;q=0.9")
      .build()

    okHttpClient.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) {
        throw IllegalStateException("NetEase anonymous login failed: HTTP ${resp.code}")
      }
      val respBytes = resp.body?.bytes() ?: ByteArray(0)
      val jsonStr = eapiResponseDecrypt(respBytes)
      val obj = JSONObject(jsonStr)
      Timber.Forest.i("ne anonymous login code: ${obj.optInt("code")}")
      // Collect cookies
      val setCookies = resp.headers("Set-Cookie")
      val cookieMap = LinkedHashMap<String, String>(preCookies)
      setCookies.forEach {
        val part = it.substringBefore(';')
        val kv = part.split('=', limit = 2)
        if (kv.size == 2) {
          val k = kv[0]
          val v = kv[1]
          if (k in listOf("NMTID", "MUSIC_A", "__csrf", "MUSIC_U")) {
            cookieMap[k] = v
          }
        }
      }
      // remove empty values
      val iterator = cookieMap.iterator()
      while (iterator.hasNext()) {
        val e = iterator.next()
        if (e.value.isEmpty()) iterator.remove()
      }
      // 合并而不是清空：匿名注册拿不到登录态 cookie，
      // 清空会把之前的登录态（MUSIC_U / __csrf）一起抹掉。
      synchronized(cookies) {
        cookies.putAll(cookieMap)
        val mu = musicU
        if (mu.isNotEmpty()) cookies["MUSIC_U"] = mu
      }

      userId = obj.optLong("userId", 0L)
      // csrf 15天过期，这里设置 10 天缓存
      expireTs = nowSeconds() + 864000
      inited = true
    }
  }

  private fun eapiRequest(path: String, params: JSONObject): JSONObject {
    // 必须先完成匿名登录拿到 cookie，header 里要用 clientSign/os/appver/deviceId，
    // 顺序反了就是拿空 cookie 构造 header，请求大概率被拒。
    initIfNeeded()

    params.put("e_r", true)
    params.put("header", getParamsHeader(cookies))
    val bodyStr = eapiParamsEncrypt(path.replace("eapi", "api"), params)

    val req = Request.Builder()
      .url(apiBase + path)
      .post(
        RequestBody.Companion.create(
          "application/x-www-form-urlencoded".toMediaType(),
          bodyStr
        )
      )
      .header("accept", "*/*")
      .header("content-type", "application/x-www-form-urlencoded")
      .header("Cookie", buildCookieHeader())
      .header("mconfig-info", """{"IuRPVVmc3WWul9fT":{"version":733184,"appver":"3.1.3.203419"}}""")
      .header("origin", "orpheus://orpheus")
      .header(
        "user-agent",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.3.203419"
      )
      .header("sec-ch-ua", "\"Chromium\";v=\"91\"")
      .header("sec-ch-ua-mobile", "?0")
      .header("sec-fetch-site", "cross-site")
      .header("sec-fetch-mode", "cors")
      .header("sec-fetch-dest", "empty")
//      .header("accept-encoding", "gzip, deflate, br")
      .header("accept-language", "en-US,en;q=0.9")
      .build()

    okHttpClient.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) {
        throw IllegalStateException("NetEase eapi request failed: HTTP ${resp.code}")
      }
      // 登录接口会把 __csrf / MUSIC_U 放在 Set-Cookie 里，
      // 之前完全丢弃，导致登录后发出的 csrf 头还是匿名那次的值，
      // 收藏 / 发评论这类写操作恒失败。
      harvestCookies(resp.headers("Set-Cookie"))
      val respBytes = resp.body?.bytes() ?: ByteArray(0)
      val jsonStr = eapiResponseDecrypt(respBytes)
      val obj = JSONObject(jsonStr)
      if (obj.optInt("code") != 200) {
        val msg = obj.optString("message", "unknown")
        throw IllegalStateException("ne API error: code=${obj.optInt("code")} msg=$msg")
      }
      return obj
    }
  }

  /** 把响应里的关键 cookie 合并进内存表 */
  private fun harvestCookies(setCookies: List<String>) {
    if (setCookies.isEmpty()) return
    synchronized(cookies) {
      setCookies.forEach { raw ->
        val part = raw.substringBefore(';')
        val kv = part.split('=', limit = 2)
        if (kv.size == 2) {
          val k = kv[0].trim()
          val v = kv[1].trim()
          if (k in listOf("MUSIC_U", "MUSIC_A", "__csrf", "NMTID") && v.isNotEmpty()) {
            cookies[k] = v
          }
        }
      }
    }
  }

  /**
   * 将 yrc/ytlrc（每字时间格式）转换为常见增强型 LRC 文本：
   * 行格式：[mm:ss.xx]，词内时间标签：<mm:ss.xx>
   */
  private fun yrcToEnhancedLrc(yrc: String): String {
    val lineRegex = Regex("^\\[(\\d+),(\\d+)\\](.*)$")
    val wordRegex = Regex(
      "(?:\\[\\d+,\\d+\\])?\\((\\d+),(\\d+),\\d+\\)([\\s\\S]*?)(?=\\(\\d+,\\d+,\\d+\\)|$)",
      setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
    )
    val sb = StringBuilder()
    yrc.lineSequence().forEach { rawLine ->
      val line = rawLine.trim()
      if (!line.startsWith("[")) return@forEach
      val m = lineRegex.matchEntire(line) ?: return@forEach
      val (startStr, _, content) = m.destructured
      val lineStartMs = startStr.toInt()

      sb.append('[').append(LrcParser.formatMs(lineStartMs)).append(']').append(' ')

      var matchedAnyWord = false
      wordRegex.findAll(content).forEach { wm ->
        val wordStartMs = wm.groupValues[1].toInt()
        val wordContent = wm.groupValues[3]
        sb.append('<').append(LrcParser.formatMs(wordStartMs)).append('>').append(wordContent)
        matchedAnyWord = true
      }

      if (!matchedAnyWord) {
        sb.append(content)
      }
      sb.append('\n')
    }
    return sb.toString().trim()
  }

  /**
   * 搜索歌曲
   */
  fun searchSongList(keyword: String): List<NetEaseSong> {
    val params = JSONObject().apply {
      put("limit", "20")
      put("offset", "0")
      put("keyword", keyword)
      put("scene", "NORMAL")
      put("needCorrect", "true")
    }
    val obj = eapiRequest("/eapi/search/song/list/page", params)
    val data = obj.optJSONObject("data") ?: return emptyList()
    val resources = data.optJSONArray("resources") ?: JSONArray()
    if (resources.length() == 0) return emptyList()
    val result = mutableListOf<NetEaseSong>()
    for (i in 0 until resources.length()) {
      val baseInfo = resources.optJSONObject(i)?.optJSONObject("baseInfo")
      val simple = baseInfo?.optJSONObject("simpleSongData")
      if (simple != null) {
        try {
          result.add(json.decodeFromString<NetEaseSong>(simple.toString()))
        } catch (e: Exception) {
          Timber.w("searchSongList: $e")
        }
      }
    }
    return result
  }

  /**
   * 搜索专辑
   */
  fun searchAlbumList(keyword: String): List<NetEaseAlbum> {
    val params = JSONObject().apply {
      put("limit", "20")
      put("offset", "0")
      put("s", keyword)
      put("queryCorrect", "true")
    }
    val obj = eapiRequest("/eapi/v1/search/album/get", params)
    val albums = obj.optJSONObject("result")?.optJSONArray("albums") ?: return emptyList()
    val result = mutableListOf<NetEaseAlbum>()
    for (i in 0 until albums.length()) {
      val item = albums.optJSONObject(i) ?: continue
      try {
        result.add(json.decodeFromString<NetEaseAlbum>(item.toString()))
      } catch (e: Exception) {
        Timber.w("searchAlbumList: $e")
      }
    }
    return result
  }

  /**
   * 搜索艺术家
   */
  fun searchArtistList(keyword: String): List<NetEaseArtist> {
    val params = JSONObject().apply {
      put("limit", "20")
      put("offset", "0")
      put("s", keyword)
      put("queryCorrect", "true")
    }
    val obj = eapiRequest("/eapi/v1/search/artist/get", params)
    val artists = obj.optJSONObject("result")?.optJSONArray("artists") ?: return emptyList()
    val result = mutableListOf<NetEaseArtist>()
    for (i in 0 until artists.length()) {
      val item = artists.optJSONObject(i) ?: continue
      try {
        result.add(json.decodeFromString<NetEaseArtist>(item.toString()))
      } catch (e: Exception) {
        Timber.w("searchArtistList: $e")
      }
    }
    return result
  }

  /**
   * 获取歌词，返回 Pair(lrcText, tlyricText?)
   * 逻辑：
   * - 若存在增强歌词(yrc)，返回增强歌词与增强翻译(ytlrc)
   * - 否则返回普通歌词(lrc)与其普通翻译(tlyric)
   */
  fun getLyrics(song: NetEaseSong): Pair<String?, String?> {
    val params = JSONObject().apply {
      put("id", song.id)
      put("lv", "-1")
      put("tv", "-1")
      put("rv", "-1")
      put("yv", "-1")
    }
    val obj = eapiRequest("/eapi/song/lyric/v1", params)

    val yrc = obj.optJSONObject("yrc")?.optString("lyric")
    if (!yrc.isNullOrBlank()) {
      return Pair(yrcToEnhancedLrc(yrc), obj.optJSONObject("ytlrc")?.optString("lyric"))
    }

    // 没有增强歌词，使用普通歌词与其翻译
    return Pair(
      obj.optJSONObject("lrc")?.optString("lyric"),
      obj.optJSONObject("tlyric")?.optString("lyric")
    )
  }

  /**
   * 获取播放直链（在线播放用）
   * @param level standard / higher / exhigh / lossless
   */
  fun getSongUrl(id: Long, level: String = "exhigh"): String? {
    // 音质选项与编码格式要匹配：只有无损才要 flac，其余要 mp3，
    // 否则服务端会因为格式不符返回空 url（表现为「这首歌没有音源」）。
    val encodeType = if (level == "lossless") "flac" else "mp3"
    val params = JSONObject().apply {
      put("ids", "[$id]")
      put("level", level)
      put("encodeType", encodeType)
      put("immerseType", "c51")
      put("trialMode", -1)
    }
    return try {
      val obj = eapiRequest("/eapi/song/enhance/player/url/v1", params)
      val data = obj.optJSONArray("data") ?: return null
      val item = data.optJSONObject(0) ?: return null
      val code = item.optInt("code", 200)
      if (code != 200) {
        Timber.w("getSongUrl $id: code=$code level=$level")
        return null
      }
      item.optString("url").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
      Timber.w("getSongUrl: $e")
      null
    }
  }

  /**
   * 获取歌单详情（含全部歌曲）
   */
  fun getPlaylistDetail(playlistId: Long): NetEasePlaylistDetail? {
    val params = JSONObject().apply {
      put("id", playlistId)
      put("n", 10000)
      put("s", 0)
    }
    return try {
      val obj = eapiRequest("/eapi/v6/playlist/detail", params)
      val pl = obj.optJSONObject("playlist") ?: return null
      val tracks = mutableListOf<NetEaseSong>()
      val arr = pl.optJSONArray("tracks") ?: JSONArray()
      for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        try {
          tracks.add(json.decodeFromString<NetEaseSong>(item.toString()))
        } catch (e: Exception) {
          Timber.w("playlist track parse: $e")
        }
      }
      NetEasePlaylistDetail(
        id = pl.optLong("id"),
        name = pl.optString("name"),
        coverUrl = pl.optString("coverImgUrl"),
        trackCount = pl.optInt("trackCount"),
        tracks = tracks
      )
    } catch (e: Exception) {
      Timber.w("getPlaylistDetail: $e")
      null
    }
  }

  /**
   * 搜索歌单
   */
  fun searchPlaylist(keyword: String): List<NetEasePlaylistDetail> {
    // 纯数字：当成歌单 id 直接查详情，比搜索更准
    val asId = keyword.trim().toLongOrNull()
    if (asId != null && asId > 0) {
      getPlaylistDetail(asId)?.let { return listOf(it) }
    }
    // cloudsearch 只认 s + type（1000=歌单）；传 keyword/scene 会直接返回 400 参数错误，
    // 之前就是这里写错导致歌单永远搜不出来。
    val params = JSONObject().apply {
      put("s", keyword)
      put("type", "1000")
      put("limit", "30")
      put("offset", "0")
      put("total", "true")
    }
    return try {
      val obj = eapiRequest("/eapi/cloudsearch/pc", params)
      val result = obj.optJSONObject("result") ?: return emptyList()
      val arr = result.optJSONArray("playlists") ?: return emptyList()
      val out = mutableListOf<NetEasePlaylistDetail>()
      for (i in 0 until arr.length()) {
        val it = arr.optJSONObject(i) ?: continue
        out.add(
          NetEasePlaylistDetail(
            id = it.optLong("id"),
            name = it.optString("name"),
            coverUrl = it.optString("coverImgUrl"),
            trackCount = it.optInt("trackCount"),
            tracks = emptyList()
          )
        )
      }
      out
    } catch (e: Exception) {
      Timber.w("searchPlaylist: $e")
      emptyList()
    }
  }

  // ===================== 登录 / 账号 =====================

  private val accountPrefs by lazy {
    context.getSharedPreferences("netease_account", Context.MODE_PRIVATE)
  }

  /** 已登录的 MUSIC_U，空表示未登录 */
  var musicU: String
    get() = accountPrefs.getString("music_u", "").orEmpty()
    set(value) {
      accountPrefs.edit().putString("music_u", value).apply()
    }

  /** 登录用户的 uid */
  var loginUserId: Long
    get() = accountPrefs.getLong("uid", 0L)
    set(value) {
      accountPrefs.edit().putLong("uid", value).apply()
    }

  /** 昵称 */
  var loginNickname: String
    get() = accountPrefs.getString("nickname", "").orEmpty()
    set(value) {
      accountPrefs.edit().putString("nickname", value).apply()
    }

  /** 头像地址 */
  var loginAvatar: String
    get() = accountPrefs.getString("avatar", "").orEmpty()
    set(value) {
      accountPrefs.edit().putString("avatar", value).apply()
    }

  /**
   * 只要拿到 MUSIC_U 就算已登录。
   * 早期版本只看 uid>0，会出现「登录了但提示请先登录」，
   * 这里统一以 token 为准，uid 仅用于拉歌单。
   */
  val isLoggedIn: Boolean get() = musicU.isNotEmpty()

  fun logout() {
    musicU = ""
    loginUserId = 0L
    loginNickname = ""
    loginAvatar = ""
  }

  /** 网页版 UA：明文接口（验证码/二维码）只认这个 */
  private val webUa =
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) " +
      "Chrome/91.0.4472.164 Safari/537.36"

  /**
   * 发短信验证码。
   *
   * 原实现走 /eapi/sms/captcha/sent（eapi 加密），在部分网络/风控下直接 401 无权限，
   * 表现为"验证码获取不了"。这里改走网页明文接口 /api/sms/captcha/sent，
   * 实测可返回风控文案（说明请求已通到服务端），比 eapi 稳。
   */
  fun sendCaptcha(phone: String): Boolean {
    val body = okhttp3.FormBody.Builder()
      .add("cellphone", phone)
      .add("countrycode", "86")
      .add("ctcode", "86")
      .build()
    val req = Request.Builder()
      .url("https://music.163.com/api/sms/captcha/sent")
      .post(body)
      .header("accept", "*/*")
      .header("content-type", "application/x-www-form-urlencoded")
      .header("referer", "https://music.163.com/")
      .header("origin", "https://music.163.com")
      .header("user-agent", webUa)
      .build()
    return runCatching {
      okHttpClient.newCall(req).execute().use { resp ->
        harvestCookies(resp.headers("Set-Cookie"))
        val txt = resp.body?.string().orEmpty()
        val obj = runCatching { JSONObject(txt) }.getOrNull()
        val code = obj?.optInt("code", -1) ?: -1
        val msg = obj?.optString("message").orEmpty()
        Timber.w("sendCaptcha phone=$phone -> code=$code msg=$msg")
        code == 200
      }
    }.getOrDefault(false)
  }

  /** 手机号 + 密码登录（密码 md5 后传输，eapi 端点与验证码登录同一个） */
  fun loginByPassword(phone: String, password: String) {
    val md5 = MessageDigest.getInstance("MD5")
      .digest(password.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
    val params = JSONObject().apply {
      put("type", "1")
      put("phone", phone)
      put("password", md5)
      put("remember", "true")
      put("https", "true")
      put("countrycode", "86")
    }
    val obj = eapiRequest("/eapi/w/login/cellphone", params)
    applyLoginSuccess(obj)
  }

  // ===================== 二维码登录 =====================

  /** 二维码登录状态：801 待扫 / 802 待确认 / 803 成功 / 800 过期 */
  data class QrState(
    val code: Int,
    val message: String = "",
    val nickname: String = "",
    val avatar: String = ""
  )

  /** 申请一个二维码 key（明文接口，实测 200） */
  fun requestQrKey(): String? {
    val req = Request.Builder()
      .url("https://music.163.com/api/login/qrcode/unikey?type=1")
      .header("referer", "https://music.163.com/")
      .header("user-agent", webUa)
      .build()
    return runCatching {
      okHttpClient.newCall(req).execute().use { resp ->
        harvestCookies(resp.headers("Set-Cookie"))
        val txt = resp.body?.string().orEmpty()
        val obj = JSONObject(txt)
        if (obj.optInt("code") == 200) obj.optString("unikey").ifBlank { null } else null
      }
    }.getOrNull()
  }

  /** 轮询扫码状态；803 时把 Set-Cookie 里的 MUSIC_U 落盘并拉一次资料 */
  fun pollQrLogin(key: String): QrState {
    val req = Request.Builder()
      .url("https://music.163.com/api/login/qrcode/client/login?key=$key&type=1")
      .header("referer", "https://music.163.com/")
      .header("user-agent", webUa)
      .header("cookie", buildCookieHeader())
      .build()
    return runCatching {
      okHttpClient.newCall(req).execute().use { resp ->
        harvestCookies(resp.headers("Set-Cookie"))
        val txt = resp.body?.string().orEmpty()
        val obj = runCatching { JSONObject(txt) }.getOrNull()
        val code = obj?.optInt("code", -1) ?: -1
        if (code == 803) {
          val mu = cookies["MUSIC_U"].orEmpty()
          if (mu.isNotEmpty()) musicU = mu
          inited = false
          expireTs = 0L
          fetchLoginProfile()
          QrState(code, "授权登录成功")
        } else {
          QrState(code, obj?.optString("message").orEmpty())
        }
      }
    }.getOrDefault(QrState(-1, "网络错误"))
  }

  /** 拉当前登录账号资料（二维码登录后补 uid/昵称/头像） */
  fun fetchLoginProfile() {
    runCatching {
      val obj = eapiRequest("/eapi/w/nuser/account/get", JSONObject())
      val profile = obj.optJSONObject("profile") ?: return@runCatching
      loginUserId = profile.optLong("userId")
      val name = profile.optString("nickname")
      val avatar = profile.optString("avatarUrl")
      if (name.isNotBlank()) loginNickname = name
      if (avatar.isNotBlank()) loginAvatar = avatar
    }.onFailure { Timber.w("fetchLoginProfile: $it") }
  }

  /** 手机号 + 验证码登录，成功后保存 MUSIC_U */
  fun loginByCaptcha(phone: String, captcha: String) {
    val params = JSONObject().apply {
      put("type", "1")
      put("phone", phone)
      put("captcha", captcha)
      put("remember", "true")
      put("https", "true")
      put("countrycode", "86")
    }
    val obj = eapiRequest("/eapi/w/login/cellphone", params)
    val token = obj.optString("token")
    if (token.isBlank()) {
      throw IllegalStateException(obj.optString("message", "登录失败"))
    }
    applyLoginSuccess(obj)
  }

  /** 登录成功的统一收尾：落盘 token/资料，并重建带登录态的 cookie/csrf */
  private fun applyLoginSuccess(obj: JSONObject) {
    val token = obj.optString("token")
    if (token.isBlank()) {
      throw IllegalStateException(obj.optString("message", "登录失败"))
    }
    musicU = token
    val account = obj.optJSONObject("account")
    loginUserId = account?.optLong("id") ?: 0L
    val profile = obj.optJSONObject("profile")
    val name = profile?.optString("nickname").orEmpty()
    val avatar = profile?.optString("avatarUrl").orEmpty()
    if (name.isNotBlank()) loginNickname = name
    if (avatar.isNotBlank()) loginAvatar = avatar
    // 登录成功后必须重新做一次匿名注册流程：
    // 1) 让 cookies 里带上登录态（MUSIC_U / __csrf）
    // 2) 拿到与登录用户配套的 csrf，否则收藏、评论会被服务端拒掉
    inited = false
    expireTs = 0L
    runCatching { initIfNeeded() }
  }

  /** 我的歌单 */
  fun getUserPlaylists(uid: Long): List<NetEasePlaylistDetail> {
    val params = JSONObject().apply {
      put("uid", uid)
      put("offset", 0)
      put("limit", 1000)
    }
    val obj = eapiRequest("/eapi/user/playlist", params)
    val arr = obj.optJSONArray("playlist") ?: return emptyList()
    val out = mutableListOf<NetEasePlaylistDetail>()
    for (i in 0 until arr.length()) {
      val it = arr.optJSONObject(i) ?: continue
      out.add(
        NetEasePlaylistDetail(
          id = it.optLong("id"),
          name = it.optString("name"),
          coverUrl = it.optString("coverImgUrl"),
          trackCount = it.optInt("trackCount"),
          tracks = emptyList(),
          specialType = it.optInt("specialType")
        )
      )
    }
    return out
  }

  /** 歌词全文，返回 (原文, 翻译) */
  fun getLyricText(id: Long): Pair<String?, String?> {
    val params = JSONObject().apply {
      put("id", id)
      put("lv", "-1")
      put("tv", "-1")
      put("rv", "-1")
      put("yv", "-1")
    }
    return try {
      val obj = eapiRequest("/eapi/song/lyric/v1", params)
      val yrc = obj.optJSONObject("yrc")?.optString("lyric")
      val tlyricPlain = obj.optJSONObject("tlyric")?.optString("lyric")
      if (!yrc.isNullOrBlank()) {
        // yrc / ytlrc 都是逐字格式，必须统一转成增强型 LRC，
        // 否则翻译行没有 [mm:ss] 前缀，LrcParser 会整段丢掉。
        val ytlrc = obj.optJSONObject("ytlrc")?.optString("lyric")
        val translation = when {
          !ytlrc.isNullOrBlank() -> yrcToEnhancedLrc(ytlrc)
          !tlyricPlain.isNullOrBlank() -> tlyricPlain
          else -> null
        }
        Pair(yrcToEnhancedLrc(yrc), translation)
      } else {
        Pair(
          obj.optJSONObject("lrc")?.optString("lyric"),
          tlyricPlain
        )
      }
    } catch (e: Exception) {
      Timber.w("getLyricText: $e")
      Pair(null, null)
    }
  }

  /** 歌曲详情（补全 album 封面等），返回 song 的 json */
  fun getSongDetail(id: Long): JSONObject? {
    val params = JSONObject().apply {
      put("c", "[{\"id\":$id}]")
    }
    return try {
      val obj = eapiRequest("/eapi/v3/song/detail", params)
      obj.optJSONArray("songs")?.optJSONObject(0)
    } catch (e: Exception) {
      Timber.w("getSongDetail: $e")
      null
    }
  }


  /** 收藏 / 取消收藏（需要登录） */
  fun likeSong(id: Long, like: Boolean): Boolean {
    val params = JSONObject().apply {
      put("trackId", id)
      put("like", if (like) "true" else "false")
      // eapi 的写操作要求 csrf_token 与 cookie 里的 __csrf 一致
      cookies["__csrf"]?.let { put("csrf_token", it) }
    }
    return try {
      val obj = eapiRequest("/eapi/song/like", params)
      obj.optInt("code") == 200
    } catch (e: Exception) {
      Timber.w("likeSong: $e")
      false
    }
  }

  /** 已收藏的歌曲 id 列表（最多 1000 首） */
  /**
   * 「我喜欢的音乐」歌曲 id 集合。
   *
   * 之前只认 `ids` 数组，但这个接口实际返回的是 `songs: [{id, ...}]`（有时是 `data.ids`），
   * 字段对不上就恒为空 → 所有歌的红心都不亮、也无法取消收藏。
   * 这里把几种常见结构都兼容掉。
   */
  /**
   * 「我喜欢的音乐」歌单 id。
   *
   * 网易云没有独立"我喜欢"列表接口，收藏就是在 `specialType == 5` 的那张歌单里。
   * 之前误用 `/eapi/song/like/list`（该端点并不返回歌曲），所以 likedIds 恒空：
   * 表现为「歌单里能看、但红心不亮、也初始化不出收藏列表」。
   */
  fun getLikedPlaylistId(uid: Long): Long? {
    return runCatching {
      getUserPlaylists(uid).firstOrNull { it.specialType == 5 }?.id
    }.getOrNull()
  }

  fun getLikedSongIds(uid: Long): Set<Long> {
    val playlistId = getLikedPlaylistId(uid) ?: run {
      Timber.w("getLikedSongIds: 没有 specialType=5 的歌单, uid=$uid")
      return emptySet()
    }
    return runCatching {
      getPlaylistDetail(playlistId)?.tracks?.map { it.id }?.toSet().orEmpty()
    }.getOrElse {
      Timber.w("getLikedSongIds: $it")
      emptySet()
    }
  }

  /** 歌曲评论（歌单/专辑同为 R_SO_4_ 前缀，type 0=歌曲） */
  /**
   * 单曲总收藏量（红心数）。
   * GET /api/song/red/count?songId=xxx → {"code":200,"data":{"count":44640,"countDesc":"1w+"}}
   * 免登录，走 music.163.com 的公开接口（不是 interface.music.163.com 的 eapi）。
   * 返回 Pair(精确值, 服务端格式化文案)，失败返回 null。
   */
  fun getSongFavoriteCount(songId: Long): Pair<Long, String>? = try {
    val url = "https://music.163.com/api/song/red/count?songId=$songId"
    val req = Request.Builder()
      .url(url)
      .get()
      .header("accept", "application/json")
      .header(
        "user-agent",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.164 Safari/537.36"
      )
      .header("Referer", "https://music.163.com/")
      .build()
    okHttpClient.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) return@use null
      val text = resp.body?.string() ?: return@use null
      val obj = JSONObject(text)
      if (obj.optInt("code") != 200) return@use null
      val data = obj.optJSONObject("data") ?: return@use null
      Pair(
        data.optLong("count"),
        data.optString("countDesc").ifBlank { formatCount(data.optLong("count")) }
      )
    }
  } catch (e: Exception) {
    Timber.w("getSongFavoriteCount: $e")
    null
  }

  /** 服务端没给 countDesc 时自己格式化：>=1w 显示 Xw+ */
  private fun formatCount(count: Long): String {
    return when {
      count >= 100_000_000L -> "${count / 100_000_000L}亿+"
      count >= 10_000L -> "${count / 10_000L}w+"
      else -> count.toString()
    }
  }

  /** 监听歌手 */
  suspend fun getComments(songId: Long, limit: Int = 30, offset: Int = 0): Pair<List<NetEaseComment>, Int> {
    val params = JSONObject().apply {
      put("limit", limit)
      put("offset", offset)
    }
    return try {
      val obj = eapiRequest("/eapi/v1/resource/comments/R_SO_4_$songId", params)
      val total = obj.optInt("total")
      val list = mutableListOf<NetEaseComment>()
      // 只有 offset == 0 时服务端才返回 hotComments，这部分不计入分页 offset
      if (offset == 0) {
        obj.optJSONArray("hotComments")?.let { arr ->
          for (i in 0 until arr.length()) {
            parseComment(arr.optJSONObject(i), hot = true)?.let { list.add(it) }
          }
        }
      }
      obj.optJSONArray("comments")?.let { arr ->
        for (i in 0 until arr.length()) {
          parseComment(arr.optJSONObject(i), hot = false)?.let { list.add(it) }
        }
      }
      Pair(list, total)
    } catch (e: Exception) {
      Timber.w("getComments: $e")
      Pair(emptyList(), 0)
    }
  }

  private fun parseComment(o: JSONObject?, hot: Boolean = false): NetEaseComment? {
    if (o == null) return null
    val user = o.optJSONObject("user")
    val replied = o.optJSONArray("beReplied")?.optJSONObject(0)
    return NetEaseComment(
      commentId = o.optLong("commentId"),
      userName = user?.optString("nickname").orEmpty(),
      avatar = user?.optString("avatarUrl").orEmpty(),
      content = o.optString("content"),
      likedCount = o.optInt("likedCount"),
      time = o.optLong("time"),
      isHot = hot,
      replyTo = replied?.optJSONObject("user")?.optString("nickname").orEmpty()
    )
  }

  /** 搜索联想词（键盘输入时的下拉候选） */
  fun searchSuggest(keyword: String): List<String> {
    if (keyword.isBlank()) return emptyList()
    val params = JSONObject().apply {
      put("keyword", keyword)
      put("type", "mobile")
    }
    return try {
      val obj = eapiRequest("/eapi/search/suggest/keyword/get", params)
      val list = mutableListOf<String>()
      val data = obj.optJSONObject("data") ?: obj
      data.optJSONArray("suggests")?.let { arr ->
        for (i in 0 until arr.length()) {
          val o = arr.optJSONObject(i) ?: continue
          val k = o.optString("keyword")
          if (k.isNotEmpty() && !list.contains(k)) list.add(k)
        }
      }
      data.optJSONArray("allMatch")?.let { arr ->
        for (i in 0 until arr.length()) {
          val o = arr.optJSONObject(i) ?: continue
          val k = o.optString("keyword")
          if (k.isNotEmpty() && !list.contains(k)) list.add(k)
        }
      }
      list.take(10)
    } catch (e: Exception) {
      Timber.w("searchSuggest: $e")
      emptyList()
    }
  }

  /** 私人雷达 / 每日推荐：/api/v3/discovery/recommend/songs */
  fun getRecommendSongs(): List<NetEaseSong> {
    val params = JSONObject().apply { }
    return try {
      val obj = eapiRequest("/eapi/v3/discovery/recommend/songs", params)
      val data = obj.optJSONObject("data") ?: return emptyList()
      val arr = data.optJSONArray("dailySongs")
        ?: data.optJSONArray("recommendReasons") ?: return emptyList()
      val out = mutableListOf<NetEaseSong>()
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val id = o.optLong("id")
        if (id <= 0) continue
        val al = o.optJSONObject("al")
        val ar = o.optJSONArray("ar")
        out.add(
          NetEaseSong(
            id = id,
            name = o.optString("name"),
            ar = buildList {
              if (ar != null) for (j in 0 until ar.length()) {
                val a = ar.optJSONObject(j) ?: continue
                add(NetEaseSong.AR(a.optLong("id"), a.optString("name")))
              }
            },
            al = al?.let {
              NetEaseSong.AL(it.optLong("id"), it.optString("name"), it.optString("picUrl"))
            },
            dt = o.optLong("dt")
          )
        )
      }
      out
    } catch (e: Exception) {
      Timber.w("getRecommendSongs: $e")
      emptyList()
    }
  }

  /** 私人雷达歌单壳信息（固定 id 3136952023），曲目走 recommend/songs */
  fun getRadarPlaylist(): NetEasePlaylistDetail {
    val shell = try {
      getPlaylistDetail(3136952023L)
    } catch (e: Exception) {
      Timber.w("getRadarPlaylist shell: $e")
      null
    }
    val songs = getRecommendSongs()
    return NetEasePlaylistDetail(
      id = 3136952023L,
      name = shell?.name ?: "私人雷达",
      coverUrl = shell?.coverUrl,
      trackCount = if (songs.isNotEmpty()) songs.size else (shell?.trackCount ?: 0),
      tracks = if (songs.isNotEmpty()) songs else shell?.tracks.orEmpty()
    )
  }

  /** 其它雷达（都是网易云的官方虚拟榜单，走歌单详情） */
  fun getNamedRadar(id: Long, title: String): NetEasePlaylistDetail? {
    if (id == 3136952023L) return getRadarPlaylist()
    return try {
      getPlaylistDetail(id)?.copy(name = title)
    } catch (e: Exception) {
      Timber.w("getNamedRadar $id: $e")
      null
    }
  }

  /** 内置雷达列表：id to 名称 */
  val radarList: List<Pair<Long, String>> = listOf(
    3136952023L to "私人雷达",
    3778678L to "热歌榜",
    19723756L to "飙升榜",
    3779629L to "新歌榜",
    2884035L to "原创榜",
    1978921795L to "电音榜",
    991319590L to "说唱榜",
    2809513713L to "欧美热歌榜",
    2250011882L to "抖音热歌榜"
  )

  /** 发表评论（需要登录）；replyTo > 0 时为回复某条评论 */
  fun postComment(songId: Long, content: String, replyTo: Long = 0L): Boolean {
    if (content.isBlank()) return false
    val params = JSONObject().apply {
      put("type", 0)
      put("id", songId)
      put("content", content)
      // 与 likeSong 同样需要 csrf_token
      cookies["__csrf"]?.let { put("csrf_token", it) }
      if (replyTo > 0L) {
        put("commentId", replyTo)
      }
    }
    val path = if (replyTo > 0L) {
      "/eapi/v1/resource/comments/reply"
    } else {
      "/eapi/v1/resource/comments/add"
    }
    return try {
      val obj = eapiRequest(path, params)
      obj.optInt("code") == 200
    } catch (e: Exception) {
      Timber.w("postComment: $e")
      false
    }
  }


  /** 我的收藏（我喜欢的音乐）歌曲 id → 歌曲详情 */
  fun getLikedSongs(uid: Long): List<NetEaseSong> {
    // 「我喜欢的音乐」就是 specialType=5 的歌单，直接拉详情最完整
    val playlistId = getLikedPlaylistId(uid)
    if (playlistId != null) {
      runCatching { getPlaylistDetail(playlistId)?.tracks }
        .getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    val ids = getLikedSongIds(uid).toList()
    if (ids.isEmpty()) return emptyList()
    val out = mutableListOf<NetEaseSong>()
    ids.chunked(200).forEach { chunk ->
      try {
        val params = JSONObject().apply {
          put("c", chunk.joinToString(",", "[", "]") { "{\"id\":$it}" })
        }
        val obj = eapiRequest("/eapi/v3/song/detail", params)
        val arr = obj.optJSONArray("songs") ?: return@forEach
        for (i in 0 until arr.length()) {
          val o = arr.optJSONObject(i) ?: continue
          val id = o.optLong("id")
          if (id <= 0) continue
          val al = o.optJSONObject("al")
          val ar = o.optJSONArray("ar")
          out.add(
            NetEaseSong(
              id = id,
              name = o.optString("name"),
              ar = buildList {
                if (ar != null) for (j in 0 until ar.length()) {
                  val a = ar.optJSONObject(j) ?: continue
                  add(NetEaseSong.AR(a.optLong("id"), a.optString("name")))
                }
              },
              al = al?.let {
                NetEaseSong.AL(it.optLong("id"), it.optString("name"), it.optString("picUrl"))
              },
              dt = o.optLong("dt")
            )
          )
        }
      } catch (e: Exception) {
        Timber.w("getLikedSongs chunk: $e")
      }
    }
    return out
  }
}
