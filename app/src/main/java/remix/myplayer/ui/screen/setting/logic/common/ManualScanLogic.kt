package remix.myplayer.ui.screen.setting.logic.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import remix.myplayer.R
import remix.myplayer.misc.MediaScanner
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.screen.setting.NormalPreference
import remix.myplayer.viewmodel.settingViewModel
import java.io.File

/**
 * 手动扫描：直接用系统文件选择器（SAF 文档树）选文件夹，
 * 选完解析成真实绝对路径后交给 MediaScanner。
 */
@Composable
fun ManualScanLogic() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val settingVM = settingViewModel
  val settingState by settingVM.settingsState.collectAsStateWithLifecycle()

  var currentPath by rememberSaveable {
    mutableStateOf(settingState.common.manualScanFolder)
  }
  var pending by remember { mutableStateOf<Uri?>(null) }

  val folderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
      } catch (_: Exception) {
      }
      pending = uri
    }
  }

  NormalPreference(stringResource(R.string.manual_scan), stringResource(R.string.manual_scan_tip)) {
    folderLauncher.launch(null)
  }

  pending?.let { uri ->
    LaunchedEffect(uri) {
      pending = null
      val path = resolveDocumentTreePath(context, uri)
      if (path.isNullOrBlank()) {
        MessageNotifier.show("无法读取所选文件夹，请换一个")
        return@LaunchedEffect
      }
      currentPath = path
      settingVM.setManualScanFolder(path)
      scope.launch {
        MediaScanner(context).scan(File(path))
        MessageNotifier.show("已扫描：$path")
      }
    }
  }
}

/**
 * SAF 文件夹树 uri（如 content://.../tree/primary%3AMusic）→ 真实绝对路径。
 * 解析不了返回 null。
 */
internal fun resolveDocumentTreePath(context: Context, uri: Uri): String? {
  return try {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    if (docId.startsWith("primary:")) {
      val rel = docId.substring("primary:".length)
      val base = android.os.Environment.getExternalStorageDirectory()
      return if (rel.isBlank()) base.absolutePath else File(base, rel).absolutePath
    }
    // 其它卷（SD 卡）按 document uri 投影 _data
    val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
    context.contentResolver.query(
      docUri, arrayOf(android.provider.MediaStore.MediaColumns.DATA),
      null, null, null
    )?.use { c ->
      if (c.moveToFirst()) {
        val data = c.getString(0)
        return if (data.isNullOrBlank()) null else data
      }
    }
    null
  } catch (t: Throwable) {
    null
  }
}