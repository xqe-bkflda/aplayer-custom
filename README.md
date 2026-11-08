# aplayer 二改版

> 一个能听网易云的 APlayer。

基于 [rRemix/APlayer](https://github.com/rRemix/APlayer) 2.1.2.1 的个人二改版本。
原项目是一个很好用的本地音乐播放器，但只有本地库和 WebDAV。这个分支把在线音乐接了进来，
顺手修了一批播放页、歌词、列表上的毛病。

**当前版本：`v2.1.2.1-custom`** · [下载 APK](https://github.com/xqe-bkflda/aplayer-custom/releases/latest)

---

## 和原版的区别

### 在线音乐

| 功能 | 说明 |
|:-|:-|
| 网易云全链路 | 搜索（单曲 / 歌单 / 联想词）、歌单、榜单、私人雷达、地址解析、下载写标签、歌词 |
| 三种登录 | 手机验证码、手机号密码、扫码登录 |
| 酷狗音源 | 搜索 + 取流，作为网易云没版权时的补充 |
| 收藏数 | 播放页红心下面显示这首歌被收藏的真实次数 |
| 评论 | 歌曲评论浮层，支持翻页加载 |
| 批量下载 | 曲库「单曲」页和歌单详情页长按进多选，一键下整页 |

### 听歌识曲

外放一段音乐，识别出歌名并直接加入在线曲库播放。

- 用 `afp.wasm` 提取声纹指纹，走网易云 `audio/match` 接口
- 录 12 秒，返回最多 3 个候选
- 桌面长按快捷方式可直达

### 播放与歌词

- 点歌后按**歌单原序**补队列，队列底部会显示「下一曲」提示
- 桌面歌词节流 + 缓存文本测量，长时间挂着不再一卡一卡
- 歌词搜索顺序：本地 → 网易云 → 酷狗 → 内嵌 → 忽略，可自己调
- 修了偶发「暂无歌词」不刷新

### 设置与细节

- 手动扫描、下载目录都改成系统文件夹选择器（SAF）
- 取消收藏可自动删除已下载的文件（默认开启）
- 关于页重做：版本、改动清单、隐私说明、接口来源、免责声明
- 去掉了检查更新、意见反馈和已经失效的 QQ 音乐相关入口
- 数据库升到 v9，播放队列会记住网易云 id

---

## 截图

| | | | |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/1-home.png" width="180" /> | <img src="screenshoots/3-playing-cover.png" width="180" /> | <img src="screenshoots/4-playing-lyric.png" width="180" /> | <img src="screenshoots/5-dark.png" width="180" /> |
| 主页 | 播放（封面） | 播放（歌词） | 深色 |

| | | |
|:-:|:-:|:-:|
| <img src="screenshoots/6-sleep-timer.png" width="180" /> | <img src="screenshoots/7-desktop-lyric-widget.png" width="180" /> | <img src="screenshoots/8-lockscreen.png" width="180" /> |
| 睡眠定时 | 桌面歌词 | 锁屏 |

---

## 安装

从 [Releases](https://github.com/xqe-bkflda/aplayer-custom/releases) 下载 `1-APlayer-*-normal-release.apk` 直接装。

- 需要 **Android 5.0+**（minSdk 21）
- 覆盖安装会自动迁移数据库；签名不一致的话先卸载（只会清掉本地曲库缓存，不删音乐文件）
- 首次进入会弹一个说明，告诉你这版改了什么、数据往哪走

## 构建

```bash
# 需要 JDK 21 + Android SDK（compileSdk 36 / NDK 28）
git clone https://github.com/xqe-bkflda/aplayer-custom.git
cd aplayer-custom

# 子模块（taglib、media3）
git submodule update --init --recursive

# 编译
./gradlew :app:assembleNormalRelease
```

产物在 `app/build/outputs/apk/normal/release/`。

> 自己编译记得改 `app/build.gradle.kts` 里的签名配置，仓库里那个 `Debug.jks` 只用于出测试包。

## 关于接口

网易云和酷狗的接口都是抓包分析出来的，没有使用任何第三方 SDK。

听歌识曲的 `afp.wasm` 和指纹编码思路参考了
[MelodyYuuka/NeteaseCloudMusic-Audio-Recognize](https://github.com/MelodyYuuka/NeteaseCloudMusic-Audio-Recognize)。

**不会上传任何数据。** 登录态只存在本地 SharedPreferences，没有统计、没有埋点、没有崩溃上报。

完整说明见应用内「设置 → 关于 → 声明」。

## 已知限制

- 网易云部分歌曲需要会员，解析不到音源时会提示「可能需要会员」
- 酷狗只做了搜索和取流，没有登录、歌单和评论
- 听歌识曲依赖 WebView 的 WebAssembly，系统 WebView 太旧（Android 5 早期）可能跑不起来
- 高版本 Android 上后台录音需要给「麦克风」权限

## 致谢

原项目作者 [rRemix](https://github.com/rRemix)，以及以下开源项目：

[XXPermissions](https://github.com/getActivity/XXPermissions) ·
[Retrofit](https://github.com/square/retrofit) ·
[OkHttp](https://github.com/square/okhttp) ·
[Glide](https://github.com/bumptech/glide) ·
[Timber](https://github.com/JakeWharton/timber) ·
[LeakCanary](https://github.com/square/leakcanary) ·
[TinyPinyin](https://github.com/promeG/TinyPinyin) ·
[TagLib for Android](https://github.com/rRemix/taglib) ·
[ZXing](https://github.com/zxing/zxing) ·
[media3](https://github.com/androidx/media)

## 许可

[GPL-3.0](LICENSE)，与原项目一致。改了什么、为什么改，随你；改了之后也请继续开源。

---

<sub>个人自用向的修改版，跟网易云音乐、酷狗音乐官方都没有关系。</sub>
