## 截图取字 v1.4.0

分词视图重写：**不再用几百个子控件 + 手写滚动**，改成「一个 TextView + Span」，滚动交还系统原生。顺手把首页记录收成 3 行。

### 之前为什么卡

- 每个词块都是独立 `TextView`（长文 200~300 个），外层 `ScrollView` 里靠 `scrollBy()` 逐帧挪动
- 没有惯性/滑动动画，速度被触摸事件频率卡死 → "快不起来"
- 每次 MOVE 都重新 `repaint()` 全部词块背景 → 掉帧、拖影
- 手指一抖就被判成"选择"，或者滑动被拦下来

### 现在的做法

- `TokenTextView`：整段文本一次布局（`SpannableString`），词元只用 `int[]` 记录字符区间
- 滚动完全交给 `ScrollView` 原生（`OverScroller` + 硬件加速），带惯性、跟手
- 轻点：`getOffsetForPosition()` → 二分找词元 → 复制该词
- 横向拖动：按字符区间加 `BackgroundColorSpan`/`ForegroundColorSpan` 高亮
- 竖向拖动：父 `ScrollView` 接管并给本控件发 `ACTION_CANCEL` → 只清高亮，**不复制**

### 首页

- 只保留最近 3 条，每条一行：`时间  前 14 字…  (N 字)`
- 点任意一条进入同一个分词视图（历史模式，"重新识别"会提示没有原图）

### 安装

去 [Releases](https://github.com/Haven-Lv/screenshot-ocr/releases/latest) 下载 `ScreenshotOCR-1.4.0.apk` 覆盖安装。

### 校验

- versionCode 14 / versionName 1.4.0
- SHA-256：`bda495dac45bcb3a2dfffd41f42fc2e0334fc7e35345b49ee720148f4a5daca0`
