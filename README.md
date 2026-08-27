# 拼音识字乐园

面向小学生的拼音考试练习安卓 APP。

## 玩法（v2.0 考试模式）

1. **直接考试**：打开即进入试题，从 4 个带调拼音选项中选择正确答案（不再分步选声母/韵母/声调）。
2. **首答惩罚**：某字**首次作答错误**后，即使随后选对也不算通过；本轮内须在未来的复习题中**首答正确**才算完成。
3. **错题复习**：答错的字按权重插入额外复习题（**不计入每轮 10 题配额**）；错得越多出现越频繁；答对积累后（如错 5 对 2）自动降频。
4. **统计展示**：每个汉字右上角显示「现 N 错 M」（出现次数 / 答错次数）。
5. **选项打乱**：曾答错过的字，选项顺序随机打乱，防止记位置蒙题。
6. **奖励游戏**：完成一轮（10 题 + 错题复习）后可选：贪吃蛇 / 俄罗斯方块 / 切水果 / **坦克大战**。

## 安装

下载 Release 中的 `pinyin-app.apk` 安装即可。

## 构建

```bash
bash build_apk.sh
```

依赖 [UniPinyin](https://github.com/nillith/UniPinyin) 在构建时生成 `assets/pinyin_speak.json`。

## 项目结构

```
src/com/cuixinyuan/pinyin/
├── MainActivity.java      # 考试主流程、错题调度、游戏入口
├── WordStatsManager.java  # 汉字统计与复习权重
├── PinyinBridge.java      # 拼音朗读例字
├── TankView.java          # 坦克大战（Canvas）
├── SnakeView.java
├── TetrisView.java
└── FruitNinjaView.java
```
