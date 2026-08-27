# 拼音识字乐园

一款面向小学生的识字练习安卓 APP，仅适配安卓手机。

## 玩法

1. **练习**：每轮展示 10 个常用汉字。
2. **作答**：学生需要为每个汉字分别选择正确的 **声母**、**韵母** 和 **声调**。
3. **拼音语音交互（基于 [UniPinyin](https://github.com/nillith/UniPinyin) 权威数据，v1.5.0）**：
   - 声母/韵母朗读例字由 UniPinyin 自动生成（`tools/GeneratePinyinData.java` → `assets/pinyin_speak.json`），不再手写易错的韵母映射表。
   - 三拼音节韵母分段读：花 `ua`→哭(u)+八(a)、窗 `uang`→哭(u)+羊(ang) 等，复韵母 `ui/iu/ie` 保持整体。
   - 整体认读音节（zhi/chi/shi/ri/zi/ci/si/yi/wu/yu 等）整段拼读直接读本字。
   - 声调按钮显示调号符号 **ā / á / ǎ / à**（不使用中文数字或「x声」）。
   - TTS 一律使用汉字例字朗读，避免拉丁字母被读成英文。
4. **复习**：每学完 10 个汉字后触发一轮 10 题检测复习，按艾宾浩斯记忆曲线混入新字与旧字，并在题目上方显示该字已学习次数。
5. **奖励流程**：先完成答题与复习，再弹出小游戏选择界面，可自由选择 **贪吃蛇**、**俄罗斯方块** 或 **切水果**，奖励默认 60 秒游玩时长。
6. **游戏时长设置**：答题界面右上角「⚙ 设置」可自定义每段游戏时长（30 / 60 / 90 / 120 秒），选择后自动保存生效。
7. **进度保留**：三款游戏的得分与进度均通过 `SharedPreferences` 持久化保存，下次奖励时可继续进行游戏。
8. **循环**：游戏时间结束（或点「结束」）后自动回到下一轮答题，开始新一轮练习。

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
- 拼音拆分为「声母 / 韵母 / 声调」三行独立选择；内置声母、韵母标准读法映射表，发音严格区分汉语拼音读音与英文字母读音（这是最高优先级修复项）；声调跟随对应汉字韵母发声。
- 学习流程顺序：**答题 10 字 → 艾宾浩斯复习 10 题 → 游戏选择**。
- 复习模块使用艾宾浩斯权重抽样，优先复习学习次数少、距上次学习较久的汉字。
- 三款奖励游戏均为原生 Canvas 实现，进度通过 `SharedPreferences` 持久化保存；游戏时长可在设置页自定义。
- 适配 `minSdkVersion=21`、`targetSdkVersion=34`。
