package remix.myplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.request.netease.NetEaseSong
import remix.myplayer.service.Command
import remix.myplayer.service.MusicService
import remix.myplayer.service.MusicServiceRemote
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.common.SelectActionBar
import remix.myplayer.ui.widget.common.SelectDot
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.MusicUtil
import remix.myplayer.viewmodel.PlayingScreenValue
import remix.myplayer.viewmodel.mainViewModel
import remix.myplayer.viewmodel.onlineMusicViewModel

/**
 * 网易云歌单 / 雷达 / 「我喜欢的音乐」的独立详情页。
 *
 * 以前这些内容都塞在在线搜索页里（tab=1 时渲染歌单列表），
 * 于是"进歌单"看起来像"嵌在搜索里"，返回也回不到原处。
 * 现在单独一个界面：进来自动拉数据，只负责展示和播放。
 *
 * @param playlistId 歌单 / 雷达 id；为 0 时表示「我喜欢的音乐」
 * @param title 顶栏标题
 * @param isRadar true 时走雷达接口（会混入推荐歌曲）
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun NetEasePlaylistScreen(
  playlistId: Long,
  title: String,
  isRadar: Boolean = false,
  likedOnly: Boolean = false
) {
  val vm = onlineMusicViewModel
  val theme = LocalTheme.current
  val mainVM = mainViewModel
  val scope = rememberCoroutineScope()
  val state by vm.uiState.collectAsStateWithLifecycle()

  // 页面自己维护歌曲列表，不污染在线搜索页的 state
  var songs by remember(playlistId, likedOnly) { mutableStateOf(emptyList<NetEaseSong>()) }
  var loading by remember(playlistId, likedOnly) { mutableStateOf(true) }
  var error by remember(playlistId, likedOnly) { mutableStateOf<String?>(null) }
  var resolvingId by remember { mutableStateOf(0L) }
  var downloadingId by remember { mutableStateOf(0L) }
  // 多选（长按进入）：替换原来的长按菜单，支持批量下载
  var selectMode by remember { mutableStateOf(false) }
  var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

  // 进入详情页时先收起播放面板：否则正在听歌的用户点开榜单，
  // 会看到播放页盖在榜单上，像是"自动跳进了播放页"。
  LaunchedEffect(Unit) {
    runCatching { mainVM.playingScreenState.snapTo(PlayingScreenValue.Hidden) }
  }

  LaunchedEffect(playlistId, likedOnly) {
    loading = true
    error = null
    songs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      runCatching {
        when {
          likedOnly -> vm.likedSongsSnapshot()
          isRadar -> vm.radarSongs(playlistId, title)
          else -> vm.playlistSongs(playlistId)
        }
      }.getOrElse {
        error = it.message ?: "加载失败"
        emptyList()
      }
    }
    loading = false
  }

  Scaffold(
    contentWindowInsets = WindowInsets.systemBars,
    topBar = {
      CommonAppBar(title = title, actions = {
        IconButton(onClick = {
          scope.launch {
            loading = true
            songs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
              runCatching {
                when {
                  likedOnly -> vm.likedSongsSnapshot()
                  isRadar -> vm.radarSongs(playlistId, title)
                  else -> vm.playlistSongs(playlistId)
                }
              }.getOrDefault(emptyList())
            }
            loading = false
          }
        }) {
          Icon(
            painter = painterResource(R.drawable.ic_refresh_24dp),
            contentDescription = "刷新",
            tint = theme.textSecondary,
            modifier = Modifier.size(20.dp)
          )
        }
      })
    },
    containerColor = theme.mainBackground
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextSecondary("${songs.size} 首", fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (songs.isNotEmpty()) {
          Text(
            "播放全部",
            color = theme.primary,
            fontSize = 13.sp,
            modifier = Modifier.combinedClickable { playFrom(vm, mainVM, scope, songs, 0) }
          )
        }
      }

      if (selectMode) {
        SelectActionBar(
          selectedCount = selectedIds.size,
          busy = state.batchDownloading,
          progress = state.batchProgress,
          onSelectAll = { selectedIds = songs.map { it.id }.toSet() },
          onDownload = { vm.downloadSongs(songs.filter { it.id in selectedIds }) },
          onCancel = { selectMode = false; selectedIds = emptySet() }
        )
      }

      if (loading) {
        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
      }
      error?.let {
        TextSecondary(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
      }

      LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(songs, key = { index, s -> "np${s.id}"+"_" + index }) { index, song ->
          val liked = song.id in state.likedIds
          val artist = song.ar?.joinToString(", ") { it.name.orEmpty() }.orEmpty()
          val selected = song.id in selectedIds

          Row(
            Modifier
              .fillMaxWidth()
              .background(
                if (selectMode && selected) theme.primary.copy(alpha = 0.12f)
                else Color.Transparent
              )
              .combinedClickable(
                enabled = resolvingId != song.id,
                onClick = {
                  if (selectMode) {
                    selectedIds = if (selected) selectedIds - song.id else selectedIds + song.id
                  } else {
                    playFrom(vm, mainVM, scope, songs, index)
                  }
                },
                onLongClick = {
                  if (selectMode) {
                    selectedIds = if (selected) selectedIds - song.id else selectedIds + song.id
                  } else {
                    selectMode = true
                    selectedIds = setOf(song.id)
                  }
                }
              )
              .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (selectMode) {
              SelectDot(selected)
              Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.size(48.dp)) {
                GlideImage(
                  model = song.al?.picUrl?.replace("http://", "https://"),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp))
                )
                if (resolvingId == song.id || downloadingId == song.id) {
                  Box(
                    Modifier.size(48.dp).background(Color(0x99000000), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                  ) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(18.dp),
                      strokeWidth = 2.dp,
                      color = Color.White
                    )
                  }
                }
              }
              Spacer(Modifier.width(11.dp))
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                TextPrimary(song.name.orEmpty(), fontSize = 14.sp, maxLine = 1)
                Spacer(Modifier.height(3.dp))
                TextSecondary(
                  listOfNotNull(artist.ifBlank { null }, song.al?.name).joinToString(" · "),
                  fontSize = 12.sp,
                  maxLine = 1
                )
              }
              IconButton(onClick = { vm.toggleLike(song) }) {
                Icon(
                  painter = painterResource(R.drawable.ic_favorite_24dp),
                  contentDescription = if (liked) "取消收藏" else "收藏",
                  tint = if (liked) Color(0xFFFF4D6D) else theme.textSecondary,
                  modifier = Modifier.size(19.dp)
                )
              }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
      }
    }
  }
}

/** 点哪首先解析哪首并开播；其余歌曲后台插到队尾 */
private fun playFrom(
  vm: remix.myplayer.viewmodel.OnlineMusicViewModel,
  mainVM: remix.myplayer.viewmodel.MainViewModel,
  scope: kotlinx.coroutines.CoroutineScope,
  songs: List<NetEaseSong>,
  index: Int
) {
  if (songs.getOrNull(index) == null) return
  vm.playNow(songs, index) { queue ->
    if (queue.isEmpty()) {
      MessageNotifier.show("这首歌没有可用音源（可能需要会员）")
      return@playNow
    }
    MusicServiceRemote.setPlayQueue(
      queue,
      MusicUtil.makeCmdIntent(Command.PLAY_AT).putExtra(MusicService.EXTRA_POSITION, 0)
    )
    scope.launch { mainVM.playingScreenState.snapTo(PlayingScreenValue.Expanded) }
  }
}
