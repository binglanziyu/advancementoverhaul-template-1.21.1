package com.dreamer.ao.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourceLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceLoader.class);
    private static final ResourceLocation PLAQUE_TEX = ModInfo.rl("custom_plaque");
    private static boolean initialized = false;
    private static boolean plaqueAvailable = false;
    private static Path chimePath;
    private static boolean chimeAvailable = false;

    private ResourceLoader() {
    }

    public static void init(Path configDir) {
        if (initialized) {
            return;
        }
        initialized = true;
        Path resDir = configDir.resolve("advancementoverhaul/resources");
        ensureDir(resDir);
        generateReadme(resDir);
        Path plaquePath = resDir.resolve("plaque.png");
        if (Files.exists(plaquePath)) {
            LOGGER.info("Found custom plaque texture: {}", plaquePath);
        }
        Path mp3Path = resDir.resolve("chime.mp3");
        if (Files.exists(mp3Path)) {
            if (validateMp3Header(mp3Path)) {
                chimePath = mp3Path;
                chimeAvailable = true;
                LOGGER.info("Found custom chime (MP3): {}", mp3Path);
            } else {
                LOGGER.warn("chime.mp3 exists but is not a valid MP3 file \u2014 ignored");
            }
        }
        for (String ext : List.of(".wav", ".ogg", ".flac", ".aac", ".m4a", ".mp4", ".avi", ".mkv", ".mov", ".webm")) {
            Path wrong = resDir.resolve("chime" + ext);
            if (!Files.exists(wrong)) continue;
            LOGGER.warn("Unsupported chime format '{}' ignored \u2014 only .mp3 is supported", wrong.getFileName());
        }
    }

    public static boolean loadPlaqueTexture(Minecraft mc, Path configDir) {
        Path plaquePath = configDir.resolve("advancementoverhaul/resources/plaque.png");
        if (!Files.exists(plaquePath)) {
            return false;
        }
        try (InputStream in = Files.newInputStream(plaquePath)) {
            NativeImage image = NativeImage.read(in);
            DynamicTexture texture = new DynamicTexture(image);
            mc.getTextureManager().register(PLAQUE_TEX, texture);
            plaqueAvailable = true;
            LOGGER.info("Custom plaque texture loaded: {}x{}", image.getWidth(), image.getHeight());
            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to load custom plaque texture: {}", e.getMessage());
            return false;
        }
    }

    public static boolean isPlaqueAvailable() {
        return plaqueAvailable;
    }

    public static ResourceLocation getPlaqueTexture() {
        return PLAQUE_TEX;
    }

    public static boolean playCustomChime() {
        if (!chimeAvailable) {
            return false;
        }
        try {
            Thread thread = new Thread(() -> {
                try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(chimePath.toFile())) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioIn);
                    clip.start();
                    Thread.sleep(clip.getMicrosecondLength() / 1000L + 100L);
                    clip.close();
                } catch (Exception e) {
                    LOGGER.warn("Failed to play custom chime: {}", e.getMessage());
                }
            }, "AO-Chime");
            thread.setDaemon(true);
            thread.start();
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to start custom chime playback: {}", e.getMessage());
            return false;
        }
    }

    private static boolean validateMp3Header(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] header = new byte[3];
            if (in.read(header) < 3) {
                return false;
            }
            // ID3 tag
            if (header[0] == 73 && header[1] == 68 && header[2] == 51) {
                return true;
            }
            // MPEG frame sync
            int b0 = header[0] & 0xFF;
            int b1 = header[1] & 0xFF;
            return b0 == 255 && (b1 & 0xE0) == 224;
        } catch (IOException e) {
            return false;
        }
    }

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create resource directory: {}", e.getMessage());
        }
    }

    private static void generateReadme(Path resDir) {
        generateReadmeCn(resDir);
        generateReadmeEn(resDir);
    }

    private static void generateReadmeCn(Path resDir) {
        Path readme = resDir.resolve("README.txt");
        if (Files.exists(readme)) {
            return;
        }
        String content = "====================================================\nAdvancement Overhaul - \u81ea\u5b9a\u4e49\u66ff\u6362\u8d44\u6e90\u8bf4\u660e\n====================================================\n\n\u4f60\u53ef\u4ee5\u5728\u6b64\u6587\u4ef6\u5939\u4e2d\u653e\u7f6e\u81ea\u5b9a\u4e49\u6587\u4ef6\u6765\u66ff\u6362\u6a21\u7ec4\u7684\u9ed8\u8ba4\u8d44\u6e90\u3002\n\u6a21\u7ec4\u542f\u52a8\u65f6\u4f1a\u81ea\u52a8\u68c0\u67e5\u8fd9\u4e9b\u6587\u4ef6\uff0c\u5b58\u5728\u5219\u4f7f\u7528\u81ea\u5b9a\u4e49\u7248\u672c\uff0c\n\u4e0d\u5b58\u5728\u5219\u4f7f\u7528\u9ed8\u8ba4\u7248\u672c\u3002\n\n----------------------------------------------------\n\u53ef\u66ff\u6362\u8d44\u6e90\u5217\u8868\n----------------------------------------------------\n\n1. \u6210\u5c31\u724c\u533e\u7eb9\u7406\n   \u6587\u4ef6\u540d\uff1aplaque.png\n   \u683c\u5f0f\uff1a  PNG\n   \u5c3a\u5bf8\uff1a  240 x 56 \u50cf\u7d20\uff08\u63a8\u8350\uff09\n   \u8bf4\u660e\uff1a  \u5b8c\u6210\u6210\u5c31\u65f6\u5c4f\u5e55\u4e0a\u65b9\u663e\u793a\u7684\u724c\u533e\u80cc\u666f\u3002\n          \u724c\u533e\u7684\u52a8\u753b\uff08\u6ed1\u5165\u3001\u6de1\u51fa\u3001\u900f\u660e\u5ea6\uff09\u7167\u5e38\u751f\u6548\u3002\n          \u6587\u5b57\uff08\"\u2726 \u6210\u5c31\u8fbe\u6210 \u2726\"\u548c\u6210\u5c31\u540d\u79f0\uff09\u4f1a\u53e0\u52a0\u5728\u7eb9\u7406\u4e4b\u4e0a\u3002\n          \u82e5\u56fe\u7247\u5c3a\u5bf8\u4e0d\u540c\uff0c\u4f1a\u88ab\u62c9\u4f38\u5230 240x56\u3002\n          \u5efa\u8bae\u4f7f\u7528\u534a\u900f\u660e\u80cc\u666f\u7684 PNG \u4ee5\u4fdd\u7559\u539f\u6709\u4f18\u96c5\u6548\u679c\u3002\n\n2. \u5b8c\u6210\u97f3\u6548\n   \u6587\u4ef6\u540d\uff1achime.mp3\n   \u683c\u5f0f\uff1a  MP3\n   \u65f6\u957f\uff1a  \u5efa\u8bae 3-4 \u79d2\n   \u8bf4\u660e\uff1a  \u5b8c\u6210\u6210\u5c31\u65f6\u64ad\u653e\u7684\u97f3\u6548\u3002\n          \u82e5\u5b58\u5728\u6b64\u6587\u4ef6\uff0c\u4f1a\u5b8c\u5168\u66ff\u6362\u9ed8\u8ba4\u7684\u7d2b\u6c34\u6676\u65cb\u5f8b\u3002\n          \u64ad\u653e\u662f\u5f02\u6b65\u7684\uff0c\u4e0d\u4f1a\u963b\u585e\u6e38\u620f\u3002\n          \u4ec5\u652f\u6301 .mp3\uff0c\u4e0d\u652f\u6301\u5176\u4ed6\u683c\u5f0f\uff08wav/ogg/flac\u7b49\uff09\u3002\n\n----------------------------------------------------\n\u683c\u5f0f\u6821\u9a8c\n----------------------------------------------------\n\n\u6a21\u7ec4\u4f1a\u68c0\u67e5\u6587\u4ef6\u5934\u9b54\u6570\u6765\u786e\u4fdd\u6587\u4ef6\u683c\u5f0f\u6b63\u786e\uff1a\n- MP3 \u6587\u4ef6\uff1a\u5fc5\u987b\u5305\u542b ID3 \u6807\u7b7e\u6216 MPEG \u5e27\u540c\u6b65\u5934\n\n\u5c06\u89c6\u9891\u6587\u4ef6\uff08.mp4/.avi/.mkv \u7b49\uff09\u91cd\u547d\u540d\u4e3a .mp3 \u4e0d\u4f1a\u88ab\n\u52a0\u8f7d\uff0c\u56e0\u4e3a\u5b83\u4eec\u4e0d\u5305\u542b\u6709\u6548\u7684 MP3 \u6587\u4ef6\u5934\u3002\n\n----------------------------------------------------\n\u6ce8\u610f\u4e8b\u9879\n----------------------------------------------------\n\n- \u6587\u4ef6\u547d\u540d\u5fc5\u987b\u4e0e\u4e0a\u8ff0\u5b8c\u5168\u4e00\u81f4\uff08\u5305\u62ec\u5927\u5c0f\u5199\uff09\u3002\n- \u4fee\u6539\u8d44\u6e90\u540e\u9700\u8981\u91cd\u542f\u6e38\u620f\u624d\u80fd\u751f\u6548\u3002\n- \u82e5\u6587\u4ef6\u683c\u5f0f\u4e0d\u6b63\u786e\uff0c\u6a21\u7ec4\u4f1a\u81ea\u52a8\u56de\u9000\u5230\u9ed8\u8ba4\u6837\u5f0f\u3002\n- \u4e0d\u8981\u5728\u6b64\u6587\u4ef6\u5939\u4e2d\u653e\u7f6e\u5176\u4ed6\u65e0\u5173\u6587\u4ef6\u3002\n- \u4ec5\u652f\u6301 MP3 \u97f3\u6548\u683c\u5f0f\u3002\n";
        try {
            Files.writeString(readme, content);
            LOGGER.info("Generated resource README at {}", readme);
        } catch (IOException e) {
            LOGGER.warn("Failed to generate README: {}", e.getMessage());
        }
    }

    private static void generateReadmeEn(Path resDir) {
        Path readme = resDir.resolve("README_EN.txt");
        if (Files.exists(readme)) {
            return;
        }
        String content = "====================================================\nAdvancement Overhaul - Custom Resource Replacement\n====================================================\n\nPlace custom files in this folder to replace the mod's\ndefault assets. The mod checks for these files at\nstartup; if found, the custom version is used,\notherwise the default is used.\n\n----------------------------------------------------\nReplaceable Resources\n----------------------------------------------------\n\n1. Achievement Plaque Texture\n   File:    plaque.png\n   Format:  PNG\n   Size:    240 x 56 pixels (recommended)\n   Notes:   Background of the plaque shown at the top\n            of the screen when an advancement is earned.\n            Animations (slide-in, fade-out, opacity)\n            still apply. Text (\"\u2726 Achievement Earned \u2726\"\n            and the advancement name) is rendered on top.\n            If the image size differs, it will be\n            stretched to 240x56. A semi-transparent\n            background is recommended.\n\n2. Completion Chime\n   File:    chime.mp3\n   Format:  MP3\n   Length:  3-4 seconds recommended\n   Notes:   Sound played when an advancement is earned.\n            If this file exists, it completely replaces\n            the default amethyst melody. Playback is\n            asynchronous and won't block the game.\n            Only .mp3 is supported (no wav/ogg/flac).\n\n----------------------------------------------------\nFormat Validation\n----------------------------------------------------\n\nThe mod checks file header magic bytes:\n- MP3 file: must contain an ID3 tag or MPEG frame sync\n\nRenaming a video file (.mp4/.avi/.mkv etc.) to .mp3\nwill NOT work \u2014 it lacks a valid MP3 file header.\n\n----------------------------------------------------\nNotes\n----------------------------------------------------\n\n- File names must match exactly (case-sensitive).\n- Restart the game after changing resource files.\n- If the file format is invalid, the mod falls back\n  to the default style.\n- Do not place unrelated files in this folder.\n- Only MP3 audio format is supported.\n";
        try {
            Files.writeString(readme, content);
            LOGGER.info("Generated resource README_EN at {}", readme);
        } catch (IOException e) {
            LOGGER.warn("Failed to generate README_EN: {}", e.getMessage());
        }
    }
}
