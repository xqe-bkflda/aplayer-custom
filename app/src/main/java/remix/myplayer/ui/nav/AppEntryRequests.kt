package remix.myplayer.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 桌面/外部快捷入口到 Compose 页面的导航请求总线。
 *
 * 用 Compose 的 mutableStateOf 存，AppNav 读它就会被订阅：
 * 无论 Activity 是冷启动还是已在运行（热启动），写入后 AppNav 都会重组消费，
 * 不依赖 intent 传递，也不依赖 HomeScreen 是否在栈顶。
 */
object AppEntryRequests {

  /** 请求进入听歌识曲页 */
  var openRecognize by mutableStateOf(false)
}
