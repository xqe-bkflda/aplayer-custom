package remix.myplayer.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import remix.myplayer.BuildConfig
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.common.TextPrimary

private const val UPSTREAM_NAME = "APlayer"
private const val UPSTREAM_URL = "https://github.com/rRemix/APlayer"
private const val RECOGNIZE_REPO = "MelodyYuuka/NeteaseCloudMusic-Audio-Recognize"

/**
 * 声明页：对比原仓库，列出这一版改了什么；并写明接口来源与免责声明。
 */
@Composable
fun DeclarationScreen() {
  val theme = LocalTheme.current

  Scaffold(
    contentWindowInsets = WindowInsets.systemBars,
    topBar = { CommonAppBar(title = "声明", actions = emptyList()) },
    containerColor = theme.mainBackground
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      SectionTitle("版本")
      Body(
        "本应用名为 aplayer，是在 $UPSTREAM_NAME（$UPSTREAM_URL）基础上二次修改的版本。\n" +
          "版本号：${BuildConfig.VERSION_NAME}\n" +
          "原项目采用 GPLv3 协议，本二改版同样按 GPLv3 发布。"
      )

      SectionTitle("相对原项目改了什么")
      SubTitle("一、在线音乐（整块重构）")
      Body(
        "· 新增网易云客户端：匿名注册与 eapi 签名、Cookie 会话、登录态持久化。\n" +
          "· 登录方式三种：手机验证码、手机号 + 密码、扫码登录，另加退出登录。\n" +
          "· 新增网易云搜索（歌曲 / 歌单）、私人雷达、各类榜单、我的歌单、我喜欢的音乐。\n" +
          "· 新增播放地址解析、在线歌曲下载（写标签、存封面、存歌词）。\n" +
          "· 新增酷狗：在线搜索、榜单、单曲取流、歌词。\n" +
          "· 搜索页可切换两个音源（网易云 / 酷狗），切源后自动用当前关键词重搜。\n" +
          "· 歌词来源顺序改为：本地 → 网易云 → 酷狗 → 内嵌 → 忽略。\n" +
          "· 单曲收藏数：播放页红心旁显示真实收藏数（走 /api/song/red/count）。\n" +
          "· 评论：播放页可直接拉出评论浮层，不登录也能看。"
      )

      SubTitle("二、听歌识曲（新增功能）")
      Body(
        "· 录 12 秒 48kHz 单声道，抽成 8kHz 后交给 wasm 算指纹，再请求网易云识别接口。\n" +
          "· 返回最多 3 个候选，点一下直接播放。\n" +
          "· 桌面长按图标有「听歌识曲」快捷方式，可直接进入识别页。"
      )

      SubTitle("三、曲库与播放")
      Body(
        "· 侧边曲库新增三个页签：网易云、在线音乐、最近播放。\n" +
          "· 默认显示：网易云 / 歌曲 / 在线音乐 / 最近播放，其余页签默认收起可在设置里打开。\n" +
          "· 最近播放改为也能记录在线歌曲（数据库 History 表新增字段，并带 7→8 迁移）。\n" +
          "· 歌单排序新增「创建时间（新→旧）」并放到第一位。\n" +
          "· 在线歌单点歌后按歌单原顺序往下播，不再从第一首重新排。\n" +
          "· 播放队列底部有「下一曲」提示；原来底部的下一曲组件去掉，只保留音量条。\n" +
          "· 修复歌词有时不显示 / 切歌后停在「暂无歌词」的问题。\n" +
          "· 修复播放页标题被一串按钮挤成省略号的问题。"
      )

      SubTitle("四、桌面歌词")
      Body(
        "· 修复一卡一卡：文本测量结果做缓存，帧率做节流。\n" +
          "· 桌面歌词颜色固定为白色，不再跟随主题色变化。\n" +
          "· 锁定状态下点一下即解锁，不再需要找按钮。\n" +
          "· 通知栏歌词按钮统一图标与行为。"
      )

      SubTitle("五、设置与其他")
      Body(
        "· 下载目录、手动扫描目录改用系统文件夹选择器（SAF），不再手打路径。\n" +
          "· 下载音质默认改为「普通」，下载目录改名「歌曲目录」。\n" +
          "· 主题色默认改为紫色系，深色模式默认跟随系统，淡入淡出默认开启。\n" +
          "· 侧边栏二级菜单：听歌识曲 / 清除缓存 / 设置 / 关于。\n" +
          "· 关于页：软件名改为 aplayer，去掉历史版本、分享、意见与反馈、检查更新。\n" +
          "· 首次启动弹一次改动声明，之后不再弹。\n" +
          "· 移除发评论入口、移除「点即播放」文案、在线与最近播放页不再显示排序按钮。"
      )

      SectionTitle("隐私与数据")
      Body(
        "· 本应用不上传、不收集、不出售任何个人数据，没有账号系统，没有统计 SDK，没有广告 SDK。\n" +
          "· 应用不读取通讯录、短信、位置、相册、剪贴板，也不会在后台偷偷录音。\n" +
          "· 麦克风只在你自己点「听歌识曲」时开启，录满 12 秒生成指纹后立刻释放，录音不会被保存到手机里。\n" +
          "· 登录网易云时的手机号、密码、Cookie 只存在本机私有目录，仅用于向网易云发起你自己的请求，" +
          "不经过任何第三方服务器；密码以 MD5 形式发送，本机不保存明文。\n" +
          "· 唯一的外发流量是你在应用内主动发起的请求：搜索、取流、歌词、评论、收藏数、听歌识曲。" +
          "除此之外不会主动联网上传任何东西。\n" +
          "· 卸载应用或清除数据后，上述本地信息随之删除。"
      )

      SectionTitle("接口来源说明")
      Body(
        "· 听歌识曲：参考开源仓库 $RECOGNIZE_REPO 的 afp.wasm 与指纹编码思路，" +
          "录音、WebView 引擎、请求与结果页为本人自行实现。\n" +
          "· 网易云音乐的全部接口（搜索、歌单、榜单、播放地址、歌词、评论、收藏数、登录、验证码）" +
          "均由本人抓包分析得出，未使用第三方 SDK。\n" +
          "· 酷狗音乐的搜索与取流接口同样由本人抓包分析得出。\n" +
          "· 所有接口均来自各平台公开的网页端 / 客户端行为，仅做协议复现，不涉及破解付费内容。"
      )

      SectionTitle("免责声明")
      Body(
        "1. 本应用为个人学习与技术研究用途的二次修改版本，非官方发布，与原项目作者无关。\n" +
          "2. 应用内所有在线内容（歌曲、歌词、评论、榜单等）版权归各平台及原作者所有，" +
          "本应用不存储、不传播任何音源文件，仅做接口转发与本地播放。\n" +
          "3. 请勿将本应用用于任何商业用途，也不要大规模抓取或分发数据。\n" +
          "4. 使用本应用产生的任何后果由使用者自行承担；若你所在地区或你本人不认可上述行为，请立即停止使用。\n" +
          "5. 若相关平台或权利人认为本应用存在侵权，请联系删除，我会在第一时间下架相关功能。\n" +
          "6. 本应用不收集、不上传任何用户数据；麦克风仅在听歌识曲时临时使用，\n" +
          "   登录凭据仅保存在本机，不会同步到任何服务器。\n" +
          "7. 原项目地址：$UPSTREAM_URL ，请优先支持原作者。"
      )

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Spacer(modifier = Modifier.height(12.dp))
  TextPrimary(
    text = text,
    fontSize = 16.sp,
    fontWeight = FontWeight.Bold,
    maxLine = Int.MAX_VALUE,
    modifier = Modifier.fillMaxWidth()
  )
  Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun SubTitle(text: String) {
  Spacer(modifier = Modifier.height(8.dp))
  TextPrimary(
    text = text,
    fontSize = 14.sp,
    fontWeight = FontWeight.Medium,
    maxLine = Int.MAX_VALUE,
    modifier = Modifier.fillMaxWidth()
  )
}

@Composable
private fun Body(text: String) {
  // 多行正文用固定行高，TextSecondary 的 lineHeight = 1.em 会把长段落挤在一起
  Text(
    text = text,
    modifier = Modifier.fillMaxWidth(),
    style = TextStyle(
      fontSize = 13.sp,
      lineHeight = 20.sp,
      color = LocalTheme.current.textSecondary
    )
  )
}
