package remix.myplayer.data.prefs

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 让非 Hilt 注入的 Composable（顶栏按钮这类）也能拿到 NetEasePrefs。
 * Hilt 生成的 EntryPoint 只允许在 @EntryPoint 接口里取，所以单独放一个壳。
 */
object NetEasePrefsHolder {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface Entry {
    fun netEasePrefs(): NetEasePrefs
  }

  @Volatile
  private var cached: NetEasePrefs? = null

  fun get(): NetEasePrefs? = cached

  fun init(context: Context) {
    if (cached != null) return
    synchronized(this) {
      if (cached != null) return
      cached = runCatching {
        EntryPointAccessors.fromApplication(
          context.applicationContext,
          Entry::class.java
        ).netEasePrefs()
      }.getOrNull()
    }
  }
}
