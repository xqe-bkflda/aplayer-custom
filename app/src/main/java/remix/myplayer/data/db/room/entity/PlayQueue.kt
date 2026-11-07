package remix.myplayer.data.db.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Created by remix on 2019/1/12
 */
@Entity(indices = [Index(value = ["audio_id"], unique = true)])
data class PlayQueue(
    val audio_id: Long,
    val title: String,
    val data: String
) {
  constructor(audio_id: Long): this(audio_id, "", "")

  @PrimaryKey(autoGenerate = true)
  var id: Int = 0

  var account: String? = null
  var pwd: String? = null

  /**
   * 网易云歌曲 id（在线歌曲才有）。
   * 队列里存下来，冷启动恢复队列后播放页才能按 id 精确认出这首歌，
   * 否则只能靠歌名猜，同名歌会收藏串号。
   */
  var netease_id: Long? = null


  companion object {
    const val TABLE_NAME = "PlayQueue"
  }
}