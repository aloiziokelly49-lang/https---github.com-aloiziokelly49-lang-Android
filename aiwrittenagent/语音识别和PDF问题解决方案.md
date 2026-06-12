# 语音识别和PDF问题解决方案

## 问题总结

根据日志分析，发现以下问题：

1. **PDF显示问题**：RecyclerView初始化时宽度为0，导致PDF页面无法正确渲染
2. **录音文件转文字**：当前只能播放，没有语音识别功能
3. **vivo设备语音识别**：vivo X90不支持系统SpeechRecognizer，只能使用输入法

---

## 已修复的问题

### 1. PDF显示问题 ✅

**修复内容：**
- 改进了RecyclerView宽度获取逻辑
- 添加了延迟渲染机制，确保布局完成后再显示
- 增强了错误处理和用户反馈

**测试方法：**
1. 打开应用，进入"分屏阅读"
2. 点击顶部工具栏选择PDF文件
3. 左侧应该能看到PDF页面显示

---

## 录音文件转文字解决方案

### 方案一：使用在线语音识别服务（推荐）

#### 1. 百度语音识别 API

**优点：**
- 识别准确率高
- 支持中文、英文等多种语言
- 有免费额度（每天50000次调用）

**集成步骤：**

1. 注册百度AI开放平台账号：https://ai.baidu.com/
2. 创建应用获取 API Key 和 Secret Key
3. 添加依赖到 `build.gradle`：

```gradle
dependencies {
    // 百度语音识别SDK
    implementation 'com.baidu.aip:java-sdk:4.16.14'
    // 或使用轻量级HTTP客户端
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
}
```

4. 创建语音识别工具类：

```java
public class BaiduAsrHelper {
    private static final String APP_ID = "你的APP_ID";
    private static final String API_KEY = "你的API_KEY";
    private static final String SECRET_KEY = "你的SECRET_KEY";
    
    public interface AsrCallback {
        void onSuccess(String result);
        void onError(String error);
    }
    
    public static void recognizeAudioFile(String audioFilePath, AsrCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 1. 读取音频文件
                byte[] audioData = Files.readAllBytes(Paths.get(audioFilePath));
                
                // 2. 获取access_token
                String accessToken = getAccessToken();
                
                // 3. 调用语音识别API
                String result = callAsrApi(audioData, accessToken);
                
                // 4. 解析结果
                JSONObject json = new JSONObject(result);
                if (json.has("result")) {
                    JSONArray results = json.getJSONArray("result");
                    if (results.length() > 0) {
                        callback.onSuccess(results.getString(0));
                    }
                } else {
                    callback.onError("识别失败");
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }
    
    private static String getAccessToken() throws IOException {
        String url = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials"
                + "&client_id=" + API_KEY
                + "&client_secret=" + SECRET_KEY;
        
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        
        JSONObject json = new JSONObject(response.body().string());
        return json.getString("access_token");
    }
    
    private static String callAsrApi(byte[] audioData, String accessToken) throws IOException {
        String url = "https://vop.baidu.com/server_api";
        
        // 构建请求参数
        JSONObject params = new JSONObject();
        params.put("format", "wav");  // 或 "pcm", "amr", "m4a"
        params.put("rate", 16000);
        params.put("channel", 1);
        params.put("cuid", "android_device");
        params.put("token", accessToken);
        params.put("speech", Base64.encodeToString(audioData, Base64.NO_WRAP));
        params.put("len", audioData.length);
        
        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(
            params.toString(), 
            MediaType.parse("application/json")
        );
        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();
            
        Response response = client.newCall(request).execute();
        return response.body().string();
    }
}
```

5. 在VoiceTranscribeActivity中使用：

```java
private void recognizeImportedAudio() {
    if (importedAudioPath == null) return;
    
    Toast.makeText(this, "正在识别录音...", Toast.LENGTH_SHORT).show();
    
    BaiduAsrHelper.recognizeAudioFile(importedAudioPath, new BaiduAsrHelper.AsrCallback() {
        @Override
        public void onSuccess(String result) {
            runOnUiThread(() -> {
                viewModel.appendSegment(result);
                Toast.makeText(VoiceTranscribeActivity.this, 
                    "识别完成", Toast.LENGTH_SHORT).show();
            });
        }
        
        @Override
        public void onError(String error) {
            runOnUiThread(() -> {
                Toast.makeText(VoiceTranscribeActivity.this, 
                    "识别失败: " + error, Toast.LENGTH_LONG).show();
            });
        }
    });
}
```

#### 2. 讯飞语音识别 API

**优点：**
- 国内领先的语音识别技术
- 支持方言识别
- 有免费额度

**官网：** https://www.xfyun.cn/

**集成步骤类似百度，参考官方文档。**

---

### 方案二：使用离线语音识别（Vosk）

**优点：**
- 完全离线，不需要网络
- 免费开源
- 隐私保护

**缺点：**
- 识别准确率略低于在线服务
- 需要下载语言模型（约50MB）

**集成步骤：**

1. 添加依赖到 `build.gradle`：

```gradle
dependencies {
    implementation 'com.alphacephei:vosk-android:0.3.47'
}
```

2. 下载中文语言模型：
   - 访问：https://alphacephei.com/vosk/models
   - 下载 `vosk-model-small-cn-0.22.zip`（约42MB）
   - 解压后放到 `app/src/main/assets/vosk-model-cn/`

3. 创建Vosk识别工具类：

```java
public class VoskAsrHelper {
    private Model model;
    
    public VoskAsrHelper(Context context) {
        try {
            // 从assets复制模型到内部存储
            File modelDir = new File(context.getFilesDir(), "vosk-model-cn");
            if (!modelDir.exists()) {
                copyAssets(context, "vosk-model-cn", modelDir);
            }
            model = new Model(modelDir.getPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public String recognizeAudioFile(String audioFilePath) throws IOException {
        Recognizer recognizer = new Recognizer(model, 16000);
        
        FileInputStream fis = new FileInputStream(audioFilePath);
        byte[] buffer = new byte[4096];
        int bytesRead;
        
        while ((bytesRead = fis.read(buffer)) != -1) {
            recognizer.acceptWaveForm(buffer, bytesRead);
        }
        
        String result = recognizer.getFinalResult();
        recognizer.close();
        fis.close();
        
        // 解析JSON结果
        JSONObject json = new JSONObject(result);
        return json.optString("text", "");
    }
    
    private void copyAssets(Context context, String assetPath, File targetDir) throws IOException {
        AssetManager assetManager = context.getAssets();
        String[] files = assetManager.list(assetPath);
        
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        for (String filename : files) {
            String assetFilePath = assetPath + "/" + filename;
            File outFile = new File(targetDir, filename);
            
            if (assetManager.list(assetFilePath).length == 0) {
                // 是文件，复制
                InputStream in = assetManager.open(assetFilePath);
                OutputStream out = new FileOutputStream(outFile);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.close();
            } else {
                // 是目录，递归
                copyAssets(context, assetFilePath, outFile);
            }
        }
    }
}
```

---

## vivo设备语音识别解决方案

### 问题分析

vivo X90等设备不支持Android系统的`SpeechRecognizer` API，这是厂商定制的限制。

### 解决方案

#### 方案一：使用输入法语音输入（当前实现）✅

**优点：**
- 无需额外开发
- 利用搜狗输入法的语音识别能力
- 识别准确率高

**使用方法：**
1. 在语音转写界面点击"打开输入法"按钮
2. 点击搜狗输入法键盘上的麦克风图标🎤
3. 说话后自动转为文字
4. 点击发送添加到文本框

#### 方案二：集成第三方语音识别SDK

**推荐SDK：**

1. **讯飞语音SDK**
   - 官网：https://www.xfyun.cn/
   - 支持实时语音识别
   - 兼容所有Android设备

2. **百度语音SDK**
   - 官网：https://ai.baidu.com/tech/speech
   - 提供离线+在线识别
   - 支持长语音识别

**集成示例（讯飞）：**

```gradle
dependencies {
    implementation 'com.iflytek:msc:5.0.0'
}
```

```java
public class XunfeiAsrHelper {
    public static void initSpeech(Context context) {
        // 初始化讯飞语音
        SpeechUtility.createUtility(context, "appid=你的APPID");
    }
    
    public static void startListening(Context context, RecognizerListener listener) {
        SpeechRecognizer recognizer = SpeechRecognizer.createRecognizer(context, null);
        
        // 设置参数
        recognizer.setParameter(SpeechConstant.DOMAIN, "iat");
        recognizer.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        recognizer.setParameter(SpeechConstant.ACCENT, "mandarin");
        
        // 开始识别
        recognizer.startListening(listener);
    }
}
```

#### 方案三：使用微信式按住说话（需要在线服务）

实现类似微信的按住说话功能：

1. 按住按钮时开始录音（使用MediaRecorder）
2. 松开按钮时停止录音
3. 将录音文件上传到语音识别服务（百度/讯飞）
4. 获取识别结果并显示

**实现代码框架：**

```java
binding.btnHoldSpeak.setOnTouchListener((v, event) -> {
    switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            // 开始录音
            startRecording();
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            // 停止录音并识别
            stopRecordingAndRecognize();
            break;
    }
    return true;
});

private void stopRecordingAndRecognize() {
    String audioPath = stopRecording();
    
    // 使用百度/讯飞API识别
    BaiduAsrHelper.recognizeAudioFile(audioPath, new AsrCallback() {
        @Override
        public void onSuccess(String result) {
            viewModel.appendSegment(result);
        }
        
        @Override
        public void onError(String error) {
            Toast.makeText(this, "识别失败", Toast.LENGTH_SHORT).show();
        }
    });
}
```

---

## 推荐实施方案

### 短期方案（1-2天）

1. **修复PDF显示** ✅ 已完成
2. **添加录音识别按钮**：在导入录音后显示"识别为文字"按钮
3. **集成百度语音API**：实现录音文件转文字功能

### 中期方案（1周）

1. **集成讯飞语音SDK**：实现vivo设备的实时语音识别
2. **优化用户体验**：添加识别进度提示、错误重试等

### 长期方案（可选）

1. **添加离线识别**：集成Vosk实现无网络环境下的语音识别
2. **支持多语言**：添加英文、粤语等语言识别

---

## 代码修改建议

### 1. 在VoiceTranscribeActivity添加识别按钮

在布局文件 `activity_voice_transcribe.xml` 中添加：

```xml
<Button
    android:id="@+id/btn_recognize_audio"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="识别为文字"
    android:visibility="gone"
    app:icon="@drawable/ic_mic" />
```

### 2. 添加识别功能

```java
// 在importAudioUri成功后显示按钮
binding.btnRecognizeAudio.setVisibility(View.VISIBLE);

// 点击识别
binding.btnRecognizeAudio.setOnClickListener(v -> {
    recognizeImportedAudio();
});
```

---

## 测试建议

### PDF显示测试
1. 准备不同大小的PDF文件（1页、10页、100页）
2. 测试横屏和竖屏模式
3. 测试快速滚动性能

### 语音识别测试
1. 测试不同格式的录音文件（WAV、MP3、M4A）
2. 测试不同长度的录音（10秒、1分钟、5分钟）
3. 测试不同音质的录音
4. 测试网络异常情况

---

## 常见问题

**Q: 百度语音API收费吗？**
A: 有免费额度，每天50000次调用。超出后按次收费，价格很便宜。

**Q: Vosk离线识别准确率如何？**
A: 对于标准普通话，准确率约85-90%。不如在线服务，但完全免费且离线可用。

**Q: vivo设备为什么不支持系统语音识别？**
A: 这是vivo的系统定制策略，可能出于安全或性能考虑。建议使用第三方SDK。

**Q: 如何选择语音识别方案？**
A: 
- 需要高准确率 → 百度/讯飞在线API
- 需要离线使用 → Vosk
- 快速实现 → 使用输入法语音输入（当前方案）

---

## 参考资源

- 百度语音识别文档：https://ai.baidu.com/ai-doc/SPEECH/Vk38lxily
- 讯飞语音识别文档：https://www.xfyun.cn/doc/asr/voicedictation/Android-SDK.html
- Vosk官网：https://alphacephei.com/vosk/
- Android SpeechRecognizer文档：https://developer.android.com/reference/android/speech/SpeechRecognizer
