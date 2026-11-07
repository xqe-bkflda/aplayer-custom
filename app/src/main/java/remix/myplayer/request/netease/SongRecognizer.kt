package remix.myplayer.request.netease

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * 听歌识曲。
 *
 * 指纹算法是网易云客户端里那个 wasm（afp.wasm），裸 JNI 跑不起来
 * （27 个 embind import 没有等价实现），所以用 WebView 跑它的浏览器版，
 * 把录音 PCM 通过 evaluateJavascript 灌进去，拿回指纹 base64，
 * 再按实测文档 POST /api/music/audio/match 换歌曲 id。
 *
 * 采样必须 48kHz；指纹要求 12 秒，喂 8 秒会返回 noMatchReason=10。
 */
object SongRecognizer {

  private const val TAG = "SongRecognizer"

  const val SAMPLE_RATE = 48000
  const val SECONDS = 12
  const val OUT_RATE = 8000
  private const val DECIMATE = SAMPLE_RATE / OUT_RATE

  private const val MATCH_URL = "https://interface.music.163.com/api/music/audio/match"
  // 官方扩展用的固定 sessionId，随机 session 会被判成 noMatchReason=10
  private const val SESSION_ID = "441df692-afea-4a54-8aff-f5f20fd34f12"
  private const val ORIGIN = "chrome-extension://pgphbbekcgpfaekhcbjamjjkegcclhhd"
  private const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

  private val client by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .build()
  }

  // ---------------------------------------------------------------- 录音

  /**
   * 录 [seconds] 秒 48kHz 单声道，返回降到 8kHz 的 Float32 PCM（指纹只需要 8k）。
   * 返回 null 表示麦克风不可用（没权限 / 被占用）。
   */
  suspend fun record(
    seconds: Int = SECONDS,
    onProgress: (Float) -> Unit = {}
  ): FloatArray? = withContext(Dispatchers.IO) {
    val total = SAMPLE_RATE * seconds
    val minBuf = AudioRecord.getMinBufferSize(
      SAMPLE_RATE,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT
    )
    if (minBuf <= 0) return@withContext null
    val bufferSize = max(minBuf, SAMPLE_RATE / 4 * 2)

    var record: AudioRecord? = null
    try {
      record = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
      )
      if (record.state != AudioRecord.STATE_INITIALIZED) return@withContext null
      record.startRecording()

      val samples = ShortArray(total)
      val block = ShortArray(bufferSize / 2)
      var read = 0
      while (read < total) {
        val n = record.read(block, 0, min(block.size, total - read))
        if (n <= 0) break
        System.arraycopy(block, 0, samples, read, n)
        read += n
        onProgress(read.toFloat() / total)
      }
      if (read < total / 2) return@withContext null

      // 6:1 抽取到 8kHz，直接给指纹用
      val outLen = min(read / DECIMATE, OUT_RATE * seconds)
      FloatArray(outLen) { samples[it * DECIMATE] / 32768f }
    } catch (t: Throwable) {
      Log.w(TAG, "record failed", t)
      null
    } finally {
      try {
        record?.stop()
      } catch (_: Throwable) {
      }
      try {
        record?.release()
      } catch (_: Throwable) {
      }
    }
  }

  // ---------------------------------------------------------------- 指纹

  /**
   * 每个界面持有一个实例（WebView 只能在主线程创建，且必须被 attach 才会跑 JS）。
   * 用完之后调 [release]。
   */
  class Engine(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var ready = false

    @Volatile
    private var pending: ((Pair<Boolean, String>) -> Unit)? = null

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    fun createView(): WebView {
      val view = WebView(appContext)
      webView = view
      view.settings.apply {
        javaScriptEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        useWideViewPort = false
        loadWithOverviewMode = false
      }
      view.setBackgroundColor(0x00000000)
      view.addJavascriptInterface(Bridge(), "AndroidBridge")
      view.loadUrl("file:///android_asset/recognizer.html")
      return view
    }

    inner class Bridge {
      @JavascriptInterface
      fun onResult(kind: String, payload: String) {
        when (kind) {
          "ready" -> ready = true
          "ok", "error" -> {
            val cb = pending
            pending = null
            cb?.invoke((kind == "ok") to payload)
          }
        }
      }
    }

    suspend fun fingerprint(pcm8k: FloatArray): String? {
      if (webView == null) return null
      // 等引擎把 wasm 编译完（首次大约 300~800ms）
      val started = withTimeoutOrNull(20_000) {
        while (!ready) {
          kotlinx.coroutines.delay(100)
        }
        true
      }
      if (started != true) {
        Log.w(TAG, "engine not ready")
        return null
      }

      val bytes = ByteBuffer.allocate(pcm8k.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply { pcm8k.forEach { putFloat(it) } }
        .array()
      val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

      val result = withTimeoutOrNull(30_000) {
        suspendCancellableCoroutine<Pair<Boolean, String>> { cont ->
          pending = { r -> if (cont.isActive) cont.resume(r) }
          main.post {
            val js = "window.__encode('$b64');"
            webView?.evaluateJavascript(js) { }
          }
          cont.invokeOnCancellation { pending = null }
        }
      }
      return if (result?.first == true) result.second else {
        Log.w(TAG, "fingerprint failed: ${result?.second}")
        null
      }
    }

    fun release() {
      main.post {
        pending = null
        webView?.let {
          it.removeJavascriptInterface("AndroidBridge")
          it.loadUrl("about:blank")
          it.destroy()
        }
        webView = null
      }
    }
  }

  // ---------------------------------------------------------------- 匹配

  /** 指纹 → 歌曲。返回 null 表示没识别出来。 */
  suspend fun match(fingerprint: String): List<NetEaseSong> = withContext(Dispatchers.IO) {
    try {
      val form = buildString {
        append("sessionId=").append(SESSION_ID)
        append("&algorithmCode=shazam_v2")
        append("&duration=").append(SECONDS)
        append("&rawdata=").append(java.net.URLEncoder.encode(fingerprint, "UTF-8"))
        append("&times=2")
        append("&decrypt=1")
      }
      val request = Request.Builder()
        .url(MATCH_URL)
        .post(form.toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType()))
        .header("origin", ORIGIN)
        .header("user-agent", UA)
        .header("accept", "application/json")
        .build()
      client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          Log.w(TAG, "match http ${response.code}")
          return@withContext emptyList()
        }
        val data = JSONObject(body).optJSONObject("data") ?: return@withContext emptyList()
        val results = data.optJSONArray("result") ?: return@withContext emptyList()
        val out = mutableListOf<NetEaseSong>()
        var i = 0
        while (i < results.length() && out.size < 3) {
          val song = results.optJSONObject(i)?.optJSONObject("song") ?: run { i++; continue }
          i++
          val id = song.optLong("id", 0L)
          if (id <= 0L) continue
          val artists = song.optJSONArray("artists")?.let { arr ->
            (0 until arr.length()).mapNotNull { j ->
              val o = arr.optJSONObject(j) ?: return@mapNotNull null
              NetEaseSong.AR(o.optLong("id", 0L), o.optString("name"))
            }
          }
          val album = song.optJSONObject("album")?.let {
            NetEaseSong.AL(
              it.optLong("id", 0L),
              it.optString("name"),
              it.optString("picUrl").takeIf { u -> u.isNotBlank() }
                ?.replace("http://", "https://")
            )
          }
          out.add(
            NetEaseSong(
              id = id,
              name = song.optString("name"),
              ar = artists,
              al = album,
              dt = song.optLong("duration", 0L)
            )
          )
        }
        out
      }
    } catch (t: Throwable) {
      Log.w(TAG, "match failed", t)
      emptyList()
    }
  }
}
