# 云墨 CloudInk — 答辩 PPT 稿件

> 使用说明：标记 `[📷 截图]` 的位置需要你从手机/模拟器截图替换。

---

## 第 1 页：封面

**云墨 CloudInk**

基于 Android 原生的多模态手写排版与数字笔记应用

答辩人：喻文卓
指导老师：邱杰凡
日期：2026年6月

[📷 截图：应用图标 + 首页截图，居中放置即可]

---

## 第 2 页：项目背景与目的

**痛点：**
- 现有手写模拟软件多为网页封装，移动端卡顿，无法深度整合系统功能
- 资料阅读、音频采集、OCR提取、排版渲染各自独立，体验割裂

**目标：**
- 打造"导入→识别→手写排版→导出"全链路闭环
- 深度集成 Android 原生生态（CameraX、PdfRenderer、SpeechRecognizer）
- 自研 Canvas 手写渲染引擎，替代 WebView 方案

[📷 截图：应用首页功能台 HomeFragment，展示6个功能卡片]

---

## 第 3 页：技术栈总览

| 层级 | 技术选型 |
|------|---------|
| 架构 | MVVM（ViewModel + LiveData + DataBinding） |
| 数据库 | Room（v4，5表，3次增量迁移） |
| 偏好存储 | Jetpack DataStore（RxJava3，11 Key） |
| 组件通信 | EventBus（greenrobot） |
| PDF渲染 | 原生 PdfRenderer（零第三方库） |
| 相机 | CameraX |
| 语音 | SpeechRecognizer + 百度 ASR |
| OCR | 百度云端 + Tesseract 离线双引擎 |
| 导出 | MediaStore + PdfDocument |
| UI | Material Design 3 |

[📷 截图：项目在 Android Studio 中的包结构树，展示 asr/ocr/rendering/data/ui 等模块]

---

## 第 4 页：系统架构图


【绘制一下】

```
┌─────────────────────────────────────────────┐
│                  UI 层                       │
│  Home │ Editor │ Export │ OCR │ History │ Login │
├─────────────────────────────────────────────┤
│              ViewModel 层                    │
│  HandwriteEditorVM │ VoiceCaptureController  │
├─────────────────────────────────────────────┤
│               业务逻辑层                      │
│  HandwritingEngine │ OcrRecognizer │ ASR     │
├─────────────────────────────────────────────┤
│               数据持久层                      │
│  Room DB (v4) │ DataStore (11 Keys)         │
├─────────────────────────────────────────────┤
│              Android 平台层                   │
│  PdfRenderer │ CameraX │ MediaRecorder      │
│  SpeechRecognizer │ MediaStore │ SAF       │
└─────────────────────────────────────────────┘
```


---

## 第 5 页：功能点 1 — 多格式文件上传与文档浏览

**小标题：** SAF 单/多文件选择 → MIME类型+扩展名两级分类 → 按格式路由展示

【粘贴文本】

- PDF：PdfRenderer + RecyclerView 逐页高清渲染，滑出即回收 Bitmap
- TXT：ScrollView 直接展示
- 图片：展示 + 一键OCR提取文字
- PDF加载失败→自动复制到缓存兜底

[📷 界面截图1：文件选择器界面，展示多文件选择]
[📷 界面截图2：PDF浏览界面，RecyclerView纵向滑动效果]


[💻 代码截图1：`PdfDocumentLoader.java` 第62～121行 — `open()` 方法完整降级加载策略]
[💻 代码截图2：`PdfViewerAdapter.java` 第104～172行 — `onBindViewHolder()` 逐页渲染 + `RENDER_MODE_FOR_DISPLAY`]

---

## 第 6 页：功能点 2 — OCR 文字识别（双引擎）

小标题：**百度云端优先 + Tesseract 离线兜底**


（绘制流程图）

```
用户拍照/选图
    ↓
图片降采样（最长边≤2048px，防OOM）
    ↓
百度OCR已配置？──是→ 百度手写识别API → 高精度结果
    │                    ↓ 失败
    └──否→ Tesseract离线识别 → 兜底结果
                    ↓
              存入Room草稿表
                    ↓
          跳转手写编辑器（Intent传参）
```

[📷 界面截图1：相机OCR拍摄界面]
[📷 界面截图2：OCR识别结果跳转到编辑器]

[💻 代码截图1：`OcrRecognizer.java` 第19～48行 — `recognize()` 百度优先→Tesseract兜底的 if-else 调度]
[💻 代码截图2：`BaiduOcrHelper.java` 第64～69行 — `detect_direction=true` 服务端方向校正参数]

---

## 第 7 页：功能点 3 — 手写渲染引擎（核心功能）

**小标题：** 自研 Android Canvas 逐字渲染引擎


【粘贴文本】

核心算法：
```
Random rnd = new Random(text.hashCode());  // 确定性种子
dx = (rnd.nextFloat() - 0.5f) × 2 × jitterScale;      // X偏移
dy = (rnd.nextFloat() - 0.5f) × 2 × jitterScale × 0.6; // Y偏移=60%X
canvas.drawText(glyph, cursorX + dx, cursorY + dy, paint);
```

【粘贴文本】

- 7 项排版参数：字号/字距/行距/抖动/笔触/纸张/字体
- 3 种笔触（钢笔/圆珠笔/马克笔）× 4色纸张 × 4种纹理 = 16 组合
- 5 款中文手写字体动态加载（.ttf Typeface）
- DataBinding 双向绑定 + 200ms 防抖 → 实时预览

[📷 界面截图1：手写编辑器主界面，展示预览区+滑块控制台]
[📷 界面截图2：切换不同笔触/纸张/字体的对比效果（3张并排或拼成一张）]


[💻 代码截图1：`HandwritingEngine.java` 第131～195行 — `render()` 方法全貌（笔触应用→分行→背景→逐字抖动）]
[💻 代码截图2：`HandwritingEngine.java` 第168～179行 — 确定性抖动核心（`text.hashCode()` 种子 + jitter公式）]
[💻 代码截图3：`HandwriteEditorViewModel.java` 第168～171行 — 200ms 防抖 `scheduleRender()`]

---

## 第 8 页：功能点 4 — 语音采集与实时转写

**小标题：三层降级策略：**


【绘制表格】

| 优先级 | 方案 | 特点 |
|--------|------|------|
| 1 | SpeechRecognizer + EXTRA_PARTIAL_RESULTS | 边录边转写，实时出字 |
| 2 | 前台Service录音 → 百度ASR转写文件 | 录完再转，准确率高 |
| 3 | 弹出键盘 → 输入法语音按钮 | 零配置通用兜底 |

【粘贴文本】

**保活机制：** 前台 Service + startForeground 通知 → 息屏/切后台录音不中断

**数据流：** EventBus → Controller → EditorVoiceState → DataBinding → 悬浮面板实时更新

[📷 界面截图1：手写编辑器 + 语音悬浮面板展开状态，显示"边录边转写中"]
[📷 界面截图2：转写完成后面板状态，显示转写文本+"插入草稿"按钮]

[💻 代码截图1：`EditorVoiceCaptureController.java` 第66～102行 — `startCapture()` 三层策略 if-else]
[💻 代码截图2：`SpeechRecognizerManager.java` 第206～214行 — `onPartialResults()` 中间结果回调 + `EXTRA_PARTIAL_RESULTS`]

---

## 第 9 页：功能点 5 — 高清预览与防OOM导出

**小标题：分块渲染策略：**

【绘制流程图】

```
长文本（如100段）
    ↓ 15段/组，拆分为7个tile
Tile1  Tile2  ...  Tile7
    ↓ 独立渲染（宽1240px，高≤4096px）
    ↓ 缩放后逐块绘制到A4画布（2480×3508，300dpi）
    ↓ 每块绘制完立即 tile.recycle()
    ↓
最终A4 Bitmap（约34MB，内存峰值≈37MB）
    ↓
┌──────────┴──────────┐
保存PNG至相册        导出标准A4 PDF
(MediaStore IS_PENDING)  (PdfDocument)
```


【粘贴文本】

**预览：** PreviewCanvas 双指缩放

[📷 界面截图1：导出预览页 ZoomableImageView，双指缩放效果]
[📷 界面截图2：系统相册中看到保存的CloudInk图片]


[💻 代码截图1：`ExportPreviewActivity.java` 第156～181行 — `renderTiles()` 15段一组分块渲染]
[💻 代码截图2：`ExportPreviewActivity.java` 第202～235行 — `stitchToA4()` A4拼接 + `tile.recycle()` 即时回收]

---

## 第 10 页：功能点 6 — Room 数据库与双分类归档

**小标题：Schema：5 张表**


【绘制表格】

| 表 | 用途 |
|----|------|
| handwrite_records | 手写记录（13字段，含排版参数+存储路径） |
| drafts | OCR/语音草稿 |
| audio_records | 录音文件元信息 |
| tags | 标签（id + name + color） |
| record_tag_join | 多对多关联中间表 |

【粘贴文本】

**设计要点：**
- UUID 主键（离线生成无冲突）
- 文件夹 + 多标签双重分类

[📷 界面截图1：历史档案室列表，展示卡片布局+标签Chip+文件夹路径]
[📷 界面截图2：标签管理界面]

[💻 代码截图2：`CloudInkRepository.java` 第92～108行 — `linkTags()` 多对多标签自动创建与关联]

---

## 第 11 页：功能点 7 — DataStore 登录与偏好记忆

【绘制流程图】

**登录流程：**
```
启动App → 读DataStore登录态 → 已登录？→ 直达首页
                                    ↓ 未登录
                              登录页 → 首次？→ 引导完善资料
                                              → 非首次？→ 首页
```


【粘贴文本】

**偏好存储：** 11 个 Key

【绘制表格】

| 类别 | Key | 默认值 |
|------|-----|--------|
| 登录 | is_logged_in | false |
| 登录 | user_phone | "" |
| 登录 | is_first_login | true |
| 排版 | char_spacing / line_spacing / jitter | 0.5 / 1.5 / 0.3 |
| 排版 | paper_index / pen_type / font_path | 0 / "fountain" / 默认字体 |
| 用户 | display_name | "" |

[📷 界面截图1：登录页面]
[📷 界面截图2：个人中心/设置页面]

[💻 代码截图1：`AppPreferences.java` 第19～39行 — 11个 `Preferences.Key` 定义]
[💻 代码截图2：`HandwriteEditorViewModel.java` 第78～91行 — `loadPreferencesIntoParams()` 启动时自动加载用户偏好]

---

## 第 12 页：核心技术亮点总结

【绘制表格】

| 亮点 | 说明 |
|------|------|
| 自研渲染引擎 | 267行纯Canvas手写引擎，非WebView方案 |
| 确定性抖动算法 | `Random(text.hashCode())` 可复现随机 |
| 双引擎OCR | 百度云端+ Tesseract离线，自动切换 |
| 三层语音降级 | 实时→文件→输入法，逐级兜底 |
| 分块渲染防OOM | 15段/组 + tile.recycle()，内存峰值~37MB |
| Room增量迁移 | v1→v4，ALTER TABLE无损升级 |
| DataStore偏好 | 11 Key，两层默认值设计 |
| 零第三方PDF库 | 纯系统API（PdfRenderer/PdfDocument） |

---

如果老师问起文档与代码的差异，参考以下口径：

| 可能问 | 回答要点 |
|--------|---------|
| "SheetViewerScreen在哪" | 设计文档统称，实际实现是 DocumentViewerActivity + PdfViewerAdapter + RecyclerView |
| "OCR不是离线吗" | 双引擎策略：在线优先保精度，离线兜底保可用，自动切换无感知 |
| "BitmapRegionDecoder呢" | 适用场景不同——它解码已有大图，我们渲染生成大图，用分块拼接更匹配 |
| "搜狗语音怎么调" | 实际是通用输入法方案——弹出键盘，任何输入法的麦克风都可语音 |

**技术栈：** MVVM · Room v4 · DataStore · CameraX · PdfRenderer · Canvas自研引擎 · SpeechRecognizer · 百度OCR/ASR · Tesseract · EventBus · Material Design 3

