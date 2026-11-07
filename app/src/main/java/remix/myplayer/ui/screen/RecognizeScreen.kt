package remix.myplayer.ui.screen

import android.Manifest
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import remix.myplayer.R
import remix.myplayer.request.netease.NetEaseSong
import remix.myplayer.request.netease.SongRecognizer
import remix.myplayer.service.Command
import remix.myplayer.service.MusicService
import remix.myplayer.service.MusicServiceRemote
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.theme.AppTheme
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.MusicUtil
import remix.myplayer.viewmodel.onlineMusicViewModel

/**
 * 听歌识曲。
 *
 * 录音 12 秒 → WebView 里的 afp.wasm 出指纹 → 网易云 match 接口换歌曲 id。
 * 整个过程不需要登录。
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun RecognizeScreen() {
  val theme = LocalTheme.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val onlineVM = onlineMusicViewModel

  var status by remember { mutableStateOf(RecognizeStatus.Idle) }
  var progress by remember { mutableStateOf(0f) }
  var result by remember { mutableStateOf<List<NetEaseSong>>(emptyList()) }
  var message by remember { mutableStateOf("") }
  // WebView 必须一直挂在视图树里才会跑 JS，所以整页共用一个引擎
  val engine = remember(context) { SongRecognizer.Engine(context) }
  androidx.compose.runtime.DisposableEffect(Unit) {
    onDispose { engine.release() }
  }

  fun fail(text: String) {
    message = text
    status = RecognizeStatus.Failed
  }

  fun start() {
    result = emptyList()
    message = ""
    val activity = context.findActivity()
    if (activity == null) {
      fail("找不到当前界面，请重进一次")
      return
    }
    if (!XXPermissions.isGranted(context, Permission.RECORD_AUDIO)) {
      XXPermissions.with(activity)
        .permission(Permission.RECORD_AUDIO)
        .request { _, allGranted ->
          if (allGranted) start() else fail("需要麦克风权限才能识别")
        }
      return
    }
    status = RecognizeStatus.Recording
    progress = 0f
    scope.launch {
      val pcm = SongRecognizer.record { p -> progress = p }
      if (pcm == null) {
        fail("录音失败，检查麦克风是否被其它应用占用")
        return@launch
      }
      status = RecognizeStatus.Fingerprinting
      val fingerprint = engine.fingerprint(pcm)
      if (fingerprint == null) {
        fail("指纹计算失败，请重试")
        return@launch
      }
      status = RecognizeStatus.Matching
      val songs = SongRecognizer.match(fingerprint)
      if (songs.isEmpty()) {
        fail("没听出来，换个环境再试一次")
        return@launch
      }
      result = songs
      // 记到 knownSongs，播放页才认得出这首的红心/评论
      songs.forEach { runCatching { onlineVM.rememberSong(it) } }
      status = RecognizeStatus.Done
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.systemBars,
    topBar = { CommonAppBar("听歌识曲", actions = emptyList()) },
    containerColor = theme.mainBackground
  ) { padding ->
    Column(
      Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(28.dp))

      when (val s = status) {
        RecognizeStatus.Idle -> RecognizeHint("点下面的按钮，让手机听 12 秒")
        RecognizeStatus.Recording -> RecognizeHint("正在听… ${(progress * 12).toInt()} / 12 秒")
        RecognizeStatus.Fingerprinting -> RecognizeHint("正在分析…")
        RecognizeStatus.Matching -> RecognizeHint("正在匹配…")
        RecognizeStatus.Failed -> RecognizeHint(message.ifBlank { "没听出来" })
        RecognizeStatus.Done -> Unit
      }

      Spacer(Modifier.height(28.dp))

      // 中心麦克风按钮 / 进度环
      Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        val busy = status == RecognizeStatus.Recording ||
            status == RecognizeStatus.Fingerprinting ||
            status == RecognizeStatus.Matching
        val pulse = if (busy) {
          val transition = rememberInfiniteTransition(label = "pulse")
          val scale by transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
              animation = tween(1100, easing = LinearEasing),
              repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
          )
          scale
        } else 1f

        Box(
          Modifier
            .size(150.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(
                listOf(theme.primary, AppTheme.darkenColor(theme.primary))
              )
            )
            .clickable(enabled = !busy) { start() },
          contentAlignment = Alignment.Center
        ) {
          if (busy) {
            if (status == RecognizeStatus.Recording) {
              CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(64.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.28f),
                strokeWidth = 4.dp
              )
            } else {
              CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                color = Color.White,
                strokeWidth = 3.dp
              )
            }
          } else {
            Icon(
              painter = painterResource(R.drawable.ic_mic_24dp),
              contentDescription = "开始识别",
              tint = Color.White,
              modifier = Modifier.size(56.dp)
            )
          }
        }
      }

      Spacer(Modifier.height(36.dp))

      result.forEach { song ->
        RecognizeResultCard(song) {
          onlineVM.playNow(listOf(song), 0) { queue ->
            if (queue.isEmpty()) {
              MessageNotifier.show("这首歌暂时没有可用音源")
              return@playNow
            }
            MusicServiceRemote.setPlayQueue(
              queue,
              MusicUtil.makeCmdIntent(Command.PLAY_AT)
                .putExtra(MusicService.EXTRA_POSITION, 0)
            )
          }
        }
        Spacer(Modifier.height(14.dp))
      }

      if (status == RecognizeStatus.Failed || status == RecognizeStatus.Done) {
        Text(
          text = "再听一次",
          color = theme.primary,
          fontSize = 15.sp,
          modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { status = RecognizeStatus.Idle }
            .padding(horizontal = 22.dp, vertical = 10.dp)
        )
      }

      Spacer(Modifier.weight(1f))

      TextSecondary(
        "若音乐不清晰可能识曲失败\n识别结果来自网易云，不需要登录",
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        maxLine = Int.MAX_VALUE,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
      )
    }

    // 隐藏的引擎载体：1x1 且完全透明，仅用于承载 wasm
    Box(Modifier.size(1.dp)) {
      androidx.compose.ui.viewinterop.AndroidView(
        factory = { engine.createView() },
        modifier = Modifier.size(1.dp)
      )
    }
  }
}

private enum class RecognizeStatus { Idle, Recording, Fingerprinting, Matching, Failed, Done }

@Composable
private fun RecognizeHint(text: String) {
  val theme = LocalTheme.current
  Text(
    text = text,
    color = theme.textSecondary,
    fontSize = 14.sp,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth()
  )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RecognizeResultCard(song: NetEaseSong, onPlay: () -> Unit) {
  val theme = LocalTheme.current
  val cover = song.al?.picUrl
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(theme.dialogBackground)
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      Modifier
        .size(64.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(theme.dialogBackground),
      contentAlignment = Alignment.Center
    ) {
      if (!cover.isNullOrBlank()) {
        GlideImage(
          model = cover,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Icon(
          painter = painterResource(R.drawable.ic_library_music_24dp),
          contentDescription = null,
          tint = theme.textSecondary,
          modifier = Modifier.size(26.dp)
        )
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      TextPrimary(
        song.name ?: "",
        fontSize = 15.sp,
        maxLine = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(Modifier.height(3.dp))
      TextSecondary(
        song.ar?.joinToString("/") { it.name ?: "" }.orEmpty(),
        fontSize = 12.sp,
        maxLine = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Spacer(Modifier.width(8.dp))
    Box(
      Modifier
        .clip(CircleShape)
        .background(theme.primary)
        .clickable(onClick = onPlay)
        .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
      Text("播放", color = Color.White, fontSize = 13.sp)
    }
  }
}

/** Compose 里拿 Activity 的兜底写法（context 可能是 ContextWrapper 套层）。 */
private fun android.content.Context.findActivity(): android.app.Activity? {
  var current: android.content.Context? = this
  while (current is android.content.ContextWrapper) {
    if (current is android.app.Activity) return current
    current = current.baseContext
  }
  return current as? android.app.Activity
}
