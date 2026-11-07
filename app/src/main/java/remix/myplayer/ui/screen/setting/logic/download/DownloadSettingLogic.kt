package remix.myplayer.ui.screen.setting.logic.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import remix.myplayer.data.prefs.NetEasePrefs
import remix.myplayer.ui.dialog.ItemsCallbackSingleChoice
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.screen.setting.NormalPreference
import remix.myplayer.ui.screen.setting.SwitchPreference

/**
 * 在线音乐下载相关设置。直接用 NetEasePrefs（单独一份 SharedPreferences）。
 */
@Composable
fun DownloadPreferenceItems() {
  val context = LocalContext.current
  // 与 Hilt 注入的是同一个 SharedPreferences 文件，这里只是本地读用
  val prefs = remember(context) { NetEasePrefs(context) }

  var withLyric by remember { mutableStateOf(prefs.downloadLyricWithSong) }
  var autoOnLike by remember { mutableStateOf(prefs.autoDownloadOnLike) }
  var deleteOnUnlike by remember { mutableStateOf(prefs.deleteLocalOnUnlike) }
  var showTimer by remember { mutableStateOf(prefs.showSleepTimerButton) }
  var dir by remember { mutableStateOf(prefs.downloadDir) }
  var levelIndex by remember {
    mutableIntStateOf(NetEasePrefs.LEVELS.indexOf(prefs.downloadLevel).let { if (it < 0) 2 else it })
  }

  val levelState = rememberDialogState(false)
  NormalPreference("下载音质", NetEasePrefs.LEVEL_TITLES[levelIndex]) {
    levelState.show()
  }
  NormalDialog(
    dialogState = levelState,
    title = "下载音质",
    positive = null,
    negative = null,
    items = NetEasePrefs.LEVEL_TITLES,
    itemsCallbackSingleChoice = ItemsCallbackSingleChoice(levelIndex) { index ->
      levelIndex = index
      prefs.downloadLevel = NetEasePrefs.LEVELS[index]
    }
  )

  // 下载目录：直接用系统文件选择器（SAF 文档树）选文件夹
  val storageRoot = android.os.Environment.getExternalStorageDirectory()
  var pickPending by remember { mutableStateOf<android.net.Uri?>(null) }
  val dirLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
  ) { uri: android.net.Uri? ->
    if (uri != null) {
      try {
        context.contentResolver.takePersistableUriPermission(
          uri,
          android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
      } catch (_: Exception) {
      }
      pickPending = uri
    }
  }
  NormalPreference("下载目录", dir) { dirLauncher.launch(null) }

  pickPending?.let { uri ->
    LaunchedEffect(uri) {
      pickPending = null
      val absPath = remix.myplayer.ui.screen.setting.logic.common.resolveDocumentTreePath(context, uri)
      if (absPath == null) {
        remix.myplayer.ui.nav.MessageNotifier.show("无法读取所选目录，请换一个")
        return@LaunchedEffect
      }
      // 相对手机存储根；选在根上就存根相对路径（空 → 默认）
      val rel = if (absPath == storageRoot.absolutePath) "" else {
        absPath.removePrefix(storageRoot.absolutePath).trim('/')
      }
      val v = remix.myplayer.request.netease.NetEaseDownloader.relDir(rel)
      dir = v
      prefs.downloadDirPref = v
    }
  }

  SwitchPreference(
    "下载音乐时同时保存歌词",
    "存成同名 .lrc 文件",
    checked = withLyric
  ) {
    withLyric = it
    prefs.downloadLyricWithSong = it
  }

  SwitchPreference(
    "收藏后自动下载",
    "点红心就把歌存到本地",
    checked = autoOnLike
  ) {
    autoOnLike = it
    prefs.autoDownloadOnLike = it
  }

  SwitchPreference(
    "取消收藏时删除已下载的文件",
    "只删下载目录里同名的歌，本地自己的歌不动",
    checked = deleteOnUnlike
  ) {
    deleteOnUnlike = it
    prefs.deleteLocalOnUnlike = it
  }

  SwitchPreference(
    "显示「伴随音乐入睡」按钮",
    "关闭后顶栏不再显示计时器图标",
    checked = showTimer
  ) {
    showTimer = it
    prefs.showSleepTimerButton = it
  }
}
