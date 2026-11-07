package remix.myplayer.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.data.model.misc.Library
import remix.myplayer.ui.nav.LocalNavController
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.nav.RouteOnlineMusic
import remix.myplayer.ui.nav.RouteRecognize
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.defaultAppBarActions
import remix.myplayer.ui.widget.popup.ScreenPopupButton
import remix.myplayer.viewmodel.onlineMusicViewModel
import remix.myplayer.viewmodel.settingViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars


@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun HomeAppBar(
  scrollBehavior: TopAppBarScrollBehavior,
  drawerState: DrawerState
) {
  val library by settingViewModel.currentLibrary.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()

  val nav = LocalNavController.current
  val theme = LocalTheme.current
  val onlineVM = onlineMusicViewModel

  TopAppBar(
    windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    scrollBehavior = scrollBehavior,
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = theme.primary,
      scrolledContainerColor = theme.primary,
      navigationIconContentColor = Color.White,
      actionIconContentColor = Color.White,
    ),
    // 最左：三条杠（抽屉菜单）
    navigationIcon = {
      IconButton(onClick = { scope.launch { drawerState.open() } }) {
        Icon(
          imageVector = Icons.Filled.Menu,
          contentDescription = "菜单",
          tint = Color.White
        )
      }
    },
    title = {
      // 搜索框：右边留出操作区，稍微往右挪一点
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 6.dp, end = 6.dp)
          .clip(RoundedCornerShape(20.dp))
          .background(Color.White.copy(alpha = 0.18f))
          .clickable {
            // 告诉在线页：这次是从搜索框进来的，必须显示搜索界面
            onlineVM.requestSearchMode()
            nav.navigate(RouteOnlineMusic)
          }
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_search_white_24dp),
          contentDescription = "搜索",
          tint = Color.White.copy(alpha = 0.9f),
          modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
          "搜索在线音乐",
          color = Color.White.copy(alpha = 0.85f),
          fontSize = 14.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    },
    // 最右：识曲 + 刷新 + 排序 + 计时器（刷新按钮在搜索框外面）
    actions = {
      // 听歌识曲：任何页面都能随手点
      IconButton(onClick = { nav.navigate(RouteRecognize) }) {
        Icon(
          painter = painterResource(R.drawable.ic_mic_24dp),
          contentDescription = "听歌识曲"
        )
      }

      // 只在在线相关页显示刷新，本地曲库页刷新没意义
      if (library.tag == Library.TAG_NETEASE || library.tag == Library.TAG_ONLINE) {
        IconButton(onClick = {
          onlineVM.refreshAll()
          onlineVM.loadKuGouRanks()
          MessageNotifier.show("已刷新在线数据")
        }) {
          Icon(
            painter = painterResource(R.drawable.ic_refresh_24dp),
            contentDescription = "刷新在线数据"
          )
        }
      }

      // 本地曲库页显示排序键（歌曲/专辑/艺术家/歌单/流派/文件夹）；在线/网易云/远程/历史 不显示
      if (library.tag != Library.TAG_REMOTE && library.tag != Library.TAG_NETEASE &&
        library.tag != Library.TAG_ONLINE && library.tag != Library.TAG_HISTORY
      ) {
        ScreenPopupButton(library)
      }

      defaultAppBarActions.map {
        IconButton(onClick = { it.action() }) {
          Icon(
            painter = painterResource(it.icon),
            contentDescription = it.contentDescription
          )
        }
      }
    })
}
