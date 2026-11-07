package remix.myplayer.ui.screen.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.data.model.audio.Song
import remix.myplayer.helper.SleepTimer
import remix.myplayer.service.Command
import remix.myplayer.service.MusicService
import remix.myplayer.service.MusicServiceRemote.setPlayQueue
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.AppBarAction
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.library.SongListHeader
import remix.myplayer.ui.widget.library.list.ListSong
import remix.myplayer.util.MusicUtil
import remix.myplayer.viewmodel.libraryViewModel
import remix.myplayer.viewmodel.playbackViewModel
import remix.myplayer.viewmodel.timerViewModel

@Composable
fun HistoryScreen() {
  val items by libraryViewModel.historySongs.collectAsStateWithLifecycle()
  val onlineVM = remix.myplayer.viewmodel.onlineMusicViewModel
  val menuSongState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Song?>(null) }
  val menuSong = menuSongState.value
  val setMenuSong: (Song?) -> Unit = { menuSongState.value = it }
  val songs = items.map { it.first }
  val playbackState by playbackViewModel.playbackUiState.collectAsStateWithLifecycle()

  menuSong?.let { target ->
    androidx.compose.material3.DropdownMenu(
      expanded = true,
      onDismissRequest = { setMenuSong(null) },
      containerColor = LocalTheme.current.dialogBackground
    ) {
      androidx.compose.material3.DropdownMenuItem(
        text = { Text("下载音乐", color = LocalTheme.current.textPrimary) },
        onClick = {
          onlineVM.downloadRemote(target)
          setMenuSong(null)
        }
      )
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.systemBars,
    topBar = {
      CommonAppBar(
        title = stringResource(R.string.drawer_history),
        actions = {
          HistoryActions()
        })
    },
    containerColor = LocalTheme.current.mainBackground,
  ) { contentPadding ->

    Column(modifier = Modifier.padding(contentPadding)) {
      if (songs.isNotEmpty()) {
        SongListHeader(songs)
      }

      LazyColumn(modifier = Modifier.weight(1f)) {
        itemsIndexed(items, key = { index, item ->
          // 在线歌曲 id 由直链哈希得出，同一 URL 可能出现多条，
          // 纯用 id 当 key 会重复 → LazyColumn 直接抛异常闪退
          "${item.first.id}_$index"
        }) { pos, item ->
          val song = item.first
          val isPlayingSong = playbackState.song.id == song.id

          ListSong(
            modifier = Modifier.height(64.dp),
            song = song,
            modelParent = song,
            selected = false,
            playing = isPlayingSong,
            num = item.second,
            onClickSong = {
              if (songs.isEmpty()) {
                return@ListSong
              }

              // 在线歌曲的直链会过期，播放前重新解析一次，避免点了没反应
              if (song.isRemote()) {
                onlineVM.replayRemote(song) { fresh ->
                  val queue = songs.toMutableList()
                  val idx = queue.indexOfFirst { it.id == song.id }
                  if (fresh != null && idx >= 0) queue[idx] = fresh
                  setPlayQueue(
                    queue, MusicUtil.makeCmdIntent(Command.PLAY_AT)
                      .putExtra(MusicService.EXTRA_POSITION, if (idx >= 0) idx else pos)
                  )
                }
              } else {
                setPlayQueue(
                  songs, MusicUtil.makeCmdIntent(Command.PLAY_AT)
                    .putExtra(MusicService.EXTRA_POSITION, pos)
                )
              }
            },
            onLongClickSong = {
              setMenuSong(song)
            })
        }
      }
    }
  }
}

@Composable
private fun HistoryActions() {
  val timerVM = timerViewModel
  val libraryVM = libraryViewModel
  val timerRunning by SleepTimer.runningState.collectAsStateWithLifecycle()
  val timerIcon = if (timerRunning) R.drawable.ic_timer_on_24dp else R.drawable.ic_timer_white_24dp

  listOf(
    AppBarAction(timerIcon, "Timer") {
      timerVM.showTimerDialog()
    },
    AppBarAction(R.drawable.ic_delete_black_24dp, "ClearHistory") {
      libraryVM.clearHistory()
    }).forEach {
    IconButton(onClick = {
      it.action()
    }) {
      Icon(
        painter = painterResource(it.icon),
        contentDescription = it.contentDescription
      )
    }
  }
}
