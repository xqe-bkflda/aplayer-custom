package remix.myplayer.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import remix.myplayer.R
import remix.myplayer.request.kugou.KuGouOnline
import remix.myplayer.request.netease.NetEasePlaylistDetail
import remix.myplayer.request.netease.NetEaseSong
import remix.myplayer.ui.nav.LocalNavController
import remix.myplayer.ui.nav.RouteOnlineMusic
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.viewmodel.onlineMusicViewModel

/**
 * 曲库里的「在线音乐」标签页：
 * 顶部切换 网易云 / 酷狗，下面分别是网易云的雷达+歌单、酷狗的榜单，都常驻显示。
 */
@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnlineMusicLibraryScreen() {
  val vm = onlineMusicViewModel
  val state by vm.uiState.collectAsStateWithLifecycle()
  val nav = LocalNavController.current
  val theme = LocalTheme.current

  Column(Modifier.fillMaxSize().background(theme.libraryBackground)) {
    // 进页面补一次数据：ViewModel 只在 init 里拉过一次，
    // 若当时未登录或拉取失败，这里会一直空着。
    androidx.compose.runtime.LaunchedEffect(state.loggedIn) {
      if (state.loggedIn && state.playlists.isEmpty()) {
        vm.loadMyPlaylists(switchToPlaylistTab = false)
      }
      if (state.loggedIn && state.likedSongs.isEmpty()) {
        vm.refreshLikedIds()
      }
    }
    // 音源切换
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
      SourceTab("网易云", state.source == 0, Modifier.weight(1f)) { vm.setSource(0) }
      Spacer(Modifier.width(10.dp))
      SourceTab("酷狗", state.source == 1, Modifier.weight(1f)) {
        vm.setSource(1)
        vm.loadKuGouRanks()
      }
    }

    LazyColumn(Modifier.fillMaxSize()) {
      if (state.source == 0) {
        // 网易云：雷达
        item {
          SectionHeader("雷达 / 榜单", "")
        }
        items(vm.radars, key = { "radar${it.first}" }) { (rid, rname) ->
          EntryRow(icon = R.drawable.ic_recent_24dp, title = rname,
            subtitle = if (rid == 3136952023L) "按你的口味实时推荐" else "ID $rid") {
            // 只是打开榜单详情，不要顺手把正在听的歌的播放页顶上来
            nav.navigate("netease_radar/$rid/${android.net.Uri.encode(rname)}")
          }
        }

        // 网易云：账号歌单
        item {
          SectionHeader("我的歌单", if (state.loggedIn) "${state.playlists.size} 个" else "未登录")
        }
        if (!state.loggedIn) {
          item {
            EntryRow(
              icon = R.drawable.ic_lab_profile_24dp,
              title = "点击登录网易云",
              subtitle = "登录后可看自己的歌单和收藏"
            ) {
              nav.navigate(remix.myplayer.ui.nav.RouteNetEaseLogin)
            }
          }
        }
        items(state.playlists, key = { "pl${it.id}" }) { pl ->
          PlaylistEntry(pl) {
            vm.openPlaylist(pl.id)
            vm.enterPlaylistView()
            nav.navigate(RouteOnlineMusic)
          }
        }
      } else {
        // 酷狗：榜单
        item { SectionHeader("酷狗榜单", "${state.kgRanks.size} 个") }
        if (state.kgRanks.isEmpty()) {
          item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
          }
        }
        items(state.kgRanks, key = { "kgr${it.first}" }) { (id, name) ->
          EntryRow(icon = R.drawable.ic_recent_24dp, title = name, subtitle = "ID $id") {
            nav.navigate("kgrank/$id/${android.net.Uri.encode(name)}")
          }
        }
      }
      item { Spacer(Modifier.height(80.dp)) }
    }
  }
}

@Composable
private fun SectionHeader(title: String, right: String) {
  Row(
    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TextPrimary(title, fontSize = 13.sp, modifier = Modifier.weight(1f))
    TextSecondary(right, fontSize = 11.sp)
  }
}

@Composable
private fun SourceTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
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

@Composable
private fun EntryRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
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
private fun PlaylistEntry(pl: NetEasePlaylistDetail, onClick: () -> Unit) {
  val theme = LocalTheme.current
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp)
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    GlideImage(
      model = pl.coverUrl?.replace("http://", "https://"),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp))
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      TextPrimary(pl.name ?: "歌单", fontSize = 15.sp, maxLine = 1)
      Spacer(Modifier.height(2.dp))
      TextSecondary("${pl.trackCount} 首 · ID ${pl.id}", fontSize = 12.sp)
    }
  }
}
