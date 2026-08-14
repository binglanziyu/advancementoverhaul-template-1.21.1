# 第三方模组接口边界说明（Compat Interface Boundary）

> 本文档说明本模组（AdvancementOverhaul，mod id: `advancementoverhaul`）与三个可选/被动集成模组的
> **接口边界、包路径、加载机制与隔离策略**。
>
> 撰写日期：2026-08-14
> 适用范围：Minecraft 1.21.1 / NeoForge 21.1.x
>
> 所有信息均基于本仓库 `src/main/java/com/dreamer/ao/compat/` 与 `src/main/java/com/dreamer/ao/mixin/` 的
> 实际源码，以及对应上游模组的公开包结构（KubeJS `dev.latvian.mods.kubejs.*`、
> FTB Quests `dev.ftb.mods.ftbquests.*`、FTB Library `dev.ftb.mods.ftblibrary.*`）。
> 上游仓库（经 GitHub 核对）：
> - KubeJS: https://github.com/kube-mods/kubejs （1.21.1 对应分支 `2601`，源码 `src/main/java/dev/latvian/mods/kubejs/`）
> - FTB Quests: https://github.com/FTBTeam/FTB-Quests （源码 `common/src/main/java/dev/ftb/mods/ftbquests/`）
> - FTB Library: https://github.com/FTBTeam/FTB-Library （FTB Quests 的强制前置库）
>
> 上游接口事实来源：GitHub 仓库源码结构 + DeepWiki 自动索引（KubeJS 索引于 2026-07-14、FTB Quests 索引于 2026-03-14）。

---

## 0. 总览

| 上游模组 | mod id | 包前缀 | 集成性质 | 隔离方式 |
|---|---|---|---|---|
| **KubeJS** | `kubejs` | `dev.latvian.mods.kubejs.*` | 被动插件（KubeJS 主动发现本模组） | `kubejs.plugins.txt` 入口类（机制硬要求含第三方） |
| **FTB Quests** | `ftbquests` | `dev.ftb.mods.ftbquests.*` | 可选弱依赖（本模组主动调用） | `FtbCompatService` 接口 + `NoOp` + 反射工厂 |
| **FTB Library** | `ftblibrary` | `dev.ftb.mods.ftblibrary.*` | FTB Quests 的前置库，被间接引用 | 经由 FTB Quests 反射层（`KnownServerRegistries`） |

**核心隔离红线**：主逻辑（network / command / event / achievement 等 common 包）**绝不保留**
任何 `dev.latvian.mods.*` 或 `dev.ftb.mods.*` 的类型签名或 import。所有第三方引用被限制在
`compat/ftb/`（FTB）与 `compat/kubejs/`（KubeJS）两个隔离包内。

---

## 1. KubeJS 接口（被动插件发现）

### 1.1 加载机制（经 GitHub/DeepWiki 核实）
KubeJS 在自身存在于 classpath 时，会扫描所有已加载模组的资源文件
`src/main/resources/kubejs.plugins.txt`，其中每行列出一个插件实现类的全限定名。KubeJS 通过反射
`new` 该类的实例（**要求 public 无参构造函数**）并调用 `KubeJSPlugin` 接口方法（即 KubeJS 的插件契约）。
发现逻辑位于 `KubeJSPlugins.java`（KubeJS 自身源码 `src/main/java/dev/latvian/mods/kubejs/plugin/`）。

**安全性**：当 KubeJS 不在 classpath 时，`kubejs.plugins.txt` 根本不会被读取，入口类也不会被
加载，因此**主逻辑零风险**——这是 KubeJS 机制的天然隔离，无需额外反射工厂。

### 1.1.1 KubeJSPlugin 完整接口方法（上游 `dev.latvian.mods.kubejs.plugin.KubeJSPlugin`）
经 DeepWiki 索引的 KubeJS 2601 分支源码确认，插件接口定义如下生命周期钩子：

| 方法 | 调用时机 | 本模组是否使用 |
|---|---|---|
| `void init()` | mod 初始化阶段，任何脚本运行之前 | ✅ 调用 `registerEvents()` + `registerBindings()` |
| `void initStartup()` | startup 脚本加载后、注册表冻结前 | ❌ 未使用 |
| `void afterInit()` | `FMLLoadCompleteEvent` 期间 | ❌ 未使用 |
| `void registerEvents()` | 为脚本注册自定义 `EventGroup` | ✅ 注册 `AdvancementOverhaul` 事件组 |
| `void registerBindings(BindingRegistry bindings)` | 向 Rhino 作用域添加全局变量/函数 | ✅ 绑定 `AdvancementOverhaul` 全局对象 |
| `void registerTypeWrappers(TypeWrapperRegistry registry)` | 注册 JS↔Java 类型转换 | ❌ 未使用 |
| `void registerRecipeSchemas()` | 定义新配方结构 | ❌ 未使用 |
| `void registerClasses(ClassFilter filter)` | 控制脚本可访问的 Java 类 | ❌ 未使用 |

> 上游参考：`https://github.com/kube-mods/kubejs/blob/2601/src/main/java/dev/latvian/mods/kubejs/plugin/KubeJSPlugin.java`

### 1.2 接口边界（本模组 ↔ KubeJS）

| 本模组文件 | 引用的 KubeJS 类型（包路径） | 用途 |
|---|---|---|
| `compat/kubejs/AdvancementOverhaulKubeJSPlugin.java` | `dev.latvian.mods.kubejs.plugin.KubeJSPlugin`（接口） | 插件入口，实现 `init()` / `registerEvents()` / `registerBindings()` |
| 同上 | `dev.latvian.mods.kubejs.event.EventGroup` | 定义事件组 `AdvancementOverhaul` |
| 同上 | `dev.latvian.mods.kubejs.event.EventHandler` | 定义 `completed` / `progress` / `reset` 三个服务端事件 |
| 同上 | `dev.latvian.mods.kubejs.event.EventGroupRegistry` | 注册事件组 |
| 同上 | `dev.latvian.mods.kubejs.event.BindingRegistry` | 绑定 JS 全局对象 `AdvancementOverhaul` |
| 同上 | `dev.latvian.mods.kubejs.event.KubeEvent` | `CompletedEventJS` / `ProgressEventJS` / `ResetEventJS` 的基类 |
| `compat/kubejs/KubeJSBindings.java` | **无第三方引用** | 纯转发到 `AdvancementAPI`（NeoForge 类型），供 JS 脚本调用 |

### 1.3 事件流
```
FTB/原版成就完成 → AdvCompletedEvent（本模组事件）
   → AdvancementOverhaulKubeJSPlugin.onAdvCompleted (SubscribeEvent)
      → COMPLETED.post(new CompletedEventJS(player, id, name))
         → KubeJS 脚本中 advancementoverhaul.completed 监听
```
`progress` / `reset` 同理，对应 `AdvProgressEvent` / `AdvResetEvent`。

### 1.4 隔离备注
`AdvancementOverhaulKubeJSPlugin` 必须 `implements KubeJSPlugin`，这是 KubeJS 插件机制的
**硬性契约**，无法用接口隔离绕过。因此该类的第三方引用是机制要求的、且已被 `kubejs.plugins.txt`
的"存在才加载"机制所隔离——符合"KubeJS 可缺失"的安全目标。

---

## 2. FTB Quests 接口（可选弱依赖，本模组主动调用）

### 2.1 加载机制
FTB Quests 是**可选**模组。本模组通过 `FtbCompatProvider.get()` 获取兼容服务：
1. `ModList.get().isLoaded("ftbquests")` 闸门判定；
2. 闸门通过后经 `Class.forName("com.dreamer.ao.compat.ftb.FtbQuestsBridge").newInstance()`
   **反射**加载真实实现；
3. 闸门为假或反射失败（`LinkageError` / `ClassNotFoundException`）时降级为
   `NoOpFtbCompatService`（空实现，所有方法安全 no-op）。

反射延后解析确保：FTB 缺失时 `FtbQuestsBridge` 类根本不被 JVM 触碰，杜绝
`NoClassDefFoundError` 整包崩溃。

### 2.2 接口边界（本模组 ↔ FTB Quests）

上游源码路径：`FTBTeam/FTB-Quests` 的 `common/src/main/java/dev/ftb/mods/ftbquests/`（经 DeepWiki 索引确认）。

| 本模组文件 | 引用的 FTB 类型（包路径） | 访问方式 | 用途 |
|---|---|---|---|
| `compat/ftb/FtbQuestsBridge.java` | `dev.ftb.mods.ftblibrary.util.KnownServerRegistries` | 直接 import | KSR 原版进度注册表桥接 |
| 同上 | `dev.ftb.mods.ftblibrary.util.KnownServerRegistries$AdvancementInfo` | 反射 `Class.forName` | 构造进度条目 |
| `compat/ftb/FtbReflectionHelper.java` | `dev.ftb.mods.ftbquests.events.QuestCompletedEvent` | 反射 | 监听任务完成事件 |
| 同上 | `dev.ftb.mods.ftbquests.quest.TeamData` | 反射 | 团队进度查询（per-team progress，使用 Fast-Util 集合） |
| 同上 | `dev.ftb.mods.ftbquests.quest.ServerQuestFile` | 反射 | 服务端任务文件单例（save/load 权威数据） |
| 同上 | `dev.ftb.mods.ftbquests.quest.BaseQuestFile` | 反射 | `getAllTeamData` / `getQuest`，含 `Long2ObjectOpenHashMap questObjectMap` 注册表 |
| 同上 | `dev.ftb.mods.ftbquests.quest.QuestObjectBase` | 反射 | 任务 ID / 标题（注：FTB 1.21.1 中 `QuestObjectBase` 为 `QuestObject` 的父类，后者提供 `isCompletedRaw()`） |

**上游事件系统补充**：FTB Quests 通过 Architectury 事件系统对外暴露完成事件，任务完成归于
`ObjectCompletedEvent.QUEST` 类别（`dev.ftb.mods.ftbquests.event.*` 体系）。本模组监听的
`QuestCompletedEvent` 即对应此扩展点。

### 2.3 隔离层结构

```
主逻辑（network/command/event）
   ↓ 仅依赖接口
FtbCompatService（接口，无第三方类型）
   ↓ FtbCompatProvider 工厂（反射 + ModList 闸门）
   ├── FtbQuestsBridge（真实实现，含 dev.ftb.mods.* 引用）
   └── NoOpFtbCompatService（空实现，状态查询返回终止态）
```

`FtbQuestListener.java` 与 `FtbKsrSyncer.java` **不含任何第三方 import**——它们的 FTB 类型
全部经由 `FtbQuestsBridge` / `FtbReflectionHelper` 的反射句柄获取，符合隔离规范。

### 2.4 KSR（KnownServerRegistries）同步
`FtbKsrSyncer` 负责将本模组的自定义成就注入 FTB 的 KSR，使 FTB 任务系统能识别它们。
KSR 类本身来自 **FTB Library**（`dev.ftb.mods.ftblibrary.util.KnownServerRegistries`），
因此 KSR 同步本质上是本模组与 FTB Library 的间接接口点。

---

## 3. FTB Library 接口（FTB Quests 的前置库，间接引用）

FTB Library 不直接被本模组作为集成目标，而是作为 FTB Quests 的依赖被**间接**使用。

### 3.1 接口边界

| 本模组文件 | 引用的 FTB Library 类型 | 用途 |
|---|---|---|
| `compat/ftb/FtbQuestsBridge.java` | `dev.ftb.mods.ftblibrary.util.KnownServerRegistries` | KSR 注册表 |
| `compat/ftb/FtbReflectionHelper.java` | `dev.ftb.mods.ftblibrary.util.KnownServerRegistries$AdvancementInfo` | 进度条目构造 |

FTB Library 的 `KnownServerRegistries` 是 FTB 生态统一的"服务端已知注册表"机制，FTB Quests
通过它把原版/自定义进度暴露给客户端同步。本模组复用同一机制实现与 FTB 任务进度的双向可见。

### 3.2 版本耦合风险
`KnownServerRegistries.AdvancementInfo` 的构造签名（RL + Component + ItemStack）随 FTB Library
版本可能变化。本模组已用反射 `getConstructor(...)` 容错——构造器签名不匹配时降级为空实现，
不会崩溃。

---

## 4. Mixin 织入点（第三方依赖的另一种边界）

除了 compat 包，本模组还通过 Mixin 在第三方类上织入逻辑。所有涉及第三方的 Mixin 均置于
`src/main/java/com/dreamer/ao/mixin/ftb/` 或标注 `@Pseudo`（FTB 缺失时静默不织入）：

| Mixin 文件 | 织入目标 | 第三方依赖 | 隔离注解 |
|---|---|---|---|
| `mixin/AdvancementRewardMixin.java` | `dev.ftb.mods.ftbquests.quest.AdvancementReward` | FTB Quests | `@Pseudo`（已补，与 AdvancementTaskMixin 对齐） |
| `mixin/AdvancementTaskMixin.java` | FTB 任务类 | FTB Quests | `@Pseudo` |
| `mixin/ftb/SyncKsrMixin.java` | FTB 内部 KSR 调度 | FTB Quests | 织入点依赖 FTB 内部 `queue(Runnable)` |
| `mixin/ftb/FtbToastMixin.java` | FTB Toast 系统 | FTB Quests | 仅字符串命名空间判定 |
| `mixin/ftb/FtbSoundMixin.java` | FTB 音效 | FTB Quests | 仅字符串命名空间判定 |
| `mixin/EnumConfigMixin.java` | FTB 配置枚举 | FTB Library/Quests | 反射私有构造器，失败回退 |

**规则**：所有 FTB 相关 Mixin 必须以 `@Pseudo` 标注，确保 FTB 缺失时不引发织入失败。

---

## 5. 隔离红线自检清单

- [x] 主逻辑（common 包）无 `dev.latvian.mods.*` / `dev.ftb.mods.*` import
- [x] FTB 调用经 `FtbCompatProvider` 反射 + `ModList` 闸门
- [x] FTB 缺失时降级 `NoOpFtbCompatService`（状态查询返回终止态，避免空转）
- [x] KubeJS 入口类因机制硬要求含第三方，但受 `kubejs.plugins.txt` "存在才加载" 隔离
- [x] 所有 FTB Mixin 标注 `@Pseudo`
- [x] 反射句柄集中为 `private static final String` / `Class.forName` 常量（见 `FtbReflectionHelper.Holders`）

---

## 6. 上游版本参考（撰写时）

| 模组 | 1.21.1 对应版本线索 | 备注 |
|---|---|---|
| KubeJS | 分支 `2601`（GitHub `kube-mods/kubejs`） | `KubeJSPlugin` 接口稳定 |
| FTB Quests | `FTBTeam/FTB-Quests` NeoForge 1.21.1 构建 | `QuestCompletedEvent` / `TeamData` API 可能随版本微调 |
| FTB Library | `FTBTeam/FTB-Library` | `KnownServerRegistries` 为 FTB 生态统一机制 |

> 注：上游 API 可能随版本变化。若集成失效，优先检查 `FtbReflectionHelper` 中反射的
> 类名/方法签名是否与当前安装的 FTB 版本匹配（日志会输出 `Failed to initialize FTB Quests
> reflection handles` 提示）。
