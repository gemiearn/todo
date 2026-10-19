# Android 待办与番茄钟 App

一个供个人长期使用的 Android 待办与番茄钟应用。首版只使用**本地数据**，不包含账号、服务器、云同步、广告、分析 SDK 或任何付费功能。

完整的开发需求与实施说明见 [Android待办与番茄钟App_开发需求与实施说明.md](Android待办与番茄钟App_开发需求与实施说明.md)。

## 当前进度

**当前版本 0.4.2（versionCode 32）**，本地数据库 schema 版本 **v14**（本版无迁移）。阶段 0–3 与「专注统计、专注记录、设置、标签、习惯打卡、外观定制」等都已落地：

- **任务**：新建/编辑/完成/重开/软删除与回收站恢复、标题+备注、优先级（直接对应四象限四档）、一层子任务、重复任务（按规则生成真实实例，规则源那一条还会显示「本系列完成 k 次 · 共专注 X」）、创建/完成/删除等关键事件历史（带标题快照，默认收起）、滑动操作、卡片上直接 ▶「开始专注」。
- **清单**：内置收件箱 + 自定义清单（新增/改名/排序/删除时把任务移入收件箱，可给清单选一个 emoji 小图标）；左侧抽屉导航（**屏幕宽度 50%**，分「智能清单 / 我的清单」两组，清单前是图标或色点、计数是药丸徽章；分隔线以下固定「已完成 / 回收站 / 习惯 / 新建清单」，其中**习惯那一行带「今天还剩几条没打」的徽章**）。
- **视图**：今天 / 未来 / 逾期、完整日历（年·月·周·三日四种粒度、前后翻页、日期选中联动、显示范围与显示设置；月视图固定六行、末尾填下月日期，圆点未完成＝琥珀橙 / 已完成＝绿）、四象限（只读展示、按优先级分格、不收已经过期的事）、已完成、回收站。
- **标签**：标签管理（改名/选色/删除）、编辑器内直接新建并勾选、任务卡与详情显示实心色底标签。
- **筛选与搜索**：清单 / 优先级 / 标签三项「且」组合的显示范围筛选；列表内搜索与排序。
- **专注（番茄钟）**：正计时 / 倒计时 / 番茄钟三种模式，暂停继续、提前结束、番茄后短休息与长休息、自产白噪音；计时绑定真实任务；进程被杀后重开接着跑（被杀那段不计入时长）；只统计真正在跑的小段。
- **专注统计**：详情 / 趋势 / 时间线 / 最佳专注时间 / 年度热力图五张卡，按标签筛选、自定义日期区间；清单页顶部与单任务详情页各有一条小结。**逐条专注记录有自己的页**（入口＝专注页左滑面板里那行「专注记录」）：每条可以换关联任务、改时长（按整分钟；改时长会连「实际跑的小段」一起改，所以图表与时长始终自洽）、或者删掉。最佳专注时间的纵轴跟着当月峰值走、但**封顶一小时**。
- **习惯打卡**：独立页面（任务视图顶栏那个「清单 / 习惯」开关点一下就切过去），每天按目标次数打卡（点一下 +1、长按 −1），最近 7 天可补打，单位与单次步长可自定义，可给习惯选一个 emoji 小图标，突出「今天」那一列并有进度视觉，不进日历/不生成未来实例/没有连续天数惩罚。（2026-10-19 起**不再关联任务**——习惯独立存在。）
- **外观**：主题三选（跟随系统 / 浅色 / 深色）、四象限色系三选（经典 / 马卡龙 / 莫兰迪）、背景五选（原色 / 暖杏 / 薄荷 / 雾紫 / 暮蓝），以及**桌面图标六选**（番茄 / 计时环 / 时钟面 / 四色方块 / 青柠渐变 / 暖阳渐变——在设置里点一下即换，桌面入口会被系统重建一次）；清单与习惯的 emoji 图标备选**按种类分 15 页**（工作 / 学习 / 生活 / 健康 / 运动 / 饮食 / 出行 / 家居 / 购物 / 社交 / 娱乐 / 自然 / 动物 / 节日 / 符号），共 **210 个**，横向页签 + 网格挑；清单与习惯共用同一套，不再按用途分组。
- 应用名＝**专注清单**；全程**纯本地**：无权限、无网络、关闭系统自动备份，数据只在本机；不引入账号、服务器、云同步、广告或分析 SDK。
- **左滑面板按页分**：任务视图与日历左滑出来的是「清单抽屉」；**专注页是「导航面板」**——里面只有一行能跳过去的「专注记录」入口（不是把统计内容塞进来），统计图表仍走顶栏右上角那个图标；四象限与更多页不响应左滑。**换页时面板一律自动收回**，不会切过去还是拉出来的状态。

**上线后新增/变更的需求与当前待办，见需求说明第 13 节「增量需求与当前状态」**（该节是 0.3.5 之后的权威口径，与前文冲突以它为准）。

仍待后续：**本地提醒**（阶段 4）、**JSON 导出与受控恢复**、**release 固定签名与版本化 APK 发布**（阶段 5），以及需求说明第 13 节里仍未拍板／未做的条目（重复任务产品模型、跨天 session 统计归属、逾期判定是否改用创建日期、收件箱语义、日期入口重复等）。

## 技术与版本

| 项目 | 取值 |
|---|---|
| 语言 / UI | Kotlin 2.0.21 / Jetpack Compose（BOM 2024.10.01）+ Material 3 |
| 构建 | Gradle 8.9 + Android Gradle Plugin 8.7.2 |
| JDK | 17 |
| compileSdk / targetSdk | 35（Android 15，可运行于 Android 15 / 16 真机） |
| minSdk | 26（Android 8.0） |
| applicationId | `com.personal.todo` |
| versionCode / versionName | 32 / 0.4.2（在 `gradle.properties` 中集中管理） |
| 数据库 schema | v14（`app/schemas/`，随每次模型变化提交） |

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

阶段 0–3 与专注、统计、设置、标签、习惯打卡、外观定制（含桌面图标切换）均已落地（见上文「当前进度」）。接下来依次做：**本地通知提醒**（阶段 4）→ **JSON 导出与受控恢复** → **release 固定签名与版本化 APK 发布**（阶段 5），中间穿插需求说明第 13 节里列出的新增需求。每批独立提交、保持主分支可构建、每批都要求构建 + 全量单元测试 + lint 全绿，详见需求说明第 9、10、13 节。
