package remix.myplayer.request.netease

import kotlinx.serialization.Serializable

@Serializable
data class NetEaseSong(
  val id: Long,
  val name: String?,
  // 艺术家
  val ar: List<AR>?,
  // 专辑
  val al: AL?,
  // 时长
  val dt: Long
) {

  @Serializable
  data class AL(
    val id: Long,
    val name: String?,
    val picUrl: String?
  )

  @Serializable
  data class AR(
    val id: Long,
    val name: String?,
//    val alias: List<String>?
  )
}

@Serializable
data class NetEaseAlbum(
  val id: Long,
  val name: String?,
  val picUrl: String?
)

@Serializable
data class NetEaseArtist(
  val id: Long,
  val name: String?,
  val picUrl: String?
)
@Serializable
data class NetEasePlaylistDetail(
  val id: Long,
  val name: String?,
  val coverUrl: String?,
  val trackCount: Int,
  val tracks: List<NetEaseSong> = emptyList(),
  /** 5 = 「我喜欢的音乐」歌单（网易云约定的 specialType） */
  val specialType: Int = 0
)

@Serializable
data class NetEaseComment(
  val commentId: Long = 0L,
  val userName: String = "",
  val avatar: String = "",
  val content: String = "",
  val likedCount: Int = 0,
  val time: Long = 0L,
  val isHot: Boolean = false,
  val replyTo: String = ""
)
