package remix.myplayer.data.db.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Created by remix on 2019/1/12
 *
 * data/title/artist/duration/is_remote 是为了让「最近播放」也能记录在线播放的歌曲
 * （在线歌曲没有本地 MediaStore id，只靠 audio_id 查不到）。
 */
@Entity
data class History(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val audio_id: Long,
    val play_count: Int,
    val last_play: Long,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val data: String = "",
    val duration: Long = 0L,
    val is_remote: Boolean = false
)
