package com.example.advancementoverhaul.client.gui.layout;

import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import static com.example.advancementoverhaul.client.gui.Theme.*;

import java.util.*;

/**
 * 画布自动布局算法。
 * <p>
 * 将画布上的所有自定义进度（以及启用的原版进度）按照依赖树拓扑排序后，
 * 以网格方式重新排列位置，确保前置条件始终位于当前进度的左侧或上方。
 * <p>
 * 通过 {@code /adv autolayout} 命令调用。
 */
public final class AutoLayout {

    private static final int GAP_X = 16, GAP_Y = 16, MARGIN = 32;

    private AutoLayout() {}

    /**
     * 仅布局自定义进度（向后兼容）。
     */
    public static void apply(Map<String, CustomAdvancement> advancements) {
        apply(advancements, Map.of(), Map.of());
    }

    /**
     * 同时布局自定义进度和启用的原版进度。
     *
     * @param advancements 所有自定义进度
     * @param vanillaMetas 启用的原版进度元数据（key = 原版 achievement ID）
     * @param parentMap    原版进度父子关系（child → parent）
     */
    public static void apply(Map<String, CustomAdvancement> advancements,
                             Map<String, VanillaAdvMeta> vanillaMetas,
                             Map<String, String> parentMap) {
        if (advancements.isEmpty() && vanillaMetas.isEmpty()) return;

        // ═══ 构建全部节点集合 ═══
        Set<String> allNodes = new LinkedHashSet<>(advancements.keySet());
        allNodes.addAll(vanillaMetas.keySet());

        // ═══ 构建前置条件映射 ═══
        Map<String, List<String>> prereqsOf = new HashMap<>();
        for (var adv : advancements.values()) {
            prereqsOf.put(adv.getId(), new ArrayList<>(adv.getPrerequisites()));
        }
        for (var e : vanillaMetas.entrySet()) {
            String vid = e.getKey();
            var meta = e.getValue();
            List<String> prqs = new ArrayList<>(meta.getPrerequisites());
            String parent = parentMap.get(vid);
            if (parent != null && allNodes.contains(parent) && !prqs.contains(parent)) {
                prqs.add(parent);
            }
            prereqsOf.put(vid, prqs);
        }

        // ═══ 构建 DAG ═══
        Map<String, List<String>> children = new HashMap<>();
        Map<String, Integer> inDeg = new HashMap<>();

        for (String id : allNodes) {
            inDeg.putIfAbsent(id, 0);
            for (String pre : prereqsOf.getOrDefault(id, List.of())) {
                if (allNodes.contains(pre)) {
                    children.computeIfAbsent(pre, k -> new ArrayList<>()).add(id);
                    inDeg.merge(id, 1, Integer::sum);
                }
            }
        }

        // ═══ 拓扑分层 ═══
        Map<String, Integer> rank = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        for (String id : allNodes) {
            if (inDeg.getOrDefault(id, 0) == 0) {
                queue.add(id);
                rank.put(id, 0);
            }
        }
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int r = rank.get(cur);
            for (String child : children.getOrDefault(cur, List.of())) {
                rank.merge(child, r + 1, Math::max);
                if (inDeg.merge(child, -1, Integer::sum) == 0) queue.add(child);
            }
        }
        for (String id : allNodes) rank.putIfAbsent(id, 0);

        int maxLayer = 0;
        Map<Integer, List<String>> layers = new TreeMap<>();
        for (var e : rank.entrySet()) {
            layers.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            maxLayer = Math.max(maxLayer, e.getValue());
        }

        // ═══ Phase 1: 自顶向下 — 重心启发式 ═══
        Map<String, Double> xPos = new HashMap<>();

        // Layer 0: 均匀铺开
        List<String> layer0 = layers.getOrDefault(0, List.of());
        for (int i = 0; i < layer0.size(); i++)
            xPos.put(layer0.get(i), (double) (MARGIN + i * (CARD_W + GAP_X)));

        // Layer 1+: 重心定位 + 重叠修正
        for (int layer = 1; layer <= maxLayer; layer++) {
            List<String> nodes = layers.get(layer);
            if (nodes == null) continue;

            Map<String, Double> bary = new HashMap<>();
            for (String id : nodes) {
                double sum = 0; int cnt = 0;
                for (String pid : prereqsOf.getOrDefault(id, List.of())) {
                    if (xPos.containsKey(pid)) { sum += xPos.get(pid); cnt++; }
                }
                bary.put(id, cnt > 0 ? sum / cnt : MARGIN);
            }

            nodes.sort(Comparator.comparingDouble(id -> bary.getOrDefault(id, 0.0)));
            double x = MARGIN;
            for (String id : nodes) {
                double ideal = bary.getOrDefault(id, x);
                xPos.put(id, Math.max(x, ideal));
                x = Math.max(x, ideal) + CARD_W + GAP_X;
            }
        }

        // ═══ Phase 2: 自底向上 — 父节点居中于子节点之上 ═══
        for (int layer = maxLayer - 1; layer >= 0; layer--) {
            List<String> nodes = layers.get(layer);
            if (nodes == null) continue;
            for (String id : nodes) {
                List<String> ch = children.getOrDefault(id, List.of());
                if (ch.isEmpty()) continue;
                double sum = 0; int cnt = 0;
                for (String cid : ch) {
                    if (xPos.containsKey(cid)) { sum += xPos.get(cid); cnt++; }
                }
                if (cnt > 0) xPos.put(id, sum / cnt);
            }
        }

        // ═══ Phase 3: 最终重叠修正 ═══
        for (int layer = 0; layer <= maxLayer; layer++) {
            List<String> nodes = layers.get(layer);
            if (nodes == null) continue;
            nodes.sort(Comparator.comparingDouble(id -> xPos.getOrDefault(id, 0.0)));
            double x = MARGIN;
            for (String id : nodes) {
                xPos.put(id, Math.max(x, xPos.getOrDefault(id, 0.0)));
                x = xPos.get(id) + CARD_W + GAP_X;
            }
        }

        // ═══ 写入坐标 ═══
        for (var entry : layers.entrySet()) {
            int y = MARGIN + entry.getKey() * (CARD_H + GAP_Y);
            for (String id : entry.getValue()) {
                int nx = (int) Math.round(xPos.getOrDefault(id, 0.0));

                CustomAdvancement a = advancements.get(id);
                if (a != null) {
                    a.setX(nx);
                    a.setY(y);
                } else {
                    VanillaAdvMeta vm = vanillaMetas.get(id);
                    if (vm != null) {
                        vm.setX(nx);
                        vm.setY(y);
                    }
                }
            }
        }
    }
}