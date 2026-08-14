package com.dreamer.ao.phase;

import java.security.SecureRandom;

/**
 * 阶段 id 自动生成器。
 * 生成规则：phase_ + 6 位 Base36 随机串，确保不与既有 id 冲突。
 */
public final class PhaseIdGenerator {
    private static final String PREFIX = "phase_";
    private static final char[] BASE36 = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private PhaseIdGenerator() {
    }

    /** 生成形如 phase_a3f9k2 的 id */
    public static String generate() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < 6; i++) {
            sb.append(BASE36[RNG.nextInt(BASE36.length)]);
        }
        return sb.toString();
    }
}
