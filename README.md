# 拼音识字乐园

一款面向小学生的识字练习安卓 APP，仅适配安卓手机。

## 玩法

1. **练习**：每轮展示 10 个常用汉字。
2. **作答**：学生需要为每个汉字分别选择正确的 **声母**、**韵母** 和 **声调**。
3. **拼音交互朗读**：
   - 点击任一拼音字母（声母 / 韵母 / 声调）即朗读该字母；声调跟随当前汉字的韵母发声（如「少」的第三声读作「ǎo」，不再固定读「啊」）。
   - 每道题完整作答后，APP 自动整段拼读：**声母 → 带调韵母 → 汉字**（如 sh → ǎo → 少）。
4. **复习**：每学完 10 个汉字后触发一轮 10 题检测复习，按艾宾浩斯记忆曲线混入新字与旧字，并在题目上方显示该字已学习次数。
5. **奖励**：每完成 10 道题目后，弹出小游戏选择界面，可自由选择 **贪吃蛇**、**俄罗斯方块** 或 **切水果**，奖励 60 秒游玩时长。
6. **进度保留**：三款游戏的得分与进度均通过 `SharedPreferences` 持久化保存，下次奖励时可继续进行游戏。
7. **循环**：游戏时间结束后自动回到答题 / 复习环节，开始新一轮练习。

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
│   ├── MainActivity.java        # 识字练习 / 复习 / 游戏选择主界面
│   ├── SnakeView.java           # 贪吃蛇奖励游戏
│   ├── TetrisView.java          # 俄罗斯方块奖励游戏
│   └── FruitNinjaView.java      # 切水果奖励游戏
├── assets/
│   └── words.json               # 汉字拼音数据集（含 sm/ym/tone）
├── res/drawable/ic_launcher.png # 启动图标
├── build_apk.sh                 # 离线构建脚本
└── pinyin-app.apk               # 构建产物（可直接安装）
```

## 技术要点

- 纯原生 Java 单 Activity 实现，UI 全部用代码构建。
- 无第三方库，除 Android SDK 自带 `org.json` 与 `TextToSpeech` 外无任何依赖。
- 拼音拆分为「声母 / 韵母 / 声调」三行独立选择；声调不再固定使用「啊」，而是跟随对应汉字韵母发声。
- 复习模块使用艾宾浩斯权重抽样，优先复习学习次数少、距上次学习较久的汉字。
- 三款奖励游戏均为原生 Canvas 实现，进度通过 `SharedPreferences` 持久化保存。
- 适配 `minSdkVersion=21`、`targetSdkVersion=34`。
