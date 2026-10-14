# Android 待办与番茄钟 App

一个供个人长期使用的 Android 待办与番茄钟应用。首版只使用**本地数据**，不包含账号、服务器、云同步、广告、分析 SDK 或任何付费功能。

完整的开发需求与实施说明见 [Android待办与番茄钟App_开发需求与实施说明.md](Android待办与番茄钟App_开发需求与实施说明.md)。

## 当前进度：阶段 1（本地任务核心）

阶段 0 建立了可构建、可测试、可下载的最小骨架；阶段 1 在此基础上实现了本地任务核心（**仍不包含**番茄钟、提醒、重复任务、习惯打卡与云同步）。

阶段 1 已包含：

- Room 本地数据库（`task_lists` / `tasks` / `task_events`，schema 版本 1，schema JSON 随仓库提交）。
- 内置收件箱与用户自定义清单：新增、改名、排序（上移/下移）、删除清单时把任务移入收件箱（禁止级联删除任务）。
- 任务的新建、编辑、完成、重新打开、软删除与回收站恢复。
- 任务标题、备注、优先级（普通/高/紧急）与一层子任务。
- 关键操作历史（创建、完成、重新打开、删除、恢复），保留标题快照，删除任务也不丢历史。
- 基础 Compose 界面：抽屉清单、任务列表、任务详情、任务编辑、已完成、回收站。
- 覆盖上述业务规则的 JVM 单元测试与 Room 数据库测试（含“重启后数据仍在”）。

仍待后续阶段：日期/截止日期与今日·未来·逾期视图、搜索与排序、简版月历、番茄钟与专注历史、本地提醒、主题设置、JSON 导出与固定签名流程（详见需求说明第 10 节）。

## 技术与版本

| 项目 | 取值 |
|---|---|
| 语言 / UI | Kotlin 2.0.21 / Jetpack Compose（BOM 2024.10.01）+ Material 3 |
| 构建 | Gradle 8.9 + Android Gradle Plugin 8.7.2 |
| JDK | 17 |
| compileSdk / targetSdk | 35（Android 15，可运行于 Android 15 / 16 真机） |
| minSdk | 26（Android 8.0） |
| applicationId | `com.personal.todo` |
| versionCode / versionName | 3 / 0.1.3（在 `gradle.properties` 中集中管理） |

`versionCode` 与 `versionName` 统一写在 [gradle.properties](gradle.properties) 的 `appVersionCode` / `appVersionName`，CI 读取 `appVersionName` 用于生成 artifact 名称。

## 项目结构

```
.
├── .github/workflows/android-ci.yml   # CI：构建 + 单测 + lint + 上传 APK
├── app/
│   ├── build.gradle.kts               # app 模块配置
│   ├── proguard-rules.pro
│   ├── schemas/                       # Room 导出的 schema JSON（纳入版本控制）
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml    # 无权限、无网络、关闭自动备份
│       │   ├── java/com/personal/todo/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/              # Repository、容器与 Room 数据库（db/）
│       │   │   ├── model/             # Priority、TaskEventType 等纯模型
│       │   │   ├── ui/                # Compose 页面与组件
│       │   │   └── viewmodel/         # Compose 用的 ViewModel
│       │   └── res/                   # 文案、主题、图标、备份规则
│       └── test/java/com/personal/todo/  # 业务规则与 Room 测试
├── gradle/
│   ├── libs.versions.toml             # 依赖与版本集中管理
│   └── wrapper/                       # Gradle Wrapper（含 gradle-wrapper.jar）
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew / gradlew.bat
```

## 使用 GitHub Actions 构建与下载 APK

**不需要在你的电脑上安装 Android Studio、Android SDK 或 Kotlin。** 所有构建与测试都在 GitHub Actions 云端完成。

触发条件：

- 推送到 `main`（或 `master`）分支；
- 针对 `main` / `master` 的 Pull Request；
- 也可以在 Actions 页面手动触发（`Run workflow`）。

工作流步骤（见 [android-ci.yml](.github/workflows/android-ci.yml)）：

1. 检出仓库；
2. 安装 JDK 17；
3. 用仓库内的 Gradle Wrapper 构建（依赖缓存仅为加速，不是构建成功的前提）；
4. 运行 `assembleDebug`（构建 Debug APK）、`testDebugUnitTest`（单元测试）、`lintDebug`（Android lint）——任一步失败，整个任务失败；
5. 上传可下载的 Debug APK。

**下载 APK 的位置**：仓库页面 → **Actions** → 选择对应的工作流运行 → 页面底部 **Artifacts** 区域：

- `app-debug-v<版本号>-<7位commit SHA>`：Debug APK（保留 14 天）；
- `unit-test-report-<SHA>`：单元测试报告（保留 7 天）；
- `lint-report-<SHA>`：Lint 报告（保留 7 天）。

失败或不产出 APK 时，工作流会明确失败，不会上传空文件冒充成功。

## 手机安装与升级注意

- Debug APK 由 GitHub 托管 runner 临时生成，**不能保证每次使用同一个 debug 签名密钥**。签名变化时，新 APK 可能无法覆盖安装旧版本，需要先卸载旧版本再安装——卸载会清除 App 本地数据，**不要把它当作长期升级方式**。
- 长期自用需要固定 release keystore，并通过 GitHub Secrets 提供给构建任务。密钥只应对私有仓库授权用户可见，**不得提交到 Git、写入日志或放入公开 artifact**。（该流程属于阶段 5。）
- 首次配置固定签名前，请先确认仓库为私有仓库。

## 本地开发（可选）

本项目的标准构建入口是 GitHub Actions，不要求本机安装 Android 环境。若确需在本机构建/运行，需要自行准备 JDK 17 与 Android SDK（`compileSdk 35`、`build-tools 35.0.0`），然后使用仓库内的 Wrapper：

```bash
./gradlew assembleDebug        # 构建 Debug APK
./gradlew testDebugUnitTest    # 单元测试
./gradlew lintDebug            # Android lint
```

## 后续阶段

阶段 1 起依次实现本地任务核心、日期与列表、番茄钟与历史、通知提醒、数据导出与签名稳定化。每阶段独立提交并保持主分支可构建，详见需求说明第 10 节。
