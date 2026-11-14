<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-Java%2026.3-5D9C3F" alt="Minecraft Java 26.3">
  <img src="https://img.shields.io/badge/Rust-stable-B7410E?logo=rust&logoColor=white" alt="Rust">
  <a href="https://github.com/ludovic111/horizon"><img src="https://img.shields.io/badge/fork%20of-Horizon-F0795A" alt="Fork of Horizon"></a>
  <img src="https://img.shields.io/badge/platform-Android-2A2470" alt="Android">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-3B2F7E" alt="MIT license"></a>
  <a href="https://discord.gg/7xcp8Y5nkv"><img src="https://img.shields.io/badge/Discord-join-5865F2?logo=discord&logoColor=white" alt="Discord"></a>
</p>

<p align="center">
  <a href="#下载"><b>下载</b></a> ·
  <a href="https://discord.gg/7xcp8Y5nkv"><b>Discord</b></a> ·
  <a href="#android-现状"><b>Android 现状</b></a> ·
  <a href="roadmap/starling.md"><b>路线图</b></a> ·
  <a href="#模组"><b>模组</b></a> ·
  <a href="#从源码构建"><b>从源码构建</b></a> ·
  <a href="CONTRIBUTING.md"><b>参与贡献</b></a> ·
  <a href="https://github.com/ludovic111/horizon"><b>Horizon</b></a>
</p>

<p align="center"><a href="README.en.md">English</a> | 简体中文</p>

# Starling

**Starling（前身为 Horizon Android）是 Minecraft: Java 版 26.3 在 Android 上的轻量移植。** 由 [JuanLuci](https://github.com/JuanLuci)（Starling 创始人）与 [Vasysik](https://github.com/Vasysik)（Horizon Android 创始人）共同发起。它是 [Horizon](https://github.com/ludovic111/horizon) 的分支——Horizon 是用 Rust 重写的 Java 版游戏：读取同样的存档、遵循同样的规则、生成同样的地形，并通过差分测试与 Java 版对照验证。你使用自己副本的文件来游玩；它免费、开源且非官方。它原生运行，没有 Java 虚拟机、也没有 OpenGL 转译层，因此在手机上启动快、运行流畅。

> 非官方 Minecraft 产品，未获 Mojang 或 Microsoft 批准或关联。Starling 不包含任何 Minecraft 代码、资源或数据：你需要自己合法获得的 Minecraft: Java Edition 26.3 副本。

## 下载

**欢迎加入 [Discord](https://discord.gg/7xcp8Y5nkv)** 获取帮助、提交 bug 和了解动态。

**[Starling v0.8.0](https://github.com/starling-client/starling/releases/latest)**。已经在用 Starling？在游戏内点 **更新** 即可。第一次用？请从[发布页](https://github.com/starling-client/starling/releases)下载 `starling-0.8.0-arm64-v8a.apk` 并在手机上打开（Android 会请你允许浏览器或文件管理器安装应用）。

**你需要** Android 8 或更高版本、兼容的图形驱动，以及约 2 GB 可用空间。ABI、GPU 与准备阶段的限制见 [Android 架构](docs/android-armv7.md)；32 位 x86 需导入准备好的资源。使用拥有 Minecraft: Java Edition 的 Microsoft 账户登录，或离线游玩（多数服务器需要登录）。没有电脑：首次启动时 Starling 会从 Mojang 服务器下载游戏文件并在手机上完成准备，仅此一次（在 POCO F6 Pro 上约 79 秒，较慢的手机会更久）。等待期间可以去钓鱼或改设置。

开发在 [starling-client/starling](https://github.com/starling-client/starling) 的 `starling` 分支进行，Starling 与 Horizon Android 已在那里合为一款应用。该分支每次推送的构建产物是 **Starling Preview**，见 [GitHub Actions](https://github.com/starling-client/starling/actions/workflows/android.yml)（一次运行的 `starling-apk-<abi>` 构件；GitHub 下载构件需要登录）：它是独立的应用，与正式版并存，拥有自己的 `StarlingPreview` 文件夹和登录状态。你也可以[从源码构建](#从源码构建)。

## 今天能玩到什么

- **在手机上完成准备：** 用 Microsoft 账户登录后，游戏文件来自 Mojang，由一个专门拉取、用完即删的 Java 运行时在手机上完成准备，覆盖游戏自带的所有语言。等待期间可以在小池塘钓鱼，或在一张为该界面手绘的皮肤里改设置（游戏本身的外观也是准备的一部分）。在电脑上准备好的资源同样可以打开。
- **游戏本体：** 单人游戏，含原版的世界生成、生物、村民、袭击、红石、全部命令、HUD 和配方书，存档为原版格式，应用离开界面即保存。1.8 至 26.2 的世界按原版方式升级。Starling 紧跟 Horizon：v0.2.0 对应其 2026-11-02 的 main 分支。
- **触控操作：** 屏幕上的每个动作，从移动摇杆到快捷栏、聊天、命令和 F3，布局可在游戏内（进入世界之前也可以）移动、缩放和调透明度，"潜行"支持按住或切换。基岩版的操控模式：摇杆（浮动或固定）或方向键，以及点按交互（点按攻击生物或使用手指下的方块，长按破坏方块、与生物或空中物品交互）或瞄准十字线。长按 F3 提供其组合键（区块边界、碰撞箱、提示框……）；"选择方块"和玩家列表可作为按钮开启；方块被破坏或物品被拆分时手机会振动，还支持转屏瞄准（陀螺仪，默认关闭）。它们使用基岩版原版的按钮样式，由手机在准备完成后从 Mojang 的示例仓库获取（Starling 本体不携带任何按钮素材，只带名称与哈希），否则使用 Starling 自绘的按钮。
- **自定义控件：** 打开 **H > 控件 > 自定义控件**，或 **选项 > 控件 > 自定义布局**。Horizon Android 的编辑器支持 MCPE 与 GoyControl 预设、独立的配置分组、撤销/重做、按钮尺寸、颜色、可见性，以及最多三组输入绑定。**交互时视角**包含攻击与使用/放置手势；**360 度移动**默认关闭。见 [Android 控件](docs/android-controls.md)。
- **基岩版物品栏：** 创造模式物品栏将物品与你的背包并排显示，含六行物品和一个 2x2 合成格，点按即取整组。默认开启；想用 Java 版布局可在 **选项 > 控件** 中关闭。
- **名字与皮肤：** 在"选项 > 皮肤自定义"中设置存档使用的名字，并从本地文件（或 `Starling/skins`）选择皮肤，经典或纤细模型皆可；用 Starling 打开皮肤 PNG 会立即穿上。
- **手机自带键盘：** 点按文本框唤起，点按旁边收起，在所有界面生效。聊天、告示牌和成书中支持联想输入、自动更正和滑动输入（命令与搜索不做自动更正），聊天栏有"粘贴"按钮，仅有一个补全时按 Enter 直接执行命令（如 `/gamemode c` 会执行 `/gamemode creative`）。
- **从 Horizon Android 迁移：** Starling 会在首次设置或标题界面主动提出迁移你的世界和资源包并完成复制；Horizon Android 保留自己的。
- **文件触手可及：** 世界、资源包、光影包、模组和截图都在手机存储的 `Starling` 文件夹里，任何文件管理器都能打开（首次启动会婉拒请求，之后可通过"打开资源包文件夹"按钮再次授权）。用 Starling 打开 `.starling`、`.hmod` 或 `.hnmod` 文件即可添加资源包、世界或模组。
- **为手机而生：** 首次启动即按设备选定设置，并配有显示 FPS、内存与温度的性能悬浮窗。
- **Horizon 的原生附加内容：** 新菜单（原版菜单一开即回）、图形预设等，凡会改变原版观感的默认关闭。见 [设置](https://playhorizon.dev/docs/settings)。
- **资源包与数据包：** 资源包可用，数据包可加载函数、谓词与物品修饰器。
- **服务器：** 离线模式服务器、经 Velocity 等代理的服务器，支持其资源包、彩色聊天、Boss 血条、告示牌、工作站界面（附魔、铁砧、锻造……）、飞行及其设置的属性。插件服务器的对话框、聊天按钮、自定义盔甲（ItemsAdder、Nexo）、骑乘生物的动画模型（ModelEngine、BetterModel）与可点击的家具可用，触控摇杆的移动方式为各服务器的反作弊（GrimAC 等）所接受：自由视角、方向键或模拟摇杆，按服务器记忆。
- **光影包：** Iris 与 OptiFine 光影（如 Complementary、MakeUp、BSL、Photon、Bliss）可在手机上运行，加载时自动转译：放入 `Starling/shaderpacks` 并在 **视频设置 > 光影包** 中选择，**Auto** 配置会按手机自动适配，旁边还有 **光影选项**、**分辨率** 与 **FPS 上限**。骁龙 8 Gen 2 的 Auto 档实测：MakeUp 87 fps，Complementary Reimagined 59，BSL 54，Complementary Unbound 39，Photon 27 到 39。见[路线图](roadmap/starling.md)。
- **Starling Visuals：** Starling 自带、专为手机制作的光影包：在 **视频设置 > 光影包** 中选择（[packaging/shaderpacks/starling-visuals](packaging/shaderpacks/starling-visuals)，发布页另有 `StarlingVisuals.zip`）。阳光与阴影、带波纹和深度的水面、低处与清晨更浓的雾、穿过树梢与云层的光束、泛光，以及手中火把的光照。
- **模组：** Horizon 的原生模组（`.hnmod`）可在手机上自行编译，内容模组（`.hmod`）即装即用。见 [模组](#模组)。

## Android 现状

Android 移植目前已覆盖的内容；正在做和接下来要做的见[路线图](roadmap/starling.md)。游戏本体是 Horizon 的：其在桌面端测得的同源对照审计见 [Horizon 项目状态](https://github.com/ludovic111/horizon#project-status)（以 Starling 的基线为准，见 [docs/horizon-parity.md](docs/horizon-parity.md)）。

| 部分 | 进度 | 备注 |
| --- | --- | --- |
| 游戏（单人） | <img src="docs/media/progress/100.svg" alt="100%"> | Horizon 的游戏本体，与桌面端一致 |
| 触控操作 | <img src="docs/media/progress/100.svg" alt="100%"> | 每个动作皆可操作，布局可编辑、可加自定义按钮；服务器上可用摇杆、方向键或自由视角；基岩版按钮来自 Mojang |
| Starling 文件夹、`.starling`、`.hmod` 与 `.hnmod` 文件 | <img src="docs/media/progress/100.svg" alt="100%"> | 资源包、世界、模组与游戏文件，用 Starling 打开 |
| 资源包 | <img src="docs/media/progress/100.svg" alt="100%"> | 服务器资源包也可以，加载时不卡顿游戏 |
| 性能调校 | <img src="docs/media/progress/95.svg" alt="95%"> | 按手机预设设置，分辨率最低 25% 照顾较弱机型；中端机型尚未实测 |
| 设置 | <img src="docs/media/progress/100.svg" alt="100%"> | 无效选项置灰，设备不支持的选项隐藏 |
| 模组 | <img src="docs/media/progress/95.svg" alt="95%"> | 原生模组可在手机上编译，已用测试模组端到端验证；内容模组可用；注册表内全部模组的遍历测试尚待完成 |
| 服务器 | <img src="docs/media/progress/100.svg" alt="100%"> | 离线模式服务器与代理、对话框、聊天按钮与链接、登录状态下的安全聊天 |
| 服务器插件 | <img src="docs/media/progress/100.svg" alt="100%"> | 任意资源包的自定义盔甲、骑乘生物的动画模型、方块/物品展示实体、可点击家具、无阴影的 HUD 文本 |
| 反作弊服务器上的移动 | <img src="docs/media/progress/95.svg" alt="95%"> | 按服务器使用自由视角、方向键或模拟摇杆，GrimAC 与 Pika 实测无回拉；破坏方块符合原版预测（先挖掘、后移入其空间）；经 ViaVersion 连接的部分旧版服务器仍会回拉 |
| 光影包 | <img src="docs/media/progress/95.svg" alt="95%"> | Complementary、MakeUp、BSL、Photon 与 Bliss 可在手机上运行，有 Auto 配置与光影选项；部分效果仍有差异 |
| APK 构建与更新 | <img src="docs/media/progress/100.svg" alt="100%"> | 发布页提供已签名的版本，自 v0.1.0.1 起游戏内直接提示并安装（首个提示的版本：v0.1.1）；每次推送也在 GitHub Actions 构建 APK |
| 局域网联机与邀请 | <img src="docs/media/progress/95.svg" alt="95%"> | Horizon 的原生实现；在手机上的测试还很少 |
| 物理手柄 | <img src="docs/media/progress/100.svg" alt="100%"> | Android 手柄，已在手机上实测 |
| 在手机上完成准备 | <img src="docs/media/progress/100.svg" alt="100%"> | 用官方 26.3 文件在手机上 79 秒完成准备，Java 运行时按需拉取；期间可钓鱼、在手绘设置里等待 |
| Microsoft 账户登录 | <img src="docs/media/progress/100.svg" alt="100%"> | 首次启动与游戏内（标题界面角落）皆可；代码登录与持库校验在手机上可用 |

标题界面的账户按钮同样管理已保存的 Microsoft、Ely.by 与离线档案。切换、注册与提供方兼容性见 [Android 账户](docs/android-accounts.md)。

ARMv7 的构建说明与 GPU/内存限制见 [Android 架构](docs/android-armv7.md)。发布 APK 提供 ARM64、ARMv7、x86 与 x86_64 四种架构；请安装与设备匹配的那一个。

本地崩溃报告、Android 退出历史与可选的 CPU/GPU 性能录制在 **选项 → 诊断** 中。导出诊断 ZIP、查看后再随 bug 报告分享。内容、限制与开关见 [Android 诊断](docs/android-diagnostics.md)。在安装前或启动早期失败后，诊断也可以在独立窗口打开；长按应用图标可在不启动游戏的情况下进入。

## 模组

Starling 运行 Horizon 的模组。从文件管理器或下载目录用 Starling 打开模组文件，它就会出现在 **选项 > 模组** 里。

- **`.hnmod`，原生模组（仅 ARM64）：** 点 **编译**。Starling 会告知风险，在你钓鱼或挖矿时用你的模组构建游戏并重启进入。构建需要几分钟和网络，游戏更新后需重新编译。构建服务目前只产出 ARM64 库；其他应用架构会说明该限制且不提供编译或预编译下载。原生模组作为游戏的一部分运行，请只安装你信任的模组。见[原生模组](docs/native-mods.md)与 [Starling 如何构建它们](docs/starling-native-mods.md)；[wrench](native-mods/wrench) 是一个示例。
- **`.hmod`，内容模组：** 纯数据模组，无需编译。见[模组](docs/mods.md)。

旧版 Starling 模组与 v0.1 的 WebAssembly 模组格式已移除。

## 从源码构建

面向开发者。要求：Rust 1.90 或更新（含 `aarch64-linux-android` target）、[cargo-ndk](https://github.com/bbqsrc/cargo-ndk)、Android SDK（build-tools 35.0.0、platform 36、NDK r28c）、JDK 25 与 Python 3.10 或更新。

```sh
git clone -b starling https://github.com/starling-client/starling.git
cd starling
python packaging/android/build_apk.py            # APK 输出到 target/android/
```

构建会以宿主 JDK 编译 Horizon 原始的 exporter 类并打包其校验过的 bundle。设置了 `HORIZON_MINECRAFT_DIR` 时它作为只读源使用；否则把校验和固定的编译输入下载到被忽略的构建缓存。Minecraft 的 jar、库与资源文件永远不会被打包。

首次启动的手绘皮肤由 `python packaging/lowbudget/make.py`（需 Pillow）生成到 `packaging/lowbudget/pack`，APK 原样携带。

APK 不包含任何游戏文件。在手机上，Starling 会从 Mojang 服务器下载并在手机上准备。也可以改为在电脑上准备：它以 **你自己的副本** 的 Minecraft: Java Edition 26.3 构建资源，并按 Horizon 的做法对照 `research-lock.json` 中的校验和验证：

1. **客户端：** 用官方启动器安装 Java 版并运行一次 26.3。Horizon 从启动器的游戏目录读取 `versions/26.3/`、`libraries/` 与 `assets/`。如果目录在别处，传 `--minecraft-dir` 或设置 `HORIZON_MINECRAFT_DIR`。
2. **服务端 jar：** 26.3 的服务端 jar，来自启动器 `versions/26.3/26.3.json` 中的 `downloads.server.url`。传 `--server-jar` 或设置 `HORIZON_SERVER_JAR`。

```sh
cargo run --release -p horizon-prepare -- --prepare --server-jar ~/Downloads/server.jar   # 需要 JDK 25
```

准备好的文件在被 gitignore 的 `.local/horizon-client/26.3`。将该文件夹压缩、把压缩包改名为 `.starling` 并用 Starling 打开。它派生自游戏：绝不提交或传播。

## 参与贡献

欢迎提交 issue 和 pull request：见 [CONTRIBUTING.md](CONTRIBUTING.md)。游戏本体的修改请提到上游 [Horizon](https://github.com/ludovic111/horizon)；Starling 是围绕它的 Android 应用。

## 许可证

[MIT](LICENSE)，仅覆盖项目自身的原创工作，与 Horizon 一致。各依赖保留自己的许可证。Minecraft 是 Mojang Synergies AB 的商标；Minecraft 代码与资源归 Mojang 与 Microsoft 所有，受 [Minecraft EULA](https://www.minecraft.net/en-us/eula) 约束。准备好的资源只留在你的设备上，绝不发布。

第三方致谢与许可证：[第三方声明](THIRD_PARTY_NOTICES.md)。

## 作者

Starling（Android 移植）由 [JuanLuci](https://github.com/JuanLuci) 制作。Horizon 由 [Ludovic Marie](https://lu4ovic.com)（[@ludovic111](https://github.com/ludovic111)）制作。

实测于 POCO F6 Pro（骁龙 8 Gen 2、Adreno 740、12 GB 内存、Android 16）。也曾在 Tecno Pova 7、Mali-G57 与 Mali-G68 GPU、Redmi Note 8（Mali-G52）等设备上运行。
