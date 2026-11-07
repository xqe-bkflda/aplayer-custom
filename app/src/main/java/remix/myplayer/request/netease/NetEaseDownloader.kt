package remix.myplayer.request.netease

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kyant.taglib.Picture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import remix.myplayer.data.prefs.NetEasePrefs
import remix.myplayer.helper.AudioTagFile
import remix.myplayer.misc.MediaScanner
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 网易云 / 酷狗 歌曲、歌词下载器。
 *
 * 目录来自 [NetEasePrefs.downloadDir]（相对内部存储根，例如 Music/APlayer）：
 * - API 29+ 走 MediaStore.RELATIVE_PATH，不需要存储权限
 * - API 29 以下映射到 Environment 公共目录
 * - 所有分支都返回**真实绝对路径**，调用方不用再猜
 */
object NetEaseDownloader {

  /** 公共目录名 -> Environment 常量，用于 API 29 以下的路径映射 */
  private val PUBLIC_DIRS = listOf(
    "Music" to Environment.DIRECTORY_MUSIC,
    "Download" to Environment.DIRECTORY_DOWNLOADS,
    "Downloads" to Environment.DIRECTORY_DOWNLOADS,
    "Podcasts" to Environment.DIRECTORY_PODCASTS,
    "Ringtones" to Environment.DIRECTORY_RINGTONES,
    "Alarms" to Environment.DIRECTORY_ALARMS,
    "Notifications" to Environment.DIRECTORY_NOTIFICATIONS,
    "Pictures" to Environment.DIRECTORY_PICTURES,
    "Movies" to Environment.DIRECTORY_MOVIES,
    "DCIM" to Environment.DIRECTORY_DCIM,
    "Documents" to Environment.DIRECTORY_DOCUMENTS
  )

  private const val UA =
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) " +
      "Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.23.204750"

  /** 统一成 "Music/APlayer" 这样的相对路径，去掉首尾分隔符，空值回落默认 */
  fun relDir(dir: String?): String =
    dir.orEmpty().trim().trim('/')
      .replace(Regex("""/+"""), "/")
      .ifBlank { NetEasePrefs.DEFAULT_DIR }

  /** 文件名去掉非法字符，保留原扩展名 */
  private fun safeName(fileName: String): String =
    fileName.replace(Regex("""[/\\:*?"<>|]"""), "_").trim().take(120).ifBlank { "unnamed" }

  /** 相对路径 -> 设备上的真实目录（API 29 以下用） */
  private fun legacyDir(rel: String): File {
    val parts = rel.split('/').filter { it.isNotBlank() }
    val head = parts.firstOrNull()?.lowercase()
    val mapped = PUBLIC_DIRS.firstOrNull { it.first.lowercase() == head }
    return if (mapped != null) {
      var base = Environment.getExternalStoragePublicDirectory(mapped.second)
      parts.drop(1).forEach { base = File(base, it) }
      base
    } else {
      // 不是公共目录名，就当相对手机存储根处理
      var base = Environment.getExternalStorageDirectory()
      parts.forEach { base = File(base, it) }
      base
    }
  }

  /** 有同名文件时追加 (1)(2)…，避免覆盖已下载的歌 */
  private fun uniqueFile(dir: File, name: String, ext: String): File {
    var candidate = File(dir, "$name$ext")
    var i = 1
    while (candidate.exists()) {
      candidate = File(dir, "$name($i)$ext")
      i++
      if (i > 999) break
    }
    return candidate
  }

  private fun openConnection(url: String): HttpURLConnection? = try {
    // 只有网易云自己的域名值得升 https；
    // 酷狗/第三方直链的 http 带签名参数，强行改协议会让校验失败。
    val cleanUrl = if (url.startsWith("http://") && url.contains(".music.163.com")) {
      url.replaceFirst("http://", "https://")
    } else {
      url
    }
    (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15000
      readTimeout = 30000
      instanceFollowRedirects = true
      setRequestProperty("User-Agent", UA)
      setRequestProperty("Referer", "https://music.163.com/")
    }
  } catch (e: Exception) {
    Timber.w("openConnection failed: $e")
    null
  }

  /** 把 url 下载到临时文件并嗅探真实格式；返回 (临时文件, 扩展名, MIME)，失败 null */
  private fun fetchToTemp(context: Context, url: String): Triple<File, String, String>? {
    var connection: HttpURLConnection? = null
    try {
      connection = openConnection(url) ?: return null
      if (connection.responseCode !in 200..299) {
        Timber.w("download http ${connection.responseCode} for $url")
        return null
      }
      val tmp = File(context.cacheDir, "dl_${System.nanoTime()}.part")
      FileOutputStream(tmp).use { out ->
        connection.inputStream.use { input -> input.copyTo(out) }
      }
      if (tmp.length() <= 0L) {
        tmp.delete()
        return null
      }
      val head = runCatching { tmp.inputStream().use { it.readNBytes(16) } }.getOrDefault(ByteArray(0))
      val (ext, mime) = detectFormat(head)
      return Triple(tmp, ext, mime)
    } catch (e: Exception) {
      Timber.w("fetchToTemp failed: $e")
      return null
    } finally {
      connection?.disconnect()
    }
  }

  /** 按文件头判断音频容器；识别不出就按 mp3 处理 */
  fun detectFormat(head: ByteArray): Pair<String, String> = when {
    head.size >= 4 && head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() &&
      head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte() -> ".flac" to "audio/flac"
    head.size >= 3 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() &&
      head[2] == '3'.code.toByte() -> ".mp3" to "audio/mpeg"
    head.size >= 2 && (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xE0) == 0xE0 ->
      ".mp3" to "audio/mpeg"
    else -> ".mp3" to "audio/mpeg"
  }

  /** 把 url 下载到文件，返回本地 File（绝对路径）；失败返回 null */
  suspend fun download(
    context: Context,
    url: String,
    fileName: String,
    dir: String = NetEasePrefs.DEFAULT_DIR
  ): File? = withContext(Dispatchers.IO) {
    var pendingUri: android.net.Uri? = null
    var tmp: File? = null
    try {
      val rel = relDir(dir)
      val baseName = safeName(fileName).removeSuffix(".mp3").removeSuffix(".flac")
      val fetched = fetchToTemp(context, url) ?: return@withContext null
      val (part, ext, mime) = fetched
      tmp = part

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
          put(MediaStore.Audio.Media.DISPLAY_NAME, "$baseName$ext")
          put(MediaStore.Audio.Media.MIME_TYPE, mime)
          put(MediaStore.Audio.Media.RELATIVE_PATH, rel)
          put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
          ?: return@withContext null
        pendingUri = uri
        val wrote = runCatching {
          resolver.openOutputStream(uri)?.use { out ->
            part.inputStream().use { it.copyTo(out) }
          } ?: throw IllegalStateException("openOutputStream null")
          true
        }.getOrElse {
          Timber.w("download write failed: $it")
          false
        }
        if (!wrote) {
          runCatching { resolver.delete(uri, null, null) }
          pendingUri = null
          return@withContext null
        }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        pendingUri = null

        val real = queryPath(resolver, uri)
        if (real != null) return@withContext File(real)
        val guess = File(legacyDir(rel), "$baseName$ext")
        return@withContext if (guess.exists()) guess else null
      } else {
        val targetDir = legacyDir(rel)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
          Timber.w("mkdirs failed: $targetDir")
          return@withContext null
        }
        val file = uniqueFile(targetDir, baseName, ext)
        part.inputStream().use { input ->
          FileOutputStream(file).use { out -> input.copyTo(out) }
        }
        if (file.length() <= 0L) {
          file.delete()
          return@withContext null
        }
        return@withContext file
      }
    } catch (e: Exception) {
      Timber.w("download failed: $e")
      null
    } finally {
      pendingUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
      tmp?.let { runCatching { it.delete() } }
    }
  }

  /** 查 MediaStore 的 _data 列，拿不到返回 null */
  private fun queryPath(
    resolver: android.content.ContentResolver,
    uri: android.net.Uri
  ): String? = runCatching {
    resolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
    }
  }.getOrNull()

  /** 把元数据（标题/歌手/专辑/封面/歌词）写进 mp3 */
  suspend fun writeTags(
    context: Context,
    file: File,
    title: String,
    artist: String,
    album: String,
    albumArtist: String = artist,
    track: String = "",
    year: String = "",
    lyrics: String? = null,
    coverBytes: ByteArray? = null,
    coverMime: String = "image/jpeg"
  ): Boolean = withContext(Dispatchers.IO) {
    try {
      val map = HashMap<String, Array<String>>()
      fun put(key: String, value: String) {
        if (value.isNotBlank()) map[key] = arrayOf(value)
      }
      put(AudioTagFile.TITLE, title)
      put(AudioTagFile.ARTIST, artist)
      put(AudioTagFile.ALBUM, album)
      put(AudioTagFile.ALBUM_ARTIST, albumArtist)
      put(AudioTagFile.DATE, year)
      put(AudioTagFile.TRACK_NUMBER, track)
      if (!lyrics.isNullOrBlank()) put(AudioTagFile.LYRICS, lyrics)

      val saved = AudioTagFile.savePropertyMap(file, map)
      if (!saved) Timber.w("savePropertyMap returned false for ${file.name}")
      if (coverBytes != null && coverBytes.isNotEmpty()) {
        val ok = runCatching {
          AudioTagFile.savePictures(
            file,
            arrayOf(Picture(coverBytes, "Cover", "Front Cover", coverMime))
          )
        }.getOrDefault(false)
        if (!ok) Timber.w("savePictures failed for ${file.name}")
      }
      MediaScanner(context).scanSingleFile(context, file)
      true
    } catch (e: Exception) {
      Timber.w("writeTags failed: $e")
      false
    }
  }

  /** 根据图片字节猜 mime（网易云有 webp） */
  fun guessImageMime(bytes: ByteArray): String = when {
    bytes.size >= 12 &&
      bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
      bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() -> "image/webp"
    bytes.size >= 4 &&
      bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
    bytes.size >= 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() -> "image/gif"
    else -> "image/jpeg"
  }

  /** 下载封面字节 */
  suspend fun fetchCover(url: String?): ByteArray? = withContext(Dispatchers.IO) {
    if (url.isNullOrBlank()) return@withContext null
    try {
      // 只有网易云自己的域名值得升 https；
    // 酷狗/第三方直链的 http 带签名参数，强行改协议会让校验失败。
    val cleanUrl = if (url.startsWith("http://") && url.contains(".music.163.com")) {
      url.replaceFirst("http://", "https://")
    } else {
      url
    }.let {
        if (it.contains("?")) it else "$it?param=600y600"
      }
      val conn = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 15000
        setRequestProperty("User-Agent", UA)
        setRequestProperty("Referer", "https://music.163.com/")
      }
      try {
        if (conn.responseCode !in 200..299) return@withContext null
        conn.inputStream.use { input ->
          // 封面按 600x600 通常 < 200KB，超过 2MB 说明拿到的是错误页/异常内容
          val buf = ByteArray(256 * 1024)
          val out = java.io.ByteArrayOutputStream()
          var total = 0
          while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            if (total > 2 * 1024 * 1024) throw IllegalStateException("cover too large")
            out.write(buf, 0, n)
          }
          out.toByteArray()
        }
      } finally {
        conn.disconnect()
      }
    } catch (e: Exception) {
      Timber.w("fetchCover failed: $e")
      null
    }
  }

  /** 歌词写到音频同目录，返回绝对路径 */
  suspend fun saveLyric(audioFile: File, lyric: String): String? =
    withContext(Dispatchers.IO) {
      runCatching {
        val lrc = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
        lrc.writeText(lyric, Charsets.UTF_8)
        lrc.absolutePath
      }.getOrElse {
        Timber.w("saveLyric failed: $it")
        null
      }
    }

  /** 只保存歌词文件（不下载音频）；返回真实绝对路径，失败返回 null */
  suspend fun saveLyricOnly(
    context: Context,
    fileName: String,
    lyric: String,
    dir: String = NetEasePrefs.DEFAULT_DIR
  ): String? = withContext(Dispatchers.IO) {
    val rel = relDir(dir)
    val name = safeName(fileName).let { if (it.endsWith(".lrc")) it else "$it.lrc" }
    val bytes = lyric.toByteArray(Charsets.UTF_8)

    // 1) API 29+ MediaStore.Files，不需要权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val resolver = context.contentResolver
      var uri: android.net.Uri? = null
      val ok = runCatching {
        val values = ContentValues().apply {
          put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
          put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
          put(MediaStore.Files.FileColumns.RELATIVE_PATH, rel)
          put(MediaStore.Files.FileColumns.IS_PENDING, 1)
        }
        uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
          ?: return@runCatching false
        resolver.openOutputStream(uri!!)?.use { it.write(bytes) }
          ?: return@runCatching false
        values.clear()
        values.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
        resolver.update(uri!!, values, null, null)
        true
      }.getOrElse {
        Timber.w("saveLyricOnly mediaStore failed: $it")
        runCatching { uri?.let { u -> resolver.delete(u, null, null) } }
        false
      }
      if (ok) {
        val real = uri?.let { queryPath(resolver, it) }
        if (real != null) return@withContext real
        val guess = File(legacyDir(rel), name)
        if (guess.exists()) return@withContext guess.absolutePath
      }
    }

    // 2) 直接写公共目录（老系统 / 已授权）
    runCatching {
      val targetDir = legacyDir(rel)
      if (!targetDir.exists()) targetDir.mkdirs()
      val file = uniqueFile(targetDir, name.removeSuffix(".lrc"), ".lrc")
      file.writeBytes(bytes)
      if (file.length() > 0) return@withContext file.absolutePath
    }.onFailure { Timber.w("saveLyricOnly publicDir failed: $it") }

    // 3) 兜底：应用自己的外部目录
    runCatching {
      val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), rel)
      if (!fallbackDir.exists()) fallbackDir.mkdirs()
      val file = File(fallbackDir, name)
      file.writeBytes(bytes)
      return@withContext file.absolutePath
    }.onFailure { Timber.w("saveLyricOnly fallback failed: $it") }

    null
  }

  // ------------------------------------------------------------ 取消收藏自动删本地

  private val AUDIO_EXTS = listOf("mp3", "flac", "m4a", "ogg", "ape", "wav", "opus", "aac")

  /** 下载时用的文件名（不含扩展名），和 downloadOne 里的规则保持一致 */
  fun downloadBaseName(title: String, artist: String): String = buildString {
    append(title)
    if (artist.isNotBlank()) append(" - ").append(artist)
  }

  /**
   * name 是否就是 baseName 下载出来的那批文件：
   * baseName.mp3 / baseName.flac / baseName(1).mp3 …（同名重复下载时 uniqueFile 会加 (1)(2)）
   * 只认这几种，避免把 "歌名 - 歌手 (Live版).mp3" 这种误删。
   */
  private fun isSameDownloadedFile(name: String, base: String): Boolean {
    if (!name.startsWith(base)) return false
    val rest = name.removePrefix(base)
    val dot = rest.lastIndexOf('.')
    if (dot < 0) return false
    val ext = rest.substring(dot + 1).lowercase()
    if (ext !in AUDIO_EXTS) return false
    val stem = rest.substring(0, dot)
    return stem.isEmpty() || Regex("""^\(\d{1,3}\)$""").matches(stem)
  }

  /**
   * 取消收藏时删掉这首歌已经下载到本地的文件（含同名 .lrc）。
   * 只删下载目录里、名字对得上的文件，返回删除的音频文件个数。
   */
  suspend fun deleteDownloaded(
    context: Context,
    fileName: String,
    dir: String = NetEasePrefs.DEFAULT_DIR
  ): Int = withContext(Dispatchers.IO) {
    val rel = relDir(dir)
    val base = safeName(fileName).removeSuffix(".mp3").removeSuffix(".flac")
    var deleted = 0
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
          MediaStore.Audio.Media._ID,
          MediaStore.Audio.Media.DISPLAY_NAME,
          MediaStore.Audio.Media.RELATIVE_PATH
        )
        // RELATIVE_PATH 不带末尾斜杠也能匹配，用 LIKE 兼容两种写法
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("$rel%")
        val ids = mutableListOf<Long>()
        resolver.query(collection, projection, selection, args, null)?.use { c ->
          val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
          val nameIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
          val pathIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
          while (c.moveToNext()) {
            val name = c.getString(nameIdx) ?: continue
            // LIKE 是前缀匹配，这里再严格校一遍，防止删到同一目录下别的歌
            val path = c.getString(pathIdx).orEmpty().trimEnd('/')
            if (path != rel) continue
            if (isSameDownloadedFile(name, base)) ids.add(c.getLong(idIdx))
          }
        }
        ids.forEach { id ->
          val uri = android.content.ContentUris.withAppendedId(collection, id)
          val ok = runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
          if (ok) deleted++
        }
      }

      // 兜底 / 老系统：直接按文件删（MediaStore 里查不到但文件确实存在的场景）
      val targetDir = legacyDir(rel)
      targetDir.listFiles()?.forEach { f ->
        if (!f.isFile) return@forEach
        val name = f.name
        val isAudio = isSameDownloadedFile(name, base)
        val isLrc = name.removeSuffix(".lrc") != name &&
          isSameDownloadedFile(name.removeSuffix(".lrc") + ".mp3", base)
        if (isAudio || isLrc) {
          if (f.delete() && isAudio) deleted++
        }
      }
    } catch (e: Exception) {
      Timber.w("deleteDownloaded failed: $e")
    }
    deleted
  }
}
