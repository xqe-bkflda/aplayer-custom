package remix.myplayer.ui.widget.app

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.data.prefs.ThemePrefs.Companion.BLACK
import remix.myplayer.data.prefs.ThemePrefs.Companion.DARK
import remix.myplayer.data.prefs.ThemePrefs.Companion.LIGHT
import remix.myplayer.ui.nav.LocalNavController
import remix.myplayer.ui.nav.RouteAbout
import remix.myplayer.ui.dialog.ItemsCallbackSingleChoice
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.data.prefs.NetEasePrefs
import remix.myplayer.data.model.misc.LyricOrder
import remix.myplayer.ui.nav.RouteRecognize
import remix.myplayer.ui.nav.RouteSetting
import remix.myplayer.ui.nav.RouteSettingDetail
import remix.myplayer.ui.theme.AppTheme
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.library.GlideCover
import remix.myplayer.util.ext.isPortraitOrientation
import remix.myplayer.viewmodel.PlaybackViewModel
import remix.myplayer.viewmodel.playbackViewModel
import remix.myplayer.viewmodel.settingViewModel

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun Drawer(drawerState: DrawerState, vm: PlaybackViewModel = playbackViewModel) {
  val navController = LocalNavController.current
  val context = LocalContext.current
  val theme = LocalTheme.current

  val drawerDefault = colorResource(
    when (theme.theme) {
      LIGHT -> R.color.drawer_default_light
      DARK -> R.color.drawer_default_dark
      BLACK -> R.color.drawer_default_black
      else -> throw IllegalArgumentException("unknown theme: $theme")
    }
  )
  val drawerEffect = colorResource(
    when (theme.theme) {
      LIGHT -> R.color.drawer_effect_light
      DARK -> R.color.drawer_effect_dark
      BLACK -> R.color.drawer_effect_black
      else -> throw IllegalArgumentException("unknown theme: $theme")
    }
  )

  ModalDrawerSheet(
    modifier = Modifier
      .width(264.dp)
      .fillMaxHeight(),
    drawerShape = RectangleShape,
    drawerContainerColor = drawerDefault,
    windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Start)
  ) {
    val scope = rememberCoroutineScope()
    val settingVM = settingViewModel
    val levelDialogState = rememberDialogState()
    val lyricOrderDialogState = rememberDialogState()
    val levelPrefs = androidx.compose.runtime.remember(context) { NetEasePrefs(context) }
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(theme.primary),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(with(LocalDensity.current) {
        WindowInsets.systemBars.getTop(this).toDp()
      }))
      val onlineVM = remix.myplayer.viewmodel.onlineMusicViewModel
      val onlineState by onlineVM.uiState.collectAsStateWithLifecycle()
      val isPortrait = context.isPortraitOrientation()
      val avatarSize = if (isPortrait) 76.dp else 60.dp

      Spacer(modifier = Modifier.height(if (isPortrait) 14.dp else 8.dp))

      // 头像：未登录显示占位图，点一下去登录 / 换头像
      Box(
        modifier = Modifier
          .size(avatarSize)
          .clip(CircleShape)
          .background(AppTheme.darkenColor(theme.primary))
          .clickable {
            navController.navigate(remix.myplayer.ui.nav.RouteNetEaseLogin)
            scope.launch { drawerState.close() }
          },
        contentAlignment = Alignment.Center
      ) {
        if (onlineState.loggedIn && onlineState.avatar.isNotBlank()) {
          com.bumptech.glide.integration.compose.GlideImage(
            model = onlineState.avatar.replace("http://", "https://") + "?param=200y200",
            contentDescription = "头像",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Icon(
            painter = painterResource(R.drawable.ic_lab_profile_24dp),
            contentDescription = "未登录",
            tint = theme.primaryReverse,
            modifier = Modifier.size(avatarSize / 2)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = if (onlineState.loggedIn && onlineState.nickname.isNotBlank()) {
          onlineState.nickname
        } else {
          "未登录"
        },
        color = theme.primaryReverse,
        fontSize = if (isPortrait) 16.sp else 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = if (onlineState.loggedIn) "UID ${onlineState.uid}" else "点击登录网易云账号",
        color = theme.primaryReverse.copy(alpha = 0.75f),
        fontSize = 12.sp,
        maxLines = 1
      )
      Spacer(modifier = Modifier.height(if (isPortrait) 18.dp else 10.dp))
    }

    LazyColumn(modifier = Modifier.background(drawerDefault)) {
      // 听歌识曲
      item {
        DrawerMenuRow(
          icon = R.drawable.ic_mic_24dp,
          text = "听歌识曲",
          tint = theme.primary,
          onClick = {
            navController.navigate(RouteRecognize)
            scope.launch { drawerState.close() }
          }
        )
      }
      // 清除缓存
      item {
        DrawerMenuRow(
          icon = R.drawable.ic_delete_black_24dp,
          text = "清除缓存",
          tint = theme.primary,
          onClick = {
            scope.launch { drawerState.close() }
            settingVM.clearCache(context) {
              remix.myplayer.ui.nav.MessageNotifier.show("缓存已清除")
            }
            Glide.get(context).clearMemory()
          }
        )
      }
      // 设置（完整设置入口）
      item {
        DrawerMenuRow(
          icon = R.drawable.ic_settings_24dp,
          text = "设置",
          tint = theme.primary,
          onClick = {
            navController.navigate(RouteSetting)
            scope.launch { drawerState.close() }
          }
        )
      }
      // 关于（放最下方）
      item {
        DrawerMenuRow(
          icon = R.drawable.ic_info_outlined_24dp,
          text = "关于",
          tint = theme.primary,
          onClick = {
            navController.navigate(RouteAbout)
            scope.launch { drawerState.close() }
          }
        )
      }

      item {
        Spacer(modifier = Modifier.weight(1f))
      }
    }
  }
}

/** 抽屉里的一行菜单 + 跳转箭头 */
@Composable
private fun DrawerMenuRow(
  icon: Int,
  text: String,
  tint: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit
) {
  val theme = LocalTheme.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 15.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(22.dp)
    )
    Spacer(Modifier.width(16.dp))
    Text(
      text = text,
      color = theme.textPrimary,
      fontSize = 15.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )
    Icon(
      painter = painterResource(R.drawable.ic_navigate_next_white_24dp),
      contentDescription = null,
      tint = theme.textSecondary,
      modifier = Modifier.size(18.dp)
    )
  }
}

/** 打开系统文件管理器到下载歌曲目录（NetEasePrefs.downloadDir）；打不开就提示 */
private fun openDownloadsDir(context: android.content.Context) {
  val prefs = remix.myplayer.data.prefs.NetEasePrefs(context)
  val base = android.os.Environment.getExternalStorageDirectory()
  val rel = prefs.downloadDir.trim('/')
  val dir = if (rel.isBlank()) base else java.io.File(base, rel)
  if (!dir.exists()) dir.mkdirs()
  val uri = androidx.core.content.FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    dir
  )
  try {
    context.startActivity(
      android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "resource/folder")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        // 部分 ROM 需要 URL 形式提高兼容性
        clipData = android.content.ClipData.newRawUri(null, uri)
      }
    )
  } catch (_: Exception) {
    remix.myplayer.ui.nav.MessageNotifier.show("没有可打开文件夹的应用")
  }
}
