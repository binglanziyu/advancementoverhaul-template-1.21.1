package com.example.advancementoverhaul.client.gui.cache;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Pre-cached registry entries for the condition selector.
 * Avoids re-scanning 1300+ items/blocks/entities every time the selector opens.
 *
 * <p>Call {@link #init()} once at screen init; call {@link #invalidate()} when
 * registry data may have changed (e.g. mod reload). After invalidation, the
 * cached lists become empty until the next {@link #init()} call.
 */
public final class RegistryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/RegistryCache");

    public record Entry(String id, String displayName) {}

    private static List<Entry> items = List.of();
    private static List<Entry> blocks = List.of();
    private static List<Entry> entities = List.of();
    private static boolean initialized = false;

    private RegistryCache() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        List<Entry> itemList = new ArrayList<>();
        for (var k : BuiltInRegistries.ITEM.keySet()) {
            var item = BuiltInRegistries.ITEM.get(k);
            if (item == null) continue;
            itemList.add(new Entry(k.toString(), new ItemStack(item).getHoverName().getString()));
        }
        itemList.sort(Comparator.comparing(Entry::displayName, String.CASE_INSENSITIVE_ORDER));
        items = Collections.unmodifiableList(itemList);

        List<Entry> blockList = new ArrayList<>();
        for (var k : BuiltInRegistries.BLOCK.keySet()) {
            var block = BuiltInRegistries.BLOCK.get(k);
            if (block == null) continue;
            String name;
            try { name = new ItemStack(block).isEmpty() ? k.getPath() : new ItemStack(block).getHoverName().getString(); }
            catch (Exception e) { name = k.getPath(); }
            blockList.add(new Entry(k.toString(), name));
        }
        blockList.sort(Comparator.comparing(Entry::displayName, String.CASE_INSENSITIVE_ORDER));
        blocks = Collections.unmodifiableList(blockList);

        List<Entry> entityList = new ArrayList<>();
        for (var k : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            var type = BuiltInRegistries.ENTITY_TYPE.get(k);
            if (type == null) continue;
            entityList.add(new Entry(k.toString(), type.getDescription().getString()));
        }
        entityList.sort(Comparator.comparing(Entry::displayName, String.CASE_INSENSITIVE_ORDER));
        entities = Collections.unmodifiableList(entityList);

        LOGGER.info("Registry cache built: {} items, {} blocks, {} entities",
                items.size(), blocks.size(), entities.size());
    }

    public static List<Entry> getItems() { return items; }
    public static List<Entry> getBlocks() { return blocks; }
    public static List<Entry> getEntities() { return entities; }

    /**
     * Marks the cache as stale and clears all cached data.
     * The next call to {@link #init()} will rebuild from the current registries.
     *
     * <p>Previously this only reset the flag without clearing the lists,
     * causing stale data to remain accessible between invalidate() and init().
     */
    public static void invalidate() {
        initialized = false;
        items = List.of();
        blocks = List.of();
        entities = List.of();
    }
}