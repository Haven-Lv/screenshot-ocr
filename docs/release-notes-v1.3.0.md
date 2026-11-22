## 截图取字 v1.3.0

结果页收成一屏：**去掉可编辑文本框，整屏只保留分词区**，并且把"想滑动却误复制"的手势冲突彻底解决。

### 改动

- 删除顶部「识别结果」编辑框，词块区占满剩余高度
- 手势统一由 `FlowSelectView` 容器判定（`onInterceptTouchEvent` 直接拦下），子 TextView 不再各自处理触摸
  - 竖向拖动（位移超过 touchSlop 且大于横向）→ 只滚动外层 ScrollView，**清空高亮、松手不复制**
  - 横向拖动 → 区间选择
  - 轻点 → 复制该词
  - ACTION_CANCEL → 一律不复制
- 词块区上下留白 18dp / 26dp，手指落在留白或词块间隙里就是纯滑动
- 底部按钮：复制全部 / 分享 / 重新识别 / 设置（"重新分词"随编辑框一起移除）

### 安装

去 [Releases](https://github.com/Haven-Lv/screenshot-ocr/releases/latest) 下载 `ScreenshotOCR-1.3.0.apk` 覆盖安装。

### 校验

- versionCode 13 / versionName 1.3.0
- SHA-256：`eabe0a47ffe507004075cb6ba142afc2a10e199e93c26e1bed5c72d94fddca64`
