# 云墨 (CloudInk) — Spec 清单（技术规格说明书）

> **语言**: Java | **最低 API**: 26 (Android 8.0) | **目标 API**: 34+

---

## 1. 项目包结构 (Package Structure)

```
com.cloudink.app/
├── CloudInkApplication.java          // Application 入口
├── data/
│   ├── local/
│   │   ├── AppDatabase.java           // Room Database 单例
│   │   ├── dao/
│   │   │   ├── HandwriteRecordDao.java
│   │   │   ├── DraftDao.java
│   │   │   ├── AudioRecordDao.java
│   │   │   └── TagDao.java
│   │   ├── entity/
│   │   │   ├── HandwriteRecord.java
│   │   │   ├── Draft.java
│   │   │   ├── AudioRecord.java
│   │   │   ├── Tag.java
│   │   │   └── RecordTagCrossRef.java  // 多对多关联
│   │   └── converter/
│   │       └── DateConverter.java       // Room TypeConverter
│   ├── preferences/
│   │   └── AppPreferences.java          // DataStore 封装
│   └── repository/
│       ├── HandwriteRepository.java
│       ├── DraftRepository.java
│       └── HistoryRepository.java
├── event/
│   ├── TextExtractEvent.java            // 分屏摘录事件
│   ├── OcrResultEvent.java              // OCR 结果流转事件
│   └── AudioTranscribeEvent.java        // 语音转写追加事件
├── ocr/
│   ├── OcrManager.java                  // ML Kit 封装
│   └── ImagePreprocessor.java           // 裁剪/透视校正
├── asr/
│   ├── AudioRecorderService.java        // 前台 Service 录音
│   └── SpeechRecognizerManager.java     // SpeechRecognizer 封装
├── rendering/
│   ├── HandwritingEngine.java           // 核心渲染引擎
│   ├── PaperTextureRenderer.java        // 纸张纹理
│   ├── PenStrokeRenderer.java           // 笔触渲染
│   └── model/
│       ├── HandwritingParams.java        // 参数模型（DataBinding 绑定）
│       └── PenType.java                  // 笔触枚举
├── export/
│   ├── ImageExporter.java               // BitmapRegionDecoder + MediaStore
│   └── PdfExporter.java                 // PdfDocument A4 导出
├── document/
│   ├── PdfViewerAdapter.java            // PdfRenderer + RecyclerView
│   └── FileImportManager.java           // 多格式导入
├── ui/
│   ├── login/
│   │   ├── LoginActivity.java
│   │   └── LoginViewModel.java
│   ├── home/
│   │   ├── HomeFragment.java
│   │   └── HomeViewModel.java
│   ├── reader/
│   │   ├── SplitReaderActivity.java
│   │   └── SplitReaderViewModel.java
│   ├── editor/
│   │   ├── HandwriteEditorActivity.java
│   │   └── HandwriteEditorViewModel.java
│   ├── export/
│   │   └── ExportPreviewActivity.java
│   ├── history/
│   │   ├── HistoryFragment.java
│   │   ├── HistoryViewModel.java
│   │   └── HistoryAdapter.java
│   └── profile/
│       ├── ProfileFragment.java
│       ├── ProfileActivity.java
│       └── ProfileViewModel.java
└── util/
    ├── UuidGenerator.java
    └── FileUtils.java
```

---

## 2. 数据库设计 (Room Database)

### 2.1 AppDatabase

```java
@Database(
    entities = {
        HandwriteRecord.class, Draft.class, AudioRecord.class,
        Tag.class, RecordTagCrossRef.class
    },
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverter.class)
public abstract class AppDatabase extends RoomDatabase {
    public abstract HandwriteRecordDao handwriteRecordDao();
    public abstract DraftDao draftDao();
    public abstract AudioRecordDao audioRecordDao();
    public abstract TagDao tagDao();
}
```

### 2.2 实体定义

#### HandwriteRecord（手写记录）
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PRIMARY KEY | UUID 主键 |
| title | String | NOT NULL | 记录标题 |
| content | String | — | 排版纯文本内容 |
| imagePath | String | — | 渲染后图片路径 |
| paperIndex | int | DEFAULT 0 | 纸张背景索引 |
| penType | String | DEFAULT "fountain" | 笔触类型 |
| createdAt | long | NOT NULL | 创建时间戳 |
| updatedAt | long | NOT NULL | 修改时间戳 |
| folderName | String | DEFAULT "默认" | 所属文件夹 |

#### Draft（草稿）
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PRIMARY KEY | UUID 主键 |
| content | String | — | 草稿文本内容 |
| source | String | NOT NULL | 来源: "OCR" / "ASR" / "MANUAL" / "EXTRACT" |
| createdAt | long | NOT NULL | 创建时间戳 |

#### AudioRecord（录音文件）
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PRIMARY KEY | UUID 主键 |
| fileName | String | NOT NULL | 文件名 |
| filePath | String | NOT NULL | 存储路径 |
| durationMs | long | — | 录音时长 ms |
| transcript | String | — | 完整转写文本 |
| recordId | String | FOREIGN KEY | 关联手写记录 |
| createdAt | long | NOT NULL | 录制时间戳 |

#### Tag（标签）
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PRIMARY KEY | UUID 主键 |
| name | String | UNIQUE, NOT NULL | 标签名 |
| color | int | — | 标签颜色 |

#### RecordTagCrossRef（记录-标签多对多）
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| recordId | String | FOREIGN KEY → HandwriteRecord | 记录 ID |
| tagId | String | FOREIGN KEY → Tag | 标签 ID |

> **主键**: 复合主键 (recordId, tagId)

### 2.3 DAO 核心查询

```java
// HandwriteRecordDao
@Query("SELECT * FROM HandwriteRecord ORDER BY updatedAt DESC")
LiveData<List<HandwriteRecord>> getAllRecords();

@Query("SELECT * FROM HandwriteRecord WHERE folderName = :folder ORDER BY updatedAt DESC")
LiveData<List<HandwriteRecord>> getRecordsByFolder(String folder);

@Query("SELECT * FROM HandwriteRecord 
        INNER JOIN RecordTagCrossRef ON HandwriteRecord.id = RecordTagCrossRef.recordId
        WHERE RecordTagCrossRef.tagId = :tagId ORDER BY updatedAt DESC")
LiveData<List<HandwriteRecord>> getRecordsByTag(String tagId);

// TagDao
@Query("SELECT * FROM Tag ORDER BY name ASC")
LiveData<List<Tag>> getAllTags();
```

---

## 3. Preferences DataStore 设计

```java
public class AppPreferences {
    // 用户登录状态
    public static final String KEY_IS_LOGGED_IN = "is_logged_in";
    public static final String KEY_USER_PHONE = "user_phone";

    // 首次登录标记
    public static final String KEY_IS_FIRST_LOGIN = "is_first_login";

    // 排版默认偏好
    public static final String KEY_DEFAULT_CHAR_SPACING = "default_char_spacing";   // float, 0.0~5.0
    public static final String KEY_DEFAULT_LINE_SPACING = "default_line_spacing";   // float, 1.0~3.0
    public static final String KEY_DEFAULT_JITTER = "default_jitter";               // float, 0.0~2.0
    public static final String KEY_DEFAULT_PAPER_INDEX = "default_paper_index";     // int
    public static final String KEY_DEFAULT_PEN_TYPE = "default_pen_type";           // String
}
```

---

## 4. 关键类设计

### 4.1 HandwritingEngine（核心渲染引擎）
```
职责:
  - 接收 HandwritingParams，生成手写风格 Bitmap
  - 基于开源算法 saurabhdaware/text-to-handwriting

核心方法:
  - Bitmap render(String text, HandwritingParams params)
  - void setPaperTexture(int paperIndex)
  - void setPenStroke(PenType penType)
  - void dispose()  // 释放 Bitmap 资源

约束:
  - 所有渲染在后台线程执行（AsyncTask / ExecutorService）
  - 大文本需分块渲染，单块高度 ≤ 4096px
```

### 4.2 OcrManager（OCR 管理）
```
职责:
  - 封装 Google ML Kit TextRecognition.getClient()
  - 支持 CameraX ImageProxy 和相册 Bitmap 两种输入

核心方法:
  - Task<String> recognizeFromImage(@NonNull ImageProxy image)
  - Task<String> recognizeFromBitmap(@NonNull Bitmap bitmap)
  - void close()  // 释放 ML Kit 资源

配置:
  - 使用 TextRecognizerOptions.DEFAULT (拉丁字母为主)
  - 如需中文支持，升级为 ChineseTextRecognizerOptions
```

### 4.3 AudioRecorderService（前台录音服务）
```
职责:
  - 继承 Service，前台运行（通知栏显示录音指示器）
  - 内部使用 MediaRecorder 采集音频
  - 通过 EventBus 发送 AudioTranscribeEvent

核心流程:
  1. startRecording() → 创建 MediaRecorder → 开始录制
  2. SpeechRecognizerManager 并行监听 → 转写文本
  3. 转写片段通过 EventBus.post(AudioTranscribeEvent) 发送
  4. stopRecording() → 停止录制 → 保存文件 → 入库
```

### 4.4 SplitReaderActivity（分屏阅读）
```
布局: ConstraintLayout + 自定义 VerticalSplitBar（可拖拽分割线）
左栏: RecyclerView + PdfViewerAdapter（PdfRenderer 逐页渲染）
右栏: DraftPanelFragment（便利贴堆叠 + 文本追加）

事件流:
  左侧长按选中文字 → EventBus.post(TextExtractEvent) → 右侧追加到草稿
```

### 4.5 ExportPreviewActivity（预览导出）
```
图片加载: BitmapRegionDecoder.newInstance(path, false)
缩放: 自定义 ScaleImageView（Matrix 手势处理）
导出: ImageExporter.saveToGallery() / PdfExporter.exportA4()
```

---

## 5. EventBus 事件定义

| 事件类 | 字段 | 方向 |
|--------|------|------|
| TextExtractEvent | String extractedText, long timestamp | SplitReader → DraftPanel |
| OcrResultEvent | String recognizedText, Bitmap sourcePreview | OCR → Editor/Draft |
| AudioTranscribeEvent | String transcribedSegment, boolean isFinal | Service → Editor |

---

## 6. Layout 文件清单

| 布局文件 | 对应页面 | 关键组件 |
|----------|----------|----------|
| `activity_login.xml` | LoginActivity | TextInputLayout, Button |
| `fragment_home.xml` | HomeFragment | GridLayout, CardView, MaterialCardView |
| `activity_split_reader.xml` | SplitReaderActivity | ConstraintLayout, RecyclerView×2, 自定义 SplitBar |
| `activity_handwrite_editor.xml` | HandwriteEditorActivity | Custom PreviewCanvas, BottomSheet, Slider×N, FloatingActionButton |
| `fragment_audio_panel.xml` | 悬浮录音胶囊 | 波形动画 View, RecyclerView(转写滚动) |
| `activity_export_preview.xml` | ExportPreviewActivity | ScaleImageView, Button(保存/导出) |
| `fragment_history.xml` | HistoryFragment | RecyclerView(瀑布流), ChipGroup |
| `fragment_profile.xml` | ProfileFragment | PreferenceFragment 或自定义 |

---

## 7. Gradle 依赖清单（开源库）

| 依赖 | 用途 | 许可证 | 引入必要性 |
|------|------|--------|-----------|
| `androidx.room:room-runtime` | 本地数据库 | Apache 2.0 | 必需 |
| `androidx.room:room-compiler` | Room 注解处理 | Apache 2.0 | 必需 |
| `androidx.datastore:datastore-preferences` | 偏好存储 | Apache 2.0 | 必需 |
| `androidx.databinding:databinding-runtime` | 双向绑定 | Apache 2.0 | 必需 |
| `androidx.lifecycle:lifecycle-viewmodel` | MVVM | Apache 2.0 | 必需 |
| `androidx.lifecycle:lifecycle-livedata` | 响应式数据 | Apache 2.0 | 必需 |
| `com.google.mlkit:text-recognition` | OCR 识别 | Apache 2.0 | 必需 |
| `androidx.camera:camera2` | 相机采集 | Apache 2.0 | 必需 |
| `org.greenrobot:eventbus` | 组件通信 | Apache 2.0 | 必需 |
| `com.google.android.material:material` | Material Design 3 | Apache 2.0 | 必需 |
| `androidx.recyclerview:recyclerview` | 列表视图 | Apache 2.0 | 必需 |
| `androidx.cardview:cardview` | 卡片视图 | Apache 2.0 | 可能合并到 Material |

### 开源手写算法依赖（待评估）

| 候选 | 语言 | 许可证 | 集成方案 | 风险 |
|------|------|--------|----------|------|
| `saurabhdaware/text-to-handwriting` | Python/JS | MIT | **移植为 Java**，参考数学模型的参数公式 | 移植工作量较大 |
| 自研 Canvas 方案 | Java | — | 基于贝塞尔曲线 + 随机化偏移模拟手写 | 效果调优需时间 |

> **建议**: 优先采用"自研 Canvas + 参考开源参数"混合方案，避免完全依赖外部移植。具体决策见阶段 1 中的**开源评估**任务。

---

## 8. AndroidManifest 权限声明

```xml
<!-- 相机 -->
<uses-permission android:name="android.permission.CAMERA" />
<!-- 录音 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<!-- 前台服务 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<!-- 存储（Android 10+ 分区存储，不使用 READ_EXTERNAL_STORAGE） -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<!-- MediaStore 不需要权限 -->
<!-- 通知（前台服务） -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## 9. 数据流架构图 (文本)

```
┌──────────────┐  ┌─────────────┐  ┌──────────────────┐
│  CameraX/    │  │  AudioRecorder│  │  FileImport      │
│  ML Kit OCR  │  │  Service      │  │  Manager         │
└──────┬───────┘  └──────┬───────┘  └────────┬─────────┘
       │ OcrResultEvent  │ AudioTranscribeEvent │
       └────────┬────────┴─────────────────────┘
                ▼
         EventBus (postSticky or post)
                ▼
   ┌─────────────────────────┐
   │  HandwriteEditorActivity│
   │  ┌───────────────────┐  │
   │  │  DraftPanel       │  │  ← 草稿聚合区
   │  └────────┬──────────┘  │
   │           │ 文本输入      │
   │           ▼              │
   │  ┌───────────────────┐  │
   │  │ HandwritingEngine  │  │  ← 核心渲染
   │  └────────┬──────────┘  │
   │           │ Bitmap       │
   │           ▼              │
   │  ┌───────────────────┐  │
   │  │  PreviewCanvas    │  │  ← 预览
   │  └───────────────────┘  │
   └───────────┬─────────────┘
               │ 保存/导出
               ▼
   ┌─────────────────────────┐
   │  Room DB (持久化)        │
   │  MediaStore (图片)       │
   │  PdfDocument (PDF)       │
   └─────────────────────────┘
```

---

> **下一步**: 生成交付清单，将 Spec 拆分为可执行的阶段里程碑。
