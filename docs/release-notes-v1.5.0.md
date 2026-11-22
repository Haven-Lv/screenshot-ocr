## 截图取字 v1.5.0

按独立代码审查（1 BLOCKER + 7 MAJOR）逐条修完的版本。

### 修复清单

| 级别 | 问题 | 修法 |
|---|---|---|
| BLOCKER | 选区高亮 100% 不显示：`setText(sp)` 默认 BufferType.NORMAL 会拷贝成不可变 `SpannedString`，`spannable()` 恒为 null | `setText(sp, BufferType.SPANNABLE)` |
| MAJOR | 英文/数字跨词复制丢空格（`PowerPointPresentation`） | `pick()` 按 raw 间隙补：有换行补换行，否则补一个空格 |
| MAJOR | 空行被折叠、与原文断行不一致 | 显示与复制都按 raw 间隙里 `\n` 的真实个数补齐（显示上限 2 行） |
| MAJOR | 设置页切了 provider 回来仍用旧配置 | `ResultActivity.onResume()` 重新 `Cfg.load` |
| MAJOR | 连续分享叠出多个结果页，`onNewIntent` 是死代码 | 分享跳转加 `CLEAR_TOP\|SINGLE_TOP` |
| MAJOR | 旋转/连分享时旧任务结果覆盖新图、重复扣额度 | `AtomicInteger gen` 请求序号 + `isFinishing()/isDestroyed()` 守卫 |
| MAJOR | targetSdk 36 强制 edge-to-edge，状态栏/手势条压住 UI | `Ui.applyInsets()` 把 systemBars/displayCutout/ime 加回根布局 padding |
| MAJOR | 大图内存：bitmap 不回收 + base64 两份中间拷贝 | `bmp.recycle()`；腾讯云 body 分块 `Base64.encode` 直写缓冲；连接 `finally disconnect()` |
| MINOR | 点在空白处 `paintSelection(-1,-1)` 数组越界崩溃 | 加负索引守卫；`ACTION_DOWN` 未命中词元直接返回 false |
| MINOR | 横拖中途被 ScrollView 抢手势 | `dragging` 首帧 `requestDisallowInterceptTouchEvent(true)`，UP/CANCEL 释放 |
| MINOR | 每次 MOVE 都重建 Span 掉帧 | 选区没变就跳过；>1500 词元时不再逐词加底色 |
| MINOR | OCR.space 超 1MB 仍上传、无重试、连接不释放 | 质量 80→60→45→35 循环降到 ≤999KB；`finally` 释放连接 |
| NIT | provider 点了不保存就返回会丢 | 点选即 `cfg.save()` |
| NIT | 首页跳设置时 `finish()` 把首页也关了 | 去掉 finish，`onResume` 刷新 provider 行 |
| NIT | `last` prefs 只写不读 | 删除该写入 |

### 安装

去 [Releases](https://github.com/Haven-Lv/screenshot-ocr/releases/latest) 下载 `ScreenshotOCR-1.5.0.apk` 覆盖安装。

### 校验

- versionCode 17 / versionName 1.5.0
- SHA-256：`9cd7a26a001e8b33c868e8dfb7c67269a3929fa87260e646a0af1ea170dba4b9`
