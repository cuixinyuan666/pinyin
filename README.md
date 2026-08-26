# 拼音识字乐园

一款面向小学生的识字练习安卓 APP，仅适配安卓手机。

## 玩法

1. **练习**：每轮展示 10 个常用汉字。
2. **作答**：学生需要为每个汉字分别选择正确的 **声母**、**韵母** 和 **声调**。
3. **朗读**：每道题作答结束后，APP 会自动语音朗读该汉字读音；也可随时点击「🔊 听一听」按钮复听。
4. **过关**：本轮 10 题的声母、韵母、声调全部答对后，进入奖励环节。
5. **奖励**：奖励一分钟的「贪吃蛇」小游戏游玩时长。
6. **进度保留**：贪吃蛇的得分会跨轮保留，条件达成后可继续进行游戏。
7. **循环**：游戏时间结束后自动回到答题环节，开始新一轮 10 个汉字的练习。

## 安装使用

1. 把 `pinyin-app.apk` 传到安卓手机。
2. 安装前按系统提示允许“安装未知来源应用”。
3. 安装完成后打开“拼音识字乐园”即可练习。

## 数据来源

内置 100+ 小学生常用汉字，每个字包含标准声母、韵母与声调。若发现读音不严谨，可直接修改 `assets/words.json`。

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
├── src/com/cuixinyuan/pinyin/
│   ├── MainActivity.java        # 识字练习主界面
│   └── SnakeView.java           # 贪吃蛇奖励游戏
├── assets/
│   └── words.json               # 汉字拼音数据集（含 sm/ym/tone）
├── res/drawable/ic_launcher.png # 启动图标
├── build_apk.sh                 # 离线构建脚本
└── pinyin-app.apk               # 构建产物（可直接安装）
```

## 技术要点

- 纯原生 Java 单 Activity 实现，UI 全部用代码构建。
- 无第三方库，除 Android SDK 自带 `org.json` 与 `TextToSpeech` 外无任何依赖。
- 拼音拆分为「声母 / 韵母 / 声调」三行独立选择，帮助学生建立完整拼音概念。
- 每题作答后自动调用系统 TTS（中文）朗读汉字。
- 奖励环节为原生 Canvas 实现的贪吃蛇小游戏，滑动屏幕控制方向，得分通过 `SharedPreferences` 跨轮保留。
- 适配 `minSdkVersion=21`、`targetSdkVersion=34`。
