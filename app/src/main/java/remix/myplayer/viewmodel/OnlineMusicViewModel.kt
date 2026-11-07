package remix.myplayer.viewmodel

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import remix.myplayer.data.prefs.NetEasePrefs
import remix.myplayer.request.netease.NetEaseClient
import remix.myplayer.request.netease.NetEaseDownloader
import remix.myplayer.request.netease.NetEasePlaylistDetail
import remix.myplayer.request.netease.NetEaseSong
import remix.myplayer.ui.nav.MessageNotifier
import timber.log.Timber
import javax.inject.Inject

/** 在线音乐页的状态 */
data class OnlineMusicUiState(
  val loading: Boolean = false,
  val error: String? = null,
  val songs: List<NetEaseSong> = emptyList(),
  /** 「我的歌单」（曲库页用，只由 loadMyPlaylists 写） */
  val playlists: List<NetEasePlaylistDetail> = emptyList(),
  /** 搜索结果里的歌单（搜索页用），和「我的歌单」分开存，
   *  否则搜一次歌单就把曲库里的我的歌单顶掉了 */
  val searchPlaylists: List<NetEasePlaylistDetail> = emptyList(),
  val playlist: NetEasePlaylistDetail? = null,
  val keyword: String = "",
  val resolvingId: Long = 0L,
  val downloadingId: Long = 0L,
  /** 曲库页：0=单曲 1=歌单 */
  val tab: Int = 0,
  /** 搜索页：0=单曲 1=歌单（和曲库页的 tab 各管各的） */
  val searchTab: Int = 0,
  val loggedIn: Boolean = false,
  val nickname: String = "",
  val avatar: String = "",
  val uid: Long = 0L,
  /** 已收藏的歌曲 id */
  val likedIds: Set<Long> = emptySet(),
  /** 我喜欢的音乐（单曲 tab 用） */
  val likedSongs: List<NetEaseSong> = emptyList(),
  /** 音源：0=网易云 1=酷狗 */
  val source: Int = 0,
  /** 批量下载中 */
  val batchDownloading: Boolean = false,
  /** 批量下载进度文案，如 "3/10" */
  val batchProgress: String = "",
  /** 酷狗搜索结果 */
  val kgSongs: List<remix.myplayer.request.kugou.KuGouOnline.KgSong> = emptyList(),
  /** 酷狗榜单 */
  val kgRanks: List<Pair<Int, String>> = emptyList(),
  /** 酷狗当前榜单/歌单名 */
  val kgTitle: String = "",
  /** 评论页当前歌曲 */
  val commentSong: NetEaseSong? = null,
  val comments: List<remix.myplayer.request.netease.NetEaseComment> = emptyList(),
  val commentTotal: Int = 0,
  val commentLoading: Boolean = false,
  /** 搜索联想词 */
  val suggests: List<String> = emptyList(),
  /** 正在评论回复的目标（0 表示新评论） */
  val replyToCommentId: Long = 0L,
  val replyToName: String = "",
  /** 评论浮层是否全屏 */
  val commentFullScreen: Boolean = false,
  /** 正在解析直链的酷狗歌曲 hash（独立字段，避免和网易云 id 撞车） */
  val kgResolvingHash: String = "",
  /** 正在下载的酷狗歌曲 hash */
  val kgDownloadingHash: String = "",
  /** knownSongs 版本号：每次写进新歌自增。播放页用它触发重新反查，避免红心/评论按钮卡在 null */
  val knownVersion: Int = 0,
  /** 单曲收藏量（红心数）文案：key=songId → "1w+" */
  val favoriteCounts: Map<Long, String> = emptyMap(),
  /** 正在加载收藏数的歌曲 id 集合 */
  val loadingFavoriteCounts: Set<Long> = emptySet()
)

@HiltViewModel
class OnlineMusicViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val netEaseClient: NetEaseClient,
  private val netEasePrefs: NetEasePrefs
) : ViewModel() {

  private val _uiState = MutableStateFlow(OnlineMusicUiState())
  val uiState: StateFlow<OnlineMusicUiState> = _uiState.asStateFlow()

  private val json = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  init {
    _uiState.value = _uiState.value.copy(
      loggedIn = netEaseClient.isLoggedIn,
      nickname = netEaseClient.loginNickname,
      avatar = netEaseClient.loginAvatar,
      uid = netEaseClient.loginUserId
    )
    // 冷启动先把上次缓存的歌单/收藏铺上，避免进页面先白屏等网络
    restoreCachedLibrary()
    restoreKnownSongs()
    // 已登录的话进页面就把歌单拉回来，避免"只有登录那次能看到"
    if (netEaseClient.isLoggedIn) {
      loadMyPlaylists()
      refreshLikedIds()
    }
  }

  /** 从顶栏搜索框进入时，强制回到"搜索"状态，不要停在之前的歌单里 */
  private var enterAsSearch = false

  fun requestSearchMode() {
    enterAsSearch = true
    // 从歌单/我喜欢的音乐里点顶栏搜索框时，要把上一次的歌曲列表和歌单详情一起清掉。
    // 以前只清 searchTab，搜索页会先把「自己的收藏 / 上一个歌单的曲目」当成搜索结果渲染出来，
    // 看起来就像"搜出来的是我自己的收藏"。
    suggestJob?.cancel()
    _uiState.value = _uiState.value.copy(
      searchTab = 0,
      keyword = "",
      suggests = emptyList(),
      songs = emptyList(),
      searchPlaylists = emptyList(),
      playlist = null,
      error = null,
      loading = false,
      resolvingId = 0L
    )
  }

  fun consumeSearchMode(): Boolean {
    val v = enterAsSearch
    enterAsSearch = false
    return v
  }

  fun setTab(index: Int) {
    _uiState.value = _uiState.value.copy(tab = index)
  }

  /** 搜索结果分段切换（单曲/歌单），不影响曲库页的 tab */
  fun setSearchTab(index: Int) {
    _uiState.value = _uiState.value.copy(searchTab = index)
  }

  fun search(keyword: String) {
    if (keyword.isBlank()) return
    _uiState.value = _uiState.value.copy(
      loading = true, error = null, keyword = keyword, playlist = null, searchTab = 0
    )
    viewModelScope.launch {
      val res = withContext(Dispatchers.IO) {
        runCatching {
          val songs = netEaseClient.searchSongList(keyword)
          val playlists = netEaseClient.searchPlaylist(keyword)
          songs to playlists
        }
      }
      _uiState.value = res.fold(
        onSuccess = { (songs, playlists) ->
          // 搜歌单时用户多半要的是歌单：关键词是纯数字（歌单 ID），
          // 或者一首歌都没搜到但有歌单，就自动切到「歌单」页签。
          val onlyId = keyword.trim().toLongOrNull() != null
          val tab = if (playlists.isNotEmpty() && (onlyId || songs.isEmpty())) 1 else 0
          _uiState.value.copy(
            loading = false,
            songs = songs,
            searchPlaylists = playlists,
            searchTab = tab
          )
        },
        onFailure = {
          Timber.w("online search failed: $it")
          _uiState.value.copy(loading = false, error = it.message ?: "搜索失败")
        }
      )
    }
  }

  private var suggestJob: kotlinx.coroutines.Job? = null

  /** 输入时拉联想词 */
  fun suggest(keyword: String) {
    suggestJob?.cancel()
    if (keyword.isBlank()) {
      _uiState.value = _uiState.value.copy(suggests = emptyList())
      return
    }
    suggestJob = viewModelScope.launch {
      kotlinx.coroutines.delay(260)
      val list = withContext(Dispatchers.IO) {
        runCatching { netEaseClient.searchSuggest(keyword) }.getOrDefault(emptyList())
      }
      _uiState.value = _uiState.value.copy(suggests = list)
    }
  }

  fun clearSuggests() {
    suggestJob?.cancel()
    _uiState.value = _uiState.value.copy(suggests = emptyList())
  }

  /** 可用雷达列表 */
  val radars: List<Pair<Long, String>> get() = netEaseClient.radarList

  /** 打开指定雷达 */
  fun openRadar(id: Long = 3136952023L, title: String = "私人雷达") {
    _uiState.value = _uiState.value.copy(loading = true, error = null)
    viewModelScope.launch {
      val detail = withContext(Dispatchers.IO) {
        runCatching { netEaseClient.getNamedRadar(id, title) }.getOrNull()
      }
      _uiState.value = if (detail == null || detail.tracks.isEmpty()) {
        _uiState.value.copy(loading = false, error = "$title 暂时拉不到（可能需要登录）")
      } else {
        _uiState.value.copy(loading = false, playlist = detail, songs = detail.tracks, tab = 0)
      }
    }
  }

  /** 旧入口保留 */
  /** 歌单视图里所有歌都显示成红心（只是显示，不触发下载） */
  fun openPlaylist(id: Long) {
    _uiState.value = _uiState.value.copy(loading = true, error = null)
    viewModelScope.launch {
      val res = withContext(Dispatchers.IO) { runCatching { netEaseClient.getPlaylistDetail(id) } }
      _uiState.value = res.fold(
        onSuccess = { detail ->
          if (detail == null) {
            _uiState.value.copy(loading = false, error = "歌单为空")
          } else {
            _uiState.value.copy(
              loading = false, playlist = detail, songs = detail.tracks
            )
          }
        },
        onFailure = {
          Timber.w("open playlist failed: $it")
          _uiState.value.copy(loading = false, error = it.message ?: "加载歌单失败")
        }
      )
    }
  }

  /**
   * 从歌单详情返回搜索结果。
   * 原来这里顺手把 keyword 清空，但列表里仍有搜索结果，搜索框会变空白，
   * 看起来像"搜的词丢了"；现在只关闭详情，关键词交给 UI 的输入框保留。
   */
  fun backToList() {
    _uiState.value = _uiState.value.copy(playlist = null, suggests = emptyList())
  }

  /** 完全重置成初始搜索态（从顶栏搜索框重新进入时用） */
  fun resetToSearch() {
    suggestJob?.cancel()
    _uiState.value = _uiState.value.copy(
      playlist = null,
      keyword = "",
      suggests = emptyList(),
      songs = emptyList(),
      searchPlaylists = emptyList(),
      error = null,
      loading = false,
      searchTab = 0
    )
  }

  /** 进入歌单后清掉搜索态，避免顶部还挂着上一次的搜索词 */
  fun enterPlaylistView() {
    _uiState.value = _uiState.value.copy(keyword = "", suggests = emptyList())
  }

  /**
   * 加载「我的歌单」。
   * @param switchToPlaylistTab 旧的入口会顺手把 tab 切成「歌单」，
   *   从曲库页静默刷新时传 false，避免覆盖用户当前的「单曲」视图。
   */
  fun loadMyPlaylists(switchToPlaylistTab: Boolean = true) {
    val uid = netEaseClient.loginUserId
    if (uid <= 0L) {
      _uiState.value = _uiState.value.copy(error = "请先登录网易云账号")
      return
    }
    _uiState.value = _uiState.value.copy(
      loading = true,
      error = null,
      tab = if (switchToPlaylistTab) 1 else _uiState.value.tab
    )
    viewModelScope.launch {
      val res = withContext(Dispatchers.IO) {
        runCatching { netEaseClient.getUserPlaylists(uid) }
      }
      _uiState.value = res.fold(
        onSuccess = { list ->
          if (list.isNotEmpty()) cachePlaylists(list)
          _uiState.value.copy(loading = false, playlists = list)
        },
        onFailure = {
          // 拉取失败时保留缓存里的歌单，别把界面清空
          _uiState.value.copy(loading = false, error = it.message ?: "加载我的歌单失败")
        }
      )
    }
  }

  // ------------------------------------------------------------ 本地缓存

  private fun restoreCachedLibrary() {
    runCatching {
      if (netEasePrefs.playlistsCache.isNotBlank()) {
        val cached: List<NetEasePlaylistDetail> =
          json.decodeFromString(netEasePrefs.playlistsCache)
        if (cached.isNotEmpty()) {
          _uiState.value = _uiState.value.copy(playlists = cached)
        }
      }
      if (netEasePrefs.likedCache.isNotBlank()) {
        val cachedLiked: List<NetEaseSong> = json.decodeFromString(netEasePrefs.likedCache)
        if (cachedLiked.isNotEmpty()) {
          _uiState.value = _uiState.value.copy(
            likedSongs = cachedLiked,
            likedIds = _uiState.value.likedIds + cachedLiked.map { it.id }
          )
        }
      }
    }.onFailure { Timber.w("restoreCachedLibrary: $it") }
  }

  private fun cachePlaylists(list: List<NetEasePlaylistDetail>) {
    runCatching { netEasePrefs.playlistsCache = json.encodeToString(list) }
      .onFailure { Timber.w("cachePlaylists: $it") }
  }

  private fun cacheLiked(list: List<NetEaseSong>) {
    runCatching { netEasePrefs.likedCache = json.encodeToString(list) }
      .onFailure { Timber.w("cacheLiked: $it") }
  }

  fun sendCaptcha(phone: String, onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching { netEaseClient.sendCaptcha(phone) }.getOrDefault(false)
      }
      onResult(ok)
    }
  }

  fun login(phone: String, captcha: String, onResult: (Boolean) -> Unit) {
    if (_uiState.value.loading) return
    _uiState.value = _uiState.value.copy(loading = true, error = null)
    viewModelScope.launch {
      val res = withContext(Dispatchers.IO) {
        runCatching { netEaseClient.loginByCaptcha(phone, captcha) }
      }
      res.fold(
        onSuccess = {
          _uiState.value = _uiState.value.copy(
            loading = false,
            loggedIn = true,
            nickname = netEaseClient.loginNickname,
            avatar = netEaseClient.loginAvatar,
            uid = netEaseClient.loginUserId,
            error = null
          )
          MessageNotifier.show("登录成功")
          // 登录态落盘需要一点时间，等 200ms 再拉歌单/收藏，避免第一次登录后
          // 服务端 cookie 还没就绪就并发请求导致空数据或异常（表现为"第一次进看不到单曲/闪退"）
          viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            runCatching { loadMyPlaylists() }
            runCatching { refreshLikedIds() }
          }
          onResult(true)
        },
        onFailure = {
          // 服务端错误信息藏在 message 里，抛出的异常已带上，直接展示给用户
          val msg = it.message.orEmpty()
            .replace("ne API error: ", "")
            .ifBlank { "登录失败，请检查手机号或验证码" }
          _uiState.value = _uiState.value.copy(loading = false, error = msg)
          MessageNotifier.show(msg)
          onResult(false)
        }
      )
    }
  }

  /** 手机号 + 密码登录 */
  fun loginByPassword(phone: String, password: String, onResult: (Boolean) -> Unit) {
    if (_uiState.value.loading) return
    _uiState.value = _uiState.value.copy(loading = true, error = null)
    viewModelScope.launch {
      val res = withContext(Dispatchers.IO) {
        runCatching { netEaseClient.loginByPassword(phone, password) }
      }
      res.fold(
        onSuccess = {
          _uiState.value = _uiState.value.copy(
            loading = false,
            loggedIn = true,
            nickname = netEaseClient.loginNickname,
            avatar = netEaseClient.loginAvatar,
            uid = netEaseClient.loginUserId,
            error = null
          )
          MessageNotifier.show("登录成功")
          viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            runCatching { loadMyPlaylists() }
            runCatching { refreshLikedIds() }
          }
          onResult(true)
        },
        onFailure = {
          val msg = it.message.orEmpty()
            .replace("ne API error: ", "")
            .ifBlank { "登录失败，请检查手机号或密码" }
          _uiState.value = _uiState.value.copy(loading = false, error = msg)
          MessageNotifier.show(msg)
          onResult(false)
        }
      )
    }
  }

  /** 申请扫码登录的 key */
  suspend fun requestQrKey(): String? = withContext(Dispatchers.IO) {
    runCatching { netEaseClient.requestQrKey() }.getOrNull()
  }

  /** 轮询扫码状态，803 时刷新登录状态 */
  suspend fun pollQrLogin(key: String): NetEaseClient.QrState = withContext(Dispatchers.IO) {
    val st = runCatching { netEaseClient.pollQrLogin(key) }
      .getOrDefault(NetEaseClient.QrState(-1, "网络错误"))
    if (st.code == 803) {
      _uiState.value = _uiState.value.copy(
        loading = false,
        loggedIn = true,
        nickname = netEaseClient.loginNickname,
        avatar = netEaseClient.loginAvatar,
        uid = netEaseClient.loginUserId,
        error = null
      )
      runCatching { loadMyPlaylists() }
      runCatching { refreshLikedIds() }
    }
    st
  }

  fun logout() {
    netEaseClient.logout()
    _uiState.value = _uiState.value.copy(
      loggedIn = false, nickname = "", avatar = "", uid = 0L, playlists = emptyList()
    )
  }

  /** 解析直链，回调返回 url */
  /** 下载歌曲（带完整标签 + 封面 + 歌词） */
  fun download(song: NetEaseSong) {
    if (_uiState.value.downloadingId != 0L) return
    if (_uiState.value.batchDownloading) {
      MessageNotifier.show("批量下载进行中，稍等一下")
      return
    }
    _uiState.value = _uiState.value.copy(downloadingId = song.id)
    viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching { downloadOne(song) }.getOrDefault(false)
      }
      _uiState.value = _uiState.value.copy(downloadingId = 0L)
      MessageNotifier.show(if (ok) "已保存到 ${netEasePrefs.downloadDir}" else "下载失败")
    }
  }

  // ------------------------------------------------------------ 批量下载

  /**
   * 批量下载给定列表。
   * 多选状态由各个页面自己维护（搜索页不参与），这里只管串行下载与进度。
   */
  fun downloadSongs(songs: List<NetEaseSong>) {
    if (_uiState.value.batchDownloading) {
      MessageNotifier.show("已经有一个批量下载在跑了")
      return
    }
    val picked = songs.distinctBy { it.id }
    if (picked.isEmpty()) {
      MessageNotifier.show("先选几首歌吧")
      return
    }
    _uiState.value = _uiState.value.copy(batchDownloading = true, batchProgress = "0/${picked.size}")
    viewModelScope.launch {
      var ok = 0
      var fail = 0
      picked.forEachIndexed { i, song ->
        _uiState.value = _uiState.value.copy(batchProgress = "${i + 1}/${picked.size}")
        val success = withContext(Dispatchers.IO) {
          runCatching { downloadOne(song) }.getOrDefault(false)
        }
        if (success) ok++ else fail++
      }
      _uiState.value = _uiState.value.copy(batchDownloading = false, batchProgress = "")
      MessageNotifier.show(
        when {
          fail == 0 -> "已下载 $ok 首到 ${netEasePrefs.downloadDir}"
          ok == 0 -> "全部下载失败（可能需要会员）"
          else -> "成功 $ok 首，失败 $fail 首"
        }
      )
    }
  }

  /** 单曲下载的实际实现，download() 与批量下载共用 */
  private suspend fun downloadOne(song: NetEaseSong): Boolean {
    val url = netEaseClient.getSongUrl(song.id, netEasePrefs.downloadLevel) ?: return false
    val artist = song.ar?.joinToString(", ") { it.name ?: "" }.orEmpty()
    val album = song.al?.name.orEmpty()
    val name = buildString {
      append(song.name.orEmpty())
      if (artist.isNotBlank()) append(" - ").append(artist)
    }
    val file = NetEaseDownloader.download(context, url, name, netEasePrefs.downloadDir) ?: return false
    val lyric = if (netEasePrefs.downloadLyricWithSong) {
      netEaseClient.getLyricText(song.id).first
    } else null
    val cover = NetEaseDownloader.fetchCover(song.al?.picUrl)
    NetEaseDownloader.writeTags(
      context, file,
      title = song.name.orEmpty(),
      artist = artist,
      album = album,
      albumArtist = artist,
      track = "",
      year = "",
      lyrics = lyric,
      coverBytes = cover,
      coverMime = cover?.let { NetEaseDownloader.guessImageMime(it) } ?: "image/jpeg"
    )
    if (netEasePrefs.downloadLyricWithSong && !lyric.isNullOrBlank()) {
      NetEaseDownloader.saveLyric(file, lyric)
    }
    return true
  }

  /** 直链有有效期，缓存 5 分钟，避免同一首歌反复请求解析接口；最多 300 首 */
  private val urlCache = java.util.concurrent.ConcurrentHashMap<Long, Pair<String, Long>>()
  private val urlCacheLock = Any()

  private suspend fun resolveUrlCached(id: Long): String? {
    val now = System.currentTimeMillis()
    urlCache[id]?.let { (u, ts) -> if (now - ts < 5 * 60_000L) return u }
    val url = runCatching { netEaseClient.getSongUrl(id) }.getOrNull() ?: return null
    synchronized(urlCacheLock) {
      if (urlCache.size >= 300) {
        // 超上限就丢掉一半最老的，避免长时间播放累积成内存地鼠洞
        val toEvict = urlCache.entries.sortedBy { it.value.second }.take(urlCache.size / 2)
        toEvict.forEach { urlCache.remove(it.key) }
      }
      urlCache[id] = url to now
    }
    return url
  }

  /** 只解析点中的那首，立刻开始播放 */
  fun playNow(songs: List<NetEaseSong>, index: Int, onReady: (List<remix.myplayer.data.model.audio.Song>) -> Unit) {
    val target = songs.getOrNull(index) ?: return
    val token = ++playToken
    // 关键：在换队列之前就把上一轮的「后台补歌」任务掐掉。
    // 以前是等新歌解析完、队列都换成 [新歌] 之后才 cancel，
    // 上一轮任务如果刚好跑到插入那一步，就会把旧歌单的残歌塞进新队列，
    // 于是「下一首」跳到别的歌单的歌，表现为上下切歌错乱。
    appendJob?.cancel()
    _uiState.value = _uiState.value.copy(resolvingId = target.id)
    viewModelScope.launch {
      val remote = withContext(Dispatchers.IO) {
        val url = resolveUrlCached(target.id) ?: return@withContext null
        toRemote(target, url)
      }
      // 只有最后一次点歌才清 loading，否则连点两首会互相把对方的转圈清掉
      if (token == playToken) {
        _uiState.value = _uiState.value.copy(resolvingId = 0L)
      }
      onReady(if (remote == null) emptyList() else listOf(remote))
      // 剩余歌曲按歌单原序（当前那首之后回绕到开头）后台解析后插队尾，
      // 保证"下一首"就是歌单里紧挨着的那首。
      if (remote != null) appendRest(songs, index, token)
    }
  }

  private var playToken = 0L

  private var appendJob: kotlinx.coroutines.Job? = null

  /**
   * 后台解析列表里其余歌曲，插进队尾。
   * 顺序按歌单原序：先放当前那首之后的所有歌，再回绕放歌曲 `0..index`（跳过当前）。
   * 这样点第 N 首时，下一首是第 N+1 首，而不是第 0 首。
   *
   * [token] 是本轮点歌的序号，插入前再对一次；对不上说明用户已经点了别的歌，
   * 这时绝不能再往队列里塞歌，否则会污染新队列导致切歌顺序错。
   */
  private fun appendRest(songs: List<NetEaseSong>, playingIndex: Int, token: Long) {
    appendJob?.cancel()
    appendJob = viewModelScope.launch {
      // 先按原序排列（当前之后回绕）
      val ordered = if (songs.isEmpty()) emptyList() else {
        val n = songs.size
        List(n - 1) { k -> songs[(playingIndex + 1 + k) % n] }
      }
      val rest = withContext(Dispatchers.IO) {
        ordered.take(60).mapNotNull { song ->
          val url = resolveUrlCached(song.id) ?: return@mapNotNull null
          toRemote(song, url)
        }
      }
      // 插入前最后一道闸：这期间用户可能又点了别的歌
      if (rest.isEmpty() || token != playToken) return@launch
      runCatching { remix.myplayer.service.MusicServiceRemote.insertToQueue(rest) }
    }
  }

  /**
   * 播放页要靠 title+artist 反查回 NetEaseSong，才能收藏/评论。
   * 主索引是网易云 id（精确，绝不串歌），title+artist 只作为兜底。
   */
  private val knownSongs =
    java.util.concurrent.ConcurrentHashMap<String, NetEaseSong>()

  /** 网易云 id → NetEaseSong。播放页优先走这张表，同名歌/多版本不会再认错 */
  private val knownById =
    java.util.concurrent.ConcurrentHashMap<Long, NetEaseSong>()

  private fun songKey(title: String, artist: String) =
    "${title.trim().lowercase()}|${artist.trim().lowercase()}"

  /** 按网易云 id 精确反查 */
  fun findSongById(id: Long): NetEaseSong? = if (id <= 0L) null else knownById[id]

  fun findSong(title: String, artist: String): NetEaseSong? {
    val key = songKey(title, artist)
    knownSongs[key]?.let { return it }
    // 以前这里会「只按歌名」兜底，同名歌 / 同歌多版本时会认错，收藏直接写到别的歌上。
    // 只在歌名唯一命中时才算，否则宁可不认（宁可没有红心按钮，也不串歌）。
    val titleOnly = title.trim().lowercase()
    val matched = knownSongs.entries.filter { it.key.substringBefore('|') == titleOnly }
    return if (matched.size == 1) matched.first().value else null
  }

  private fun toRemote(song: NetEaseSong, url: String): remix.myplayer.data.model.audio.Song.Remote {
    val artist = song.ar?.joinToString(", ") { it.name ?: "" }.orEmpty()
    registerKnownSong(song, artist)
    return songToRemote(song, url)
  }

  /** 供听歌识曲等外部入口登记歌曲，播放页才认得出红心/评论 */
  fun rememberSong(song: NetEaseSong) {
    registerKnownSong(song, song.ar?.joinToString(", ") { it.name ?: "" }.orEmpty())
  }

  /** 记录反查缓存并落盘，同时 bump knownVersion 让播放页刷新按钮 */
  private fun registerKnownSong(song: NetEaseSong, artist: String) {
    val key = songKey(song.name.orEmpty(), artist)
    knownSongs[key] = song
    if (song.id > 0L) knownById[song.id] = song
    // 内存上限 400 条，超出丢一半，避免播放列表攒太多
    if (knownSongs.size > 400) {
      val evict = knownSongs.keys.take(knownSongs.size / 2)
      evict.forEach { k ->
        knownSongs.remove(k)?.let { old -> knownById.remove(old.id) }
      }
    }
    bumpKnownVersion()
  }

  private fun bumpKnownVersion() {
    _uiState.value = _uiState.value.copy(knownVersion = _uiState.value.knownVersion + 1)
    // 落盘：只在内存里取最近 N 条，避免每次写整张表
    persistKnownSongsDebounced()
  }

  private var knownPersistPending = false
  private fun persistKnownSongsDebounced() {
    if (knownPersistPending) return
    knownPersistPending = true
    viewModelScope.launch {
      kotlinx.coroutines.delay(800)
      knownPersistPending = false
      persistKnownSongsNow()
    }
  }

  private fun persistKnownSongsNow() {
    runCatching {
      val cached = knownSongs.values.toList()
      val snapshot = ArrayList(cached.takeLast(200))
      netEasePrefs.knownSongsCache = json.encodeToString<List<NetEaseSong>>(snapshot)
    }.onFailure { Timber.w("persistKnownSongs: $it") }
  }

  private fun restoreKnownSongs() {
    runCatching {
      if (netEasePrefs.knownSongsCache.isNotBlank()) {
        val cached: List<NetEaseSong> = json.decodeFromString(netEasePrefs.knownSongsCache)
        cached.forEach { song ->
          val artist = song.ar?.joinToString(", ") { it.name ?: "" }.orEmpty()
          knownSongs[songKey(song.name.orEmpty(), artist)] = song
          if (song.id > 0L) knownById[song.id] = song
        }
      }
    }.onFailure { Timber.w("restoreKnownSongs: $it") }
  }

  private fun songToRemote(song: NetEaseSong, url: String) =
    remix.myplayer.data.model.audio.Song.Remote(
      title = song.name.orEmpty(),
      album = song.al?.name.orEmpty(),
      artist = song.ar?.joinToString(", ") { it.name ?: "" }.orEmpty(),
      duration = song.dt,
      data = url,
      size = 0L,
      year = "",
      genre = "",
      track = null,
      dateModified = 0L,
      account = "",
      pwd = "",
      neteaseId = song.id
    )

  /**
   * 从「最近播放」点在线歌曲：历史里存的是当时解析的直链，网易云直链几小时就失效，
   * 直接用会播放失败。这里按歌名+歌手重新搜索一次并重新解析，拿到新直链再播。
   */
  fun replayRemote(
    song: remix.myplayer.data.model.audio.Song,
    onReady: (remix.myplayer.data.model.audio.Song?) -> Unit
  ) {
    viewModelScope.launch {
      val fresh = withContext(Dispatchers.IO) {
        runCatching {
          val keyword = listOf(song.title, song.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
          val candidates = if (keyword.isBlank()) emptyList()
          else netEaseClient.searchSongList(keyword)
          val title = song.title.trim().lowercase()
          val artist = song.artist.trim().lowercase()
          val hit = candidates.firstOrNull { c ->
            c.name.orEmpty().trim().lowercase() == title &&
              c.ar?.any { it.name.orEmpty().trim().lowercase() == artist } == true
          } ?: candidates.firstOrNull { c ->
            c.name.orEmpty().trim().lowercase() == title
          } ?: return@runCatching null
          val url = resolveUrlCached(hit.id) ?: return@runCatching null
          toRemote(hit, url)
        }.getOrNull()
      }
      onReady(fresh)
    }
  }

  // ------------------------------------------------------------ 独立详情页用

  /** 歌单全部歌曲（给 NetEasePlaylistScreen 用，不写进 uiState） */
  suspend fun playlistSongs(playlistId: Long): List<NetEaseSong> =
    withContext(Dispatchers.IO) {
      runCatching { netEaseClient.getPlaylistDetail(playlistId)?.tracks.orEmpty() }
        .getOrDefault(emptyList())
    }

  /** 雷达歌曲（会混入服务端推荐） */
  suspend fun radarSongs(id: Long, title: String): List<NetEaseSong> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (id == 3136952023L) netEaseClient.getRadarPlaylist().tracks
        else netEaseClient.getNamedRadar(id, title)?.tracks.orEmpty()
      }.getOrDefault(emptyList())
    }

  /**
   * 「我喜欢的音乐」快照（对应网易云 specialType=5 的歌单）。
   *
   * 以前返回内存里的 likedSongs，冷启动时它是空的，
   * 界面就一直显示"还没有收藏的歌"，即使服务端其实有几千首。
   * 现在：内存有就用内存，没有就现拉，拉到了写回 state 与本地缓存。
   */
  suspend fun likedSongsSnapshot(): List<NetEaseSong> {
    val cached = _uiState.value.likedSongs
    if (cached.isNotEmpty()) return cached

    val uid = netEaseClient.loginUserId
    if (uid <= 0L) {
      Timber.w("likedSongsSnapshot: uid=0，未登录")
      return emptyList()
    }
    val fresh = withContext(Dispatchers.IO) {
      runCatching { netEaseClient.getLikedSongs(uid) }
        .onFailure { Timber.w(it, "getLikedSongs failed") }
        .getOrDefault(emptyList())
    }
    if (fresh.isNotEmpty()) {
      _uiState.value = _uiState.value.copy(
        likedSongs = fresh,
        likedIds = _uiState.value.likedIds + fresh.map { it.id }
      )
      cacheLiked(fresh)
    } else {
      Timber.w("likedSongsSnapshot: 拉到的收藏为空 uid=$uid")
    }
    return fresh
  }

  /** 拿一个已有直链的远程歌曲，直接下载到本地（历史页/播放页复用） */
  fun downloadRemote(song: remix.myplayer.data.model.audio.Song) {
    if (_uiState.value.downloadingId != 0L) return
    if (song.data.isBlank()) {
      MessageNotifier.show("这首歌没有直链，无法下载")
      return
    }
    _uiState.value = _uiState.value.copy(downloadingId = song.id)
    viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching {
          val name = buildString {
            append(song.title)
            if (song.artist.isNotBlank()) append(" - ").append(song.artist)
          }
          val file = NetEaseDownloader.download(
            context, song.data, name, netEasePrefs.downloadDir
          ) ?: return@runCatching false
          // 远程歌没有封面来源，只写文字标签
          NetEaseDownloader.writeTags(
            context, file,
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumArtist = song.artist,
            track = "",
            year = "",
            lyrics = null,
            coverBytes = null
          )
          true
        }.getOrDefault(false)
      }
      _uiState.value = _uiState.value.copy(downloadingId = 0L)
      MessageNotifier.show(if (ok) "已保存到 ${netEasePrefs.downloadDir}" else "下载失败")
    }
  }

  /** 只下载歌词 */
  fun downloadLyric(song: NetEaseSong) {
    if (_uiState.value.downloadingId != 0L) return
    _uiState.value = _uiState.value.copy(downloadingId = song.id)
    viewModelScope.launch {
      var path: String? = null
      var err: String? = null
      withContext(Dispatchers.IO) {
        try {
          val lyric = netEaseClient.getLyricText(song.id).first
          if (lyric.isNullOrBlank()) {
            err = "这首歌没有歌词"
          } else {
            val artist = song.ar?.joinToString(", ") { it.name ?: "" }.orEmpty()
            val name = buildString {
              append(song.name.orEmpty())
              if (artist.isNotBlank()) append(" - ").append(artist)
            }
            path = NetEaseDownloader.saveLyricOnly(context, name, lyric, netEasePrefs.downloadDir)
            if (path == null) err = "保存失败"
          }
        } catch (e: Exception) {
          Timber.w("downloadLyric failed: $e")
          err = e.message ?: "下载歌词失败"
        }
      }
      _uiState.value = _uiState.value.copy(downloadingId = 0L)
      MessageNotifier.show(path?.let { "歌词已保存：${java.io.File(it).name}" } ?: (err ?: "下载歌词失败"))
    }
  }


  // ------------------------------------------------------------ 收藏

  /**
   * 刷新「我喜欢的音乐」。
   *
   * 以前无论拉到什么都直接覆盖 likedIds，服务端偶发返回空（或没配对结构）时，
   * 刚点亮、刚收藏成功的红心会被立刻刷回灰色，看起来就像"点了没反应"。
   * 现在：拉到的结果为空就保留现有状态，只做加法合并；只有确实拿到数据才整体替换。
   */
  fun refreshLikedIds() {
    val uid = netEaseClient.loginUserId
    if (uid <= 0L) return
    viewModelScope.launch {
      val res = withContext(Dispatchers.IO) {
        val ids = runCatching { netEaseClient.getLikedSongIds(uid) }.getOrDefault(emptySet())
        val songs = if (ids.isEmpty()) emptyList()
        else runCatching { netEaseClient.getLikedSongs(uid) }.getOrDefault(emptyList())
        ids to songs
      }
      val (ids, songs) = res
      if (ids.isEmpty()) {
        // 拿不到就不动界面，避免把乐观更新的红心刷掉
        return@launch
      }
      val current = _uiState.value
      _uiState.value = current.copy(
        // 收藏列表里出现的歌顺带补进 likedIds，防止两套数据不同步
        likedIds = ids + songs.map { it.id },
        likedSongs = songs.ifEmpty { current.likedSongs }
      )
      if (songs.isNotEmpty()) cacheLiked(songs)
    }
  }

  /**
   * 搜索键按下时刷新：歌单 + 我喜欢 + 收藏状态。
   * 这里必须传 switchToPlaylistTab = false：以前默认 true，
   * 每搜一次歌就把曲库页的 tab 强切成「歌单」，用户看到的就是"歌单一搜就被弄坏"。
   */
  fun refreshAll() {
    loadMyPlaylists(switchToPlaylistTab = false)
    refreshLikedIds()
  }

  /** 每首歌的收藏请求版本号，连点时只认最后一次 */
  private val likeTokens = java.util.concurrent.ConcurrentHashMap<Long, Long>()
  private val likeJobs = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Job>()

  /**
   * 加载单曲收藏量（红心数）并缓存到 state；同一首歌只请求一次。
   * 播到哪首就加载哪首，结果塞进 favoriteCounts，播放页红心旁显示。
   */
  private val favoriteCountJobs = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Job>()

  fun loadFavoriteCount(songId: Long) {
    if (songId <= 0L) return
    if (songId in _uiState.value.favoriteCounts) return
    if (songId in _uiState.value.loadingFavoriteCounts) return
    if (favoriteCountJobs.containsKey(songId)) return
    _uiState.value = _uiState.value.copy(
      loadingFavoriteCounts = _uiState.value.loadingFavoriteCounts + songId
    )
    favoriteCountJobs[songId] = viewModelScope.launch {
      val res = withContext(Dispatchers.IO) {
        runCatching { netEaseClient.getSongFavoriteCount(songId) }.getOrNull()
      }
      favoriteCountJobs.remove(songId)
      if (res != null) {
        // 最多记住 500 首的收藏量，超出丢一半，避免一直点歌时 Map 无限长大
        val now = _uiState.value.favoriteCounts
        // 显示真实收藏数（完整数字，不要 1w+ 这种缩写）
        val realCount = res.first.toString()
        val merged = if (now.size >= 500) {
          now.entries.drop(now.size / 2).associate { it.key to it.value } + (songId to realCount)
        } else {
          now + (songId to realCount)
        }
        _uiState.value = _uiState.value.copy(
          favoriteCounts = merged,
          loadingFavoriteCounts = _uiState.value.loadingFavoriteCounts - songId
        )
      } else {
        // 失败也记一个占位，避免反复请求
        _uiState.value = _uiState.value.copy(
          loadingFavoriteCounts = _uiState.value.loadingFavoriteCounts - songId
        )
      }
    }
  }

  fun toggleLike(song: NetEaseSong) {
    if (!netEaseClient.isLoggedIn) {
      MessageNotifier.show("请先登录网易云账号")
      return
    }
    // 同一个歌连点时，取消上一个未完成的请求，以最后一次点的结果为准
    likeJobs.remove(song.id)?.cancel()
    val token = (likeTokens[song.id] ?: 0L) + 1L
    likeTokens[song.id] = token

    val likedBefore = song.id in _uiState.value.likedIds
    val target = !likedBefore
    // 乐观更新
    _uiState.value = _uiState.value.copy(
      likedIds = if (target) _uiState.value.likedIds + song.id
      else _uiState.value.likedIds - song.id
    )
    likeJobs[song.id] = viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching { netEaseClient.likeSong(song.id, target) }.getOrDefault(false)
      }
      // 如果这期间又点了一次，这次的结果作废，不要覆盖新状态
      if (likeTokens[song.id] != token) return@launch

      if (!ok) {
        _uiState.value = _uiState.value.copy(
          likedIds = if (likedBefore) _uiState.value.likedIds + song.id
          else _uiState.value.likedIds - song.id
        )
        MessageNotifier.show(if (target) "收藏失败" else "取消收藏失败")
        return@launch
      }
      MessageNotifier.show(if (target) "已收藏" else "已取消收藏")

      // 「我喜欢的音乐」列表要跟着变；歌单里的歌属于歌单，取消收藏不该把它从歌单里删掉
      if (target) {
        if (netEasePrefs.autoDownloadOnLike) download(song)
        refreshLikedIds()
      } else {
        _uiState.value = _uiState.value.copy(
          likedSongs = _uiState.value.likedSongs.filterNot { it.id == song.id }
        )
        // 设置里开着「取消收藏时删除已下载的文件」就把本地那份也删掉
        if (netEasePrefs.deleteLocalOnUnlike) deleteDownloaded(song)
      }
    }
  }

  /**
   * 删掉这首歌已经下载到本地的那份文件（只删下载目录里、名字对得上的）。
   * 文件名规则和 downloadOne 一致：「歌名 - 歌手」。
   */
  private suspend fun deleteDownloaded(song: NetEaseSong) {
    val artist = song.ar?.joinToString(", ") { it.name ?: "" }.orEmpty()
    val name = remix.myplayer.request.netease.NetEaseDownloader.downloadBaseName(
      song.name.orEmpty(), artist
    )
    val n = withContext(Dispatchers.IO) {
      runCatching {
        remix.myplayer.request.netease.NetEaseDownloader.deleteDownloaded(
          context, name, netEasePrefs.downloadDir
        )
      }.getOrDefault(0)
    }
    if (n > 0) MessageNotifier.show("已取消收藏并删除本地文件")
  }

  // ------------------------------------------------------------ 评论

  fun openComments(song: NetEaseSong) {
    commentOffset = 0
    commentIds.clear()
    _uiState.value = _uiState.value.copy(
      commentSong = song, comments = emptyList(), commentTotal = 0, commentLoading = true
    )
    viewModelScope.launch {
      val res = withContext(Dispatchers.IO) {
        runCatching { netEaseClient.getComments(song.id) }
          .getOrDefault(emptyList<remix.myplayer.request.netease.NetEaseComment>() to 0)
      }
      // 首页 = 热门评论 + 普通评论，热门部分不能算进 offset
      val hot = res.first.count { it.isHot }
      commentOffset = (res.first.size - hot).coerceAtLeast(0)
      res.first.forEach { commentIds.add(it.commentId) }
      _uiState.value = _uiState.value.copy(
        comments = res.first, commentTotal = res.second, commentLoading = false
      )
    }
  }

  fun closeComments() {
    commentOffset = 0
    commentIds.clear()
    _uiState.value = _uiState.value.copy(
      commentSong = null,
      comments = emptyList(),
      commentTotal = 0,
      commentLoading = false,
      commentFullScreen = false,
      replyToCommentId = 0L,
      replyToName = ""
    )
  }

  fun setCommentFullScreen(full: Boolean) {
    _uiState.value = _uiState.value.copy(commentFullScreen = full)
  }

  fun setReplyTarget(commentId: Long, name: String) {
    _uiState.value = _uiState.value.copy(replyToCommentId = commentId, replyToName = name)
  }

  /** 发评论 / 回复 */
  fun postComment(content: String, onResult: (Boolean) -> Unit) {
    val song = _uiState.value.commentSong
    if (song == null) {
      onResult(false)
      return
    }
    if (!netEaseClient.isLoggedIn) {
      MessageNotifier.show("请先登录网易云账号")
      onResult(false)
      return
    }
    val replyTo = _uiState.value.replyToCommentId
    viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching { netEaseClient.postComment(song.id, content, replyTo) }.getOrDefault(false)
      }
      if (ok) {
        MessageNotifier.show(if (replyTo > 0L) "回复成功" else "评论成功")
        _uiState.value = _uiState.value.copy(
          replyToCommentId = 0L,
          replyToName = ""
        )
        // 重新拉第一页，看到自己刚发的
        openComments(song)
      } else {
        MessageNotifier.show("发送失败")
      }
      onResult(ok)
    }
  }

  /** 已经翻到第几条「普通评论」，热门评论不计入 offset */
  private var commentOffset = 0
  private val commentIds = java.util.HashSet<Long>()

  fun loadMoreComments() {
    val song = _uiState.value.commentSong ?: return
    if (_uiState.value.commentLoading) return
    _uiState.value = _uiState.value.copy(commentLoading = true)
    viewModelScope.launch {
      val offset = commentOffset
      val res = withContext(Dispatchers.IO) {
        runCatching {
          netEaseClient.getComments(song.id, 30, offset)
        }.getOrDefault(emptyList<remix.myplayer.request.netease.NetEaseComment>() to 0)
      }
      val fresh = res.first.filter { commentIds.add(it.commentId) }
      // 新一批里第一条若原本出现在 hotComments 里，说明 offset 已经被热门评论挤偏，
      // 这一页只能当作补充，不要累加 offset，避免越翻越偏。
      commentOffset += res.first.size
      _uiState.value = _uiState.value.copy(
        comments = _uiState.value.comments + fresh,
        commentTotal = if (res.second > 0) res.second else _uiState.value.commentTotal,
        commentLoading = false
      )
    }
  }

  // ------------------------------------------------------------ 酷狗

  fun setSource(index: Int) {
    _uiState.value = _uiState.value.copy(source = index)
    // 切到酷狗时顺带把榜单拉回来（如果还没拉过）
    if (index == 1 && _uiState.value.kgRanks.isEmpty()) {
      loadKuGouRanks()
    }
  }

  fun loadKuGouRanks() {
    viewModelScope.launch {
      val ranks = withContext(Dispatchers.IO) {
        runCatching { remix.myplayer.request.kugou.KuGouOnline.ranks() }.getOrDefault(emptyList())
      }
      _uiState.value = _uiState.value.copy(kgRanks = ranks)
    }
  }

  fun searchKuGou(keyword: String) {
    if (keyword.isBlank()) return
    _uiState.value = _uiState.value.copy(
      loading = true, kgTitle = "搜索：$keyword", keyword = keyword
    )
    viewModelScope.launch {
      val songs = withContext(Dispatchers.IO) {
        runCatching { remix.myplayer.request.kugou.KuGouOnline.search(keyword) }
          .getOrDefault(emptyList())
      }
      _uiState.value = _uiState.value.copy(loading = false, kgSongs = songs)
    }
  }

  fun openKuGouRank(rankId: Int, title: String) {
    _uiState.value = _uiState.value.copy(loading = true, kgTitle = title)
    viewModelScope.launch {
      val songs = withContext(Dispatchers.IO) {
        runCatching { remix.myplayer.request.kugou.KuGouOnline.rankSongs(rankId) }
          .getOrDefault(emptyList())
      }
      _uiState.value = _uiState.value.copy(loading = false, kgSongs = songs)
    }
  }

  /** 酷狗点歌：解析直链后播放 */
  /** 酷狗歌曲缓存：播放页要靠 title+singer 反查回 hash 才能下载 */
  private val knownKuGou = java.util.concurrent.ConcurrentHashMap<String, remix.myplayer.request.kugou.KuGouOnline.KgSong>()

  private fun kuGouKey(title: String, singer: String) =
    "${title.trim().lowercase()}|${singer.trim().lowercase()}"

  /** 播放页判断当前是不是酷狗歌曲 */
  fun findKuGouSong(title: String, singer: String): remix.myplayer.request.kugou.KuGouOnline.KgSong? {
    val key = kuGouKey(title, singer)
    knownKuGou[key]?.let { return it }
    val titleOnly = title.trim().lowercase()
    return knownKuGou.entries
      .firstOrNull { it.key.substringBefore('|') == titleOnly }
      ?.value
  }

  /** 下载酷狗歌曲（播放页按钮调用） */
  fun downloadKuGou(song: remix.myplayer.request.kugou.KuGouOnline.KgSong) {
    if (_uiState.value.kgDownloadingHash.isNotEmpty()) return
    _uiState.value = _uiState.value.copy(kgDownloadingHash = song.hash)
    viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching {
          val url = remix.myplayer.request.kugou.KuGouOnline.playUrl(song.hash)
            ?: return@runCatching false
          val name = buildString {
            append(song.name)
            if (song.singer.isNotBlank()) append(" - ").append(song.singer)
          }
          val file = NetEaseDownloader.download(context, url, name, netEasePrefs.downloadDir)
            ?: return@runCatching false
          NetEaseDownloader.writeTags(
            context, file,
            title = song.name,
            artist = song.singer,
            album = song.albumName,
            albumArtist = song.singer,
            track = "",
            year = "",
            lyrics = null,
            coverBytes = null
          )
          true
        }.getOrDefault(false)
      }
      _uiState.value = _uiState.value.copy(kgDownloadingHash = "")
      MessageNotifier.show(
        if (ok) "已保存到 ${netEasePrefs.downloadDir}" else "「${song.name}」没有免费直链"
      )
    }
  }

  fun playKuGou(songs: List<remix.myplayer.request.kugou.KuGouOnline.KgSong>, index: Int) {
    val target = songs.getOrNull(index) ?: return
    // 记下来，播放页才能显示下载键
    knownKuGou[kuGouKey(target.name, target.singer)] = target
    songs.take(60).forEach { knownKuGou[kuGouKey(it.name, it.singer)] = it }
    _uiState.value = _uiState.value.copy(kgResolvingHash = target.hash)
    viewModelScope.launch {
      val remote = withContext(Dispatchers.IO) {
        val url = remix.myplayer.request.kugou.KuGouOnline.playUrl(target.hash)
          ?: return@withContext null
        remix.myplayer.data.model.audio.Song.Remote(
          title = target.name,
          album = target.albumName,
          artist = target.singer,
          duration = target.durationMs.toLong(),
          data = url,
          size = 0L,
          year = "",
          genre = "",
          track = null,
          dateModified = 0L,
          account = "",
          pwd = ""
        )
      }
      _uiState.value = _uiState.value.copy(kgResolvingHash = "")
      if (remote == null) {
        MessageNotifier.show("酷狗这首歌没有免费直链（可能需要会员）")
        return@launch
      }
      runCatching {
        remix.myplayer.service.MusicServiceRemote.setPlayQueue(
          listOf(remote),
          remix.myplayer.util.MusicUtil.makeCmdIntent(remix.myplayer.service.Command.PLAY_AT)
            .putExtra(remix.myplayer.service.MusicService.EXTRA_POSITION, 0)
        )
      }.onFailure { MessageNotifier.show("播放失败：${it.message}") }
    }
  }

}

val LocalOnlineMusicViewModel = compositionLocalOf<OnlineMusicViewModel> {
  error("OnlineMusicViewModel not provided")
}


val onlineMusicViewModel: OnlineMusicViewModel
  @Composable
  @ReadOnlyComposable
  get() = LocalOnlineMusicViewModel.current
