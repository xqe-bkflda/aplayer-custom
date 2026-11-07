package remix.myplayer.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.service.Command
import remix.myplayer.service.MusicService
import remix.myplayer.service.MusicServiceRemote.setPlayQueue
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.ui.widget.library.SongListHeader
import remix.myplayer.ui.widget.library.list.ListSong
import remix.myplayer.util.MusicUtil
import remix.myplayer.viewmodel.libraryViewModel
import remix.myplayer.viewmodel.playbackViewModel

/** 曲库里的「最近播放」标签页（复用播放历史数据，没有独立顶栏） */
@Composable
fun HistoryTabScreen() {
  val theme = LocalTheme.current
  val libraryVM = libraryViewModel
  val onlineVM = remix.myplayer.viewmodel.onlineMusicViewModel
  val items by libraryVM.historySongs.collectAsStateWithLifecycle()
  val songs = items.map { it.first }
  val playbackState by playbackViewModel.playbackUiState.collectAsStateWithLifecycle()

  Column(Modifier.fillMaxSize().background(theme.libraryBackground)) {
    Row(
      Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(Modifier.weight(1f)) {
        TextPrimary("最近播放", fontSize = 16.sp)
        Spacer(Modifier.height(2.dp))
        TextSecondary("${songs.size} 首", fontSize = 12.sp)
      }
      IconButton(onClick = { libraryVM.clearHistory() }) {
        Icon(
          painter = painterResource(R.drawable.ic_delete_black_24dp),
          contentDescription = "清空",
          tint = theme.textSecondary,
          modifier = Modifier.size(18.dp)
        )
      }
    }

    if (songs.isNotEmpty()) {
      SongListHeader(songs)
    }

    LazyColumn(Modifier.weight(1f)) {
      itemsIndexed(items, key = { index, item -> "${item.first.id}_$index" }) { pos, item ->
        val song = item.first
        ListSong(
          modifier = Modifier.height(64.dp),
          song = song,
          modelParent = song,
          selected = false,
          playing = playbackState.song.id == song.id,
          num = item.second,
          onClickSong = {
            if (songs.isEmpty()) return@ListSong
            // 在线歌曲直链会过期，播放前重新解析
            if (song.isRemote()) {
              onlineVM.replayRemote(song) { fresh ->
                val queue = songs.toMutableList()
                val idx = queue.indexOfFirst { it.id == song.id }
                if (fresh != null && idx >= 0) queue[idx] = fresh
                setPlayQueue(
                  queue,
                  MusicUtil.makeCmdIntent(Command.PLAY_AT)
                    .putExtra(MusicService.EXTRA_POSITION, if (idx >= 0) idx else pos)
                )
              }
            } else {
              setPlayQueue(
                songs,
                MusicUtil.makeCmdIntent(Command.PLAY_AT)
                  .putExtra(MusicService.EXTRA_POSITION, pos)
              )
            }
          },
          onLongClickSong = {}
        )
      }
    }
  }
}
