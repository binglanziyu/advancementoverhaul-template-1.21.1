package com.example.advancementoverhaul.client.gui.layout;


import com.example.advancementoverhaul.data.DataStore;
import static com.example.advancementoverhaul.client.gui.Theme.*;

import java.util.*;

public final class AutoLayout {

    private static final int GAP_X = 16, GAP_Y = 16, MARGIN = 32;

    private AutoLayout() {}

    public static void apply(Map<String, DataStore.CustomAdvancement> advancements) {
        if (advancements.isEmpty()) return;

        // ── 构建 DAG ──
        Map<String, List<String>> children = new HashMap<>();
        Map<String, Integer> inDeg = new HashMap<>();

        for (var adv : advancements.values()) {
            inDeg.putIfAbsent(adv.getId(), 0);
            for (String pre : adv.getPrerequisites()) {
                if (advancements.containsKey(pre)) {
                    children.computeIfAbsent(pre, k -> new ArrayList<>()).add(adv.getId());
                    inDeg.merge(adv.getId(), 1, Integer::sum);
                }
            }
        }

        // ── 拓扑分层 ──
        Map<String, Integer> rank = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        for (var adv : advancements.values()) {
            if (inDeg.getOrDefault(adv.getId(), 0) == 0) {
                queue.add(adv.getId());
                rank.put(adv.getId(), 0);
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
        for (var adv : advancements.values()) rank.putIfAbsent(adv.getId(), 0);

        int maxLayer = 0;
        Map<Integer, List<String>> layers = new TreeMap<>();
        for (var e : rank.entrySet()) {
            layers.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            maxLayer = Math.max(maxLayer, e.getValue());
        }

        // ── Phase 1: 自顶向下 — 重心启发式 ──
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
                DataStore.CustomAdvancement adv = advancements.get(id);
                if (adv == null) continue;
                double sum = 0; int cnt = 0;
                for (String pid : adv.getPrerequisites()) {
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

        // ── Phase 2: 自底向上 — 父节点居中于子节点之上 ──
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

        // ── Phase 3: 最终重叠修正 ──
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

        // ── 写入坐标 ──
        for (var entry : layers.entrySet()) {
            int y = MARGIN + entry.getKey() * (CARD_H + GAP_Y);
            for (String id : entry.getValue()) {
                DataStore.CustomAdvancement a = advancements.get(id);
                if (a != null) {
                    a.setX((int) Math.round(xPos.getOrDefault(id, 0.0)));
                    a.setY(y);
                }
            }
        }
    }
}