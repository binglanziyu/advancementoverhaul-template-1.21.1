package com.dreamer.ao.achievement.event;

import com.dreamer.ao.data.ConditionType;
import com.dreamer.ao.logic.ConditionEvaluator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 游戏事件到条件类型的映射模板。
 * <p>
 * 由 {@link ServerEventHandler} 的 {@code @SubscribeEvent} 方法委托调用，
 * 集中承载各事件处理器中重复的注册表查找与伤害计算流程。
 * 本类不直接订阅事件。
 */
final class ConditionEventMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionEventMapper.class);

    private ConditionEventMapper() {}

    /**
     * 注册表事件处理模板，提取各事件处理器中重复的 getKey/空值检查流程。
     * <p>
     * 被以下事件处理方法复用：onEntityKill、onItemCrafted、onBlockBreak、
     * onBlockPlace、onFishItem。
     *
     * @param target       注册表查找目标（如 EntityType、Item、Block）
     * @param keyExtractor ResourceLocation 提取函数
     * @param player       触发事件的玩家
     * @param type         条件类型（用于日志）
     * @param checker      条件检查回调（接收玩家和注册名 ID 字符串）
     */
    static <T> void handleRegistryEvent(T target,
                                        Function<T, ResourceLocation> keyExtractor,
                                        ServerPlayer player, ConditionType type,
                                        BiConsumer<ServerPlayer, String> checker) {
        ResourceLocation id = keyExtractor.apply(target);
        if (id == null) {
            LOGGER.debug("Skipping {} check: unregistered {}", type.name(), target);
            return;
        }
        checker.accept(player, id.toString());
    }

    /**
     * 伤害事件处理模板，合并 DEAL_DAMAGE 和 TAKE_DAMAGE 两处的重复逻辑。
     *
     * @param event  伤害事件
     * @param isDeal true=造成伤害（source→玩家），false=受到伤害（entity→玩家）
     * @param type   对应的条件类型
     */
    static void handleDamageEvent(LivingDamageEvent.Pre event, boolean isDeal,
                                  ConditionType type) {
        ServerPlayer player;
        if (isDeal) {
            if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;
            player = p;
        } else {
            if (!(event.getEntity() instanceof ServerPlayer p)) return;
            player = p;
        }
        float raw = event.getOriginalDamage();
        if (raw <= 0) return;
        int amount = Math.max(1, Math.round(raw));
        ConditionEvaluator.checkProgress(player, type, "", amount);
    }
}
