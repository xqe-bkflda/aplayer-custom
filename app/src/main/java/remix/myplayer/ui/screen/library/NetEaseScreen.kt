package remix.myplayer.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import remix.myplayer.R
import remix.myplayer.request.netease.NetEasePlaylistDetail
import remix.myplayer.ui.nav.LocalNavController
import remix.myplayer.ui.nav.RouteOnlineMusic
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.SelectActionBar
import remix.myplayer.ui.widget.common.SelectDot
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.viewmodel.onlineMusicViewModel

/**
 * 曲库里的「网易云」标签页：显示登录账号的歌单。
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun NetEaseScreen() {
  val vm = onlineMusicViewModel
  val state by vm.uiState.collectAsStateWithLifecycle()
  val nav = LocalNavController.current
  val theme = LocalTheme.current
  val mainVM = remix.myplayer.viewmodel.mainViewModel
  val scope = androidx.compose.runtime.rememberCoroutineScope()

  // 多选（长按进入）：只在「单曲」页用，歌单详情页自己有一套
  var selectMode by remember { mutableStateOf(false) }
  var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

  Column(Modifier.fillMaxSize().background(theme.libraryBackground)) {
    // 单曲页展示的是「我喜欢的音乐」歌单内容，进来补一次
    androidx.compose.runtime.LaunchedEffect(state.loggedIn) {
      if (state.loggedIn && state.likedSongs.isEmpty()) {
        vm.refreshLikedIds()
      }
    }

    if (!state.loggedIn) {
      // 原来跳 RouteOnlineMusic（搜索在线音乐页），登录按钮当然应该去登录页
      NotLoggedIn {
        nav.navigate(remix.myplayer.ui.nav.RouteNetEaseLogin)
      }
      return@Column
    }

    // 顶部：单曲 / 歌单 切换 + 常驻刷新键
    Row(
      Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      SegTab("单曲", state.tab == 0, Modifier.weight(1f)) { vm.setTab(0) }
      Spacer(Modifier.width(10.dp))
      SegTab("歌单", state.tab == 1, Modifier.weight(1f)) { vm.setTab(1) }
    }

    // 多选操作条
    if (state.tab == 0 && selectMode) {
      SelectActionBar(
        selectedCount = selectedIds.size,
        busy = state.batchDownloading,
        progress = state.batchProgress,
        onSelectAll = { selectedIds = state.likedSongs.map { it.id }.toSet() },
        onDownload = { vm.downloadSongs(state.likedSongs.filter { it.id in selectedIds }) },
        onCancel = { selectMode = false; selectedIds = emptySet() }
      )
    }

    if (state.loading && state.songs.isEmpty() && state.playlists.isEmpty()) {
      Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
      }
    }

    LazyColumn(Modifier.fillMaxSize()) {
      if (state.tab == 0) {
        // 单曲 tab 只放「我喜欢的音乐」的全部歌曲，不放别的入口
        if (state.likedSongs.isEmpty()) {
          item {
            Column(
              Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 28.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              TextSecondary(
                if (state.loggedIn) "还没有收藏的歌，点歌曲右边的红心试试" else "登录后这里会显示你收藏的歌",
                fontSize = 13.sp
              )
              if (state.loggedIn) {
                Spacer(Modifier.height(12.dp))
                Box(
                  Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.primary)
                    .clickable { vm.refreshLikedIds() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                  TextPrimary("重新拉取收藏", fontSize = 13.sp, color = theme.primaryReverse)
                }
              }
            }
          }
        }
        itemsIndexed(state.likedSongs, key = { index, s -> "lk${s.id}"+"_" + index }) { index, song ->
          OnlineRow(
            song = song,
            resolving = state.resolvingId == song.id,
            liked = song.id in state.likedIds,
            selecting = selectMode,
            selected = song.id in selectedIds,
            onClick = {
              if (selectMode) {
                selectedIds = if (song.id in selectedIds) selectedIds - song.id
                else selectedIds + song.id
              } else {
                vm.playNow(state.likedSongs, index) { queue ->
                  if (queue.isEmpty()) return@playNow
                  remix.myplayer.service.MusicServiceRemote.setPlayQueue(
                    queue,
                    remix.myplayer.util.MusicUtil.makeCmdIntent(remix.myplayer.service.Command.PLAY_AT)
                      .putExtra(remix.myplayer.service.MusicService.EXTRA_POSITION, 0)
                  )
                  scope.launch {
                    mainVM.playingScreenState
                      .snapTo(remix.myplayer.viewmodel.PlayingScreenValue.Expanded)
                  }
                }
              }
            },
            onLongClick = {
              if (selectMode) {
                selectedIds = if (song.id in selectedIds) selectedIds - song.id
                else selectedIds + song.id
              } else {
                selectMode = true
                selectedIds = setOf(song.id)
              }
            },
            onLike = { vm.toggleLike(song) },
            onDownload = { vm.download(song) },
            onDownloadLyric = { vm.downloadLyric(song) }
          )
        }
      } else {
        // 歌单 tab 只保留「我的歌单」（雷达/榜单入口已移除）
        item {
          SectionHeader("我的歌单", if (state.playlists.size == 0) "空" else "${state.playlists.size} 个")
        }
        if (state.playlists.isEmpty()) {
          item {
            TextSecondary(
              "还没有歌单，稍后下拉刷新或检查网络",
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              fontSize = 12.sp
            )
          }
        }
        items(state.playlists, key = { "pl${it.id}" }) { pl ->
          PlaylistRow(pl) {
            nav.navigate("netease_playlist/${pl.id}/${android.net.Uri.encode(pl.name ?: "歌单")}")
          }
        }
      }

      item { Spacer(Modifier.height(80.dp)) }
    }
  }
}

@Composable
private fun SegTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val theme = LocalTheme.current
  Box(
    modifier
      .clip(RoundedCornerShape(16.dp))
      .background(if (selected) theme.primary else theme.dialogBackground)
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    TextPrimary(
      text,
      fontSize = 13.sp,
      color = if (selected) theme.primaryReverse else theme.textSecondary
    )
  }
}

@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OnlineRow(
  song: remix.myplayer.request.netease.NetEaseSong,
  resolving: Boolean,
  liked: Boolean,
  selecting: Boolean = false,
  selected: Boolean = false,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onLike: () -> Unit,
  onDownload: () -> Unit,
  onDownloadLyric: () -> Unit
) {
  val theme = LocalTheme.current
  var menu by remember { mutableStateOf(false) }
  val artist = song.ar?.joinToString(", ") { it.name ?: "" }.orEmpty()

  Box {
    Row(
      Modifier
        .fillMaxWidth()
        .background(
          if (selecting && selected) theme.primary.copy(alpha = 0.12f)
          else androidx.compose.ui.graphics.Color.Transparent
        )
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 14.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (selecting) {
        SelectDot(selected)
        Spacer(Modifier.width(10.dp))
      }
      GlideImage(
        model = song.al?.picUrl?.replace("http://", "https://"),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp))
      )
      Spacer(Modifier.width(11.dp))
      Column(Modifier.weight(1f)) {
        TextPrimary(song.name ?: "", fontSize = 14.sp, maxLine = 1)
        Spacer(Modifier.height(3.dp))
        TextSecondary(
          listOfNotNull(artist.ifBlank { null }, song.al?.name).joinToString(" · "),
          fontSize = 12.sp,
          maxLine = 1
        )
      }
      if (resolving) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
      } else {
        IconButton(onClick = onLike) {
          Icon(
            painter = painterResource(R.drawable.ic_favorite_24dp),
            contentDescription = "收藏",
            tint = if (liked) androidx.compose.ui.graphics.Color(0xFFFF4D6D) else theme.textSecondary,
            modifier = Modifier.size(19.dp)
          )
        }
      }
    }

    androidx.compose.material3.DropdownMenu(
      expanded = menu,
      onDismissRequest = { menu = false },
      containerColor = theme.dialogBackground
    ) {
      androidx.compose.material3.DropdownMenuItem(
        text = { TextPrimary("下载音乐", fontSize = 14.sp) },
        onClick = { menu = false; onDownload() }
      )
      androidx.compose.material3.DropdownMenuItem(
        text = { TextPrimary("下载歌词", fontSize = 14.sp) },
        onClick = { menu = false; onDownloadLyric() }
      )
    }
  }
}

/** 分组标题 */
@Composable
private fun SectionHeader(title: String, right: String) {
  val theme = LocalTheme.current
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TextPrimary(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
    TextSecondary(right, fontSize = 11.sp)
  }
}

/** 通用入口行：图标 + 标题 + 副标题 + 箭头 */
@Composable
private fun SectionRow(
  title: String,
  subtitle: String,
  icon: Int,
  onClick: () -> Unit
) {
  val theme = LocalTheme.current
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(theme.dialogBackground)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = null,
      tint = theme.primary,
      modifier = Modifier.size(20.dp)
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      TextPrimary(title, fontSize = 15.sp, maxLine = 1)
      Spacer(Modifier.height(2.dp))
      TextSecondary(subtitle, fontSize = 12.sp, maxLine = 1)
    }
    Icon(
      painter = painterResource(R.drawable.ic_navigate_next_white_24dp),
      contentDescription = null,
      tint = theme.textSecondary,
      modifier = Modifier.size(18.dp)
    )
  }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun PlaylistRow(pl: NetEasePlaylistDetail, onClick: () -> Unit) {
  val theme = LocalTheme.current
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    GlideImage(
      model = pl.coverUrl?.replace("http://", "https://"),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      TextPrimary(pl.name ?: "歌单", fontSize = 15.sp, maxLine = 1)
      Spacer(Modifier.height(3.dp))
      TextSecondary("${pl.trackCount} 首 · ID ${pl.id}", fontSize = 12.sp)
    }
    Icon(
      painter = painterResource(R.drawable.ic_navigate_next_white_24dp),
      contentDescription = null,
      tint = theme.textSecondary,
      modifier = Modifier.size(18.dp)
    )
  }
}

@Composable
private fun NotLoggedIn(onLogin: () -> Unit) {
  val theme = LocalTheme.current
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      TextSecondary("还没有登录网易云账号", fontSize = 14.sp)
      Spacer(Modifier.height(12.dp))
      Box(
        Modifier
          .clip(RoundedCornerShape(20.dp))
          .background(theme.primary)
          .clickable(onClick = onLogin)
          .padding(horizontal = 20.dp, vertical = 10.dp)
      ) {
        TextPrimary("去登录", fontSize = 14.sp, color = theme.primaryReverse)
      }
    }
  }
}
