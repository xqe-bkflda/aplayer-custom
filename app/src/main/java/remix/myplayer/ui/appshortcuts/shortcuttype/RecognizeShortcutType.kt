package remix.myplayer.ui.appshortcuts.shortcuttype

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.os.Build
import remix.myplayer.R
import remix.myplayer.ui.appshortcuts.AppShortcutActivity

/**
 * 桌面长按图标菜单里的"听歌识曲"项。
 */
@TargetApi(Build.VERSION_CODES.N_MR1)
class RecognizeShortcutType(context: Context) : BaseShortcutType(context) {

  override val shortcutInfo: ShortcutInfo
    get() = ShortcutInfo.Builder(context, ID_PREFIX + "recognize")
      .setShortLabel(context.getString(R.string.recognize_song))
      .setLongLabel(context.getString(R.string.recognize_song))
      .setIcon(Icon.createWithResource(context, R.drawable.ic_mic_24dp))
      .setIntent(getIntent(AppShortcutActivity.SHORTCUT_TYPE_RECOGNIZE))
      .build()
}