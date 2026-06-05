# Contributing

感谢你愿意改进拾象。这个项目优先关注稳定、清晰、可维护的本地下载体验。

## 开发环境

- Android Studio
- JDK 17
- Android SDK 35
- Python 3.10+

构建：

```bash
./gradlew :app:assembleDebug
```

如果 Chaquopy 找不到 Python：

```bash
./gradlew :app:assembleDebug -Pchaquopy.python=/path/to/python3
```

快速编译验证：

```bash
./gradlew :app:compileDebugKotlin
```

## 提交前检查

- 不提交 `local.properties`、keystore、Cookie、日志、下载历史或个人媒体文件。
- 不提交生成目录，如 `.gradle/`、`build/`、`app/build/`。
- 新增平台兼容逻辑时，记录失败场景和可复现链接类型。
- 涉及下载、Cookie、分享、导出、播放器的改动，需要说明手动验证步骤。
- UI 改动请确认小屏设备上文字不重叠、不溢出。

## 可接受的贡献

- 下载失败分类和错误提示改进。
- 平台解析兼容性修复。
- Cookie/WebView 辅助流程改进。
- 本地媒体库、播放器、分享和导出体验优化。
- 构建、文档、测试和可维护性改进。

## 不接受的贡献

- 绕过 DRM、付费墙、账号权限或平台访问控制的实现。
- 收集、上传、共享用户 Cookie 或下载历史的实现。
- 隐蔽网络请求、广告追踪或无关分析 SDK。
- 包含真实账号、Cookie、私有链接或 copyrighted media 的测试材料。

## Issue 建议

报告问题时请尽量提供：

- App 版本。
- Android 版本和设备型号。
- 链接所属平台。
- 错误提示或相关日志片段。
- 是否需要登录态、验证码或会员权限。

不要公开粘贴 Cookie、token 或私人链接。
