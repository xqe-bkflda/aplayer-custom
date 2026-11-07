package remix.myplayer.ui.nav

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import remix.myplayer.App
import remix.myplayer.util.Util

/**
 * app 在前台时显示 snackbar，否则展示 toast。
 *
 * 节流规则从「1 秒内直接丢弃」改成「1 秒内合并」：
 * 同一时间内连续来的消息不再消失，只保留最后一条，
 * 这样「下载成功 → 标签写入完成」这类连发不会被静默吞掉。
 */
object MessageNotifier {

  private const val MIN_INTERVAL_MS = 1000L

  @Volatile
  private var lastShownAt = 0L

  /** 被节流挡下的最后一条消息，等节流窗口过去再补发 */
  @Volatile
  private var pending: String? = null

  @Volatile
  private var pendingToken = 0L

  private val _messages = MutableSharedFlow<String>(
    replay = 0,
    extraBufferCapacity = 8,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  internal val messages = _messages.asSharedFlow()

  private val mainHandler = Handler(Looper.getMainLooper())

  fun show(message: String) {
    if (message.isBlank()) return
    val now = SystemClock.uptimeMillis()
    if (now - lastShownAt < MIN_INTERVAL_MS) {
      // 窗口内：先记下，等窗口结束再发最新那条
      pending = message
      val token = ++pendingToken
      mainHandler.postDelayed({
        if (token == pendingToken) {
          val msg = pending
          pending = null
          if (msg != null) emit(msg)
        }
      }, MIN_INTERVAL_MS - (now - lastShownAt) + 20)
      return
    }
    emit(message)
  }

  private fun emit(message: String) {
    lastShownAt = SystemClock.uptimeMillis()
    if (Util.isAppOnForeground) {
      _messages.tryEmit(message)
    } else {
      showToast(message)
    }
  }

  fun show(@StringRes resId: Int, vararg formatArgs: Any) {
    val msg = if (formatArgs.isNotEmpty()) {
      App.context.getString(resId, *formatArgs)
    } else {
      App.context.getString(resId)
    }
    show(msg)
  }

  private fun showToast(message: String) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      Toast.makeText(App.context, message, Toast.LENGTH_SHORT).show()
    } else {
      mainHandler.post {
        Toast.makeText(App.context, message, Toast.LENGTH_SHORT).show()
      }
    }
  }
}
