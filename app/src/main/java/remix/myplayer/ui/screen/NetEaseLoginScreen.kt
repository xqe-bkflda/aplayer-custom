package remix.myplayer.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import remix.myplayer.ui.nav.LocalNavController
import remix.myplayer.ui.nav.MessageNotifier
import remix.myplayer.ui.theme.AppTheme
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.viewmodel.onlineMusicViewModel

/**
 * 网易云登录页（独立界面）。
 *
 * 提供三种登录方式：手机验证码 / 手机密码 / 扫码。
 * 已登录时只显示账号信息与退出登录，不再叠一层手机号表单。
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun NetEaseLoginScreen() {
  val vm = onlineMusicViewModel
  val state by vm.uiState.collectAsStateWithLifecycle()
  val nav = LocalNavController.current
  val theme = LocalTheme.current

  // 0 = 验证码，1 = 密码，2 = 扫码
  var mode by remember { mutableIntStateOf(0) }
  var phone by remember { mutableStateOf("") }
  var code by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var countdown by remember { mutableIntStateOf(0) }

  // 扫码状态
  var qrKey by remember { mutableStateOf("") }
  var qrHint by remember { mutableStateOf("正在获取二维码…") }
  var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var qrTicket by remember { mutableIntStateOf(0) }

  LaunchedEffect(countdown) {
    if (countdown > 0) {
      delay(1000)
      countdown -= 1
    }
  }

  // 进入扫码模式（或点刷新）时申请 key 并轮询状态
  LaunchedEffect(mode, qrTicket) {
    if (mode != 2 || state.loggedIn) return@LaunchedEffect
    qrHint = "正在获取二维码…"
    qrBitmap = null
    qrKey = ""
    val key = vm.requestQrKey()
    if (key.isNullOrBlank()) {
      qrHint = "二维码获取失败，点下方重试"
      return@LaunchedEffect
    }
    qrKey = key
    qrBitmap = buildQrBitmap("https://music.163.com/login?codekey=$key")
    qrHint = "请用网易云音乐 App 扫码"
    while (true) {
      delay(2000)
      val st = vm.pollQrLogin(key)
      when (st.code) {
        800 -> {
          qrHint = "二维码已过期，点下方重新获取"
          return@LaunchedEffect
        }

        801 -> qrHint = "请用网易云音乐 App 扫码"
        802 -> qrHint = "已扫码，请在手机上确认登录"
        803 -> {
          qrHint = "登录成功"
          MessageNotifier.show("登录成功")
          nav.popBackStack()
          return@LaunchedEffect
        }

        else -> if (st.message.isNotBlank()) qrHint = st.message
      }
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.systemBars,
    topBar = {
      CommonAppBar(title = if (state.loggedIn) "账号信息" else "网易云登录", actions = {})
    },
    containerColor = theme.mainBackground
  ) { padding ->
    Column(
      Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
      // 已登录：只显示账号信息 + 退出登录
      if (state.loggedIn) {
        Spacer(Modifier.height(24.dp))
        TextPrimary("当前已登录", fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          GlideImage(
            model = state.avatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(CircleShape)
          )
          Spacer(Modifier.width(12.dp))
          Column {
            TextPrimary(state.nickname.ifBlank { "网易云用户" }, fontSize = 16.sp)
            TextSecondary("UID ${if (state.uid > 0L) state.uid else "-"}", fontSize = 12.sp)
          }
        }
        Spacer(Modifier.height(32.dp))
        Box(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.dialogBackground)
            .clickable {
              vm.logout()
              MessageNotifier.show("已退出登录")
            }
            .padding(vertical = 14.dp),
          contentAlignment = Alignment.Center
        ) {
          TextSecondary("退出登录", fontSize = 14.sp)
        }
        state.error?.let {
          Spacer(Modifier.height(16.dp))
          TextSecondary(it, fontSize = 12.sp)
        }
        return@Column
      }

      Spacer(Modifier.height(12.dp))

      // 登录方式切换
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(theme.dialogBackground)
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        listOf("验证码登录", "密码登录", "扫码登录").forEachIndexed { index, title ->
          val selected = mode == index
          Box(
            Modifier
              .weight(1f)
              .clip(RoundedCornerShape(11.dp))
              .background(if (selected) theme.primary else Color.Transparent)
              .clickable { mode = index }
              .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              title,
              color = if (selected) theme.primaryReverse else theme.textSecondary,
              fontSize = 13.sp
            )
          }
        }
      }

      Spacer(Modifier.height(20.dp))

      when (mode) {
        0 -> {
          TextPrimary("手机号登录", fontSize = 20.sp)
          Spacer(Modifier.height(6.dp))
          TextSecondary("登录后可以看自己的歌单和收藏", fontSize = 12.sp)
          Spacer(Modifier.height(24.dp))

          PhoneField(phone, theme) { if (it.length <= 11) phone = it.filter { c -> c.isDigit() } }
          Spacer(Modifier.height(12.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            CodeField(code, theme) { if (it.length <= 6) code = it.filter { c -> c.isDigit() } }
            Spacer(Modifier.width(10.dp))
            val canSend = phone.length >= 11 && countdown == 0
            Box(
              Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (canSend) theme.primary else theme.dialogBackground)
                .clickable(enabled = canSend) {
                  vm.sendCaptcha(phone) { ok ->
                    if (ok) {
                      countdown = 60
                      MessageNotifier.show("验证码已发送")
                    } else {
                      MessageNotifier.show("发送失败，稍后再试")
                    }
                  }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
              Text(
                if (countdown > 0) "${countdown}s" else "获取验证码",
                color = if (canSend) theme.primaryReverse else theme.textSecondary,
                fontSize = 14.sp
              )
            }
          }

          Spacer(Modifier.height(24.dp))
          SubmitButton("登录", enabled = code.isNotBlank(), theme) {
            vm.login(phone, code) { ok ->
              if (ok) {
                MessageNotifier.show("登录成功")
                nav.popBackStack()
              }
            }
          }
        }

        1 -> {
          TextPrimary("手机号密码登录", fontSize = 20.sp)
          Spacer(Modifier.height(6.dp))
          TextSecondary("使用网易云账号密码登录", fontSize = 12.sp)
          Spacer(Modifier.height(24.dp))

          PhoneField(phone, theme) { if (it.length <= 11) phone = it.filter { c -> c.isDigit() } }
          Spacer(Modifier.height(12.dp))

          val pwdFocus = remember { FocusRequester() }
          Box(
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(theme.dialogBackground)
              .clickable { pwdFocus.requestFocus() }
              .padding(horizontal = 16.dp, vertical = 14.dp)
          ) {
            BasicTextField(
              value = password,
              onValueChange = { password = it },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().focusRequester(pwdFocus),
              textStyle = TextStyle(fontSize = 16.sp, color = theme.textPrimary),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              visualTransformation = PasswordVisualTransformation(),
              decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                  if (password.isEmpty()) TextSecondary("密码", fontSize = 16.sp)
                  inner()
                }
              }
            )
          }

          Spacer(Modifier.height(24.dp))
          SubmitButton("登录", enabled = phone.length >= 11 && password.isNotBlank(), theme) {
            vm.loginByPassword(phone, password) { ok ->
              if (ok) {
                MessageNotifier.show("登录成功")
                nav.popBackStack()
              }
            }
          }
        }

        else -> {
          TextPrimary("扫码登录", fontSize = 20.sp)
          Spacer(Modifier.height(6.dp))
          TextSecondary("打开网易云音乐 App，扫一扫即可登录", fontSize = 12.sp)
          Spacer(Modifier.height(24.dp))

          Box(
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(theme.dialogBackground)
              .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              val bmp = qrBitmap
              if (bmp != null) {
                Image(
                  bitmap = bmp.asImageBitmap(),
                  contentDescription = "登录二维码",
                  modifier = Modifier.size(200.dp)
                )
              } else {
                Box(
                  Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.mainBackground),
                  contentAlignment = Alignment.Center
                ) {
                  TextSecondary("…", fontSize = 20.sp)
                }
              }
              Spacer(Modifier.height(16.dp))
              TextSecondary(qrHint, fontSize = 12.sp, maxLine = 3)
              Spacer(Modifier.height(14.dp))
              Box(
                Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(theme.primary)
                  .clickable { qrTicket += 1 }
                  .padding(horizontal = 18.dp, vertical = 9.dp)
              ) {
                Text("刷新二维码", color = theme.primaryReverse, fontSize = 13.sp)
              }
            }
          }
        }
      }

      // 提交后如未成功，展示错误
      state.error?.let {
        Spacer(Modifier.height(16.dp))
        TextSecondary(it, fontSize = 12.sp)
      }
    }
  }
}

@Composable
private fun PhoneField(phone: String, theme: AppTheme, onChange: (String) -> Unit) {
  val focus = remember { FocusRequester() }
  Box(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(theme.dialogBackground)
      .clickable { focus.requestFocus() }
      .padding(horizontal = 16.dp, vertical = 14.dp)
  ) {
    BasicTextField(
      value = phone,
      onValueChange = onChange,
      singleLine = true,
      modifier = Modifier.fillMaxWidth().focusRequester(focus),
      textStyle = TextStyle(fontSize = 16.sp, color = theme.textPrimary),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      decorationBox = { inner ->
        Box(Modifier.fillMaxWidth()) {
          if (phone.isEmpty()) TextSecondary("手机号", fontSize = 16.sp)
          inner()
        }
      }
    )
  }
}

@Composable
private fun RowScope.CodeField(
  code: String,
  theme: AppTheme,
  onChange: (String) -> Unit
) {
  val focus = remember { FocusRequester() }
  Box(
    Modifier
      .weight(1f)
      .clip(RoundedCornerShape(14.dp))
      .background(theme.dialogBackground)
      .clickable { focus.requestFocus() }
      .padding(horizontal = 16.dp, vertical = 14.dp)
  ) {
    BasicTextField(
      value = code,
      onValueChange = onChange,
      singleLine = true,
      modifier = Modifier.fillMaxWidth().focusRequester(focus),
      textStyle = TextStyle(fontSize = 16.sp, color = theme.textPrimary),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      decorationBox = { inner ->
        Box(Modifier.fillMaxWidth()) {
          if (code.isEmpty()) TextSecondary("验证码", fontSize = 16.sp)
          inner()
        }
      }
    )
  }
}

@Composable
private fun SubmitButton(
  text: String,
  enabled: Boolean,
  theme: AppTheme,
  onClick: () -> Unit
) {
  Box(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(if (enabled) theme.primary else theme.dialogBackground)
      .clickable(enabled = enabled, onClick = onClick)
      .padding(vertical = 15.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text,
      color = if (enabled) theme.primaryReverse else theme.textSecondary,
      fontSize = 16.sp
    )
  }
}

/** 用 zxing 把文本编成二维码位图；失败返回 null，界面显示占位 */
private fun buildQrBitmap(content: String, size: Int = 512): Bitmap? {
  return runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
      val offset = y * w
      for (x in 0 until w) {
        pixels[offset + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
      }
    }
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
      setPixels(pixels, 0, w, 0, 0, w, h)
    }
  }.getOrNull()
}
