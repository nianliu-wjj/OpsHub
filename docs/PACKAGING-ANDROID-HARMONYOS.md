# LiveMD 移动端打包指南：Android 与鸿蒙 HarmonyOS NEXT

> 适用范围：将 LiveMD（Tauri 2 + Vue 3 + Vite）打包产出 Android 可执行安装包（APK/AAB）与 HarmonyOS NEXT 安装包（HAP/App）。
> 文档基线：Tauri 2.12 / Rust ≥1.77.2 / pnpm，版本号以 `src-tauri/tauri.conf.json`（当前 1.0.18）为准。

---

## 0. 现状盘点与可行性结论（先读这一节）

| 平台 | 支持方式 | 成熟度 | 打包产物 |
|------|----------|--------|----------|
| Android | Tauri 2 **官方支持**（`tauri android` 命令族） | ✅ 稳定 | `.apk` / `.aab` |
| 鸿蒙 HarmonyOS NEXT | Tauri **官方暂不支持**（[tauri#7287](https://github.com/tauri-apps/tauri/issues/7287) 跟踪中；社区成员 richerfu 正在移植 winit/wry/tao 到 OHOS，实验仓库见 [richerfu/tauri-demo](https://github.com/richerfu/tauri-demo)） | ⚠️ 无官方方案 | 需走 **ArkTS + ArkWeb 壳工程**路线或社区实验方案 |

两条硬性结论，决定后面流程的先后顺序：

1. **前端（`src/` + `dist/`）可以直接 100% 复用**。LiveMD 的 UI 全部是 Vue 3 Web 内容，两个平台最终都是"原生壳 + WebView 渲染 dist"的架构。
2. **打包前必须先做一次"桌面专属代码隔离"改造**（见第 1 步）。当前 `src-tauri/` 中存在以下桌面/平台强依赖，不隔离则移动端编译会失败：

| 依赖 | 位置 | 问题 | 处理方式 |
|------|------|------|----------|
| `tauri-plugin-single-instance` | `lib.rs:18` | 仅支持 Windows/Linux/macOS | `#[cfg(desktop)]` 条件注册 |
| `rfd`（原生文件对话框） | `Cargo.toml` | 桌面 native dialog，Android 上无需/可能编译失败 | 命令层 `cfg` 隔离，移动端改走 `tauri-plugin-dialog` 保存/打开 |
| `objc2-app-kit` 等打印/菜单 | `Cargo.toml` `[target.'cfg(target_os = "macos")'.dependencies]` | 已用 target 隔离 ✅ | 无需改动 |
| `gtk` / `webkit2gtk` | Linux target 依赖 | 已用 target 隔离 ✅ | 无需改动 |
| `clipboard-win` | Windows target 依赖 | 已用 target 隔离 ✅ | 无需改动 |
| `menu` / 托盘 / `window::platform_chrome` | `src-tauri/src/menu.rs`、`window.rs` | 移动端无菜单栏/托盘/自定义标题栏 | `#[cfg(desktop)]` 隔离调用点 |
| 自定义窗口拖拽（`commands/drag.rs`） | 前后端 `drag` 命令 | 移动端无"拖拽移动窗口"交互 | 命令保留不注册或前端按平台跳过 |

---

## 1. 前置改造：桌面/移动端代码隔离（两个平台共用，必须先做）

按顺序执行：

1. **Rust 侧条件编译插件注册**。修改 `src-tauri/src/lib.rs`，把 `single-instance` 的注册包在 `#[cfg(desktop)]` 逻辑里（Tauri 两步写法）：

   ```rust
   fn main() {
     #[cfg(desktop)]
     {
       // single-instance 仅桌面
     }
   }
   ```

   同时把 `menu::install`（已是 `cfg!(target_os = "macos")` ✅）、`on_menu_event`、`window::platform_chrome` 中的桌面专属分支用 `#[cfg(desktop)]` 隔离，并为移动端准备"启动即建一个全屏 WebviewWindow"的路径。

2. **命令注册表按平台拆分**。在 `src-tauri/src/commands/mod.rs` 的 `invoke_handler` 中，将 `drag`、原生打印、`single-instance` 回调里引用的命令标 `#[cfg(desktop)]`；移动端可用的命令（文件读写、搜索、导出、工作区管理、剪贴板 via plugin）保持注册。**注意对前端的影响**：前端 `@tauri-apps/api` 调用点上按平台守卫（见第 4 步）。

3. **Cargo.toml 清理**。`rfd`、`fontdb` 若在移动端编译报错，改为 `[target.'cfg(desktop)'.dependencies]` 分组；`notify`（文件 watch）在 Android 上可用但鸿蒙壳工程里要替换实现，先保留。

4. **capabilities 权限文件**。当前 `src-tauri/capabilities/*.json` 只描述了 desktop 权限。执行第 2 步 `android init` 后 Tauri 会自动生成 mobile schema；为移动端单独新建 `capabilities/mobile.json`（identifier 如 `mobile`，只声明移动端真正需要的 `core:default` + dialog/clipboard 子集）。

5. **前端平台守卫**。排查 `src/` 中所有 `invoke()` 调用点（全局搜索 `invoke(`），把仅桌面的能力（拖拽标题栏、原生菜单、打印、单实例唤起）用 `@tauri-apps/api` 的 `platform()` / `isDesktop` 判断包起来；移动端的窗口尺寸逻辑（`adaptive_size`）在前端同样按移动端跳过。

6. **验证改造**：先在桌面回归，`pnpm tauri dev` + `pnpm tauri build` 全绿后，再进入平台打包。

---

## 2. Android 打包完整流程（按顺序执行）

### 2.1 环境准备（一次性）

1. **JDK 17**（Android Gradle Plugin 要求）：

   ```bash
   java -version   # 应为 17.x；macOS 可用 brew install --cask temurin@17 并切换 JAVA_HOME
   ```

2. **Android Studio**（内置 SDK Manager / AVD Manager）：

   ```bash
   brew install --cask android-studio
   ```

3. 通过 Android Studio → SDK Manager 安装：
   - **Android SDK Platform**：API 34 及以上
   - **SDK Platform-Tools**、**SDK Build-Tools**
   - **NDK (Side by side)**：版本以 `pnpm tauri android init` 生成的配置要求为准（Tauri 2 当前要求 NDK 25+）

4. **Rust Android 编译目标**：

   ```bash
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
   ```

5. **环境变量**（写入 `~/.zshrc`，路径按实际 SDK 安装位置调整）：

   ```bash
   export ANDROID_HOME="$HOME/Library/Android/sdk"
   export NDK_HOME="$ANDROID_HOME/ndk/<你的NDK版本号>"
   export PATH="$ANDROID_HOME/platform-tools:$PATH"
   ```

6. **验证**：`adb --version`、`rustup target list --installed`、`echo $NDK_HOME` 均正常输出后进入下一步。

### 2.2 初始化 Android 工程

```bash
pnpm tauri android init
```

- 产物：`src-tauri/gen/android/`（完整 Gradle 工程，目录名取自 `tauri.conf.json` 的 `identifier` → 包名 `io.github.nianliu-wjj.livemd`）。
- **此目录需提交到 git**（Tauri 官方建议：提交一次后作为手工可改的工程对待）。
- 检查 `tauri.conf.json` 是否需要补充移动端专属 icon（`bundle.android` icon 引用）；如缺失，用 `pnpm tauri icon src-tauri/icons/128x128.png` 重新生成全套含 Android 的图标。

### 2.3 开发调试

```bash
# 连接真机（需开启 USB 调试）或启动 AVD 模拟器后：
pnpm tauri android dev        # 默认产物架构，调试构建
pnpm tauri android dev --host  # 以局域网 IP 提供 devUrl（真机与电脑不同网段时）
```

在此阶段验证：窗口创建、文件读写命令、导出、剪贴板、语言包加载在 Android WebView 中是否正常。

### 2.4 生成签名密钥（发布必做，一次性）

```bash
keytool -genkey -v -keystore ~/livemd-release.keystore \
  -alias livemd -keyalg RSA -keysize 2048 -validity 10000
```

- 妥善保管 keystore 与密码（丢失将无法更新上架应用）。
- 在 `src-tauri/gen/android/app/key.properties`（**不要提交**）或 Gradle 环境变量中登记：

  ```properties
  storeFile=/Users/<你>/livemd-release.keystore
  keyAlias=livemd
  storePassword=***
  keyPassword=***
  ```

### 2.5 构建安装包

```bash
# 调试 APK（可直接 adb install）
pnpm tauri android build --apk

# 发布 AAB（Google Play 上架要求的格式，自动签名）
pnpm tauri android build

# 指定架构减小体积（现代设备只出 arm64 即可）
pnpm tauri android build --target aarch64
```

产物位置：

- APK：`src-tauri/gen/android/app/build/outputs/apk/`
- AAB：`src-tauri/gen/android/app/build/outputs/bundle/universalRelease/`（或对应 arch 目录）

### 2.6 验证与安装

```bash
adb install -r src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release.apk
adb logcat | grep -iE "livemd|tauri"   # 观察启动与 WebView 日志
```

重点验证清单：

- [ ] 冷启动白屏时间（asset 协议 + CSP 策略在移动端的行为差异）
- [ ] 文件保存路径（Android scoped storage，导出目录需用系统保存对话框而非直写路径）
- [ ] 软键盘弹出时编辑器视口自适应
- [ ] 深色模式跟随系统

### 2.7 发布渠道

| 渠道 | 格式 | 说明 |
|------|------|------|
| Google Play | AAB | 需开发者账号，Play Console 上传 + 签名由 Play App Signing 托管 |
| 国内应用商店 | APK | 一般要求软著材料；华为/小米等各自开发者后台单独提审 |
| 官网直发 | APK | 自行分发，_name 可与 Play 版区分 |

---

## 3. 鸿蒙 HarmonyOS NEXT 打包流程

### 3.0 路线选型（先决策）

| 路线 | 说明 | 建议 |
|------|------|------|
| **A. ArkTS + ArkWeb 壳工程（推荐）** | 用 DevEco Studio 新建原生鸿蒙工程，以 ArkWeb 加载 LiveMD 的 `dist/` 前端产物，原生抽壳解决系统能力 | ✅ 当前进度最快的可落地路线 |
| B. Tauri OHOS 社区移植 | richerfu 等正在把 winit/wry/tao 移植到 OHOS（rustc 已支持 `aarch64-unknown-linux-ohos` Tier 2 目标），含 `cargo tauri ohos init` 实验命令的第三方分支 | ⚠️ 实验性，建议持续关注 [tauri#7287](https://github.com/tauri-apps/tauri/issues/7287) 与 richerfu/tauri-demo，待其稳定后可整体平移减少壳层工作 |
| C. 纯原生 ArkTS 重写 | 完全放弃 WebView 方案 | ❌ 成本过高，不建议 |

> **注意**：整个 Tauri Rust 后端在鸿蒙壳里**不可直接复用**。Tauri 的 IPC、窗口管理、插件体系均绑定其宿主实现；路线 A 中 Rust 侧只能按需以 NAPI 方式移植核心逻辑（如下述的文件监听、批量导出）。

### 3.1 路线 A 详细流程（按顺序）

#### 第 1 步：环境准备

1. **DevEco Studio**（华为官方 IDE，基于 IntelliJ）：从[华为开发者官网](https://developer.huawei.com/consumer/cn/download/)下载安装。
2. **Node.js LTS**（ArkTS 工具链 hvigor 需要）。
3. 注册**华为开发者账号**并完成[实名认证](https://developer.huawei.com/consumer/cn/)（AppGallery Connect，简称 AGC）。
4. **验证**：在 DevEco Studio 中创建任意空工程能跑通模拟器即环境 OK。

#### 第 2 步：创建项目与工程结构

1. 打开 DevEco Studio → Create Project → Application（ArkTS 语言）。
2. 项目命名 LiveMD，包名使用与 Android 侧反向域名一致的 `io.github.nianliu.wjj.livemd`（鸿蒙 bundleName 需在 AGC 有唯一性，建议与三端统一）。
3. **前端产物接入**：将 `pnpm build:web` 产物 `dist/` 全量拷入鸿蒙工程的 `entry/src/main/resources/rawfile/` 目录（便于随包分发、离线可用）。建议在 `package.json` 加一条脚本自动化：

   ```json
   "build:hmos": "vite build && rm -rf <hmos工程>/entry/src/main/resources/rawfile/dist && cp -r dist <hmos工程>/entry/src/main/resources/rawfile/dist"
   ```

#### 第 3 步：ArkWeb 壳层实现（EntryAbility + 主页面）

1. 在 `module.json5` 中申请必要权限：

   ```json5
   "requestPermissions": [
     { "name": "ohos.permission.INTERNET" },
     { "name": "ohos.permission.FILE_ACCESS_MANAGER" },
     { "name": "ohos.permission.READ_MEDIA" },
     { "name": "ohos.permission.WRITE_MEDIA" }
   ]
   ```

2. 主页面（`Index.ets`）以 `Web` 组件加载 `rawfile` 中的 `index.html`：

   ```typescript
   import { webview } from '@kit.ArkWeb';

   @Entry
   @Component
   struct Index {
     controller: webview.WebviewController = new webview.WebviewController();

     build() {
       Web({ src: $rawfile('dist/index.html'), controller: this.controller })
         .onPageBegin(() => this.controller.runJavaScriptOnDocumentStart(...))
         .javaScriptAccess(true)
         .domStorageAccess(true)
         .mixedMode(MixedMode.All)
         .fileAccess(true)
     }
   }
   ```

3. **桥接层（核心工作量）**。前端所有 `invoke('save_file' | 'read_file' | 'export_pdf' | ...)` 与 `@tauri-apps/plugin-dialog|clipboard|opener` 调用，都在鸿蒙端替换为"两层适配"：
   - **前端适配层**：抽出 `src/core/bridge/` 平台无关接口（如 `saveFile(path, content)`），Tauri 端走 invoke，鸿蒙端走 `controller.postMessage` → ArkTS `onControllerMessage` 分发。
   - **ArkTS 实现层**：文件读写用 `@ohos.file.fs`、保存/打开对话框用 `@kit.CoreFileKit` 的 `DocumentViewPicker`、剪贴板用 `@ohos.pasteboard`、外链打开用 `openLink`。
4. 对话框、Toast 等纯前端 UI 组件（Naive UI）无需改动。

#### 第 4 步：（可选）移植 Rust 核心到 NAPI

如需复用 `search.rs`（全文检索）等纯计算逻辑：

1. `rustup target add aarch64-unknown-linux-ohos`（Tier 2 官方目标，需 OpenHarmony SDK 提供的 Clang sysroot 交叉编译，详见 [rustc 平台支持文档](https://doc.rust-lang.org/rustc/platform-support/openharmony.html)）。
2. 以 `cdylib` 产出 `.so`，通过 ArkTS 的 Native API（NAPI）桥接进 Application。
3. 仅建议移植**无系统依赖的纯算法模块**；文件监听（`notify`/watcher）等直接用鸿蒙 `@ohos.file.fs` observer 重写更省事。

#### 第 5 步：真机/模拟器验证

```bash
hvigorw assembleHap --mode module -p product=default    # 或直接在 DevEco Studio 中 Build
# 用 hdc 工具列出设备并安装
hdc list targets
hdc install entry/build/default/outputs/default/entry-default-signed.hap
```

重点验证清单：

- [ ] ArkWeb 中 contenteditable 编辑 + 自定义光标逻辑正常工作（与 Chromium 行为差异排查）
- [ ] 输入法（百度/讯飞等三方输入法）composition 事件处理
- [ ] 暗黑模式（`prefers-color-scheme`）跟随系统
- [ ] 安全区（挖孔屏）与软键盘避让
- [ ] 保存的文件能被鸿蒙"文件管家"识别（文件落盘到 Documents 目录时需要正确授权）

#### 第 6 步：签名与上架

1. **生成 CSR 并申请证书**：
   - AGC 门户创建 Profile（发布类型）→ 申请 Debug/Release 证书 → 下载 `.p7b`（Profile）与 `.cer`。
2. DevEco Studio 中配置 File → Project Structure → Signing Configs，填入证书与 Profile。
3. **构建带签名 Release HAP**：Build → Build Hap(s)/APP → 选择 Release。上架商用还需上架资产：
4. AGC 上架需依次完成：
   - 创建应用信息（名称、图标、类别为工具/效率）
   - 提供隐私政策 URL（LiveMD 中 `lang/index.json` 内文本均需与声明一致）
   - 上传 `.app`（Release 包）
   - 审核通过后上架 AGC 商店

---

## 4. 三端发布版本对应关系

每轮发布，三端应统一 bump 版本号：

| 配置文件 | 字段 | 说明 |
|----------|------|------|
| `src-tauri/tauri.conf.json` | `version` | 桌面（macOS/Windows/Linux）与 Android 共用的主版本 |
| `src-tauri/gen/android/.../build.gradle.kts` | `versionCode` / `versionName` | Tauri 自动同步自 tauri.conf.json 的 version |
| `<hmos工程>/AppScope/app.json5` | `versionCode` / `versionName` | 鸿蒙侧手工维护，注意保持三端语义一致 |

---

## 5. 常见问题速查

| 症状 | 平台 | 原因 / 解法 |
|------|------|-------------|
| `android init` 后编译报 `single-instance` / `rfd` 相关错误 | Android | 第 1 步的 `#[cfg(desktop)]` 隔离未完成 |
| WebView 启动后白屏 | 两平台 | CSP / asset 协议：检查 tauri.conf 的 `assetProtocol` 或 grep 构建产物中是否包含本地资源路径 |
| 保存文件路径异常（scoped storage） | Android | 导出类命令改走 `tauri-plugin-dialog` 的保存对话框，返回用户选择的 URI，而不是直写 app 目录 |
| `ohos` target 编译 `reqwest` 失败 | 鸿蒙 | reqwest 依赖 openssl，需配置 [ohos-openssl](https://gitee.com/openharmony-sig/openssl) 或换 rustls feature |
| 鸿蒙软键盘遮挡光标 | 鸿蒙 | ArkWeb 加 `expandSafeArea`，并监听键盘高度通知前端 |
| Android 输入法组合输入乱跳 | Android | IME composition 阶段勿触发markdown 语法重排（参考 `docs/BUGFIX-CARET-REGION.md` 中桌面同类问题的因果链） |

---

## 6. Windows 系统上的完整打包流程

> 前面第 1–3 节的平台改造、工程结构、桥接层设计、验证清单在 Windows 上**完全一致**，本节只罗列 Windows 环境下按顺序执行的差异部分。命令统一以 **PowerShell**（Windows 10/11 自带）为准。

### 6.1 Windows 通用环境准备（两个平台共用，一次性）

1. **Windows 长路径支持**（Gradle/hvigor 会在深层目录产生大量产物，260 字符路径限制会导致莫名其妙的构建失败）：

   ```powershell
   # 以管理员身份运行 PowerShell
   New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" `
     -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force
   git config --global core.longpaths true
   ```

2. **Rust（MSVC 工具链）**。Android/OHOS 产物由 NDK/SDK 的 Clang 交叉编译，不依赖宿主链接器，但 Tauri 构建脚本运行在宿主上，需要完整的 MSVC 环境：

   ```powershell
   winget install Rustlang.Rustup           # 或官网 rustup-init.exe
   # MSVC 工具链要求已安装 Visual Studio 2022 Build Tools（含 "使用 C++ 的桌面开发" 工作负载）
   winget install Microsoft.VisualStudio.2022.BuildTools
   rustup default stable-x86_64-pc-windows-msvc
   rustc --version
   ```

3. **Node.js LTS 与 pnpm**：

   ```powershell
   winget install OpenJS.NodeJS.LTS
   npm install -g pnpm
   ```

4. **PowerShell 执行策略**（避免 pnpm/hvigor 脚本被拦截）：

   ```powershell
   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
   ```

5. **杀毒软件排除项**：将项目目录与各 SDK 目录加入 Windows Defender 排除列表（`设置 → 隐私和安全性 → Windows 安全中心 → 病毒防护 → 排除项`），否则 Rust 编译与 Gradle 增量构建会被实时扫描拖慢 2–5 倍，偶尔还因文件占用导致构建失败。

### 6.2 在 Windows 上打包 Android（按顺序）

#### 第 1 步：安装 JDK 17

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
# 验证（新开一个 PowerShell 窗口使环境变量生效）
java -version
```

> 如系统装有多个 JDK，设置 `JAVA_HOME` 指向 Temurin 17 目录（如 `C:\Program Files\Eclipse Adoptium\jdk-17.x.x`），必要时在 Android Studio 的 Gradle Settings 中显式指定 Gradle JDK。

#### 第 2 步：安装 Android Studio 与 SDK 组件

```powershell
winget install Google.AndroidStudio
```

1. 启动 Android Studio → Settings → Languages & Frameworks → **Android SDK**，安装：
   - **Android SDK Platform**：API 34+
   - **SDK Platform-Tools**、**SDK Build-Tools**
   - **NDK (Side by side)**（Tauri 2 要求 NDK 25+）
2. Windows 默认 SDK 安装路径：`C:\Users\<你>\AppData\Local\Android\Sdk`。

#### 第 3 步：设置环境变量

用 GUI（`系统 → 高级系统设置 → 环境变量`）或命令行（**注意 `setx` 设置后需重开终端**）：

```powershell
setx ANDROID_HOME "$env:LOCALAPPDATA\Android\Sdk"
# NDK 版本号以实际安装为准，通过 Get-ChildItem 查看：
Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\ndk"
setx NDK_HOME "$env:LOCALAPPDATA\Android\Sdk\ndk\<你的NDK版本号>"
```

然后用 `rustup` 添加 Android 目标并验证：

```powershell
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
adb version          # 位于 %LOCALAPPDATA%\Android\Sdk\platform-tools\，Android Studio 安装后一般已入 PATH
echo $env:NDK_HOME   # 应输出上一步 setx 的值
```

#### 第 4 步：初始化工程（首次）

```powershell
cd C:\Users\<你>\Worksapce\LiveMD   # 项目路径建议短、纯英文、无中文与空格
pnpm tauri android init
```

> **路径红线**：Windows 上项目路径若含 **中文、空格或嵌套很深**，NDK/Gradle 极易炸出奇怪编译错误。项目务必放在类似 `C:\dev\LiveMD` 的短路径下，或用 `subst` / 目录联接映射。

#### 第 5 步：开发调试

```powershell
# 连接真机（设置 → 开发者选项 → USB 调试）或启动 AVD 后：
pnpm tauri android dev
# 真机不在本机网段时（手机上确认与 PC 同 Wi-Fi 或指定 IP）：
pnpm tauri android dev --host
```

> **防火墙**：Windows 首次运行 dev 时会弹出"Windows 安全中心警报"，必须勾选**专用网络和公用网络**都允许，否则真机访问不到 PC 上的 5173 端口（表现为一直转圈白屏）。

#### 第 6 步：生成签名密钥

`keytool` 随 JDK 一起安装，在 PowerShell 中运行：

```powershell
keytool -genkey -v -keystore $env:USERPROFILE\livemd-release.keystore `
  -alias livemd -keyalg RSA -keysize 2048 -validity 10000
```

随后在 `src-tauri\gen\android\app\key.properties`（不要提交）登记，格式与 2.4 节一致，路径写 Windows 形式（`storeFile=C\:\\Users\\<你>\\livemd-release.keystore` 转义形式或正斜杠均可）。

#### 第 7 步：构建与安装

```powershell
pnpm tauri android build --apk            # 调试直装包
pnpm tauri android build                   # 发布 AAB（Google Play 上架格式）
pnpm tauri android build --target aarch64 # 只出 arm64，体积最小

# 产物形态与 macOS 完全一致：
# APK → src-tauri\gen\android\app\build\outputs\apk\
# AAB → src-tauri\gen\android\app\build\outputs\bundle\

adb install -r .\src-tauri\gen\android/app/build/outputs/apk/universal/release/app-universal-release.apk
adb logcat | Select-String -Pattern "livemd|tauri"
```

#### 第 8 步：Windows 专属问题速查

| 症状 | 原因 / 解法 |
|------|-------------|
| 构建中途报"路径不存在"但文件明明有 | 260 字符长路径限制未开启 → 执行 6.1 第 1 条 |
| `link.exe not found` | VS Build Tools C++ 工作负载未装，或未用 MSVC 工具链 → `rustup show` 确认 host 为 `stable-x86_64-pc-windows-msvc` |
| 构建极慢 / 随机 file locked | Defender 实时扫描占用产物文件 → 6.1 第 5 条加排除项 |
| 真机 dev 模式一直白屏 | 防火墙拦截 5173 端口 → 重新允许，或在防火墙放行 Node/Vite |
| `NDK_HOME` 生效但 Tauri 找不到 NDK | `setx` 后未重开终端窗口 → 重开 |
| 项目路径含中文导致 NDK 报错 | 迁移项目到纯 ASCII 短路径下 |

### 6.3 在 Windows 上打包鸿蒙 HarmonyOS NEXT（按顺序）

#### 第 1 步：安装 DevEco Studio（Windows 版）

1. 从[华为开发者官网](https://developer.huawei.com/consumer/cn/download/)下载 **Windows 版 DevEco Studio**（提供 `.exe` 安装器）。
2. 安装时使用**默认路径**（鸿蒙工具链对含中文/空格的自定义路径兼容性差，SDK 默认装在 `%DEVECO_SDK_HOME%`，形如 `C:\Program Files\Huawei\DevEco Studio\sdk`）。
3. 首次启动会下载 HarmonyOS SDK、检查与配置 Node.js（IDE 自带 Node 亦可）。在 SDK 页面确认安装了 **ArkTS、HarmonyOS、Toolchains** 三套组件。

验证：新建空工程 → 启动内置模拟器（Device Manager 中的 Pixel 级 Huawei Phone 镜像）能跑通即环境 OK。

#### 第 2 步：创建项目与前端产物注入

流程与 3.1 第 2 步完全一致（ArkTS Application，bundleName 三端统一）。Windows 下把构建脚本写进 `package.json`（注意 PowerShell/CRLF 差异）：

```json
"build:hmos": "vite build && node scripts/copy-dist-to-rawfile.mjs"
```

建议用 3 行的 Node 脚本替代跨平台不稳定的 `rm -rf` / `cp -r` 命令（`scripts/copy-dist-to-rawfile.mjs`，Node 20+ 原生支持递归复制）：

```javascript
import { cpSync, rmSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

const target = resolve('<hmos工程>/entry/src/main/resources/rawfile/dist')
existsSync(target) && rmSync(target, { recursive: true, force: true })
cpSync('dist', target, { recursive: true })
console.log('OK:', target)
```

#### 第 3 步：ArkWeb 壳层与桥接层

与 macOS 一致，见第 3.1 节第 3 步——这部分纯 ArkTS/前端代码，与宿主操作系统无关。在 Windows 上的 Depcheck 只需注意 `module.json5` 权限声明与真机测试（模拟器对 clipboard/pasteboard 等 API 的表现与真机有差异，**必须真机复核**）。

#### 第 4 步：真机连接与安装（hdc 的 Windows 形态）

`hdc.exe` 在 Windows SDK 的位置示例：

```powershell
# 位于 DevEco Studio 安装的 SDK toolchains 内（版本不同路径可能微调）：
$env:HDC = "C:\Program Files\Huawei\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe"

& $env:HDC list targets                 # 应显示已连真机/模拟器的设备号
& $env:HDC install .\entry\build\default\outputs\default\entry-default-signed.hap
```

真机连接要点：

- 用华为原装数据线，打开手机**开发者模式**与 **USB 调试**，首次连接需在手机弹窗中允许调试授权。
- 若 `hdc list targets` 空但设备管理器已识别，**以管理员身份**运行 PowerShell 后重试，并重插 USB。
- 模拟器路径参考 `DevEco Studio → Tools → Device Manager`，模拟器不依赖 USB 直连，适合没有华为真机时做初步验证。

#### 第 5 步：命令行构建（可选，DevEco Studio 内构建为主）

Windows 下命令行构建用 `hvigorw.bat`（对应 macOS 下的 shell 脚本）：

```powershell
cd <hmos工程路径>
.\hvigorw.bat assembleHap --mode module -p product=default
```

#### 第 6 步：签名与上架

1. AGC 申请证书/Profile 的云流程与 3.1 第 6 步完全一致（浏览器中操作）。
2. DevEco Studio 中配置签名：**File → Project Structure → Signing Configs**，勾选"Automatically generate signature"（自动签名，用华为账号一键申请调试证书，仅适合 Debug 包）或手工录入 Release 证书与 Profile。
3. Release 包构建、`hdc` 验证清单、AGC 上架流程与 macOS 端逐条相同（见 3.1 第 5、6 步），差异仅在于命令行工具形态（`hdc.exe`、`hvigorw.bat`）。

#### 第 7 步：Windows 鸿蒙专属问题速查

| 症状 | 原因 / 解法 |
|------|---------------|
| DevEco Studio 启动报 JDK/Node 缺失 | 未用安装器默认流程安装，或自定义路径含中文；卸载重装，使用默认路径 |
| `hvigorw.bat` 卡在下载依赖 | 公司网络需配代理；检查 `C:\Users\<你>\.hvigor` 目录无可写权限问题 |
| `hdc list targets` 找不到真机 | 更换原厂数据线 + 管理员 PowerShell + 手机端重新授权 |
| 编译报 rawfile 资源找不到 | 前端产物未拷贝或拷贝路径大小写不一致；重跑 6.3 第 2 步脚本并 Refresh 工程 |
| 模拟器无法启动（虚拟化报错） | BIOS 中 CPU 虚拟化（VT-x/AMD-V）未开启，或 Hyper-V 与 DevEco 模拟器冲突，按 IDE 提示切换 |
| 升级 DevEco Studio 后 SDK 混乱 | `deveco-path` 环境变量指向残留旧版；清空重装，卸载时选同时清除 SDK |

---

## 7. 参考资料

- Tauri 官方 Android 打包：https://tauri.app/start/android/
- Tauri 移动端开发总览：https://v2.tauri.app/develop/
- tauri#7287 Huawei WebView / HarmonyOS 支持进展：https://github.com/tauri-apps/tauri/issues/7287
- 社区 OHOS 移植示例（richerfu）：https://github.com/richerfu/tauri-demo
- Rust `*-unknown-linux-ohos` 交叉编译：https://doc.rust-lang.org/rustc/platform-support/openharmony.html
- ArkWeb（鸿蒙 Web 组件）官方文档：https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/arkweb-*
- AGC 应用上架流程：https://developer.huawei.com/consumer/cn/agcenter/
