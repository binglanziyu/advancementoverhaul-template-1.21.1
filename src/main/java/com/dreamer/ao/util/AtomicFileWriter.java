package com.dreamer.ao.util;

import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用原子文件写入工具。
 *
 * <p>所有持久化模块（数据存档、玩家分档、统计分档、阶段定义等）共用同一套
 * 「临时文件 → 原子替换 → 失败回退」语义，避免各自重复实现导致行为不一致。</p>
 *
 * <h2>写入语义</h2>
 * <ol>
 *   <li>先写入 {@code path + ".tmp"} 临时文件；</li>
 *   <li>优先 {@link StandardCopyOption#ATOMIC_MOVE} 原地替换目标；</li>
 *   <li>若文件系统不支持原子移动（如跨卷），退化为先备份目标为 {@code path + ".bak"}，
 *       再非原子移动临时文件；</li>
 *   <li>当 {@code keepGenerations > 0} 时，在成功替换前滚动历史备份
 *       {@code path.1} / {@code path.2} ...，便于灾难恢复。</li>
 * </ol>
 *
 * <p>写入过程中若发生异常，目标文件保持写入前状态（临时文件残留会被清理）。</p>
 */
public final class AtomicFileWriter {

    private static final Logger LOGGER = LogManager.getLogger(AtomicFileWriter.class);

    private AtomicFileWriter() {
    }

    /**
     * 原子写入文本内容。
     *
     * @param path             目标文件路径
     * @param content          待写入内容
     * @param keepGenerations  保留的历史备份代数（0 表示不保留）
     */
    public static void writeString(Path path, String content, int keepGenerations) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = Path.of(path + ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);

            // 滚动历史备份（在替换目标之前）
            if (keepGenerations > 0) {
                rotateBackups(path, keepGenerations);
            }

            try {
                Files.move(tmp, path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailed) {
                // ATOMIC_MOVE 不支持（例如跨文件系统）— 退化为两步移动。
                LOGGER.debug("Atomic move unsupported for {}, using .bak fallback", path.getFileName());
                Path bak = Path.of(path + ".bak");
                if (Files.exists(path)) {
                    Files.move(path, bak, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // 清理任何残留的临时文件（正常路径已被移走）
            try {
                if (Files.exists(tmp)) {
                    Files.deleteIfExists(tmp);
                }
            } catch (IOException ignored) {
                // 临时文件清理失败不影响主流程，下次写入会覆盖
            }
        }
    }

    /**
     * 原子写入并保留 1 代历史备份。
     */
    public static void writeString(Path path, String content) throws IOException {
        writeString(path, content, 1);
    }

    /**
     * 滚动历史备份：{@code path.1} → {@code path.2} → ... → {@code path.keepGenerations}。
     */
    private static void rotateBackups(Path path, int keepGenerations) throws IOException {
        for (int gen = keepGenerations; gen >= 1; gen--) {
            Path newer = gen == 1 ? path : path.resolveSibling(path.getFileName() + "." + (gen - 1));
            Path older = path.resolveSibling(path.getFileName() + "." + gen);
            if (Files.exists(newer)) {
                Files.move(newer, older, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * 读取目标文件；文件不存在时回退到 {@code .bak}，再回退到最近一代历史备份。
     * 全部失败时返回 {@code null}（调用方应使用默认值）。
     */
    public static String readWithFallback(Path path, int keepGenerations) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(path);
        candidates.add(Path.of(path + ".bak"));
        for (int gen = 1; gen <= keepGenerations; gen++) {
            candidates.add(path.resolveSibling(path.getFileName() + "." + gen));
        }
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                try {
                    return Files.readString(candidate, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    LOGGER.warn("Failed to read {}: {}", candidate, e.getMessage());
                }
            }
        }
        return null;
    }
}
