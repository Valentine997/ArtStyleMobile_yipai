# 🎨 ArtStyle Mobile

> 一款基于轻量化深度学习的纯本地离线图像风格迁移 Android 应用。无需联网、保护隐私，一键将照片转换为艺术风格。

## ✨ 核心特性
- 📸 **灵活输入**：支持系统相机拍照 / 相册选图
- 🎨 **内置风格**：预置 4 种艺术风格（糖果 / 马赛克 / 雨之公主 / 乌迪）
- 📱 **纯端侧推理**：基于 PyTorch Mobile 加载 `.ptl` 模型，全程离线运行，零网络依赖
- ⚡ **性能优化**：固定 512×512 输入 + TorchScript 算子融合，GPU 推理耗时 `<5s`
- 💾 **便捷导出**：一键保存至系统相册，支持调用原生分享面板

## 🛠 技术栈
- **移动端**：Kotlin / Android Jetpack (ViewModel + Coroutines) / Room (SQLite)
- **AI 推理**：PyTorch Mobile (`org.pytorch:pytorch_android`) / TorchScript Lite
- **模型底座**：Fast Neural Style Transfer (官方权重微调 → `.ptl` 导出)
- **目标平台**：Android 8.0+ (API 26) / ARM64-v8a

## 🚀 快速开始
1. 克隆仓库：`git clone https://github.com/你的用户名/ArtStyleMobile.git`
2. 使用 **Android Studio** (推荐 Iguana 或更高版本) 打开项目根目录
3. 同步 Gradle 依赖后，连接真机或模拟器（需支持 GPU 加速）
4. 点击 `Run` 即可编译安装并体验

> 📦 模型文件已存放于 `app/src/main/assets/` 目录，APK 安装后即可直接调用，无需额外下载。

## 📁 项目结构
├── app/src/main/java/... # Kotlin 源码 (UI / 业务逻辑 / 推理封装)

├── app/src/main/res/ # 布局、主题、矢量图标

├── app/src/main/assets/ # .ptl 模型文件与静态资源

├── fast_neural_style/ # PC端微调与导出脚本 (Python)

└── docs/ # 需求规格说明书 / 数据库设计说明书等

## 📝 说明
本项目为高校学生软件创新实践项目，遵循 **MVP 原则** 优先交付核心功能。所有图像数据仅在本地内存流转，不采集、不上传任何用户隐私信息。详细设计文档见 `docs/` 目录。

## 📄 License
MIT License © 2026 ArtStyle Mobile Team  
本项目基于开源技术构建，仅供学习与技术交流使用。
