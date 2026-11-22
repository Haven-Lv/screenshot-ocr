## 截图取字 v1.1.9

设置页 provider 高亮修正：**选中哪个，哪个才高亮并打 ✓**。

### 问题

之前三个按钮在创建时把 "OCR.space" 写成了 `primary=true`，另外两个是描边样式，所以不管当前用哪个，第一个永远是蓝绿色实心、看起来像被选中。

### 修复

- `Ui.setButtonActive()`：统一处理选中/未选中样式切换
- `SettingsActivity.renderProvider()`：按 `cfg.provider` 重新刷三个按钮，选中的实心 + "✓" 前缀，其余描边
- 顶部"当前：xxx" 文案颜色统一为强调色（之前 OCR.space 是橙色，像告警）

### 安装

去 [Releases](https://github.com/Haven-Lv/screenshot-ocr/releases/latest) 下载 `ScreenshotOCR-1.1.9.apk` 覆盖安装。

### 校验

- versionCode 11 / versionName 1.1.9
- SHA-256：`44a462960da4eaf24a613eacb4f185e4aeb6a8f77979af89130580fec7ceff18`
