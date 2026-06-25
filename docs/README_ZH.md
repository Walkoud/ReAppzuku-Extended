[English](./README.md) | [Русский](./README_RU.md) | **简体中文** | [Espanol](./README_ES.md)

---

![Logo](https://github.com/gree1d/ReAppzuku/blob/main/docs/images/logo.png)
<p align="center">
<img src="https://img.shields.io/github/v/release/gree1d/ReAppzuku?label=Release&" alt="最新版本">
<img src="https://img.shields.io/github/downloads/gree1d/ReAppzuku/total?label=%E4%B8%8B%E8%BD%BD%E9%87%8F&color=a855f7" alt="下载量" />
<img src="https://img.shields.io/badge/许可证-GPLv3-64748b.svg" alt="许可证">
<img src="https://img.shields.io/badge/Android-6.0%2B-f97316.svg" alt="Android 版本">
<img src="https://img.shields.io/badge/Root-支持-brightgreen.svg"/>
<img src="https://img.shields.io/badge/Shizuku-支持-brightgreen.svg"/>
</p>

ReAppzuku 是 Appzuku（Shappky）的一个分支，增强了对 Android 应用后台活动的控制。

监控并停止在后台消耗 RAM、电池和 CPU 的应用。\
支持一键手动强制停止、定时周期性 Kill，以及针对选定应用的灵活后台限制。\
\
需要 Root 或 Shizuku 权限。

## ⚙️ 功能

* **智能自动化：**
  * 周期性 Auto-Kill：间隔从 10 秒到 5 分钟。
  * 锁屏 Kill：屏幕关闭后立即强制停止后台进程。
  * RAM 阈值：仅当 RAM 使用达到设定上限（75%–100%）时触发 Kill。
  * 硬件事件/应用启动 Kill：由选定硬件事件或目标应用启动触发 Kill，可选择额外清理 RAM。
  * Auto-Kill 预设：在特定时间自定义和调度 Auto-Kill 行为。
* **手动控制：**
  * 主屏幕：查看所有活跃后台进程及 RAM 使用量，批量选择和 Kill。
  * 快捷磁贴："停止应用"Kill 当前前台应用；"停止后台应用"使用您的列表运行 Auto-Kill。
  * 桌面小部件：显示当前 RAM 使用量和最近 12 小时的 Auto-Kill 统计。
  * 应用快捷方式：长按应用图标立即 Kill 当前前台应用。
* **后台限制**（Android 11+）：
  * 轻度模式：在系统层面阻止自启动——如果您打开应用，它会继续运行，但不会自行唤醒。
  * 中度模式：部分限制后台应用活动。
  * 重度模式：最小化后立即终止进程，阻止其留在内存中哪怕一秒。
  * 手动模式：手动选择并应用所需限制。
* **限制调度器：** 设置时间窗口以临时解除限制，激活时可选择启动组件。
* **休眠模式：** 在设定的空闲计时器（5–60 分钟）后完全冻结选定应用，屏幕解锁时自动解冻。
* **应用触发器：** 深度诊断工具，分析后台活动的真实原因——Foreground Service、Sticky Service、Wakelock、Alarms、JobScheduler、网络连接、开机接收器及其他 54 种因素（取决于 Android 版本）。
* **分析与日志：**
  * 最近 12 小时 Auto-Kill 日志：每个应用的 Kill、重启、释放 RAM。
  * 问题应用排行：按 RAM 消耗和重启频率排序（12 小时/24 小时/7 天/全部时间）。
  * 后台限制日志：已应用、错误、未应用——最多 200 条。
  * 资源使用图表（RAM、CPU、电池），时间段 2、6、12、24 小时。
* **灵活列表：** 白名单（Auto-Kill 排除项）、黑名单（Auto-Kill 目标）、隐藏应用（完全排除在列表和 Auto-Kill 之外）。
* **备份与恢复：** 导出和导入所有设置到 JSON 文件——白名单、黑名单、隐藏应用、限制、休眠模式和自动化参数。

## 🛠 要求

| 组件 | 要求 |
|---|---|
| Android | 6.0+（后台限制需要 11+） |
| 权限 | Root 或 Shizuku |

## 🚀 快速开始

* **设置权限：** 安装并激活 [Shizuku](https://github.com/thedjchi/Shizuku)，或授予 Root 权限。
* **后台运行：** 为 ReAppzuku 禁用电池优化并将其固定在最近任务中——否则系统可能会 Kill 管理服务本身。
* **选择策略：** 白名单 + 周期性 Kill 以最大化节省，或仅黑名单以针对性控制特定应用。

## 🛡 安全性

ReAppzuku 自动保护关键系统进程——Google Play Services、System UI、当前键盘、当前启动器、电话、蓝牙、NFC 和 Shizuku 本身。OEM 特定系统应用（小米安全中心、Samsung Device Care、OPPO 手机管家等）也会受到保护。

## 🎨 自定义

* 系统、浅色、深色和 AMOLED 主题。
* 可配置的强调色：靛蓝、深红、森林绿、琥珀等。

## 🌐 翻译

欢迎翻译！\
帮助本地化应用：
* 提交 **Pull Request** 修改 `/values/strings.xml`, `README.md`, `HELP.md`。
* 提交 **Issue** 并附上您的 `/values/strings.xml`, `README.md`, `HELP.md`（先打包为 `.zip`).

## 🖼️ 截图

<p align="center">
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot1.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot1.jpg" width="100" alt="截图 1">
  </a>
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot2.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot2.jpg" width="100" alt="截图 2">
  </a>
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot3.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot3.jpg" width="100" alt="截图 3">
  </a>
</p>

## 许可证

ReAppzuku 基于 [GNU General Public License v3.0](LICENSE) 许可。

## 致谢

Fork 自 [northmendo/Appzuku](https://github.com/northmendo/Appzuku)。
<br><br>
>![Claude](https://img.shields.io/badge/Claude-D97757?logo=claude&logoColor=fff)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini-886FBF?logo=googlegemini&logoColor=fff)
![Grok / xAI](https://img.shields.io/badge/Grok-000000?logo=xai&logoColor=white)
> ReAppzuku 使用 vibecoding 构建——一种通过 AI（LLM）辅助生成大量代码的方法。
