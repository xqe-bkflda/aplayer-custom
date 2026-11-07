package remix.myplayer.ui.screen.playing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioManager.STREAM_MUSIC
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import remix.myplayer.R
import remix.myplayer.data.prefs.SettingPrefs.Companion.BOTTOM_SHOW_NONE
import remix.myplayer.service.playback.PlaybackUiState
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.widget.common.LineSlider
import remix.myplayer.ui.widget.common.defaultLineSliderProperties
import remix.myplayer.util.ext.CenterInBox
import remix.myplayer.util.ext.clickWithRipple
import remix.myplayer.util.ext.clickableWithoutRipple

private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
private const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"

@Composable
internal fun PlayingUtilityBar(
  modifier: Modifier,
  playingScreenBottom: Int,
  musicState: PlaybackUiState,
  swatch: Palette.Swatch
) {
  Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
    val swatchColor = Color(swatch.rgb)
    assert(playingScreenBottom != BOTTOM_SHOW_NONE)

    // 底部栏现在只有音量条这一个功能；走到这里说明用户开了底部栏，
    // 因此初始必定显示音量（旧配置里存的 BOTTOM_SHOW_NEXT=0 也当显示处理）。
    var volumeIsVisible by remember {
      mutableStateOf(true)
    }

    var refreshKey by remember(playingScreenBottom) {
      mutableIntStateOf(0)
    }

    AnimatedVisibility(volumeIsVisible, enter = fadeIn(), exit = fadeOut()) {
      VolumeSeekbar(swatchColor) {
        refreshKey++
      }
    }

    LaunchedEffect(refreshKey, playingScreenBottom) {
      // 「点击切换」模式下，3 秒没操作就收起音量条
      if (refreshKey > 0) {
        delay(3000)
        volumeIsVisible = false
      }
    }
  }
}

@Composable
private fun VolumeSeekbar(swatchColor: Color, onValueChange: () -> Unit) {
  val context = LocalContext.current
  val audioManager = remember {
    context.getSystemService(AUDIO_SERVICE) as AudioManager
  }
  Row(
    horizontalArrangement = Arrangement.spacedBy(40.dp),
    modifier = Modifier.padding(horizontal = 18.dp)
  ) {
    CenterInBox(
      Modifier
        .size(48.dp)
        .clickWithRipple {
          audioManager.adjustStreamVolume(
            STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_PLAY_SOUND
          )
          onValueChange()
        }) {
      Image(
        painter = painterResource(R.drawable.ic_volume_down_black_24dp),
        contentDescription = "PlayingBottomBarVolumeDown",
        colorFilter = ColorFilter.tint(swatchColor.copy(0.5f))
      )
    }

    VolumeSeekBar(audioManager, swatchColor, onValueChange)

    CenterInBox(
      Modifier
        .size(48.dp)
        .clickWithRipple {
          audioManager.adjustStreamVolume(
            STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_PLAY_SOUND
          )
          onValueChange()
        }) {
      Image(
        painter = painterResource(R.drawable.ic_volume_up_black_24dp),
        contentDescription = "PlayingBottomBarVolumeUp",
        colorFilter = ColorFilter.tint(swatchColor.copy(0.5f))
      )
    }
  }
}

@Composable
private fun RowScope.VolumeSeekBar(
  audioManager: AudioManager,
  swatchColor: Color,
  onValueChange: () -> Unit
) {
  val context = LocalContext.current
  var min by remember {
    mutableIntStateOf(0)
  }
  var max by remember {
    mutableIntStateOf(1)
  }
  var current by remember {
    mutableIntStateOf(0)
  }
  var isSliderDragging by remember {
    mutableStateOf(false)
  }

  LineSlider(
    value = current.toFloat(),
    valueRange = min.toFloat()..max.toFloat(),
    steps = (max - min - 1).coerceAtLeast(0),
    onValueChange = {
      isSliderDragging = true
      val volume = it.roundToInt().coerceIn(min, max)
      if (volume != current) {
        current = volume
        audioManager.setStreamVolume(
          STREAM_MUSIC,
          current,
          0
        )
      }
      onValueChange()
    },
    onValueChangeFinished = {
      isSliderDragging = false
      current = audioManager.getStreamVolume(STREAM_MUSIC).coerceIn(min, max)
    },
    modifier = Modifier
      .height(48.dp)
      .weight(1f),
    properties = defaultLineSliderProperties.copy(
      trackHeight = 2.dp,
      trackBackgroundColor = playingTrackBackgroundColor,
      trackProgressColor = swatchColor,
      thumbColor = swatchColor,
      thumbWidth = 2.dp,
      thumbHeight = 6.dp,
      thumbShape = RectangleShape
    )
  )

  LaunchedEffect(Unit) {
    min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      audioManager.getStreamMinVolume(STREAM_MUSIC)
    } else 0
    max = audioManager.getStreamMaxVolume(STREAM_MUSIC)
    current = audioManager.getStreamVolume(STREAM_MUSIC).coerceIn(min, max)
  }

  DisposableEffect(audioManager, context, min, max) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_VOLUME_CHANGED) return
        if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) != STREAM_MUSIC) return
        if (isSliderDragging) return

        val volume = intent.getIntExtra(
          EXTRA_VOLUME_STREAM_VALUE,
          audioManager.getStreamVolume(STREAM_MUSIC)
        )
        current = volume.coerceIn(min, max)
      }
    }
    val filter = IntentFilter(ACTION_VOLUME_CHANGED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      context.registerReceiver(receiver, filter)
    }

    onDispose {
      runCatching {
        context.unregisterReceiver(receiver)
      }
    }
  }
}
