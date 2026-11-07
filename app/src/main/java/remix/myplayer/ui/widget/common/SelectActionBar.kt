package remix.myplayer.ui.widget.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import remix.myplayer.R
import remix.myplayer.ui.theme.LocalTheme
import androidx.compose.material3.Text

/**
 * 列表多选时顶部的操作条：已选数量 + 全选 / 下载 / 取消。
 *
 * 搜索页有自己的一套长按菜单，不用这个；只有曲库里的网易云单曲与各歌单详情用。
 */
@Composable
fun SelectActionBar(
  selectedCount: Int,
  busy: Boolean,
  progress: String,
  onSelectAll: () -> Unit,
  onDownload: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  val theme = LocalTheme.current
  Row(
    modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      "已选 $selectedCount 首",
      color = theme.textPrimary,
      fontSize = 14.sp,
      modifier = Modifier.weight(1f)
    )
    Text(
      "全选",
      color = theme.primary,
      fontSize = 13.sp,
      modifier = Modifier
        .clip(RoundedCornerShape(14.dp))
        .clickable(enabled = !busy, onClick = onSelectAll)
        .padding(horizontal = 10.dp, vertical = 6.dp)
    )
    Spacer(Modifier.width(4.dp))
    Box(
      Modifier
        .clip(RoundedCornerShape(14.dp))
        .background(if (selectedCount == 0 || busy) theme.dialogBackground else theme.primary)
        .clickable(enabled = selectedCount > 0 && !busy, onClick = onDownload)
        .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
      Text(
        if (busy && progress.isNotEmpty()) "下载中 $progress" else "下载",
        color = if (selectedCount == 0 || busy) theme.textSecondary else theme.primaryReverse,
        fontSize = 13.sp
      )
    }
    Spacer(Modifier.width(4.dp))
    Text(
      "取消",
      color = theme.textSecondary,
      fontSize = 13.sp,
      modifier = Modifier
        .clip(RoundedCornerShape(14.dp))
        .clickable(enabled = !busy, onClick = onCancel)
        .padding(horizontal = 10.dp, vertical = 6.dp)
    )
  }
}

/** 多选勾选圈 */
@Composable
fun SelectDot(selected: Boolean) {
  val theme = LocalTheme.current
  Box(
    Modifier
      .size(22.dp)
      .clip(CircleShape)
      .background(if (selected) theme.primary else androidx.compose.ui.graphics.Color.Transparent),
    contentAlignment = Alignment.Center
  ) {
    if (selected) {
      Icon(
        painter = painterResource(R.drawable.ic_check_white_24dp),
        contentDescription = null,
        tint = theme.primaryReverse,
        modifier = Modifier.size(14.dp)
      )
    } else {
      Box(
        Modifier
          .size(20.dp)
          .border(1.dp, theme.textSecondary.copy(alpha = 0.6f), CircleShape)
      )
    }
  }
}
