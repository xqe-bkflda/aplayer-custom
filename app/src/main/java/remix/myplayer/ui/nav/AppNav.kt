package remix.myplayer.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.toRoute
import androidx.savedstate.SavedState
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import remix.myplayer.data.db.room.entity.PlayList
import remix.myplayer.data.db.room.entity.Smb
import remix.myplayer.data.db.room.entity.WebDav
import remix.myplayer.data.model.audio.APlayerModel
import remix.myplayer.data.model.audio.Album
import remix.myplayer.data.model.audio.Artist
import remix.myplayer.data.model.audio.Folder
import remix.myplayer.data.model.audio.Genre
import remix.myplayer.misc.cache.DiskCache
import remix.myplayer.ui.AppScaffold
import remix.myplayer.ui.dialog.DialogContainer
import remix.myplayer.ui.screen.AboutScreen
import remix.myplayer.ui.screen.DeclarationScreen
import remix.myplayer.ui.screen.CustomSortScreen
import remix.myplayer.ui.screen.EQScreen
import remix.myplayer.ui.screen.LastAddedScreen
import remix.myplayer.ui.screen.SearchScreen
import remix.myplayer.ui.screen.KuGouRankScreen
import remix.myplayer.ui.screen.NetEaseLoginScreen
import remix.myplayer.ui.screen.NetEasePlaylistScreen
import remix.myplayer.ui.screen.OnlineMusicScreen
import remix.myplayer.ui.screen.RecognizeScreen
import remix.myplayer.ui.screen.SongChooserScreen
import remix.myplayer.ui.screen.SupportScreen
import remix.myplayer.ui.screen.TagEditScreen
import remix.myplayer.ui.screen.crop.CropScreen
import remix.myplayer.ui.screen.detail.DetailScreen
import remix.myplayer.ui.screen.history.HistoryScreen
import remix.myplayer.ui.screen.home.HomeScreen
import remix.myplayer.ui.screen.setting.SettingDetailScreen
import remix.myplayer.ui.screen.setting.SettingScreen
import remix.myplayer.ui.screen.setting.ReplayGainSettingScreen
import remix.myplayer.ui.screen.smb.SmbDetailScreen
import remix.myplayer.ui.screen.webdav.WebDavDetailScreen
import remix.myplayer.util.Constants
import remix.myplayer.viewmodel.libraryViewModel
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

const val RouteHome = "home"
const val RouteSetting = "setting"
const val RouteSettingDetail = "setting_detail"
const val RouteReplayGain = "replay_gain"
const val RouteSongChoose = "song_choose"
const val RouteAbout = "about"
const val RouteDeclaration = "declaration"
const val RouteCustomSort = "custom_sort"
const val RouteLastAdded = "last_added"
const val RouteHistory = "history"
const val RouteSearch = "search"
const val RouteTagEdit = "tag_edit"
const val RouteCustomCoverCrop = "custom_cover_crop"
const val RouteTagEditCrop = "tag_edit_crop"
const val RouteEq = "eq"
const val RouteSupport = "support"
const val RouteOnlineMusic = "online_music"
const val RouteNetEaseLogin = "netease_login"
const val RouteKuGouRank = "kgrank"
const val RouteNetEasePlaylist = "netease_playlist"
const val RouteNetEaseRadar = "netease_radar"
const val RouteNetEaseLiked = "netease_liked"
const val RouteRecognize = "recognize"

// 存在目标页 NavBackStackEntry.savedStateHandle 里的标志，
// 标记该页是从播放页浮窗跳转过来的，退出时需要恢复浮窗
const val ExtraRestorePlayingScreen = "restore_playing_screen"

val playingScreenDeepLink = "aplayer://playingScreen".toUri()

@Composable
fun AppNav() {
  val snackBarHostState = remember { SnackbarHostState() }
  ProvideSnackBarHostState(snackBarHostState) {
    Box(modifier = Modifier.fillMaxSize()) {
      AppScaffold {
        Box(modifier = Modifier.fillMaxSize()) {
          DialogContainer()

          // 桌面长按「听歌识曲」：AppNav 常驻组合，冷/热启动都能消费这个请求
          val appNavController = LocalNavController.current
          val recognizeRequested = AppEntryRequests.openRecognize
          LaunchedEffect(recognizeRequested) {
            if (recognizeRequested) {
              AppEntryRequests.openRecognize = false
              runCatching {
                appNavController.navigate(RouteRecognize) { launchSingleTop = true }
              }
            }
          }

          NavHost(LocalNavController.current, startDestination = RouteHome) {
            normalAnimatedScreen(
              RouteHome,
            ) {
              HomeScreen()
            }

            normalAnimatedScreen(RouteSetting) {
              SettingScreen()
            }

            normalAnimatedScreen(
              "$RouteSettingDetail/{category}",
              arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) {
              val category = it.arguments?.getString("category") ?: return@normalAnimatedScreen
              SettingDetailScreen(category)
            }

            normalAnimatedScreen(RouteReplayGain) {
              ReplayGainSettingScreen()
            }

            normalAnimatedScreen(
              "${RouteSongChoose}/{id}/{name}", arguments = listOf(navArgument("id") {
                type = NavType.LongType
              })
            ) {
              val id = it.arguments?.getLong("id") ?: return@normalAnimatedScreen
              val name = Uri.decode(it.arguments?.getString("name") ?: return@normalAnimatedScreen)
              SongChooserScreen(id, name)
            }

            normalAnimatedScreen(RouteAbout) {
              AboutScreen()
            }

            normalAnimatedScreen(RouteDeclaration) {
              DeclarationScreen()
            }

            composable<DetailScreenRoute>(
              typeMap = mapOf(
                typeOf<Album?>() to ModelRouteType.album,
                typeOf<Artist?>() to ModelRouteType.artist,
                typeOf<Genre?>() to ModelRouteType.genre,
                typeOf<PlayList?>() to ModelRouteType.playList,
                typeOf<Folder?>() to ModelRouteType.folder,
              ),
              enterTransition = enterTransition(),
              exitTransition = exitTransition(),
              popEnterTransition = popEnterTransition(),
              popExitTransition = popExitTransition(),
            ) {
              val route = it.toRoute<DetailScreenRoute>()

              DetailScreen(route.findNotNull(), openFromLibrary = route.openFromLibrary)
            }

            normalAnimatedScreen("${RouteCustomSort}/{id}", arguments = listOf(navArgument("id") {
              type = NavType.LongType
            })) {
              val id = it.arguments?.getLong("id") ?: return@normalAnimatedScreen
              CustomSortScreen(id)
            }

            normalAnimatedScreen(RouteLastAdded) {
              LastAddedScreen()
            }

            normalAnimatedScreen(RouteHistory) {
              HistoryScreen()
            }

            normalAnimatedScreen(RouteSearch) {
              SearchScreen()
            }

            normalAnimatedScreen(RouteTagEdit) {
              TagEditScreen(it)
            }

            composable<WebDav>(
              enterTransition = enterTransition(),
              exitTransition = exitTransition(),
              popEnterTransition = popEnterTransition(),
              popExitTransition = popExitTransition(),
            ) {
              val webDav = it.toRoute<WebDav>()
              WebDavDetailScreen(webDav)
            }

            composable<Smb>(
              enterTransition = enterTransition(),
              exitTransition = exitTransition(),
              popEnterTransition = popEnterTransition(),
              popExitTransition = popExitTransition(),
            ) {
              val smb = it.toRoute<Smb>()
              SmbDetailScreen(smb)
            }

            normalAnimatedScreen(
              "${RouteCustomCoverCrop}/{id}/{type}",
              arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("type") { type = NavType.IntType })
            ) {
              val id = it.arguments?.getLong("id") ?: return@normalAnimatedScreen
              val type = it.arguments?.getInt("type") ?: return@normalAnimatedScreen
              val context = LocalContext.current
              val nav = LocalNavController.current
              val libraryVM = libraryViewModel

              val destinationUri = remember(id, type) {
                val cacheDir = DiskCache.getDiskCacheDir(context, "thumbnail")
                if (!cacheDir.exists() && !cacheDir.mkdir()) {
                  Uri.EMPTY
                } else {
                  val file = File(cacheDir, "$type-${id}.jpg")
                  Uri.fromFile(file)
                }
              }

              CropScreen(destinationUri = destinationUri, onCropSuccess = {
                libraryVM.fetchMedia(
                  clear = true,
                  updateAlbumVersion = type == Constants.ALBUM,
                  updateArtistVersion = type == Constants.ARTIST,
                  updatePlayListVersion = type == Constants.PLAYLIST,
                )
                nav.popBackStack()
              }, onCancel = {
                nav.popBackStack()
              })
            }

            composable(
              "$RouteTagEditCrop/{uri}",
              arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) {
              val uriString = it.arguments?.getString("uri") ?: return@composable
              val destinationUri = Uri.decode(uriString).toUri()
              val nav = LocalNavController.current

              CropScreen(destinationUri = destinationUri, onCropSuccess = {
                nav.previousBackStackEntry?.savedStateHandle?.set(
                  "song_crop_result", System.currentTimeMillis()
                )
                nav.popBackStack()
              }, onCancel = {
                nav.popBackStack()
              })
            }

            normalAnimatedScreen(RouteEq) {
              EQScreen(it)
            }

            normalAnimatedScreen(RouteOnlineMusic) {
              OnlineMusicScreen()
            }

            normalAnimatedScreen(RouteNetEaseLogin) {
              NetEaseLoginScreen()
            }

            normalAnimatedScreen(
              "$RouteKuGouRank/{id}/{name}",
              arguments = listOf(
                navArgument("id") { type = NavType.IntType },
                navArgument("name") { type = NavType.StringType }
              )
            ) {
              val id = it.arguments?.getInt("id") ?: return@normalAnimatedScreen
              val name = Uri.decode(it.arguments?.getString("name") ?: return@normalAnimatedScreen)
              KuGouRankScreen(id, name)
            }

            // 网易云歌单详情（独立页面，不再嵌在搜索页里）
            normalAnimatedScreen(
              "$RouteNetEasePlaylist/{id}/{name}",
              arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType }
              )
            ) {
              val id = it.arguments?.getLong("id") ?: return@normalAnimatedScreen
              val name = Uri.decode(it.arguments?.getString("name") ?: return@normalAnimatedScreen)
              NetEasePlaylistScreen(playlistId = id, title = name)
            }

            // 雷达 / 榜单详情
            normalAnimatedScreen(
              "$RouteNetEaseRadar/{id}/{name}",
              arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType }
              )
            ) {
              val id = it.arguments?.getLong("id") ?: return@normalAnimatedScreen
              val name = Uri.decode(it.arguments?.getString("name") ?: return@normalAnimatedScreen)
              NetEasePlaylistScreen(playlistId = id, title = name, isRadar = true)
            }

            // 我喜欢的音乐
            normalAnimatedScreen(RouteNetEaseLiked) {
              NetEasePlaylistScreen(
                playlistId = 0L,
                title = "我喜欢的音乐",
                likedOnly = true
              )
            }

            normalAnimatedScreen(RouteRecognize) {
              RecognizeScreen()
            }

            normalAnimatedScreen(RouteSupport) {
              SupportScreen()
            }
          }
        }
      }

      SnackbarHost(
        hostState = snackBarHostState,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues())
      )
    }

    LaunchedEffect(Unit) {
      MessageNotifier.messages.collect {
        snackBarHostState.currentSnackbarData?.dismiss()
        snackBarHostState.showSnackbar(it)
      }
    }
  }
}

private fun NavGraphBuilder.normalAnimatedScreen(
  route: String,
  arguments: List<NamedNavArgument> = emptyList(),
  deepLinks: List<NavDeepLink> = emptyList(),
  content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
  composable(
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    enterTransition = enterTransition(),
    exitTransition = exitTransition(),
    popEnterTransition = popEnterTransition(),
    popExitTransition = popExitTransition(),
    content = content
  )
}

@Serializable
data class DetailScreenRoute(
  val album: Album? = null,
  val artist: Artist? = null,
  val genre: Genre? = null,
  val playList: PlayList? = null,
  val folder: Folder? = null,
  val openFromLibrary: Boolean = false
) {

  fun findNotNull(): APlayerModel {
    return when {
      album != null -> album
      artist != null -> artist
      genre != null -> genre
      playList != null -> playList
      folder != null -> folder
      else -> error("valid model not found")
    }
  }

}

private object ModelRouteType {

  val album = RouteType(Album::class)
  val artist = RouteType(Artist::class)
  val genre = RouteType(Genre::class)
  val playList = RouteType(PlayList::class)
  val folder = RouteType(Folder::class)

  @OptIn(InternalSerializationApi::class)
  class RouteType<T : APlayerModel>(private val kClass: KClass<T>) : NavType<T?>(true) {

    override fun put(bundle: SavedState, key: String, value: T?) {
      if (value != null) {
        bundle.putString(key, Json.encodeToString(kClass.serializer(), value))
      }
    }

    override fun get(bundle: SavedState, key: String): T? {
      return Json.decodeFromString(kClass.serializer(), bundle.getString(key) ?: return null)
    }

    override fun parseValue(value: String): T? {
      if (value.isEmpty()) {
        return null
      }
      return Json.decodeFromString(kClass.serializer(), Uri.decode(value))
    }

    override fun serializeAsValue(value: T?): String {
      if (value == null) {
        return Uri.EMPTY.toString()
      }
      return Uri.encode(Json.encodeToString(kClass.serializer(), value))
    }

  }

}
