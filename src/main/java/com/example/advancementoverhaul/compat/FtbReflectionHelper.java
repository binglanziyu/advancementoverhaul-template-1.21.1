package com.example.advancementoverhaul.compat;

import dev.ftb.mods.ftblibrary.util.KnownServerRegistries;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * FTB Quests 反射工具类。
 * <p>
 * 在初始化时一次性解析所有反射句柄，后续调用直接使用缓存的句柄，避免重复反射。
 * <p>
 * <b>反射策略分层</b>：本类使用两种不同的反射方式，取决于目标字段的访问级别和模块归属——
 * <ul>
 *   <li><b>VarHandle / MethodHandle</b> — 用于 <i>public</i> 字段和公开 API 方法。
 *       这些成员在 FTB Quests 的导出包中，Java 模块系统允许跨模块访问。
 *       例如 KnownServerRegistries.client、ServerQuestFile.INSTANCE。</li>
 *   <li><b>Field 直接反射（setAccessible + get/set）</b> — 用于 <i>protected/private</i> 字段。
 *       Java 9+ 模块系统中，{@code MethodHandles.Lookup.unreflectVarHandle()}
 *       对 <b>非公开</b> 字段会做严格的模块边界检查，即使调用 {@code setAccessible(true)}
 *       也无法绕过。但 {@code Field.get()/set()} + {@code setAccessible(true)} 可以工作，
 *       因为 NeoForge 启动时添加了必要的 {@code --add-opens} JVM 参数。
 *       例如 BaseQuestFile.teamDataMap、TeamData.completed。</li>
 * </ul>
 * <p>
 * <b>为什么不全用 Field？</b> VarHandle 提供更好的性能（接近直接字段访问）和类型安全，
 * 在能使用的地方优先使用。只在模块边界受限时才降级为 Field。
 * <p>
 * 当 FTB Quests 版本升级后，只需修改此类的初始化逻辑，其他模块无需变动。
 */
public final class FtbReflectionHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/FTBReflect");

    /** Whether reflection handles are successfully initialized */
    private static volatile boolean initialized = false;

    // ═══════════════ KnownServerRegistries 句柄 ═══════════════

    /** KSR class */
    static Class<?> ksrClass;
    /** KSR.client static field (VarHandle for atomic access) */
    static VarHandle ksrClientField;
    /** KSR.server static field */
    static VarHandle ksrServerField;
    /** KSR.advancements() method */
    static MethodHandle ksrAdvancementsMethod;
    /** KSR.AdvancementInfo constructor */
    static Constructor<?> advancementInfoCtor;

    // ═══════════════ QuestCompletedEvent 句柄 ═══════════════

    /** QuestCompletedEvent class */
    static Class<?> questCompletedEventClass;
    /** QuestCompletedEvent.EVENT field */
    static VarHandle questCompletedEventField;
    /** QuestCompletedEvent.getTeamData() method */
    static MethodHandle questCompletedGetTeamData;
    /** QuestCompletedEvent.getQuest() method */
    static MethodHandle questCompletedGetQuest;
    /** TeamData.getOnlineMembers() method */
    static MethodHandle teamDataGetOnlineMembers;
    /** QuestObjectBase.getId() method */
    static MethodHandle questObjectBaseGetId;
    /** QuestObjectBase.getTitle() method (may be null if not available) */
    static MethodHandle questObjectBaseGetTitle;

    // ═══════════════ BaseQuestFile / ServerQuestFile 句柄 ═══════════════

    /** ServerQuestFile class */
    static Class<?> serverQuestFileClass;
    /** ServerQuestFile.INSTANCE field */
    static VarHandle serverQuestFileInstance;
    /** BaseQuestFile class */
    static Class<?> baseQuestFileClass;
    /** BaseQuestFile.teamDataMap field (Field reflection — protected cross-module) */
    static Field baseQuestFileTeamDataMapField;
    /** BaseQuestFile.questObjectMap field (Field reflection — protected cross-module) */
    static Field baseQuestFileQuestObjectMapField;
    /** TeamData class */
    static Class<?> teamDataClass;
    /** TeamData.completed field (Field reflection — protected cross-module) */
    static Field teamDataCompletedField;

    // ═══════════════ 事件注册句柄 ═══════════════

    /** Event<T>.register(Object) method */
    static MethodHandle eventRegisterMethod;

    private FtbReflectionHelper() {}

    /**
     * Initialize all reflection handles.
     * Called once when FTB Quests is first detected.
     * Idempotent: subsequent calls have no effect.
     */
    public static synchronized void init() {
        if (initialized) return;

        try {
            initKsrHandles();
            initEventHandles();
            initQuestFileHandles();
            initialized = true;
            LOGGER.info("All FTB Quests reflection handles initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize FTB Quests reflection handles — " +
                    "FTB Quests integration will be limited. Version info may help diagnose: " +
                    e.getMessage(), e);
        }
    }

    public static boolean isInitialized() { return initialized; }

    // ═══════════════ KSR handles ═══════════════

    /**
     * 初始化 KnownServerRegistries 相关句柄。
     * <p>
     * 这里的 {@code client} 和 {@code server} 都是 <b>public static</b> 字段，
     * 属于 FTB Library 的导出 API，因此可以安全使用 VarHandle 获得最佳性能。
     */
    private static void initKsrHandles() throws Exception {
        ksrClass = KnownServerRegistries.class;

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Field clientF = ksrClass.getField("client");
        Field serverF = ksrClass.getField("server");
        ksrClientField = lookup.unreflectVarHandle(clientF);
        ksrServerField = lookup.unreflectVarHandle(serverF);

        ksrAdvancementsMethod = lookup.findVirtual(ksrClass, "advancements",
                MethodType.methodType(Map.class));

        Class<?> infoClass = Class.forName(
                "dev.ftb.mods.ftblibrary.util.KnownServerRegistries$AdvancementInfo");
        advancementInfoCtor = infoClass.getConstructor(
                ResourceLocation.class, Component.class, ItemStack.class);
    }

    // ═══════════════ Event handles ═══════════════

    private static void initEventHandles() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        try {
            questCompletedEventClass = Class.forName(
                    "dev.ftb.mods.ftbquests.events.QuestCompletedEvent");
            Field eventF = questCompletedEventClass.getField("EVENT");
            questCompletedEventField = lookup.unreflectVarHandle(eventF);

            questCompletedGetTeamData = lookup.findVirtual(questCompletedEventClass,
                    "getTeamData", MethodType.methodType(Object.class));
            questCompletedGetQuest = lookup.findVirtual(questCompletedEventClass,
                    "getQuest", MethodType.methodType(Object.class));

            Class<?> teamDataC = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
            teamDataGetOnlineMembers = lookup.findVirtual(teamDataC,
                    "getOnlineMembers", MethodType.methodType(java.util.Collection.class));

            try {
                Class<?> questObjBaseC = Class.forName(
                        "dev.ftb.mods.ftbquests.quest.QuestObjectBase");
                questObjectBaseGetId = lookup.findVirtual(questObjBaseC,
                        "getId", MethodType.methodType(Object.class));
                try {
                    questObjectBaseGetTitle = lookup.findVirtual(questObjBaseC,
                            "getTitle", MethodType.methodType(Component.class));
                } catch (NoSuchMethodException e) {
                    questObjectBaseGetTitle = null;
                    LOGGER.debug("QuestObjectBase.getTitle() not found — using ID as display name");
                }
            } catch (ClassNotFoundException e) {
                LOGGER.debug("QuestObjectBase class not found — using numeric ID");
            }

            // Event<T>.register(Object) method for registering listener
            Object eventInstance = questCompletedEventField.get();
            if (eventInstance != null) {
                eventRegisterMethod = lookup.findVirtual(eventInstance.getClass(),
                        "register", MethodType.methodType(void.class, Object.class));
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("QuestCompletedEvent not available (FTB Quests version may lack it)");
        }
    }

    // ═══════════════ Quest file handles ═══════════════

    /**
     * 初始化 FTB Quests 内部数据结构句柄。
     * <p>
     * <b>关键设计决策</b>：{@code teamDataMap}、{@code questObjectMap}、
     * {@code completed} 这三个字段是 <b>protected/private</b> 且位于其他模块中。
     * Java 9+ 模块系统禁止 {@code Lookup.unreflectVarHandle()} 跨模块访问非公开字段，
     * 因此必须使用 {@link Field} + {@code setAccessible(true)} 的方式。
     * <p>
     * NeoForge 在启动时为 MC 和大多数模组添加了 {@code --add-opens} 参数，
     * 使得 {@code Field.get()/set()} 可以跨模块工作，
     * 但 {@code VarHandle} 的 {@code Lookup} 检查更为严格，不受 {@code --add-opens} 影响。
     */
    @SuppressWarnings("unchecked")
    private static void initQuestFileHandles() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        serverQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
        Field instF = serverQuestFileClass.getField("INSTANCE");
        serverQuestFileInstance = lookup.unreflectVarHandle(instF);

        baseQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
        // Use plain Field reflection for protected/private fields in foreign modules —
        // VarHandle (MethodHandles.Lookup.unreflectVarHandle) is blocked by Java 9+ module
        // boundaries even after setAccessible(true). This is by design in the JVM.
        Field tdmF = baseQuestFileClass.getDeclaredField("teamDataMap");
        tdmF.setAccessible(true);
        baseQuestFileTeamDataMapField = tdmF;

        Field qomF = baseQuestFileClass.getDeclaredField("questObjectMap");
        qomF.setAccessible(true);
        baseQuestFileQuestObjectMapField = qomF;

        teamDataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
        Field compF = teamDataClass.getDeclaredField("completed");
        compF.setAccessible(true);
        teamDataCompletedField = compF;
    }

    // ═══════════════ Convenience accessors (keeps callers clean) ═══════════════

    /** Get KnownServerRegistries.client */
    public static Object getKsrClient() {
        try { return ksrClientField.get(); }
        catch (Exception e) { return null; }
    }

    /** Get KnownServerRegistries.server */
    public static Object getKsrServer() {
        try { return ksrServerField.get(); }
        catch (Exception e) { return null; }
    }

    /** Get advancements map from a KSR instance */
    @SuppressWarnings("unchecked")
    public static Map<ResourceLocation, Object> getKsrAdvancements(Object ksr) {
        try { return (Map<ResourceLocation, Object>) ksrAdvancementsMethod.invoke(ksr); }
        catch (Throwable e) { return null; }
    }

    /** Create a new AdvancementInfo instance */
    public static Object createAdvancementInfo(ResourceLocation id, Component name, ItemStack icon) {
        try { return advancementInfoCtor.newInstance(id, name, icon); }
        catch (Exception e) { return null; }
    }

    /** Get ServerQuestFile.INSTANCE (public field → VarHandle is safe) */
    public static Object getServerQuestFileInstance() {
        try { return serverQuestFileInstance.get(); }
        catch (Exception e) { return null; }
    }

    /**
     * Get BaseQuestFile.teamDataMap from a ServerQuestFile instance.
     * Uses Field reflection instead of VarHandle because teamDataMap is protected
     * and Java 9+ module boundaries block VarHandle access to non-public fields.
     */
    @SuppressWarnings("unchecked")
    public static Map<?, ?> getTeamDataMap(Object sqf) {
        try { return (Map<?, ?>) baseQuestFileTeamDataMapField.get(sqf); }
        catch (Exception e) { return null; }
    }

    /**
     * Get BaseQuestFile.questObjectMap from a ServerQuestFile instance.
     * Uses Field reflection instead of VarHandle — same Java module boundary reason.
     */
    @SuppressWarnings("unchecked")
    public static Map<Long, Object> getQuestObjectMap(Object sqf) {
        try { return (Map<Long, Object>) baseQuestFileQuestObjectMapField.get(sqf); }
        catch (Exception e) { return null; }
    }

    /**
     * Get TeamData.completed field.
     * Uses Field reflection — completed is protected and cross-module VarHandle is blocked.
     */
    @SuppressWarnings("unchecked")
    public static Map<Long, Long> getTeamDataCompleted(Object teamData) {
        try { return (Map<Long, Long>) teamDataCompletedField.get(teamData); }
        catch (Exception e) { return null; }
    }

    /** Register a listener on the QuestCompletedEvent */
    public static void registerEventListener(Object listener) {
        try {
            Object eventInstance = questCompletedEventField.get();
            if (eventInstance != null && eventRegisterMethod != null) {
                eventRegisterMethod.invoke(eventInstance, listener);
            }
        } catch (Throwable e) {
            LOGGER.debug("Failed to register FTB event listener: {}", e.getMessage());
        }
    }

    /** Get team data from QuestCompletedEvent */
    public static Object getTeamDataFromEvent(Object event) {
        if (questCompletedGetTeamData == null) return null;
        try { return questCompletedGetTeamData.invoke(event); }
        catch (Throwable e) { return null; }
    }

    /** Get team online members */
    @SuppressWarnings("unchecked")
    public static java.util.Collection<net.minecraft.server.level.ServerPlayer> getOnlineMembers(Object teamData) {
        try { return (java.util.Collection<net.minecraft.server.level.ServerPlayer>) teamDataGetOnlineMembers.invoke(teamData); }
        catch (Throwable e) { return java.util.Collections.emptyList(); }
    }

    /** Get quest object from event */
    public static Object getQuestFromEvent(Object event) {
        try { return questCompletedGetQuest.invoke(event); }
        catch (Throwable e) { return null; }
    }

    /** Get quest ID from quest object */
    public static Object getQuestId(Object quest) {
        if (questObjectBaseGetId == null) return null;
        try { return questObjectBaseGetId.invoke(quest); }
        catch (Throwable e) { return null; }
    }

    /** Get quest title as Component from quest object */
    public static Component getQuestTitle(Object quest) {
        if (questObjectBaseGetTitle == null) return null;
        try { return (Component) questObjectBaseGetTitle.invoke(quest); }
        catch (Throwable e) { return null; }
    }
}
