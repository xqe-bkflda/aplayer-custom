package remix.myplayer.ui.widget.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import remix.myplayer.R
import remix.myplayer.helper.SleepTimer
import remix.myplayer.ui.nav.LocalNavController
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.viewmodel.timerViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonAppBar(
  title: String?,
  showBack: Boolean = true,
  onBack: (() -> Unit)? = null,
  actions: @Composable () -> Unit
) {
  val navController = LocalNavController.current

  TopAppBar(
    windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    title = {
      if (!title.isNullOrEmpty()) {
        Text(
          title,
          color = Color.White,
          fontSize = 18.sp,
          modifier = Modifier.padding(start = 16.dp)
        )
      }
    },
    modifier = Modifier,
    navigationIcon = {
      if (showBack) {
        IconButton(onClick = {
          if (onBack != null) {
            onBack.invoke()
          } else {
            navController.popBackStack()
          }
        }) {
          Icon(
            painter = painterResource(R.drawable.ic_arrow_back_white_24dp),
            contentDescription = "Back"
          )
        }
      }
    },
    actions = {
      actions()
    },
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = LocalTheme.current.primary,
      scrolledContainerColor = LocalTheme.current.primary,
      navigationIconContentColor = Color.White,
      actionIconContentColor = Color.White,
    ),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonAppBar(
  title: String,
  showBack: Boolean = true,
  onBack: (() -> Unit)? = null,
  actions: List<AppBarAction> = defaultAppBarActions
) {
  CommonAppBar(
    title,
    showBack,
    onBack,
    actions = {
      actions.map {
        IconButton(onClick = it.action) {
          Icon(
            painter = painterResource(it.icon),
            contentDescription = it.contentDescription
          )
        }
      }
    },
  )
}

val defaultAppBarActions: List<AppBarAction>
  @Composable
  get() {
    val timerVM = timerViewModel
    val timerRunning by SleepTimer.runningState.collectAsStateWithLifecycle()
    val timerIcon = if (timerRunning) R.drawable.ic_timer_on_24dp else R.drawable.ic_timer_white_24dp

    // 设置里可以关掉「伴随音乐入睡」按钮，默认关闭。
    // 直接读 SharedPreferences 不会触发重组，这里挂监听把值转成 Compose state，
    // 这样在设置页开关一改，回到主界面顶栏立刻生效。
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember(ctx) {
      remix.myplayer.data.prefs.NetEasePrefsHolder.init(ctx)
      remix.myplayer.data.prefs.NetEasePrefsHolder.get() ?: remix.myplayer.data.prefs.NetEasePrefs(ctx)
    }
    var show by androidx.compose.runtime.remember(prefs) {
      androidx.compose.runtime.mutableStateOf(prefs.showSleepTimerButton)
    }
    androidx.compose.runtime.DisposableEffect(prefs) {
      val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == remix.myplayer.data.prefs.NetEasePrefs.KEY_SHOW_TIMER) {
          show = prefs.showSleepTimerButton
        }
      }
      prefs.sp.registerOnSharedPreferenceChangeListener(listener)
      // 注册瞬间补一次，避免注册前已经改过
      show = prefs.showSleepTimerButton
      onDispose { prefs.sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    if (!show) return emptyList()
    return listOf(
      AppBarAction(timerIcon, "伴随音乐入睡") {
        timerVM.showTimerDialog()
      }
    )
  }


class AppBarAction(
  val icon: Int,
  val contentDescription: String? = null,
  val action: () -> Unit
)
