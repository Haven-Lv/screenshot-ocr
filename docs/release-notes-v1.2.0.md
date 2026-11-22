## 截图取字 v1.2.0

长文本截图不再被裁掉：**结果框与分词区各自独立上下滚动**。

### 问题

原来整个页面是一个外层 `ScrollView`，里面又套了两个 `ScrollView`（结果框、分词区）。Android 上外层会拦截纵向滑动，内层收不到手势，所以：

- 字多的截图只显示最上面一部分（文本本身是完整的，复制也是全的，只是看不到）；
- 两个框都滑不动。

### 修复

结果页改成「根 LinearLayout + 上下两栏各占 1/2 权重」：

- 根容器不再滚动，纵向空间按权重分配；
- 结果框、分词区各自内部滚动，互不抢事件；
- 操作按钮固定在底部一行，不随内容滚走。

### 安装

去 [Releases](https://github.com/Haven-Lv/screenshot-ocr/releases/latest) 下载 `ScreenshotOCR-1.2.0.apk` 覆盖安装。

### 校验

- versionCode 12 / versionName 1.2.0
- SHA-256：`283ce1ea40c499112e9462746194b02a84d4ec15b4bed1ab1013ca4f82c75def`
