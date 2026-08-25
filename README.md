# 拼音识字乐园

一款面向小学生的识字练习安卓 APP，仅适配安卓手机。

## 玩法

1. **练习**：每轮展示 10 个常用汉字。
2. **作答**：学生先点选汉字的拼音音节，再点选声调。
3. **过关**：本轮 10 题的拼音、声调全部答对后，进入奖励环节。
4. **奖励**：联网加载公开网络资源，播放约 1 分钟的幼儿向奖励短视频。
5. **循环**：视频播放完成后自动开始新一轮 10 个汉字的练习。

## 安装使用

1. 把 `pinyin-app.apk` 传到安卓手机。
2. 安装前按系统提示允许“安装未知来源应用”。
3. 安装完成后打开“拼音识字乐园”即可练习。

## 数据来源

内置 100+ 小学生常用汉字，每个字包含标准拼音音节与声调。若发现读音不严谨，可直接修改 `assets/words.json`。

## 自定义奖励视频

默认会依次尝试以下公开可访问的示例视频：

- `ForBiggerFun.mp4`（约 60 秒，推荐）
- `ForBiggerBlazes.mp4`
- `ForBiggerJoyrides.mp4`
- `BigBuckBunny.mp4`

如想换成更贴合“幼儿向”的视频，打开 `src/com/cuixinyuan/pinyin/MainActivity.java`，修改 `REWARD_VIDEOS` 数组，填入任意公开可访问的 MP4 直链即可。

若手机无网络或所有视频都无法播放，APP 会自动播放内置的本地庆祝动画（`assets/reward.html`）作为兜底，保证孩子始终能获得正向反馈。

## 自行编译

### 环境要求

- Windows（当前仅在此环境验证）
- Git Bash / MinGW
- JDK 17+
- Android SDK（路径 `D:\SOFTWARE\android_sdk`，或设置 `ANDROID_SDK` 环境变量）

### 编译命令

```bash
bash build_apk.sh
```

脚本使用 Android SDK 自带的 `aapt2`、`d8`、`zipalign`、`apksigner` 离线构建，不依赖 Gradle / Android Studio。构建完成后会在项目根目录生成 `pinyin-app.apk`。

## 项目结构

```
.
├── AndroidManifest.xml          # 包名 com.cuixinyuan.pinyin，仅竖屏
├── src/com/cuixinyuan/pinyin/MainActivity.java
├── assets/
│   ├── words.json                # 汉字拼音数据集
│   └── reward.html               # 本地奖励动画
├── res/drawable/ic_launcher.png  # 启动图标
├── build_apk.sh                  # 离线构建脚本
└── pinyin-app.apk                # 构建产物（可直接安装）
```

## 技术要点

- 纯原生 Java 单 Activity 实现，UI 全部用代码构建。
- 无第三方库，除 Android SDK 自带 `org.json` 外无任何依赖。
- 视频奖励使用 `VideoView` 联网播放，失败时退回到本地 `WebView` 动画。
- 适配 `minSdkVersion=21`、`targetSdkVersion=34`。
