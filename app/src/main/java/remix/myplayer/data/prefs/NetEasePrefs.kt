package remix.myplayer.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在线音乐相关的本地设置（下载、收藏联动等）。
 * 单独一份 prefs，避免动既有 SettingsState 的序列化结构。
 */
@Singleton
class NetEasePrefs @Inject constructor(
  @ApplicationContext context: Context
) : AbstractPref(context, "netease_prefs") {

  /** 下载歌曲时同时保存歌词（.lrc） */
  var downloadLyricWithSong by PrefsDelegate(sp, KEY_DOWNLOAD_LYRIC, true)

  /** 收藏后自动下载该歌曲 */
  var autoDownloadOnLike by PrefsDelegate(sp, KEY_AUTO_DOWNLOAD_ON_LIKE, false)

  /**
   * 取消收藏时自动删掉这首歌已经下载到本地的文件，默认开启。
   * 只在"本地确实有这个文件、并且是下载目录里的"情况下才会删，不会碰用户自己的歌。
   */
  var deleteLocalOnUnlike by PrefsDelegate(sp, KEY_DELETE_LOCAL_ON_UNLIKE, true)

  /** 下载音质：standard / higher / exhigh / lossless */
  var downloadLevel by PrefsDelegate(sp, KEY_DOWNLOAD_LEVEL, "standard")

  /** 下载目录，相对于公共存储根，例如 Music/APlayer */
  var downloadDirPref by PrefsDelegate(sp, KEY_DOWNLOAD_DIR, DEFAULT_DIR)

  /** 顶栏「伴随音乐入睡」按钮是否显示，默认关 */
  var showSleepTimerButton by PrefsDelegate(sp, KEY_SHOW_TIMER, false)

  /**
   * 「我的歌单」本地缓存（JSON）。
   * 歌单只存在内存里，进程被系统杀掉后重新打开必须等网络，
   * 网络慢或失败就会看到"歌单不见了"。这里落一份盘，冷启动先显示缓存再刷新。
   */
  var playlistsCache by PrefsDelegate(sp, KEY_PLAYLISTS_CACHE, "")

  /** 「我喜欢的音乐」本地缓存（JSON） */
  var likedCache by PrefsDelegate(sp, KEY_LIKED_CACHE, "")

  /**
   * 在线歌曲反查缓存（JSON）。
   * knownSongs 用来在播放页按 歌名+歌手 反查出网易云歌曲，拿到 id 才能收藏/评论。
   * 之前它只在内存里，进程被杀后重新打开（比如从系统恢复播放队列）就反查不到，
   * 播放页的红心/评论/下载按钮整排消失。这里落盘一份，冷启动先恢复再增量合并。
   */
  var knownSongsCache by PrefsDelegate(sp, KEY_KNOWN_SONGS_CACHE, "")

  /** 下载目录（动态，可在设置里改） */
  val downloadDir: String
    get() = downloadDirPref.ifBlank { DEFAULT_DIR }

  companion object {
    private const val KEY_DOWNLOAD_LYRIC = "download_lyric_with_song"
    private const val KEY_AUTO_DOWNLOAD_ON_LIKE = "auto_download_on_like"
    private const val KEY_DELETE_LOCAL_ON_UNLIKE = "delete_local_on_unlike"
    private const val KEY_DOWNLOAD_LEVEL = "download_level"
    private const val KEY_DOWNLOAD_DIR = "download_dir"
    const val KEY_SHOW_TIMER = "show_sleep_timer_button"
    const val KEY_PLAYLISTS_CACHE = "playlists_cache"
    const val KEY_LIKED_CACHE = "liked_cache"
    const val KEY_KNOWN_SONGS_CACHE = "known_songs_cache"

    const val DEFAULT_DIR = "Music/APlayer"

    val LEVELS = listOf("standard", "higher", "exhigh", "lossless")
    val LEVEL_TITLES = listOf("标准 128k", "较高 192k", "极高 320k", "无损")

    /** 下载目录预设 */
    val DIR_PRESETS = listOf("Music/APlayer", "Music", "Download", "Download/APlayer")
  }
}
