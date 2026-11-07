package remix.myplayer.ui.screen.playing

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.data.model.audio.Song
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.theme.popupButton
import remix.myplayer.util.ext.clickWithRipple
import remix.myplayer.viewmodel.PlayingScreenValue
import remix.myplayer.viewmodel.mainViewModel
import remix.myplayer.viewmodel.onlineMusicViewModel

@Composable
@Stable
internal fun PlayingTopBar(song: Song, swatch: Palette.Swatch) {
  val mainVM = mainViewModel
  val scope = rememberCoroutineScope()
  val theme = LocalTheme.current
  val titleColor = if (theme.isLight) Color(swatch.titleTextColor) else theme.textPrimary
  val bodyColor = if (theme.isLight) Color(swatch.bodyTextColor) else theme.textSecondary
  val tintColor = if (theme.isLight) titleColor else theme.popupButton()

  Row(modifier = Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .padding(8.dp)
        .size(40.dp)
        .clickWithRipple {
          scope.launch {
            mainVM.playingScreenState.animateTo(PlayingScreenValue.Hidden)
          }
        }
    ) {
      Image(
        painter = painterResource(R.drawable.icon_player_back),
        colorFilter = ColorFilter.tint(tintColor),
        contentDescription = "PlayingBack"
      )
    }

    Column(
      modifier = Modifier
        .weight(1f),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      val title = song.title
      val artist = song.artist
      val album = song.album

      val detail = when {
        artist == "" -> {
          song.album
        }

        album == "" -> {
          song.artist
        }

        else -> {
          String.format("%s-%s", song.artist, song.album)
        }
      }

      Text(
        title.ifEmpty { stringResource(R.string.unknown_song) },
        color = titleColor,
        fontSize = 16.sp,
        maxLines = 1
      )
      Text(detail, color = bodyColor, fontSize = 14.sp, maxLines = 1, textAlign = TextAlign.Center)
    }

    // 在线歌曲（本地曲库反查不到）才显示收藏/评论
    val onlineVM = onlineMusicViewModel
    val onlineState by onlineVM.uiState.collectAsStateWithLifecycle()
    // 用 knownVersion 触发重算，避免第一次进来反查不到就永久是 null（红心/评论按钮凭空消失）
    val onlineFoundVersion = onlineState.knownVersion
    // 首选精确路径：在线歌曲的 Remote 自带网易云 id，直接按 id 取，绝不串歌。
    // 老队列/历史里的 Remote 没有 neteaseId（=0）时才退回 title+artist 反查。
    val songNeteaseId = (song as? Song.Remote)?.neteaseId ?: 0L
    val netease = remember(song.id, songNeteaseId, onlineFoundVersion, song.title, song.artist) {
      if (songNeteaseId > 0L) onlineVM.findSongById(songNeteaseId)
      else onlineVM.findSong(song.title, song.artist)
    }
    val currentNetease = if (netease != null && netease.id > 0L) netease else null
    // 右侧动作按钮统一为紧凑横向排，避免把标题挤没
    if (currentNetease != null) {
      val liked = currentNetease.id in onlineState.likedIds
      // 红心 + 收藏数横排，数字在图标右侧（不再竖排撑高顶栏）
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .padding(horizontal = 4.dp)
          .clickWithRipple { onlineVM.toggleLike(currentNetease) }
      ) {
        Image(
          painter = painterResource(R.drawable.ic_favorite_24dp),
          colorFilter = ColorFilter.tint(if (liked) Color(0xFFFF4D6D) else tintColor),
          contentDescription = "收藏",
          modifier = Modifier.size(20.dp)
        )
        val favCount = onlineState.favoriteCounts[currentNetease.id]
        if (favCount != null) {
          Spacer(Modifier.width(2.dp))
          Text(
            text = favCount,
            color = if (liked) Color(0xFFFF4D6D) else tintColor.copy(alpha = 0.85f),
            fontSize = 10.sp,
            maxLines = 1,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
          )
        }
      }
      // 进入时触发加载收藏量
      androidx.compose.runtime.LaunchedEffect(currentNetease.id) {
        onlineVM.loadFavoriteCount(currentNetease.id)
      }
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .padding(3.dp)
          .size(36.dp)
          .clickWithRipple { onlineVM.download(currentNetease) }
      ) {
        Image(
          painter = painterResource(R.drawable.ic_save_white_24dp),
          colorFilter = ColorFilter.tint(
            if (onlineState.downloadingId == currentNetease.id) Color(0xFFFF4D6D) else tintColor
          ),
          contentDescription = "下载",
          modifier = Modifier.size(20.dp)
        )
      }
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .padding(3.dp)
          .size(36.dp)
          .clickWithRipple { onlineVM.openComments(currentNetease) }
      ) {
        Image(
          painter = painterResource(R.drawable.ic_comment_24dp),
          colorFilter = ColorFilter.tint(tintColor),
          contentDescription = "评论",
          modifier = Modifier.size(20.dp)
        )
      }
    }

    // 酷狗歌曲：本地曲库和网易云都反查不到，单独给一个下载键
    if (currentNetease == null) {
      val kgSong = remember(song.id, song.title) {
        onlineVM.findKuGouSong(song.title, song.artist)
      }
      if (kgSong != null) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .padding(3.dp)
            .size(36.dp)
            .clickWithRipple { onlineVM.downloadKuGou(kgSong) }
        ) {
          if (onlineState.kgDownloadingHash == kgSong.hash) {
            androidx.compose.material3.CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = tintColor
            )
          } else {
            Image(
              painter = painterResource(R.drawable.ic_save_white_24dp),
              colorFilter = ColorFilter.tint(tintColor),
              contentDescription = "下载",
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }

    var expanded by remember { mutableStateOf(false) }

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .padding(6.dp)
        .size(36.dp)
        .clickWithRipple {
          expanded = !expanded
        }
    ) {
      Image(
        painter = painterResource(R.drawable.icon_player_more),
        colorFilter = ColorFilter.tint(tintColor),
        contentDescription = "PlayingMore",
        modifier = Modifier.size(22.dp)
      )

      PlayingDropDownMenu(expanded, song) {
        expanded = false
      }
    }
  }
}
