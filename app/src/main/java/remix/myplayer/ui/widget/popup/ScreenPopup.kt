package remix.myplayer.ui.widget.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.data.model.misc.Library
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.viewmodel.LibraryViewModel
import remix.myplayer.viewmodel.libraryViewModel
import remix.myplayer.viewmodel.settingViewModel
import remix.myplayer.viewmodel.settings.SortCategory

@Composable
fun ScreenPopupButton(library: Library?, vm: LibraryViewModel = libraryViewModel) {
  if (library == null) {
    return
  }
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()
  var expanded by remember { mutableStateOf(false) }
  var iconHeight by remember { mutableStateOf(0.dp) }

  IconButton(
    modifier = Modifier.onSizeChanged {
      iconHeight = it.height.dp
    },
    onClick = {
      expanded = !expanded
    }) {
    Icon(
      painter = painterResource(R.drawable.ic_sort_white_24dp),
      contentDescription = "ScreenSortOrderPopUp"
    )
  }

  // 只处理支持排序的标签页，其它（远程/网易云/最近播放）直接不显示排序按钮
  val sortableTags = listOf(
    Library.TAG_SONG, Library.TAG_ALBUM, Library.TAG_ARTIST,
    Library.TAG_GENRE, Library.TAG_PLAYLIST, Library.TAG_FOLDER
  )
  val sortable = library.tag in sortableTags

  val onlineVM = remix.myplayer.viewmodel.onlineMusicViewModel
  val nav = remix.myplayer.ui.nav.LocalNavController.current

  val sortOrderItems = library.menuItems
  val sortOrders = library.sortOrders
  val sortOrder = if (sortable) when (library.tag) {
    Library.TAG_SONG -> settingState.library.songSortOrder
    Library.TAG_ALBUM -> settingState.library.albumSortOrder
    Library.TAG_ARTIST -> settingState.library.artistSortOrder
    Library.TAG_PLAYLIST -> settingState.library.playlistSortOrder
    Library.TAG_GENRE -> settingState.library.genreSortOrder
    Library.TAG_FOLDER -> settingState.library.folderSortOrder
    else -> ""
  } else ""
  val selectedIndex = sortOrders.indexOf(sortOrder)

  DropdownMenu(
    modifier = Modifier.wrapContentSize(),
    expanded = expanded,
    offset = DpOffset(x = 0.dp, y = -iconHeight),
    containerColor = LocalTheme.current.dialogBackground,
    onDismissRequest = { expanded = false }
  ) {
    fun saveSortOrder(index: Int) {
      val type = library.tag
      val ret = sortOrders[index]
      when (type) {
        Library.TAG_SONG -> {
          settingVM.setSortOrder(SortCategory.SONG, ret)
        }

        Library.TAG_ALBUM -> {
          settingVM.setSortOrder(SortCategory.ALBUM, ret)
        }

        Library.TAG_ARTIST -> {
          settingVM.setSortOrder(SortCategory.ARTIST, ret)
        }

        Library.TAG_PLAYLIST -> {
          settingVM.setSortOrder(SortCategory.PLAYLIST, ret)
        }

        Library.TAG_GENRE -> {
          settingVM.setSortOrder(SortCategory.GENRE, ret)
        }

        Library.TAG_FOLDER -> {
          settingVM.setSortOrder(SortCategory.FOLDER, ret)
        }
      }
      expanded = false
      vm.fetchMedia()
    }

    if (sortable) sortOrderItems.forEachIndexed { index, res ->
      DropdownMenuItem(
        text = {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(stringResource(res), color = LocalTheme.current.textPrimary)
            RadioButton(
              colors = RadioButtonDefaults.colors(selectedColor = LocalTheme.current.primary),
              selected = selectedIndex == index,
              onClick = { saveSortOrder(index) }
            )
          }
        },
        onClick = {
          saveSortOrder(index)
        }
      )
    }
  }
}
