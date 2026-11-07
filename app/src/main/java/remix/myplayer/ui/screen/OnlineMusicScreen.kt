package remix.myplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.data.model.audio.Song
import remix.myplayer.request.netease.NetEaseSong
import remix.myplayer.service.Command
import remix.myplayer.service.MusicService
import remix.myplayer.service.MusicServiceRemote
import remix.myplayer.ui.nav.LocalNavController
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.nav.RouteNetEaseLogin
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.MusicUtil
import remix.myplayer.viewmodel.OnlineMusicUiState
import remix.myplayer.viewmodel.OnlineMusicViewModel
import remix.myplayer.viewmodel.PlayingScreenValue
import remix.myplayer.viewmodel.mainViewModel
import remix.myplayer.viewmodel.onlineMusicViewModel

/**
 * 在线搜索页：只做「搜歌曲 / 歌手 / 歌单」。
 * 榜单、雷达、我的歌单都挪到曲库的「在线音乐」「网易云」标签页了。
 * 登录在独立页面 RouteNetEaseLogin。
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun OnlineMusicScreen() {
  val vm = onlineMusicViewModel
  val state by vm.uiState.collectAsStateWithLifecycle()
  val nav = LocalNavController.current
  val theme = LocalTheme.current
  val mainVM = mainViewModel
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  var keyword by remember { mutableStateOf(state.keyword) }

  // 从顶栏搜索框进来 -> 必须是干净的搜索界面
  androidx.compose.runtime.LaunchedEffect(Unit) {
    if (vm.consumeSearchMode()) {
      vm.resetToSearch()
      keyword = ""
    }
  }

  Box(
    Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(
            theme.primary.copy(alpha = 0.10f),
            theme.mainBackground,
            theme.mainBackground
          )
        )
      )
  ) {
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
      item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }

      // 标题栏
      item {
        Row(
          Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = { nav.popBackStack() }) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back_white_24dp),
              contentDescription = "返回",
              tint = theme.textPrimary
            )
          }
          Text(
            "在线搜索",
            color = theme.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
          )
          if (!state.loggedIn) {
            Box(
              Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(theme.dialogBackground)
                .clickable { nav.navigate(RouteNetEaseLogin) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
              TextSecondary("登录", fontSize = 13.sp)
            }
          }
        }
      }

      // 歌曲源切换：网易云 / 酷狗
      item {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          SourcePill("网易云", state.source == 0, Modifier.weight(1f)) {
            vm.setSource(0)
            if (keyword.isNotBlank()) doSearch(vm, 0, keyword)
          }
          SourcePill("酷狗", state.source == 1, Modifier.weight(1f)) {
            vm.setSource(1)
            if (keyword.isNotBlank()) doSearch(vm, 1, keyword)
          }
        }
      }

      // 搜索框 + 刷新
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(theme.dialogBackground, RoundedCornerShape(24.dp))
            .padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_search_white_24dp),
            contentDescription = null,
            tint = theme.textSecondary,
            modifier = Modifier.size(19.dp)
          )
          Spacer(Modifier.width(10.dp))
          BasicTextField(
            value = keyword,
            onValueChange = {
              keyword = it
              vm.suggest(it)
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = theme.textPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
              vm.clearSuggests()
              doSearch(vm, state.source, keyword)
            }),
            decorationBox = { inner ->
              Box {
                if (keyword.isEmpty()) {
                  TextSecondary("搜歌曲 / 歌手 / 歌单", fontSize = 15.sp)
                }
                inner()
              }
            }
          )
          if (keyword.isNotEmpty()) {
            Icon(
              painter = painterResource(R.drawable.ic_close_white_24dp),
              contentDescription = "清空",
              tint = theme.textSecondary,
              modifier = Modifier
                .size(18.dp)
                .clickable {
                  keyword = ""
                  vm.clearSuggests()
                }
            )
            Spacer(Modifier.width(8.dp))
          }
          IconButton(onClick = { vm.refreshAll() }) {
            Icon(
              painter = painterResource(R.drawable.ic_refresh_24dp),
              contentDescription = "刷新",
              tint = theme.textSecondary,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      // 候选词
      if (state.suggests.isNotEmpty()) {
        item {
          Column(
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(theme.dialogBackground)
          ) {
            state.suggests.forEach { k ->
              Row(
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    keyword = k
                    vm.clearSuggests()
                    doSearch(vm, state.source, k)
                  }
                  .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  painter = painterResource(R.drawable.ic_search_white_24dp),
                  contentDescription = null,
                  tint = theme.textSecondary,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(10.dp))
                TextPrimary(k, fontSize = 14.sp, maxLine = 1)
              }
            }
          }
        }
      }

      // 加载
      if (state.loading) {
        item {
          Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
          }
        }
      }

      state.error?.let {
        item { TextSecondary(it, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) }
      }

      // 结果分段：单曲 / 歌单
      item {
        if (state.songs.isNotEmpty() || state.searchPlaylists.isNotEmpty()) {
          Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            SegButton("单曲", state.searchTab == 0, Modifier.weight(1f)) { vm.setSearchTab(0) }
            Spacer(Modifier.width(10.dp))
            SegButton("歌单", state.searchTab == 1, Modifier.weight(1f)) { vm.setSearchTab(1) }
          }
        }
      }

      // 歌单详情：openPlaylist 会把歌曲塞进 songs 并把 playlist 置上，
      // 此时必须优先渲染详情（返回键回到歌单列表），否则点了歌单看起来没反应。
      val detail = state.playlist
      if (detail != null) {
        item {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = {
              vm.backToList()
              vm.setSearchTab(1)
            }) {
              Icon(
                painter = painterResource(R.drawable.ic_arrow_back_white_24dp),
                contentDescription = "返回歌单",
                tint = theme.textPrimary
              )
            }
            Column(Modifier.weight(1f)) {
              TextPrimary(detail.name ?: "歌单", fontSize = 16.sp, maxLine = 1)
              Spacer(Modifier.height(2.dp))
              TextSecondary("${detail.tracks.size} 首", fontSize = 12.sp)
            }
          }
        }
        itemsIndexed(state.songs, key = { index, s -> "pd${s.id}"+"_" + index }) { index, song ->
          SongRow(
            song = song,
            resolving = state.resolvingId == song.id,
            downloading = state.downloadingId == song.id,
            liked = song.id in state.likedIds,
            onClick = {
              playSongs(vm, state.songs, index)
              scope.launch { mainVM.playingScreenState.snapTo(PlayingScreenValue.Expanded) }
            },
            onLike = { vm.toggleLike(song) },
            onDownload = { vm.download(song) },
            onDownloadLyric = { vm.downloadLyric(song) }
          )
        }
      } else if (state.searchTab == 1) {
        // 歌单搜索结果
        items(state.searchPlaylists, key = { "pl${it.id}" }) { pl ->
          Row(
            Modifier
              .fillMaxWidth()
              .clickable {
                vm.openPlaylist(pl.id)
                vm.enterPlaylistView()
              }
              .padding(horizontal = 16.dp, vertical = 6.dp),
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
              TextPrimary(pl.name ?: "", fontSize = 15.sp, maxLine = 1)
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
      }

      // 单曲结果（歌单详情打开时由上面的分支接管，避免同一批歌渲染两遍）
      if (state.searchTab == 0 && state.playlist == null) {
        when (state.source) {
          1 -> {
            // 酷狗结果
            if (state.kgSongs.isNotEmpty()) {
              itemsIndexed(state.kgSongs, key = { index, k -> "kg${k.hash}_" + index }) { index, kg ->
                val singer = kg.singer
                Box {
                  Row(
                    Modifier
                      .fillMaxWidth()
                      .clickable {
                        vm.playKuGou(state.kgSongs, index)
                        scope.launch { mainVM.playingScreenState.snapTo(PlayingScreenValue.Expanded) }
                      }
                      .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(Modifier.size(52.dp)) {
                      GlideImage(
                        model = kg.imgUrl.replace("http://", "https://").ifBlank { null },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                      )
                      if (state.kgResolvingHash == kg.hash) {
                        Box(
                          Modifier.size(52.dp).background(Color(0x99000000), RoundedCornerShape(10.dp)),
                          contentAlignment = Alignment.Center
                        ) {
                          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        }
                      }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                      TextPrimary(kg.name, fontSize = 15.sp, maxLine = 1)
                      Spacer(Modifier.height(3.dp))
                      TextSecondary(listOfNotNull(singer.ifBlank { null }, kg.albumName).joinToString(" · "), fontSize = 12.sp, maxLine = 1)
                    }
                    IconButton(onClick = { vm.downloadKuGou(kg) }) {
                      Icon(
                        painter = painterResource(R.drawable.ic_save_white_24dp),
                        contentDescription = "下载",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                  }
                }
              }
            }
          }
          else -> {
            // 网易云单曲
            itemsIndexed(state.songs, key = { index, s -> "s${s.id}" + "_" + index }) { index, song ->
              SongRow(
                song = song,
                resolving = state.resolvingId == song.id,
                downloading = state.downloadingId == song.id,
                liked = song.id in state.likedIds,
                onClick = {
                  playSongs(vm, state.songs, index)
                  scope.launch { mainVM.playingScreenState.snapTo(PlayingScreenValue.Expanded) }
                },
                onLike = { vm.toggleLike(song) },
                onDownload = { vm.download(song) },
                onDownloadLyric = { vm.downloadLyric(song) }
              )
            }
          }
        }
      }
    }
  }

  // 评论浮层（只读）
  state.commentSong?.let { CommentSheet(vm, it, state) }
}

/** 源切换小胶囊 */
@Composable
private fun SourcePill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val theme = LocalTheme.current
  Box(
    modifier
      .clip(RoundedCornerShape(16.dp))
      .background(if (selected) theme.primary else theme.dialogBackground)
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text,
      color = if (selected) theme.primaryReverse else theme.textSecondary,
      fontSize = 13.sp
    )
  }
}

@Composable
private fun SegButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val theme = LocalTheme.current
  Box(
    modifier
      .clip(RoundedCornerShape(18.dp))
      .background(if (selected) theme.primary else theme.dialogBackground)
      .clickable(onClick = onClick)
      .padding(vertical = 9.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text,
      color = if (selected) theme.primaryReverse else theme.textSecondary,
      fontSize = 14.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    )
  }
}

@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
  song: NetEaseSong,
  resolving: Boolean,
  downloading: Boolean,
  liked: Boolean,
  onClick: () -> Unit,
  onLike: () -> Unit,
  onDownload: () -> Unit,
  onDownloadLyric: () -> Unit
) {
  val theme = LocalTheme.current
  val artist = song.ar?.joinToString(", ") { it.name ?: "" }.orEmpty()
  var menu by remember { mutableStateOf(false) }

  Box {
    Row(
      Modifier
        .fillMaxWidth()
        .combinedClickable(
          enabled = !resolving,
          onClick = onClick,
          onLongClick = { menu = true }
        )
        .padding(horizontal = 16.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(Modifier.size(52.dp)) {
        GlideImage(
          model = song.al?.picUrl?.replace("http://", "https://"),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
        )
        if (resolving || downloading) {
          Box(
            Modifier.size(52.dp).background(Color(0x99000000), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp,
              color = Color.White
            )
          }
        }
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
        TextPrimary(song.name ?: "", fontSize = 15.sp, maxLine = 1)
        Spacer(Modifier.height(3.dp))
        TextSecondary(
          listOfNotNull(artist.ifBlank { null }, song.al?.name).joinToString(" · "),
          fontSize = 12.sp,
          maxLine = 1
        )
      }
      IconButton(onClick = onLike) {
        Icon(
          painter = painterResource(R.drawable.ic_favorite_24dp),
          contentDescription = if (liked) "取消收藏" else "收藏",
          tint = if (liked) Color(0xFFFF4D6D) else theme.textSecondary,
          modifier = Modifier.size(20.dp)
        )
      }
    }

    DropdownMenu(
      expanded = menu,
      onDismissRequest = { menu = false },
      containerColor = theme.dialogBackground
    ) {
      DropdownMenuItem(
        text = { Text("下载音乐", color = theme.textPrimary, fontSize = 14.sp) },
        onClick = { menu = false; onDownload() }
      )
      DropdownMenuItem(
        text = { Text("下载歌词", color = theme.textPrimary, fontSize = 14.sp) },
        onClick = { menu = false; onDownloadLyric() }
      )
      DropdownMenuItem(
        text = { Text(if (liked) "取消收藏" else "收藏", color = theme.textPrimary, fontSize = 14.sp) },
        onClick = { menu = false; onLike() }
      )
    }
  }
}

/**
 * 按当前歌曲源执行搜索。
 * 0=网易云（歌曲+歌单） 1=酷狗（只歌曲）
 */
private fun doSearch(vm: remix.myplayer.viewmodel.OnlineMusicViewModel, source: Int, keyword: String) {
  when (source) {
    1 -> vm.searchKuGou(keyword)
    else -> {
      vm.search(keyword)
      vm.refreshAll()
    }
  }
}

/** 点哪首先解析哪首，立刻开播；其余歌曲后台解析后插到队列尾部。 */
private fun playSongs(vm: OnlineMusicViewModel, songs: List<NetEaseSong>, startIndex: Int) {
  if (songs.isEmpty()) return
  if (songs.getOrNull(startIndex) == null) return
  vm.playNow(songs, startIndex) { queue ->
    if (queue.isEmpty()) {
      MessageNotifier.show("这首歌没有可用音源（可能需要会员）")
      return@playNow
    }
    MusicServiceRemote.setPlayQueue(
      queue,
      MusicUtil.makeCmdIntent(Command.PLAY_AT).putExtra(MusicService.EXTRA_POSITION, 0)
    )
  }
}

/** 评论浮层：只读，不提供发评论。 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun CommentSheet(vm: OnlineMusicViewModel, song: NetEaseSong, state: OnlineMusicUiState) {
  val theme = LocalTheme.current
  val full = state.commentFullScreen

  Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
    Column(
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .then(if (full) Modifier.fillMaxSize() else Modifier.height(520.dp))
        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        .background(theme.mainBackground)
    ) {
      Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          TextPrimary(song.name ?: "", fontSize = 16.sp, maxLine = 1)
          Spacer(Modifier.height(2.dp))
          TextSecondary("评论 ${state.commentTotal}", fontSize = 12.sp)
        }
        IconButton(onClick = { vm.setCommentFullScreen(!full) }) {
          Icon(
            painter = painterResource(R.drawable.ic_format_list_bulleted_white_24dp),
            contentDescription = if (full) "退出全屏" else "全屏",
            tint = theme.textSecondary
          )
        }
        IconButton(onClick = { vm.closeComments() }) {
          Icon(
            painter = painterResource(R.drawable.ic_close_white_24dp),
            contentDescription = "关闭",
            tint = theme.textSecondary
          )
        }
      }
      Spacer(Modifier.height(4.dp))

      if (state.commentLoading && state.comments.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
      }

      Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        state.comments.forEach { c ->
          Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            GlideImage(
              model = c.avatar.replace("http://", "https://"),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(32.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              TextSecondary(c.userName.ifBlank { "匿名用户" }, fontSize = 12.sp)
              Spacer(Modifier.height(3.dp))
              Text(c.content, color = theme.textPrimary, fontSize = 14.sp)
              Spacer(Modifier.height(4.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                TextSecondary(
                  buildString {
                    if (c.likedCount > 0) append("赞 ${c.likedCount}")
                    if (c.replyTo.isNotBlank()) {
                      if (isNotEmpty()) append("   ")
                      append("回复 @${c.replyTo}")
                    }
                  },
                  fontSize = 11.sp
                )
              }
            }
          }
        }
        if (!state.commentLoading) {
          Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            Row(
              Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(theme.dialogBackground)
                .clickable { vm.loadMoreComments() }
                .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
              Text("加载更多", color = theme.textPrimary, fontSize = 13.sp)
            }
          }
        }
      }

      // 已移除发评论输入框（二改版去掉发评论功能）
    }
  }
}

/**
 * 评论输入栏。
 * ViewModel 早就备好了 postComment / replyTo，但界面只有只读列表，
 * 这里补上输入框，让登录用户能实际发出评论。
 */
@Composable
private fun CommentComposer(vm: OnlineMusicViewModel, state: OnlineMusicUiState) {
  val theme = LocalTheme.current
  var text by remember { mutableStateOf("") }
  val replying = state.replyToCommentId != 0L
  val loggedIn = state.loggedIn

  // 切换回复目标时清空已输入内容，避免把回复 A 的文字发到 B 下面
  androidx.compose.runtime.LaunchedEffect(state.replyToCommentId) {
    text = ""
  }

  Column(
    Modifier
      .fillMaxWidth()
      .background(theme.dialogBackground)
      .padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    if (replying) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextSecondary("回复 @${state.replyToName}", fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(
          "取消",
          color = theme.textSecondary,
          fontSize = 11.sp,
          modifier = Modifier.clickable { vm.setReplyTarget(0L, "") }
        )
      }
      Spacer(Modifier.height(4.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      BasicTextField(
        value = text,
        onValueChange = { if (it.length <= 140) text = it },
        modifier = Modifier.weight(1f),
        singleLine = true,
        enabled = loggedIn,
        textStyle = TextStyle(fontSize = 14.sp, color = theme.textPrimary),
        decorationBox = { inner ->
          Box {
            if (text.isEmpty()) {
              TextSecondary(if (loggedIn) "说点什么…" else "登录后可发表评论", fontSize = 14.sp)
            }
            inner()
          }
        }
      )
      Spacer(Modifier.width(8.dp))
      val canSend = loggedIn && text.isNotBlank() && !state.commentLoading
      Box(
        Modifier
          .clip(RoundedCornerShape(14.dp))
          .background(if (canSend) theme.primary else theme.mainBackground)
          .clickable(enabled = canSend) {
            val body = text.trim()
            if (body.isEmpty()) return@clickable
            vm.postComment(body) { ok -> if (ok) text = "" }
          }
          .padding(horizontal = 14.dp, vertical = 8.dp)
      ) {
        Text(
          "发送",
          color = if (canSend) theme.primaryReverse else theme.textSecondary,
          fontSize = 13.sp
        )
      }
    }
  }
}
