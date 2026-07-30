/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.item.ItemStack
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.advancementoverhaul.compat.ftb;

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
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"AdvancementOverhaul/FTBReflect");
    private static volatile boolean initialized = false;
    static Class<?> ksrClass;
    static VarHandle ksrClientField;
    static VarHandle ksrServerField;
    static MethodHandle ksrAdvancementsMethod;
    static Constructor<?> advancementInfoCtor;
    static Class<?> questCompletedEventClass;
    static VarHandle questCompletedEventField;
    static MethodHandle questCompletedGetTeamData;
    static MethodHandle questCompletedGetQuest;
    static MethodHandle teamDataGetOnlineMembers;
    static MethodHandle questObjectBaseGetId;
    static MethodHandle questObjectBaseGetTitle;
    static Class<?> serverQuestFileClass;
    static VarHandle serverQuestFileInstance;
    static Class<?> baseQuestFileClass;
    static Field baseQuestFileTeamDataMapField;
    static Field baseQuestFileQuestObjectMapField;
    static Class<?> teamDataClass;
    static Field teamDataCompletedField;
    static MethodHandle eventRegisterMethod;

    private FtbReflectionHelper() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            FtbReflectionHelper.initKsrHandles();
            FtbReflectionHelper.initEventHandles();
            FtbReflectionHelper.initQuestFileHandles();
            initialized = true;
            LOGGER.info("All FTB Quests reflection handles initialized successfully");
        }
        catch (Exception e) {
            LOGGER.error("Failed to initialize FTB Quests reflection handles \u2014 FTB Quests integration will be limited. Version info may help diagnose: " + e.getMessage(), (Throwable)e);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    private static void initKsrHandles() throws Exception {
        ksrClass = KnownServerRegistries.class;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Field clientF = ksrClass.getField("client");
        Field serverF = ksrClass.getField("server");
        ksrClientField = lookup.unreflectVarHandle(clientF);
        ksrServerField = lookup.unreflectVarHandle(serverF);
        ksrAdvancementsMethod = lookup.findVirtual(ksrClass, "advancements", MethodType.methodType(Map.class));
        Class<?> infoClass = Class.forName("dev.ftb.mods.ftblibrary.util.KnownServerRegistries$AdvancementInfo");
        advancementInfoCtor = infoClass.getConstructor(ResourceLocation.class, Component.class, ItemStack.class);
    }

    private static void initEventHandles() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            questCompletedEventClass = Class.forName("dev.ftb.mods.ftbquests.events.QuestCompletedEvent");
            Field eventF = questCompletedEventClass.getField("EVENT");
            questCompletedEventField = lookup.unreflectVarHandle(eventF);
            questCompletedGetTeamData = lookup.findVirtual(questCompletedEventClass, "getTeamData", MethodType.methodType(Object.class));
            questCompletedGetQuest = lookup.findVirtual(questCompletedEventClass, "getQuest", MethodType.methodType(Object.class));
            Class<?> teamDataC = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
            teamDataGetOnlineMembers = lookup.findVirtual(teamDataC, "getOnlineMembers", MethodType.methodType(Collection.class));
            try {
                Class<?> questObjBaseC = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObjectBase");
                questObjectBaseGetId = lookup.findVirtual(questObjBaseC, "getId", MethodType.methodType(Object.class));
                try {
                    questObjectBaseGetTitle = lookup.findVirtual(questObjBaseC, "getTitle", MethodType.methodType(Component.class));
                }
                catch (NoSuchMethodException e) {
                    questObjectBaseGetTitle = null;
                    LOGGER.debug("QuestObjectBase.getTitle() not found \u2014 using ID as display name");
                }
            }
            catch (ClassNotFoundException e) {
                LOGGER.debug("QuestObjectBase class not found \u2014 using numeric ID");
            }
            Object eventInstance = questCompletedEventField.get();
            if (eventInstance != null) {
                eventRegisterMethod = lookup.findVirtual(eventInstance.getClass(), "register", MethodType.methodType(Void.TYPE, Object.class));
            }
        }
        catch (ClassNotFoundException e) {
            LOGGER.debug("QuestCompletedEvent not available (FTB Quests version may lack it)");
        }
    }

    private static void initQuestFileHandles() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        serverQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
        Field instF = serverQuestFileClass.getField("INSTANCE");
        serverQuestFileInstance = lookup.unreflectVarHandle(instF);
        baseQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
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

    public static Object getKsrClient() {
        try {
            return ksrClientField.get();
        }
        catch (Exception e) {
            return null;
        }
    }

    public static Object getKsrServer() {
        try {
            return ksrServerField.get();
        }
        catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<ResourceLocation, Object> getKsrAdvancements(Object ksr) {
        try {
            return (Map<ResourceLocation, Object>) ksrAdvancementsMethod.invoke(ksr);
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static Object createAdvancementInfo(ResourceLocation id, Component name, ItemStack icon) {
        try {
            return advancementInfoCtor.newInstance(id, name, icon);
        }
        catch (Exception e) {
            return null;
        }
    }

    public static Object getServerQuestFileInstance() {
        try {
            return serverQuestFileInstance.get();
        }
        catch (Exception e) {
            return null;
        }
    }

    public static Map<?, ?> getTeamDataMap(Object sqf) {
        try {
            return (Map)baseQuestFileTeamDataMapField.get(sqf);
        }
        catch (Exception e) {
            return null;
        }
    }

    public static Map<Long, Object> getQuestObjectMap(Object sqf) {
        try {
            return (Map)baseQuestFileQuestObjectMapField.get(sqf);
        }
        catch (Exception e) {
            return null;
        }
    }

    public static Map<Long, Long> getTeamDataCompleted(Object teamData) {
        try {
            return (Map)teamDataCompletedField.get(teamData);
        }
        catch (Exception e) {
            return null;
        }
    }

    public static void registerEventListener(Object listener) {
        try {
            Object eventInstance = questCompletedEventField.get();
            if (eventInstance != null && eventRegisterMethod != null) {
                eventRegisterMethod.invoke(eventInstance, listener);
            }
        }
        catch (Throwable e) {
            LOGGER.debug("Failed to register FTB event listener: {}", (Object)e.getMessage());
        }
    }

    public static Object getTeamDataFromEvent(Object event) {
        if (questCompletedGetTeamData == null) {
            return null;
        }
        try {
            return questCompletedGetTeamData.invoke(event);
        }
        catch (Throwable e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Collection<ServerPlayer> getOnlineMembers(Object teamData) {
        try {
            return (Collection<ServerPlayer>) teamDataGetOnlineMembers.invoke(teamData);
        }
        catch (Throwable e) {
            return Collections.emptyList();
        }
    }

    public static Object getQuestFromEvent(Object event) {
        try {
            return questCompletedGetQuest.invoke(event);
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static Object getQuestId(Object quest) {
        if (questObjectBaseGetId == null) {
            return null;
        }
        try {
            return questObjectBaseGetId.invoke(quest);
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static Component getQuestTitle(Object quest) {
        if (questObjectBaseGetTitle == null) {
            return null;
        }
        try {
            return (Component) questObjectBaseGetTitle.invoke(quest);
        }
        catch (Throwable e) {
            return null;
        }
    }
}

