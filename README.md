# Artisan AI — 高奢风格 AI 图像生成 App

> 基于 Nano Banana 2 (gemini-3.1-flash-image-preview) 的 Android 生图应用

---

## 功能概览

| 功能 | 说明 |
|------|------|
| 🎨 AI 生图 | 调用 Nano Banana 2，支持 2K/4K、14 种宽高比 |
| 🔍 图像搜索增强 | Grounding 模式，参考真实世界图像 |
| 🧠 思维模式 | minimal / high 两档，复杂提示词更精准 |
| ✨ AI 润色提示词 | Gemini 3.1 Flash Lite 将中文描述转为专业英文提示词 |
| 🖼 反推参考图 | 上传参考图，AI 分析并生成对应提示词 |
| 📋 并发任务队列 | 最多 10 个任务同时运行，实时进度展示 |
| 🗂 图库管理 | 内部图库 + 一键下载到系统相册 |
| 📱 自适应布局 | 手机竖屏（底部 Tab）/ 平板横屏（三栏布局）|
| ⚙️ 用户自填 Key | 设置页输入自己的 API Key，安全可控 |

---

## 快速开始（本地开发）

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 35

### 步骤

```bash
# 1. 克隆仓库
git clone https://github.com/你的用户名/ArtisanAI.git
cd ArtisanAI

# 2. 用 Android Studio 打开项目
# File → Open → 选择 ArtisanAI 文件夹

# 3. 等待 Gradle 同步完成

# 4. 连接设备或启动模拟器，点击运行
```

### 运行后配置

1. 打开 App → 点击右上角 **设置** 齿轮图标
2. 填写您的 **API Key**（前往 [api.apiyi.com](https://api.apiyi.com) 注册获取）
3. Base URL 默认填写 `https://api.apiyi.com`，无需修改
4. 点击 **保存配置** 即可开始生图

---

## GitHub Actions 自动打包

### 首次设置

1. 在 GitHub 创建仓库（可私有）
2. 推送代码到 `main` 分支
3. GitHub Actions 自动触发构建

```bash
git init
git add .
git commit -m "Initial commit: ArtisanAI"
git remote add origin https://github.com/你的用户名/ArtisanAI.git
git push -u origin main
```

### 下载 APK

1. 进入 GitHub 仓库 → **Actions** 标签
2. 点击最新的构建任务
3. 在页面底部 **Artifacts** 区域下载 APK
4. 安装到手机（需开启「允许未知来源」）

---

## API 说明

### 生图 API（Nano Banana 2）

```
POST https://api.apiyi.com/v1beta/models/gemini-3.1-flash-image-preview:generateContent
```

**默认参数：**
- 分辨率：2K
- 宽高比：9:16
- 思维模式：minimal

### Agent API（Gemini 3.1 Flash Lite）

```
POST https://api.apiyi.com/v1/chat/completions
model: gemini-3.1-flash-lite-preview
```

用于润色提示词和反推参考图提示词。

---

## 项目结构

```
app/src/main/java/com/artisanai/
├── ArtisanApp.kt              # Application 类
├── MainActivity.kt            # 入口 Activity
├── data/
│   ├── local/Database.kt      # Room 数据库
│   └── model/Models.kt        # 数据模型
├── repository/
│   ├── ImageGenRepository.kt  # 生图 API
│   ├── AgentRepository.kt     # 润色/反推 API
│   └── GalleryRepository.kt   # 图库存储
├── util/
│   └── ApiKeyManager.kt       # Key 管理
├── viewmodel/
│   └── MainViewModel.kt       # 状态管理 + 并发控制
└── ui/
    ├── theme/Theme.kt         # 高奢主题（黑金）
    ├── components/Components.kt  # 通用组件
    └── screens/
        ├── MainScreen.kt      # 自适应主界面
        ├── GeneratePanel.kt   # 生成控制面板
        ├── TaskQueuePanel.kt  # 任务队列
        ├── GalleryPanel.kt    # 图库
        └── SettingsScreen.kt  # 设置页
```

---

## 版本管理

- **包名**：`com.artisanai.v3`（3.0 起）。旧版 2.0 为 `com.artisanai.v2`，**两者独立共存**，互不覆盖；确认 3.0 可用后可自行卸载旧版 2.0。
- **签名**：固定密钥库，经 GitHub Secrets 注入 CI（`ARTISAN_KEYSTORE_BASE64` / `_PASSWORD` / `ARTISAN_KEY_ALIAS` / `ARTISAN_KEY_PASSWORD`）。**同一包名内所有版本共用此密钥，因此 3.0.x 之间可直接覆盖升级**（无需卸载）。
- **版本号**：`versionCode = CI run_number`（单调递增，永不回退）；`versionName = 3.0.<run_number>`，与构建号一一对应。
- **升级规则**：换包名（大版本不兼容/需共存）才会要求重新安装；否则保持包名 + 固定签名即可一路覆盖升级。**切勿更换或丢失签名密钥**，否则后续版本将无法覆盖升级（密钥库备份与密码见本机 `artisan-release-keystore-info.txt`）。

## 注意事项

- API Key 使用 SharedPreferences 本地存储，不会上传服务器
- 生成的图片保存在 App 内部存储，需手动点击下载才会存入系统相册
- Nano Banana 2 当前为 Preview 状态，偶有不稳定
- 4K 分辨率生成时间约 15-25 秒，请耐心等待
