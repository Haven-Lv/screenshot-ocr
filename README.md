# 截图取字 · Screenshot OCR

截图 → 系统分享 → 本应用 → 云端 OCR → 结果页。中文分词后按住词块滑动即可区间取词，分词区**按原文换行**排版，不显示回车符。

- 最新版：**v1.1.7**（versionCode 9，minSdk 26 / targetSdk 36）
- 包名：`cn.yg.ocrshare`
- 体积：约 33 KB，无第三方依赖（纯 framework + `org.json`）

## 安装

直接去 **Releases** 页下载 APK 安装即可：

**[→ 下载最新版 ScreenshotOCR-1.1.7.apk](https://github.com/Haven-Lv/screenshot-ocr/releases/latest)**

- 手机浏览器打开上面的链接，或电脑下载后传到手机；
- 首次安装需在系统里允许「安装未知来源应用」；
- 后续版本直接覆盖安装（签名不变），无需卸载。

## 功能

| 能力 | 说明 |
|---|---|
| 分享即识别 | 注册 `ACTION_SEND image/*`，截图 / 相册 / 微信图片分享进来即开始识别 |
| 可编辑结果 | 结果落在一个多行编辑框，支持手动修正、复制、再分享 |
| 分词选词 | Android 自带 ICU 词典离线分词；**按住词块横向滑动 = 区间选择**，松手自动复制 |
| 按原文换行 | 分词区不再出现 ↵ 词块，遇到原文换行就另起一行，复制出来的文本与原文断行一致 |
| 双 provider | OCR.space（免签名，500 次/天/IP）与腾讯云通用印刷体（1000 次/月，TC3 签名已内置） |
| 图片预处理 | 自动按 EXIF 转正、长边压到 2000px、JPEG q80（>950KB 自动降到 q60） |
| 零配置可跑 | 默认 OCR.space 官方测试 key `HELLOWORLD`，开箱即用（有全局限流，建议换自己的） |

## 使用

1. 截图 → 分享 → 选「截图取字」；
2. 等 1~3 秒出结果；
3. 在「分词选词」卡片里按住任意词块横向滑动，松手即把选中区间复制到剪贴板；单点只复制该词。

## 配置

设置页两种 provider 二选一：

- **OCR.space**：填 apikey，免费申请 https://ocr.space/ocrapi/freekey（500 次/天/IP）。中文固定走 `OCREngine=2`。
- **腾讯云**：填 SecretId / SecretKey，地域默认 `ap-guangzhou`，接口 `GeneralBasicOCR`，1000 次/月。TC3-HMAC-SHA256 由 `OcrClient.tencent()` 现场计算；鉴权失败会提示检查密钥与设备时间。

## 构建

```bash
python tools/build_cli.py --clean
```

不依赖 Gradle：`aapt2 → javac → d8 → zipalign → apksigner`。需要 JDK 17+ 与 Android SDK（build-tools 37 / platforms-android-37）。
首次构建会在 `tools/keystore/ocrshare.jks` 生成自签证书（alias/pass 均为 `ocrshare`），该目录已在 `.gitignore` 中，不会进仓库。

## 结构

```
app/src/main/java/cn/yg/ocrshare/
├── MainActivity.java      启动页：选图识别 / 设置 / 上次结果
├── ShareActivity.java     透明分享入口（SEND image/*）
├── ResultActivity.java    识别、结果编辑、分词选词
├── SettingsActivity.java  provider / 密钥 / 压缩参数
├── OcrClient.java         OCR.space(multipart) + 腾讯云(TC3)
├── WordSeg.java           ICU BreakIterator 分词（带规则兜底）
├── FlowSelectView.java    词块流式布局 + 滑动区间选择 + 原文换行
├── Img.java               解码 / EXIF 转正 / 缩放 / 压缩 / base64
├── Cfg.java               SharedPreferences 配置
└── Ui.java                深色 UI 组件
```

## 更新日志

- **v1.1.7**：分词区按原文换行（去掉 ↵ 词块）；README 安装说明改为走 Release。
- v1.1.6：分词选词（滑动区间）、点选复制。
- v1.1.0 ~ 1.1.5：双 provider、TC3 签名、结果可编辑、EXIF/压缩、分享入口。

## 已知边界

- OCR 是云端能力：OCR.space 免费额度按 IP 计，多设备共用出口会共享；腾讯云按账号计。
- 复杂版式/表格 OCR.space 表现一般，切腾讯云更好。
- 分词基于 ICU 词典，专业术语、生僻词可能切得偏碎；「重新分词」可对编辑后的文本再切一次。
