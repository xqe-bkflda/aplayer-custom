package remix.myplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import remix.myplayer.R
import remix.myplayer.request.kugou.KuGouOnline
import remix.myplayer.service.Command
import remix.myplayer.service.MusicService
import remix.myplayer.service.MusicServiceRemote
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.MusicUtil
import remix.myplayer.viewmodel.mainViewModel
import remix.myplayer.viewmodel.onlineMusicViewModel
import remix.myplayer.viewmodel.PlayingScreenValue
import androidx.compose.foundation.gestures.snapTo
import kotlinx.coroutines.launch

/**
 * 酷狗榜单详情页：列表 + 每行下载按钮。
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun KuGouRankScreen(rankId: Int, title: String) {
  val vm = onlineMusicViewModel
  val theme = LocalTheme.current
  val mainVM = mainViewModel
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = androidx.compose.runtime.rememberCoroutineScope()

  var songs by remember { mutableStateOf(emptyList<KuGouOnline.KgSong>()) }
  var loading by remember { mutableStateOf(true) }
  var downloadingHash by remember { mutableStateOf("") }
  // 批量下载用：串行跑，避免并发抢占同一个下载槽位
  val prefs = remember(context) { remix.myplayer.data.prefs.NetEasePrefs(context) }
  var batch by remember { mutableStateOf(false) }

  LaunchedEffect(rankId) {
    loading = true
    songs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      runCatching { KuGouOnline.rankSongs(rankId, 100) }.getOrDefault(emptyList())
    }
    loading = false
  }

  suspend fun downloadOne(song: KuGouOnline.KgSong): Boolean {
    downloadingHash = song.hash
    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      runCatching {
        val url = KuGouOnline.playUrl(song.hash) ?: return@runCatching false
        val name = buildString {
          append(song.name)
          if (song.singer.isNotBlank()) append(" - ").append(song.singer)
        }
        val file = remix.myplayer.request.netease.NetEaseDownloader.download(
          context, url, name, prefs.downloadDir
        ) ?: return@runCatching false
        remix.myplayer.request.netease.NetEaseDownloader.writeTags(
          context, file,
          title = song.name, artist = song.singer, album = song.albumName,
          albumArtist = song.singer, track = "", year = "",
          lyrics = null, coverBytes = null
        )
        true
      }.getOrDefault(false)
    }
    downloadingHash = ""
    return ok
  }

  fun download(song: KuGouOnline.KgSong) {
    if (downloadingHash.isNotEmpty() || batch) return
    scope.launch {
      val ok = downloadOne(song)
      MessageNotifier.show(if (ok) "已下载：${song.name}" else "「${song.name}」没有免费直链")
    }
  }

  fun downloadAll() {
    if (batch || songs.isEmpty()) return
    batch = true
    scope.launch {
      var success = 0
      var failed = 0
      songs.forEach { song ->
        if (downloadOne(song)) success++ else failed++
      }
      batch = false
      MessageNotifier.show("批量下载完成：成功 $success 首，失败 $failed 首")
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.systemBars,
    topBar = {
      CommonAppBar(title = title, actions = {
        IconButton(onClick = { downloadAll() }) {
          if (batch) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = theme.textSecondary
            )
          } else {
            Icon(
              painter = painterResource(R.drawable.ic_save_white_24dp),
              contentDescription = "全部下载"
            )
          }
        }
      })
    },
    containerColor = theme.mainBackground
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      if (loading) {
        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
      }
      LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(songs, key = { index, s -> "kr${s.hash}"+"_" + index }) { index, song ->
          Row(
            Modifier
              .fillMaxWidth()
              .clickable {
                vm.playKuGou(songs, index)
                scope.launch {
                  mainVM.playingScreenState.snapTo(PlayingScreenValue.Expanded)
                }
              }
              .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            TextSecondary("${index + 1}", fontSize = 12.sp, modifier = Modifier.width(26.dp))
            GlideImage(
              model = song.imgUrl.replace("{size}", "240"),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              TextPrimary(song.name, fontSize = 15.sp, maxLine = 1)
              Spacer(Modifier.height(2.dp))
              TextSecondary(song.singer, fontSize = 12.sp, maxLine = 1)
            }
            IconButton(onClick = { download(song) }) {
              if (downloadingHash == song.hash) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
              } else {
                Icon(
                  painter = painterResource(R.drawable.ic_save_white_24dp),
                  contentDescription = "下载",
                  tint = theme.textSecondary,
                  modifier = Modifier.size(19.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}
