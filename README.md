# 🗡️ Enchantment Tooltip Cleaner (提示清理模组)

<p align="center">
  <img src="Forge 1.20.1/src/main/resources/logo.png" alt="Logo" width="128" height="128" />
</p>

<p align="center">
  <strong>现代化、高纯净、零延迟的 Minecraft 物品提示净化与自定义管理模组</strong><br>
  <em>A modern, high-performance, and deeply configurable Minecraft tooltip cleaner and rule manager.</em>
</p>

<p align="center">
  <a href="https://github.com/xdyyj/EnchantmentTooltipCleaner/releases"><img src="https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-brightgreen.svg" alt="Minecraft Version"></a>
  <a href="https://github.com/xdyyj/EnchantmentTooltipCleaner"><img src="https://img.shields.io/badge/Platform-Forge%20%7C%20NeoForge-blue.svg" alt="Platform"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"></a>
  <a href="https://github.com/xdyyj/EnchantmentTooltipCleaner"><img src="https://img.shields.io/badge/i18n-zh__cn%20%7C%20zh__tw%20%7C%20en__us-orange.svg" alt="Languages"></a>
</p>

---

## ✨ 核心特性 (Key Features)

- 🧹 **深度净化 (Deep Tooltip Cleaning)**:
  - 一键清理/隐藏附魔条目（如 锋利 V、保护 IV）。
  - 隐藏附魔详细说明文字（附魔描述模组添加的冗长灰字）。
  - 隐藏攻击伤害、攻击速度、护甲值等属性修饰符。
  - 隐藏药水及食物赋予的状态效果与时长。
- 🎛️ **游戏内可拖拽快捷悬浮窗 (Draggable Quick Overlay)**:
  - 在背包或任意容器界面按快捷键（默认 `H`，可自定义）即时呼出悬浮设置窗。
  - 自由拖拽定位，位置自动记忆；提供一键重置默认坐标。
- 🛡️ **背景防护与防穿透隔离 (Slot Shielding & EMI Integration)**:
  - 悬浮面板具备强力事件拦截，彻底阻断对背包后方物品槽位的点击、抓取、丢弃与悬浮穿透。
  - 自动与 EMI 动态排除区同步，彻底避免与 EMI 侧边栏物品列表重叠或相互遮挡。
- 🔍 **五重自定义规则过滤库 (5-Layer Rule Engine)**:
  1. **文本 - 包含**：只要提示行包含关键词即隐藏。
  2. **文本 - 精准**：整行文本完全相同时隐藏。
  3. **文本 - 正则 (Regex)**：支持高级正则模式，内置 ReDoS 纳秒级稀疏采样超时防御（>25ms 自动阻断，保障客户端永不卡顿卡死）。
  4. **翻译键 - 包含**：按底层语言 Key 过滤，不受语言切换影响。
  5. **翻译键 - 精准**：精准拦截指定翻译键。
- 🏷️ **极速排除白名单 (Smart Whitelisting)**:
  - 悬停任意物品按快捷键（默认 `X`）一键将该物品加入/移出白名单。
  - 悬浮窗支持一键排除整模组（Mod ID 命名空间）。
  - 支持右键轮转切换并排除物品所属的标签（Tags，如 `#minecraft:swords`）。
- 💾 **预设库与剪贴板极速同步 (Presets & Clipboard)**:
  - 内置开箱即用的「纯净体验预设」。
  - 支持将当前规则保存为新的独立预设，一键导入、重命名、删除或打开预设文件夹。
  - 支持一键导出全部规则到系统剪贴板（JSON 格式），或一键导入合并他人分享的规则配置。
- 🌐 **100% 国际化多语言支持 (Complete i18n)**:
  - 简体中文 (`zh_cn`)
  - 正体中文 (`zh_tw`，采用地道台湾本地化词汇)
  - 英语 (`en_us`)
  - 全界面零硬编码，支持社区自由扩展本地化。

---

## ⌨️ 快捷操作指引 (Keybindings & Controls)

| 快捷键 / 操作 | 使用场景 | 作用与功能 |
| :--- | :--- | :--- |
| **`H`** | 容器/背包界面 | 开关快捷设置悬浮窗 (可在游戏控制设置中自定义按键) |
| **`X`** | 容器/背包界面 | 快速将当前悬停的物品加入或移出排除白名单 |
| **鼠标右键** | 悬浮窗标签按钮 | 在当前物品拥有的多个 Tags 标签之间轮转切换 |
| **`Ctrl` + 左键** | 调试模式下 | 点击物品提示快速复制其物品 ID 与翻译键名至剪贴板 |
| **`ESC` / `Enter`** | 规则管理全屏 | 顺畅的焦点与无障碍层级交互，按 Enter 快速保存，按 ESC 逐层回退 |

---

## 📂 项目结构 (Project Structure)

本仓库采用 Monorepo 单仓库架构管理双平台版本，核心业务与界面交互保持二进制级对称：

```
EnchantmentTooltipCleaner/
├── Forge 1.20.1/          # Minecraft 1.20.1 Forge 平台版本
│   ├── src/main/java/     # 源码 (ClientEvents, Overlay, GUI, Config)
│   ├── src/main/resources/# 资源包 (i18n, Presets, Meta)
│   └── build.gradle
├── NeoForge 1.21.1/       # Minecraft 1.21.1 NeoForge 平台版本
│   ├── src/main/java/     # 源码 (1.21.1 组件架构适配)
│   ├── src/main/resources/# 资源包 (1.21.1 对齐)
│   └── build.gradle
├── README.md              # 项目主说明文档
├── LICENSE                # MIT 开源协议
└── .gitignore
```

---

## 🔨 源码构建 (Building from Source)

环境要求：
- **JDK 17** (或更高版本)
- **Git**

### 构建 Forge 1.20.1:
```bash
cd "Forge 1.20.1"
./gradlew build
```
编译产物位于 `Forge 1.20.1/build/libs/`。

### 构建 NeoForge 1.21.1:
```bash
cd "NeoForge 1.21.1"
./gradlew build
```
编译产物位于 `NeoForge 1.21.1/build/libs/`。

---

## 📄 开源许可 (License)

本项目基于 [MIT License](LICENSE) 开源。欢迎提交 Issue 与 Pull Request！
