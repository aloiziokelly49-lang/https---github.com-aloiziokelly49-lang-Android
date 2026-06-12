# 云墨 (CloudInk)

> **一站式多模态手写笔记工具** — Android 原生 · 离线 OCR · 语音转写 · 防 OOM 手写渲染

[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Language](https://img.shields.io/badge/language-Java-orange.svg)](https://www.java.com)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

## 项目简介

**云墨 (CloudInk)** 是一款面向数字化学习与无纸化办公场景的现代 Android 应用。它深度融合了**端侧离线 OCR**、**语音实时转写**、**防 OOM 分块手写渲染引擎** 和 **原生 PDF 逐页浏览**，打通了从"资料导入 → 内容提取 → 语音输入 → 手写排版 → 高清导出"的全链路闭环。

| 特性 | 技术实现 |
|------|----------|
| 📄 多格式文档导入 | PdfRenderer API（零第三方库）|
| 📷 离线 OCR 文字提取 | Google ML Kit + CameraX |
| 🎙️ 语音实时转写 | SpeechRecognizer + EventBus 流式推送 |
| ✍️ 参数化手写渲染 | Canvas 原生绘制 + 抖动算法 |
| 📖 分屏双开阅读 | ConstraintLayout + 自定义 SplitBar |
| 🖼️ 防 OOM 高清导出 | 分块渲染 + A4 拼接 + MediaStore |
| 🗂️ 历史档案管理 | Room + 多对多标签 + 文件夹 |
| 🌙 深色模式 | Material3 Dark + 自适应 |

---

## 四大核心技术壁垒

### 1. 离线 ML Kit OCR 引擎
```java
// 端侧识别, 零网络延迟, 隐私数据不离开设备
TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
recognizer.process(InputImage.fromBitmap(bitmap, 0))
    .addOnSuccessListener(text -> { /* 流式推送到草稿箱 */ });
```
- 支持 CameraX 实时帧和相册 Bitmap 双输入源
- 自动 TextBlock → Line 拼接, 保留段落结构
- ImageProxy 零拷贝识别, 无需额外内存

### 2. 防 OOM 大图分块渲染引擎
```
长文本 → 按段落分组 → 逐块渲染(tile≤4096px高) → A4画布拼接(2480×3508)
                                                          ↓
                                         tile.recycle() 立刻释放内存
```
- 参考 `saurabhdaware/text-to-handwriting` (MIT) 抖动算法, 纯 Java Canvas 实现
- **随机种子确定性**: `text.hashCode() ^ jitterBits` → 相同输入永远输出相同结果
- 逐字符 Gaussian 偏移 (X/Y/旋转/字号/字距), 模拟自然手写不规则感

### 3. 多模态语音协同
```
FAB 点击 → RECORD_AUDIO 权限检查
         → SpeechRecognizerManager.startListening()
         → onPartialResults() → EventBus.post(AudioTranscribeEvent)
         → @Subscribe(MAIN) → LiveData → UI 实时滚动
         → 用户点击"拼接到草稿" → HandwritingEngine 渲染
```
- 录音胶囊悬浮面板: 红色脉冲指示器 + 动态文本滚动
- EventBus 解耦: 转写引擎与 UI 完全分离
- AudioRecorderService 前台保活: 息屏/切后台不中断

### 4. 零第三方 PDF 库渲染
```java
PdfRenderer renderer = new PdfRenderer(parcelFileDescriptor);
PdfRenderer.Page page = renderer.openPage(index);
Bitmap bmp = Bitmap.createBitmap(width, height, ARGB_8888);
page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
page.close();
```
- 完全使用 Android 系统原生 `android.graphics.pdf.PdfRenderer`
- RecyclerView 逐页渲染 + onViewRecycled 回收 Bitmap
- 配套 PdfDocument 实现 A4 标准 PDF 输出

---

## 架构概览

```
┌─────────────────────────────────────────────────────┐
│                   UI Layer (DataBinding)             │
│  LoginActivity  HomeFragment  SplitReaderActivity   │
│  HandwriteEditorActivity  ExportPreviewActivity     │
│  HistoryFragment  ProfileActivity                   │
├─────────────────────────────────────────────────────┤
│               ViewModel Layer (MVVM)                 │
│  LoginVM  HomeVM  SplitReaderVM  HandwriteEditorVM │
│  HistoryVM                                          │
├─────────────────────────────────────────────────────┤
│                   Data Layer                         │
│  Room Database (5 entities, 4 DAOs, v1→v2 migration)│
│  Preferences DataStore (RxJava3)                    │
├─────────────────────────────────────────────────────┤
│              Engine Layer (Java)                     │
│  HandwritingEngine  OcrManager                      │
│  SpeechRecognizerManager  AudioRecorderService      │
│  ImageExporter  PdfExporter                         │
└─────────────────────────────────────────────────────┘
```

---

## 项目结构

```
app/src/main/java/com/cloudink/app/
├── CloudInkApplication.java      # Application 入口
├── data/local/                    # Room 数据库层
│   ├── AppDatabase.java           # 单例 + migration v1→v2
│   ├── dao/                       # 4 DAO 接口
│   ├── entity/                    # 5 Entity + 交叉引用
│   └── converter/                 # TypeConverter
├── data/preferences/
│   └── AppPreferences.java        # DataStore 封装
├── event/                         # EventBus 事件
│   ├── TextExtractEvent.java      # 分屏摘录
│   ├── OcrResultEvent.java        # OCR 识别
│   ├── AudioTranscribeEvent.java  # 语音转写
│   └── RecordingStateEvent.java   # 录音状态
├── ocr/OcrManager.java            # ML Kit 离线 OCR
├── asr/                           # 语音模块
│   ├── AudioRecorderService.java  # 前台录音服务
│   └── SpeechRecognizerManager.java # 实时转写引擎
├── rendering/                     # 手写渲染
│   ├── HandwritingEngine.java     # 核心渲染引擎
│   ├── PreviewCanvas.java         # 预览画布
│   └── model/                     # PenType + Params
├── document/
│   └── PdfViewerAdapter.java      # PdfRenderer 适配器
├── export/
│   ├── ImageExporter.java         # 图片导出
│   └── PdfExporter.java           # PDF 导出
├── ui/                            # Activity/Fragment
│   ├── login/   home/   reader/
│   ├── editor/  export/ history/
│   └── profile/
└── util/UuidGenerator.java
```

---

## 功能截图

> *(答辩/文档中替换为实际截图)*

| 首页卡片 | 手写编辑器 | 分屏阅读 |
|:--------:|:----------:|:--------:|
| ![home](screenshots/home.png) | ![editor](screenshots/editor.png) | ![split](screenshots/split.png) |

| 语音胶囊 | 导出预览 | 历史档案 |
|:--------:|:--------:|:--------:|
| ![audio](screenshots/audio.png) | ![export](screenshots/export.png) | ![history](screenshots/history.png) |

---

## 快速开始

```bash
# 克隆项目
git clone https://github.com/yourname/CloudInk.git

# 设置环境变量
export ANDROID_HOME=/path/to/Android/Sdk
export JAVA_HOME=/path/to/jdk

# 编译 Debug APK
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk

# 运行单元测试
./gradlew testDebugUnitTest

# 运行集成测试 (需要设备/模拟器)
./gradlew connectedDebugAndroidTest
```

---

## 依赖与开源致谢

| 库 | 用途 | 许可证 |
|----|------|--------|
| AndroidX (Room, Lifecycle, DataStore...) | 架构组件 | Apache 2.0 |
| Google ML Kit | 离线 OCR | Apache 2.0 |
| EventBus (greenrobot) | 组件通信 | Apache 2.0 |
| RxJava3 / RxAndroid | 响应式编程 | Apache 2.0 |
| CameraX | 相机采集 | Apache 2.0 |
| Material Design 3 | UI 组件 | Apache 2.0 |
| `saurabhdaware/text-to-handwriting` | 抖动算法参考 (MIT) | MIT |

> **致谢**: 手写渲染引擎的抖动算法灵感来源于开源项目 [text-to-handwriting](https://github.com/saurabhdaware/text-to-handwriting) (MIT License)。本项目使用 Java 原生 Canvas API 完全重写实现, 未直接复制其源代码。

---

## 许可证

MIT © 2026 CloudInk Project
