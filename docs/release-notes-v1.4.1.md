## 截图取字 v1.4.1

把「看得见的分词」补回来：v1.4.0 重构成单 TextView 后滚动顺了，但词块边界也没了，看着像一整段。

### 做法

- 渲染时在每个词元之间插入 `U+2009`（窄空格）并给词元加独立底色 → 仍然是一个 TextView，肉眼上看还是一词一块
- 命中测试与高亮用「显示坐标」（含窄空格），复制用「原文坐标」→ 不会把人工插入的窄空格带进剪贴板
- 滚动、惯性、手势判定与 v1.4.0 一致（竖向交给 ScrollView，横向才选区）

### 安装

去 [Releases](https://github.com/Haven-Lv/screenshot-ocr/releases/latest) 下载 `ScreenshotOCR-1.4.1.apk` 覆盖安装。

### 校验

- versionCode 15 / versionName 1.4.1
- SHA-256：`13405f351453577bda7b1b4ddeb332a31e81a22b5736a9fdcffbdf251b355b7d`
