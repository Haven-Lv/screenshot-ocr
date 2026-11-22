## 截图取字 v1.1.6

截图 → 系统分享 → 本应用 → 云端 OCR → 结果页；中文分词后**按住词块滑动**即可区间取词并自动复制。

### 安装

```bash
adb install -r ScreenshotOCR-1.1.6.apk
```

### 本版内容

- `ACTION_SEND image/*` 分享入口（Uri / ClipData 双兼容），分享后立刻识别
- OCR.space（免签名，500 次/天/IP）与腾讯云 `GeneralBasicOCR`（1000 次/月，TC3-HMAC-SHA256 内置实现，无需 SDK）
- ICU `BreakIterator` 离线中文分词；`FlowSelectView` 支持点选单词块与滑动区间选择
- EXIF 转正、长边 2000px、JPEG q80（>950KB 自动降到 q60）
- 结果可编辑，一键复制 / 再分享；设置页可切 provider、语言与压缩参数

### 校验

- versionCode 8 / versionName 1.1.6 / minSdk 26 / targetSdk 36
- SHA-256：`ae11dcef4b43b744a401e7e3d908fb8fff64a45b676a85a5f2404375c8e70a75`

> 默认使用 OCR.space 官方测试 key，有全局限流；建议在设置里填自己的 apikey，或改填腾讯云 SecretId/SecretKey。
