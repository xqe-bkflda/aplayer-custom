package remix.myplayer.data.prefs

import android.content.Context
import android.content.res.Configuration
import androidx.core.graphics.toColorInt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePrefs @Inject constructor(
  @param:ApplicationContext val context: Context,
  settingPrefs: SettingPrefs
) :
  AbstractPref(context, name = PrefKeys.Theme.NAME) {

  var primaryColor by PrefsDelegate(sp, PrefKeys.Theme.PRIMARY_COLOR, "#BC88FF".toColorInt())
  var secondaryColor by PrefsDelegate(sp, PrefKeys.Theme.SECONDARY_COLOR, "#7ED100".toColorInt())

  // 从settingPrefs迁移
  var darkTheme by PrefsDelegate(
    sp,
    PrefKeys.Theme.DARK_THEME,
    settingPrefs.sp.getString(PrefKeys.Theme.DARK_THEME, FOLLOW_SYSTEM)!!
  )
  var blackTheme by PrefsDelegate(
    sp,
    PrefKeys.Theme.BLACK_THEME,
    settingPrefs.sp.getBoolean(PrefKeys.Theme.BLACK_THEME, true)
  )

  var coloredNaviBar by PrefsDelegate(sp, PrefKeys.Theme.COLOR_NAVIGATION, false)

  /**
   * 主色调改过一次（蓝 → 金），老用户存的是旧值，光改 default 不会生效。
   * 用版本号做一次性覆盖，之后用户自己改的强调色不会再被冲掉。
   */
  var themeVersion by PrefsDelegate(sp, PrefKeys.Theme.THEME_VERSION, 0)

  fun migratePrimaryColorOnce() {
    if (themeVersion < THEME_CONFIG_VERSION) {
      // 只在用户没手动改过（仍是旧默认蓝）时覆盖
      if (primaryColor == 0xFF698CF6.toInt() || primaryColor == 0xFF8F6E00.toInt()) primaryColor = DEFAULT_PRIMARY
      if (secondaryColor == 0xFF698CF6.toInt() || secondaryColor == 0xFF8F6E00.toInt()) secondaryColor = DEFAULT_SECONDARY
      themeVersion = THEME_CONFIG_VERSION
    }
  }

  fun resolveTheme(
    darkTheme: String,
    blackTheme: Boolean,
    uiMode: Int = context.resources.configuration.uiMode
  ): String {
    return if (darkTheme == ALWAYS_ON || (darkTheme == FOLLOW_SYSTEM && (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)) {
      if (blackTheme) {
        BLACK
      } else {
        DARK
      }
    } else {
      LIGHT
    }
  }

  companion object {

    /** 主题配置版本：改默认主色时递增即可让老配置跟随一次 */
    private const val THEME_CONFIG_VERSION = 2

    const val LIGHT = "light"
    const val DARK = "dark"
    const val BLACK = "black"

    const val ALWAYS_OFF = "always_off"
    const val DEFAULT_PRIMARY = 0xFFBC88FF.toInt()
    const val DEFAULT_SECONDARY = 0xFF7ED100.toInt()

    const val ALWAYS_ON = "always_on"
    const val FOLLOW_SYSTEM = "follow_system"

  }
}
