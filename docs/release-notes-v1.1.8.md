## 截图取字 v1.1.8

识别准确率补强：新增 **腾讯云·高精度** `GeneralAccurateOCR`，小字、密排、聊天截图比 OCR.space 稳明显一截。

### 安装

去 [Releases](https://github.com/Haven-Lv/screenshot-ocr/releases/latest) 下载 `ScreenshotOCR-1.1.8.apk` 覆盖安装即可。

### 本版改动

- 设置页 provider 三选一：OCR.space / 腾讯云·通用（`GeneralBasicOCR`）/ 腾讯云·高精度（`GeneralAccurateOCR`）
- 两者共用腾讯云同一个免费资源包（1000 次/月，每月 1 号发放）
- 状态栏会显示当前 provider 名称，便于确认到底走的哪条
- 分词按原文换行的修正沿用 v1.1.7

### 腾讯云密钥怎么拿

1. 实名认证：https://console.cloud.tencent.com/qualify
2. 开通服务：https://console.cloud.tencent.com/ocr → 通用文字识别
3. 建密钥：https://console.cloud.tencent.com/cam/capi → 新建密钥 → 复制 SecretId / SecretKey
4. 填进本应用设置，地域保持 `ap-guangzhou`

### 校验

- versionCode 10 / versionName 1.1.8
- SHA-256：`0796a9b1f95205799f77d9d6caa5378e66916f62c86bf80fdbdca850f77229cf`
