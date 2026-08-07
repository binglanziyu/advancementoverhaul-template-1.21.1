package com.dreamer.ao.event;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享的玩家事件跟踪器，用于避免多个 EventHandler 之间的集合重复。
 * <p>
 * StatsEventHandler 和 TimelineEventHandler 各自维护了相同的跟踪集合
 * （sunriseChecked、sunsetChecked、wasSleeping），本工具类将它们集中管理，
 * 消除内存浪费和潜在的并发不一致问题。
 */
public final class PlayerEventTrackers {

    /** 当日已触发日出事件的玩家 */
    private static final ConcurrentHashMap<UUID, Boolean> SUNRISE_CHECKED = new ConcurrentHashMap<>();

    /** 当日已触发日落事件的玩家 */
    private static final ConcurrentHashMap<UUID, Boolean> SUNSET_CHECKED = new ConcurrentHashMap<>();

    /** 正在睡觉的玩家 */
    private static final ConcurrentHashMap<UUID, Boolean> WAS_SLEEPING = new ConcurrentHashMap<>();

    private PlayerEventTrackers() {}

    // ────── Sunrise ──────

    public static boolean hasSeenSunrise(UUID uuid) {
        return Boolean.TRUE.equals(SUNRISE_CHECKED.get(uuid));
    }

    public static void markSunrise(UUID uuid) {
        SUNRISE_CHECKED.put(uuid, true);
    }

    public static void resetSunrise(UUID uuid) {
        SUNRISE_CHECKED.remove(uuid);
    }

    // ────── Sunset ──────

    public static boolean hasSeenSunset(UUID uuid) {
        return Boolean.TRUE.equals(SUNSET_CHECKED.get(uuid));
    }

    public static void markSunset(UUID uuid) {
        SUNSET_CHECKED.put(uuid, true);
    }

    public static void resetSunset(UUID uuid) {
        SUNSET_CHECKED.remove(uuid);
    }

    // ────── Sleeping ──────

    public static boolean wasSleeping(UUID uuid) {
        return Boolean.TRUE.equals(WAS_SLEEPING.get(uuid));
    }

    public static void markSleeping(UUID uuid) {
        WAS_SLEEPING.put(uuid, true);
    }

    public static void clearSleeping(UUID uuid) {
        WAS_SLEEPING.remove(uuid);
    }

    // ────── 全局清理 ──────

    /** 清除所有跟踪状态（服务器重载时调用） */
    public static void clearAll() {
        SUNRISE_CHECKED.clear();
        SUNSET_CHECKED.clear();
        WAS_SLEEPING.clear();
    }
}
