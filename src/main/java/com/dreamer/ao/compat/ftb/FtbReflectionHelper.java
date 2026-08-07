package com.dreamer.ao.compat.ftb;

import dev.ftb.mods.ftblibrary.util.KnownServerRegistries;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FtbReflectionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FtbReflectionHelper.class);
    private static volatile boolean initialized = false;

    /** 反射句柄集中存储，支持生命周期管理。 */
    static final class Holders {
        Class<?> ksrClass;
        VarHandle ksrClientField;
        VarHandle ksrServerField;
        MethodHandle ksrAdvancementsMethod;
        Constructor<?> advancementInfoCtor;
        Class<?> questCompletedEventClass;
        VarHandle questCompletedEventField;
        MethodHandle questCompletedGetTeamData;
        MethodHandle questCompletedGetQuest;
        MethodHandle teamDataGetOnlineMembers;
        MethodHandle questObjectBaseGetId;
        MethodHandle questObjectBaseGetTitle;
        Class<?> serverQuestFileClass;
        VarHandle serverQuestFileInstance;
        Class<?> baseQuestFileClass;
        Field baseQuestFileTeamDataMapField;
        Field baseQuestFileQuestObjectMapField;
        Class<?> teamDataClass;
        Field teamDataCompletedField;
        MethodHandle eventRegisterMethod;
    }

    private static volatile Holders h = null;

    private FtbReflectionHelper() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            Holders holders = new Holders();
            initKsrHandles(holders);
            initEventHandles(holders);
            initQuestFileHandles(holders);
            h = holders;
            initialized = true;
            LOGGER.info("All FTB Quests reflection handles initialized successfully");
        } catch (Exception e) {
            clear();
            LOGGER.error("Failed to initialize FTB Quests reflection handles \u2014 FTB Quests integration will be limited. Version info may help diagnose: " + e.getMessage(), e);
        }
    }

    /** 释放所有反射句柄，在 FTB 不可用时调用以回收内存。 */
    public static void clear() {
        h = null;
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized && h != null;
    }

    private static void initKsrHandles(Holders h) throws Exception {
        h.ksrClass = KnownServerRegistries.class;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Field clientF = h.ksrClass.getField("client");
        Field serverF = h.ksrClass.getField("server");
        h.ksrClientField = lookup.unreflectVarHandle(clientF);
        h.ksrServerField = lookup.unreflectVarHandle(serverF);
        h.ksrAdvancementsMethod = lookup.findVirtual(h.ksrClass, "advancements", MethodType.methodType(Map.class));
        Class<?> infoClass = Class.forName("dev.ftb.mods.ftblibrary.util.KnownServerRegistries$AdvancementInfo");
        h.advancementInfoCtor = infoClass.getConstructor(ResourceLocation.class, Component.class, ItemStack.class);
    }

    private static void initEventHandles(Holders h) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            h.questCompletedEventClass = Class.forName("dev.ftb.mods.ftbquests.events.QuestCompletedEvent");
            Field eventF = h.questCompletedEventClass.getField("EVENT");
            h.questCompletedEventField = lookup.unreflectVarHandle(eventF);
            h.questCompletedGetTeamData = lookup.findVirtual(h.questCompletedEventClass, "getTeamData", MethodType.methodType(Object.class));
            h.questCompletedGetQuest = lookup.findVirtual(h.questCompletedEventClass, "getQuest", MethodType.methodType(Object.class));
            Class<?> teamDataC = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
            h.teamDataGetOnlineMembers = lookup.findVirtual(teamDataC, "getOnlineMembers", MethodType.methodType(Collection.class));
            try {
                Class<?> questObjBaseC = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObjectBase");
                h.questObjectBaseGetId = lookup.findVirtual(questObjBaseC, "getId", MethodType.methodType(Object.class));
                try {
                    h.questObjectBaseGetTitle = lookup.findVirtual(questObjBaseC, "getTitle", MethodType.methodType(Component.class));
                } catch (NoSuchMethodException e) {
                    h.questObjectBaseGetTitle = null;
                    LOGGER.debug("QuestObjectBase.getTitle() not found \u2014 using ID as display name");
                }
            } catch (ClassNotFoundException e) {
                LOGGER.debug("QuestObjectBase class not found \u2014 using numeric ID");
            }
            Object eventInstance = h.questCompletedEventField.get();
            if (eventInstance != null) {
                h.eventRegisterMethod = lookup.findVirtual(eventInstance.getClass(), "register", MethodType.methodType(Void.TYPE, Object.class));
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("QuestCompletedEvent not available (FTB Quests version may lack it)");
        }
    }

    private static void initQuestFileHandles(Holders h) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        h.serverQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
        Field instF = h.serverQuestFileClass.getField("INSTANCE");
        h.serverQuestFileInstance = lookup.unreflectVarHandle(instF);
        h.baseQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
        Field tdmF = h.baseQuestFileClass.getDeclaredField("teamDataMap");
        tdmF.setAccessible(true);
        h.baseQuestFileTeamDataMapField = tdmF;
        Field qomF = h.baseQuestFileClass.getDeclaredField("questObjectMap");
        qomF.setAccessible(true);
        h.baseQuestFileQuestObjectMapField = qomF;
        h.teamDataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
        Field compF = h.teamDataClass.getDeclaredField("completed");
        compF.setAccessible(true);
        h.teamDataCompletedField = compF;
    }

    public static Object getKsrClient() {
        try {
            return h != null ? h.ksrClientField.get() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static Object getKsrServer() {
        try {
            return h != null ? h.ksrServerField.get() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<ResourceLocation, Object> getKsrAdvancements(Object ksr) {
        try {
            return h != null ? (Map<ResourceLocation, Object>) h.ksrAdvancementsMethod.invoke(ksr) : null;
        } catch (Throwable e) {
            return null;
        }
    }

    public static Object createAdvancementInfo(ResourceLocation id, Component name, ItemStack icon) {
        try {
            return h != null ? h.advancementInfoCtor.newInstance(id, name, icon) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static Object getServerQuestFileInstance() {
        try {
            return h != null ? h.serverQuestFileInstance.get() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<?, ?> getTeamDataMap(Object sqf) {
        try {
            return h != null ? (Map<?, ?>) h.baseQuestFileTeamDataMapField.get(sqf) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<Long, Object> getQuestObjectMap(Object sqf) {
        try {
            return h != null ? (Map<Long, Object>) h.baseQuestFileQuestObjectMapField.get(sqf) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<Long, Long> getTeamDataCompleted(Object teamData) {
        try {
            return h != null ? (Map<Long, Long>) h.teamDataCompletedField.get(teamData) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void registerEventListener(Object listener) {
        try {
            if (h == null || h.questCompletedEventField == null) return;
            Object eventInstance = h.questCompletedEventField.get();
            if (eventInstance != null && h.eventRegisterMethod != null) {
                h.eventRegisterMethod.invoke(eventInstance, listener);
            }
        } catch (Throwable e) {
            LOGGER.debug("Failed to register FTB event listener: {}", e.getMessage());
        }
    }

    public static Object getTeamDataFromEvent(Object event) {
        if (h == null || h.questCompletedGetTeamData == null) {
            return null;
        }
        try {
            return h.questCompletedGetTeamData.invoke(event);
        } catch (Throwable e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Collection<ServerPlayer> getOnlineMembers(Object teamData) {
        try {
            return h != null ? (Collection<ServerPlayer>) h.teamDataGetOnlineMembers.invoke(teamData) : Collections.emptyList();
        } catch (Throwable e) {
            return Collections.emptyList();
        }
    }

    public static Object getQuestFromEvent(Object event) {
        try {
            return h != null ? h.questCompletedGetQuest.invoke(event) : null;
        } catch (Throwable e) {
            return null;
        }
    }

    public static Object getQuestId(Object quest) {
        if (h == null || h.questObjectBaseGetId == null) {
            return null;
        }
        try {
            return h.questObjectBaseGetId.invoke(quest);
        } catch (Throwable e) {
            return null;
        }
    }

    public static Component getQuestTitle(Object quest) {
        if (h == null || h.questObjectBaseGetTitle == null) {
            return null;
        }
        try {
            return (Component) h.questObjectBaseGetTitle.invoke(quest);
        } catch (Throwable e) {
            return null;
        }
    }
}
