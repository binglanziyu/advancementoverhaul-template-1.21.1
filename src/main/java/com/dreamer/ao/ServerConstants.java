package com.dreamer.ao;

/**
 * 服务端内部实现常量集中管理。
 * <p>
 * 归集原先散落在 {@code NetworkHandler}、{@code ServerEventHandler}、
 * {@code ConditionEvaluator} 等类中的魔法数字，便于统一审阅与调整。
 *
 * <h2>边界原则</h2>
 * 本类仅承载<b>实现细节常量</b>。面向用户的可调项一律定义在 {@link Config} 中，
 * 不在此处重复；也不将内部常量升格为配置项——每一项配置都是长期的兼容性负担。
 * <p>
 * 不可实例化的纯常量类。
 */
public final class ServerConstants {

    // ══════════════════════════════════════════════════════════
    // 网络层 — C2S 频率限制
    // ══════════════════════════════════════════════════════════

    /** 同一玩家两次 C2S 命令之间的最小间隔（毫秒）。 */
    public static final long COOLDOWN_MS = 100;

    /**
     * 冷却表条目数软上限。
     * <p>
     * 正常情况下条目在玩家登出时即被回收，条目数收敛于在线玩家数。
     * 此上限仅作为异常场景（崩溃退出、登出事件缺失）的兜底清理触发阈值。
     */
    public static final int COOLDOWN_MAX_SIZE = 1024;

    /** 兜底清理时，冷却表条目的过期时长（毫秒）。 */
    public static final long COOLDOWN_EXPIRE_MS = 60_000;

    /** 同一玩家两次文件导入之间的最小间隔（毫秒）。 */
    public static final long IMPORT_COOLDOWN_MS = 2000;

    // ══════════════════════════════════════════════════════════
    // 网络层 — C2S 体积限额
    // ══════════════════════════════════════════════════════════

    /**
     * 携带 JSON 载荷的命令（{@code adv createjson} / {@code adv updatejson}）的
     * UTF-8 字节上限。
     * <p>
     * 合法的进度定义（含名称、描述、图标、多条件、前置列表）经 Base64 编码后
     * 可能达到数 KB，因此保留较宽松的限额。更细粒度的校验由
     * {@code validateUpdateJson} 在解码后完成。
     */
    public static final int CMD_JSON_MAX_UTF8_BYTES = 16384;

    /**
     * 普通命令（{@code adv complete}、{@code adv setname} 等）的 UTF-8 字节上限。
     * <p>
     * 这类命令仅携带 ID、坐标或短文本，1 KB 已有充分余量。
     * 相较此前对所有命令统一使用 16 KB，显著收窄了攻击面。
     */
    public static final int CMD_PLAIN_MAX_UTF8_BYTES = 1024;

    /** 文件导入内容的字符数上限。 */
    public static final int IMPORT_MAX_CHARS = 1_048_576;

    /**
     * JSON 嵌套深度上限，防止深度嵌套导致栈溢出 / DoS。
     * <p>
     * 正常 JSON 嵌套深度通常在 5-15 层，32 层为防御性上限：<br>
     * — Gson JsonParser 使用递归解析，无显式深度保护<br>
     * — 攻击者可能构造 10,000+ 层嵌套的 JSON（Billion Laughs 变体）<br>
     * — 32 层足以容纳任何合法配置文件且远低于 JVM 默认栈限制<br>
     * — 如确实需要更深嵌套，建议重构数据结构而非调高此值
     */
    public static final int JSON_MAX_DEPTH = 32;

    // ══════════════════════════════════════════════════════════
    // 维度锁
    // ══════════════════════════════════════════════════════════

    /** 维度锁提示消息的冷却时间（毫秒），防止同一 tick 内重复弹出。 */
    public static final long DIM_LOCK_COOLDOWN_MS = 1000;

    /** 维度锁冷却条目最大保留时间（毫秒），超时自动清理防止内存泄漏。 */
    public static final long DIM_LOCK_CLEANUP_TIMEOUT_MS = 600_000;

    /**
     * 维度锁冷却表条目数软上限。
     * <p>
     * 超过此值时在写入路径上立即触发一次驱逐，不再等待周期清理。
     */
    public static final int DIM_LOCK_MAX_SIZE = 256;

    /** 在 (x, z) 处搜索安全落点时，向上/下扫描的最大层数。 */
    public static final int SAFE_GROUND_SCAN_RANGE = 64;

    // ══════════════════════════════════════════════════════════
    // 服务端 Tick 调度
    // ══════════════════════════════════════════════════════════

    /**
     * 周期性维护任务的执行间隔（tick）。
     * <p>
     * 600 tick ≈ 30 秒。维度锁冷却驱逐与条件去重表驱逐共用此周期，
     * 合并到单个取模分支中执行，避免每 tick 多次取模判断。
     */
    public static final int MAINTENANCE_INTERVAL_TICKS = 600;

    /** KSR 同步重试的初始退避（tick）。 */
    public static final int KSR_RETRY_BACKOFF_BASE_TICKS = 20;

    /** KSR 同步重试的退避上限（tick），1200 tick ≈ 60 秒。 */
    public static final int KSR_RETRY_BACKOFF_MAX_TICKS = 1200;

    // ══════════════════════════════════════════════════════════
    // 条件评估
    // ══════════════════════════════════════════════════════════

    /**
     * 级联深度上限，防止超长前置链（A→B→C→...→A 循环依赖）导致死循环。
     * <p>
     * 选择 64 的原因：<br>
     * — 原版进度系统最深层级约 16 层（如 nether/all_effects 链）<br>
     * — 自定义模组进度链通常不超过 10 层<br>
     * — 64 层提供 4x 安全余量，同时确保每次遍历耗时在数毫秒内<br>
     * — 超出此深度视为前置链存在循环依赖，记录警告并终止级联计算
     */
    public static final int MAX_CASCADE_DEPTH = 64;

    /**
     * Tick 级重入保护表的条目保留窗口（tick）。
     * <p>
     * 去重语义只需覆盖「同一 tick」，因此任何大于 1 的窗口都足够；
     * 取 100 tick（5 秒）是为了在服务端 tick 计数回绕或调度抖动时保留余量，
     * 同时确保表规模不会无界增长。
     */
    public static final int DEDUP_RETENTION_TICKS = 100;

    // ══════════════════════════════════════════════════════════
    // 数据持久化
    // ══════════════════════════════════════════════════════════

    /**
     * 常规数据文件保留的滚动备份代数。
     * <p>
     * 保留 2 代可覆盖「最近一次写入即损坏」与「连续两次写入均损坏」两种场景，
     * 同时把磁盘占用控制在原文件的 2 倍以内。
     */
    public static final int BACKUP_GENERATIONS = 2;

    /**
     * 大体积缓存文件（如原版进度原始缓存）保留的备份代数。
     * <p>
     * 该文件可重新从服务端注册表生成，损坏代价低，因此只留 1 代以节省磁盘。
     */
    public static final int BACKUP_GENERATIONS_LARGE = 1;

    private ServerConstants() {}
}
