package remix.myplayer.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import remix.myplayer.data.db.room.dao.HistoryDao
import remix.myplayer.data.db.room.entity.History
import remix.myplayer.data.model.audio.Song
import remix.myplayer.data.prefs.SettingPrefs
import remix.myplayer.data.prefs.historySortOrderFlow
import javax.inject.Inject

interface HistoryRepository {

  fun allHistories(): Flow<List<History>>

  suspend fun update(song: Song, checkDuplicate: Boolean = true): Int

  suspend fun clear()
}

class HistoryRepoImpl @Inject constructor(
  private val historyDao: HistoryDao,
  private val settingPrefs: SettingPrefs
) : HistoryRepository, AbstractRepository(settingPrefs), CoroutineScope by MainScope() {

  override fun allHistories(): Flow<List<History>> {
    return settingPrefs
      .historySortOrderFlow()
      .flatMapLatest { sortOrder ->
        historyDao.selectAll(sortOrder)
      }
  }

  private var lastHistoryAudioId: Long? = null

  override suspend fun update(song: Song, checkDuplicate: Boolean): Int {
    // 在线歌曲的直链每次解析都不同，用 url 哈希当 id 会产生重复记录，
    // 改成「歌名+歌手」派生稳定 id，同一首歌只占一行。
    val audioId = if (song.isRemote()) {
      val h = (song.title + "|" + song.artist).hashCode().toLong()
      val stable = -kotlin.math.abs(h)
      if (stable == 0L) -1L else stable
    } else {
      song.id
    }
    if (checkDuplicate && audioId == lastHistoryAudioId) {
      return 0
    }
    lastHistoryAudioId = audioId

    val currentTime = System.currentTimeMillis()

    // 先判断是否存在
    val existingHistory = historyDao.selectByAudioId(audioId)

    return if (existingHistory != null) {
      // 如果存在则更新 play_count 和 last_play
      val updatedHistory = existingHistory.copy(
        play_count = existingHistory.play_count + 1,
        last_play = currentTime,
        title = song.title,
        artist = song.artist,
        album = song.album,
        data = song.data,
        duration = song.duration,
        is_remote = song.isRemote()
      )
      historyDao.update(updatedHistory)
    } else {
      // 如果不存在，创建新的历史记录
      val newHistory = History(
        id = 0,
        audio_id = audioId,
        play_count = 1,
        last_play = currentTime,
        title = song.title,
        artist = song.artist,
        album = song.album,
        data = song.data,
        duration = song.duration,
        is_remote = song.isRemote()
      )
      historyDao.insertHistory(newHistory).toInt()
    }
  }

  override suspend fun clear() = historyDao.clear()
}