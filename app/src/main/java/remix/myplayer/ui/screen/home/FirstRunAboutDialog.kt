package remix.myplayer.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import remix.myplayer.data.prefs.SettingPrefs
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.nav.LocalNavController
import remix.myplayer.ui.nav.RouteDeclaration

/**
 * 首次进入弹"二改版声明"提示，只弹一次。
 * 完整改动清单、接口来源与免责声明在「关于 → 声明」页。
 */
@Composable
fun FirstRunAboutDialog(settingPrefs: SettingPrefs) {
  var show by remember { mutableStateOf(!settingPrefs.firstRunAboutShown) }
  val dialogState = rememberDialogState()
  val nav = LocalNavController.current

  LaunchedEffect(show) {
    if (show) {
      dialogState.show()
      settingPrefs.firstRunAboutShown = true
    }
  }

  NormalDialog(
    dialogState = dialogState,
    autoDismiss = true,
    title = "声明",
    content = "本应用是 APlayer 的二次修改版（aplayer），与原项目作者无关，" +
      "仅供个人学习研究使用。\n\n" +
      "相对原项目主要改动：\n" +
      "· 重构在线音乐：网易云、酷狗双音源\n" +
      "· 新增听歌识曲、桌面歌词、曲库在线页签\n" +
      "· 修复歌词、播放顺序、下载目录等一批问题\n\n" +
      "隐私：本应用不上传、不收集任何个人数据，没有广告与统计 SDK。\n" +
      "麦克风只在你自己点「听歌识曲」时开启，录满 12 秒生成指纹后立即释放，" +
      "录音不会保存，也不会外传。\n" +
      "登录凭据（手机号、Cookie）只存在本机私有目录，密码以 MD5 发送、不存明文。\n\n" +
      "听歌识曲的指纹引擎参考开源仓库 " +
      "MelodyYuuka/NeteaseCloudMusic-Audio-Recognize，" +
      "其余在线接口均为本人抓包分析得出。\n\n" +
      "应用内在线内容版权归各平台及原作者所有，本应用不存储任何音源文件；" +
      "请勿商用，使用后果由使用者自行承担。",
    positive = "知道了",
    neutral = "完整声明",
    negative = null,
    onNeutral = {
      dialogState.dismiss()
      show = false
      nav.navigate(RouteDeclaration)
    },
    onPositive = {
      dialogState.dismiss()
      show = false
    }
  )
}
