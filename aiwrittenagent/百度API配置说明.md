# 百度语音识别API配置说明

## 📝 为什么需要配置？

录音转文字功能使用了百度语音识别API，需要API密钥才能使用。

**好消息：** 百度提供免费额度，每天50000次调用，足够个人使用！

---

## 🔑 获取API密钥（5分钟）

### 步骤1：注册百度AI账号

1. 访问：https://ai.baidu.com/
2. 点击右上角"登录/注册"
3. 使用手机号或百度账号登录

### 步骤2：创建应用

1. 登录后，点击"控制台"
2. 在左侧菜单找到"语音技术" → "语音识别"
3. 点击"创建应用"
4. 填写应用信息：
   - **应用名称：** CloudInk语音识别（随意填写）
   - **应用描述：** 个人学习使用（随意填写）
   - **接口选择：** 勾选"短语音识别"
   - **应用归属：** 个人
5. 点击"立即创建"

### 步骤3：获取密钥

创建成功后，会显示：
- **AppID：** 一串数字
- **API Key：** 一串字母数字组合
- **Secret Key：** 一串字母数字组合

**重要：** 复制保存 **API Key** 和 **Secret Key**

---

## 🛠️ 配置到应用中

### 方法1：修改代码（推荐）

1. 打开文件：
   ```
   app/src/main/java/com/cloudink/app/asr/BaiduAsrHelper.java
   ```

2. 找到第18-19行：
   ```java
   private static final String API_KEY = "YOUR_API_KEY_HERE";
   private static final String SECRET_KEY = "YOUR_SECRET_KEY_HERE";
   ```

3. 替换为你的密钥：
   ```java
   private static final String API_KEY = "你复制的API_KEY";
   private static final String SECRET_KEY = "你复制的SECRET_KEY";
   ```

4. 保存文件

5. 重新编译安装应用：
   ```bash
   gradlew assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

### 方法2：Android Studio

1. 在Android Studio中打开项目
2. 导航到 `app/src/main/java/com/cloudink/app/asr/BaiduAsrHelper.java`
3. 修改第18-19行的密钥
4. 点击运行按钮（绿色三角形）
5. 应用会自动编译并安装

---

## ✅ 验证配置

### 测试步骤

1. 打开应用，进入"语音转写"
2. 点击"导入录音"，选择一个录音文件
3. 点击"识别为文字"按钮
4. 如果配置正确：
   - 显示"正在识别录音，请稍候..."
   - 几秒后文字添加到编辑框
   - 提示"识别完成！"

### 如果配置错误

**错误1：仍然提示"请先配置API密钥"**
- 原因：密钥没有正确替换
- 解决：检查是否保存了文件，是否重新编译

**错误2：提示"获取access token失败"**
- 原因：API Key或Secret Key错误
- 解决：重新复制密钥，注意不要有空格

**错误3：提示"识别失败: err_no=110"**
- 原因：API Key对应的应用没有开通语音识别权限
- 解决：在百度控制台检查应用权限

---

## 📊 查看使用情况

1. 登录百度AI控制台：https://console.bce.baidu.com/ai/
2. 点击"语音技术" → "语音识别"
3. 查看"调用量统计"
4. 可以看到：
   - 今日调用次数
   - 本月调用次数
   - 剩余免费额度

---

## 💰 费用说明

### 免费额度

- **每天：** 50000次调用
- **每月：** 不限制
- **永久：** 免费

### 超出免费额度

如果每天调用超过50000次（基本不可能），会按以下收费：
- 0-50000次：免费
- 50001次以上：0.0035元/次

**对于个人使用，完全免费！**

---

## 🔒 安全建议

### 保护你的密钥

1. **不要分享** API Key和Secret Key
2. **不要上传** 到公开的代码仓库（如GitHub）
3. **定期更换** 密钥（可选）

### 如果密钥泄露

1. 登录百度AI控制台
2. 找到对应的应用
3. 点击"重置Secret Key"
4. 获取新密钥并重新配置

---

## 🎯 配置示例

### 正确的配置

```java
// ✅ 正确
private static final String API_KEY = "AbCdEf1234567890";
private static final String SECRET_KEY = "XyZ9876543210aBcDeF";
```

### 错误的配置

```java
// ❌ 错误：没有替换
private static final String API_KEY = "YOUR_API_KEY_HERE";

// ❌ 错误：有空格
private static final String API_KEY = " AbCdEf1234567890 ";

// ❌ 错误：有引号
private static final String API_KEY = ""AbCdEf1234567890"";

// ❌ 错误：缺少引号
private static final String API_KEY = AbCdEf1234567890;
```

---

## 🐛 常见问题

### Q1: 我没有百度账号怎么办？

A: 可以使用手机号注册，或者使用百度网盘、百度地图等百度产品的账号登录。

### Q2: 创建应用时需要企业认证吗？

A: 不需要，选择"个人"即可。

### Q3: API Key和Secret Key有什么区别？

A: 
- API Key：应用的公开标识
- Secret Key：应用的私密密钥，用于验证身份
- 两个都需要配置

### Q4: 可以多个应用共用一个密钥吗？

A: 可以，但建议每个应用创建独立的密钥，方便管理。

### Q5: 配置后需要重启手机吗？

A: 不需要，只需要重新编译安装应用即可。

### Q6: 可以不配置吗？

A: 可以，但录音转文字功能无法使用。其他功能（PDF阅读、输入法语音）不受影响。

---

## 📞 需要帮助？

如果配置过程中遇到问题：

1. **检查密钥格式**
   - 确保没有多余的空格
   - 确保在引号内
   - 确保没有特殊字符

2. **查看日志**
   ```bash
   adb logcat | grep BaiduAsrHelper
   ```

3. **测试网络**
   ```bash
   adb shell ping -c 3 baidu.com
   ```

4. **重新获取密钥**
   - 在百度控制台重置密钥
   - 重新配置

---

## ✅ 配置完成检查清单

- [ ] 已注册百度AI账号
- [ ] 已创建语音识别应用
- [ ] 已获取API Key和Secret Key
- [ ] 已修改BaiduAsrHelper.java文件
- [ ] 已保存文件
- [ ] 已重新编译安装应用
- [ ] 已测试录音识别功能
- [ ] 识别功能正常工作

---

**配置完成后，就可以使用录音转文字功能了！** 🎉

---

**相关文档：**
- 问题修复总结.md
- 修复说明和使用指南.md
- 快速测试指南.md
