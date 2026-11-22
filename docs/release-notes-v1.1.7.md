## 截图取字 v1.1.7

分词区排版修正：**不再显示 ↵ 回车词块，改为按原文换行**，滑动取词复制出来的文本与原文断行一致。

### 安装

去 [Releases](https://github.com/Haven-Lv/screenshot-ocr/releases/latest) 下载 `ScreenshotOCR-1.1.7.apk` 直接安装（覆盖旧版即可，签名不变）。

### 本版改动

- `FlowSelectView`：换行改为结构标记 `breakBefore`，遇到原文换行强制另起一行；不再生成"↵"词块
- `textOf()`：区间复制时按 `breakBefore` 还原 `\n`，保证复制结果与原文断行一致
- `WordSeg`：换行 token 不再参与渲染，仅作分段
- README：安装说明改为走 Release 页，不再写 adb 命令

### 校验

- versionCode 9 / versionName 1.1.7 / minSdk 26 / targetSdk 36
- SHA-256：`f50f8692a1a2067d6b2ee8ce96789b871fc9eb5643553f2e1055e47b5b633c81`

> 默认 OCR.space 测试 key 有全局限流，建议在设置里填自己的 apikey，或改填腾讯云 SecretId/SecretKey。
