package com.dreamer.ao.client.gui;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.panel.ListSelector;
import com.dreamer.ao.client.gui.state.OverlayState.CtxAct;
import com.dreamer.ao.client.gui.state.OverlayType;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 右键上下文菜单处理。
 * <p>
 * 管理所有右键菜单场景：自定义成就、原版成就、图片元素、多选批量操作、画布空白区域、查看模式。
 * 确认对话框（删除、批量删除、图片删除）也在本类中统一处理。
 * <p>
 * 从 {@link AdvancementScreen} 拆分而来。
 */
final class ContextMenuHandler {

    private final AdvancementScreen screen;

    ContextMenuHandler(AdvancementScreen screen) {
        this.screen = screen;
    }

    // ═══════════════ 自定义成就右键菜单 ═══════════════

    void showCtx(double mx, double my, String id) {
        var ov = screen.overlay;
        ov.ctxX = (int) mx; ov.ctxY = (int) my; ov.ctxActions.clear();
        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.DETAIL_TITLE).getString(), () -> openDetail(id)));
        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.EDIT_TITLE).getString(), () -> screen.openEdit(id)));
        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.DELETE).getString(), () -> requestDelete(id)));
        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.RESET).getString(), () -> GuiUtils.sendCommand("adv reset @s " + id)));
        var a = screen.adv(id);
        if (a != null) {
            String key = a.isHidden() ? LangKeys.SHOW : LangKeys.HIDE;
            ov.ctxActions.add(new CtxAct(Component.translatable(key).getString(), () -> GuiUtils.sendCommand("adv togglehidden " + id)));
        }
        ov.current = OverlayType.CTX;
    }

    // ═══════════════ 原版成就右键菜单 ═══════════════

    void showVanillaCtx(double mx, double my, String id) {
        var ov = screen.overlay;
        ov.ctxX = (int) mx; ov.ctxY = (int) my; ov.ctxActions.clear();
        boolean enabled = ClientDataStore.getInstance().isVanillaEnabled(id);

        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.DETAIL_TITLE).getString(),
                () -> openDetail(id)));

        if (enabled) {
            ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.ADV_TT_DISABLE_BTN).getString(),
                    () -> GuiUtils.sendCommand("adv vanilla disable " + id)));
            ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.VANILLA_ASSIGN_TAB).getString(),
                    () -> screen.tabManager.openVanillaTabSel(id)));
            ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.EDIT_TITLE).getString(),
                    () -> openVanillaEdit(id)));
        } else {
            ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.VANILLA_ASSIGN_TAB).getString(),
                    () -> screen.tabManager.openVanillaTabSel(id)));
        }

        ov.current = OverlayType.CTX;
    }

    private void openVanillaEdit(String id) {
        var va = screen.vanillaAdvMap.get(id);
        String name = va != null ? va.getLocalizedName() : id;
        String desc = va != null ? va.getLocalizedDesc() : "";
        screen.editPanel.openVanillaEdit(Minecraft.getInstance().font, id, name, desc);
        screen.overlay.current = OverlayType.EDIT;
    }

    // ═══════════════ 批量右键菜单 ═══════════════

    void showBatchCtx(double mx, double my) {
        var ov = screen.overlay;
        ov.ctxX = (int) mx; ov.ctxY = (int) my; ov.ctxActions.clear();
        int n = screen.selection.multiSel.size();
        ov.ctxActions.add(new CtxAct(
                String.format(Component.translatable(LangKeys.BATCH_DELETE).getString(), n),
                () -> requestBatchDelete()));
        ov.ctxActions.add(new CtxAct(
                String.format(Component.translatable(LangKeys.BATCH_HIDE).getString(), n),
                () -> {
                    for (String id : screen.selection.multiSel) {
                        if (screen.isVanillaAdvId(id)) GuiUtils.sendCommand("adv vanilla disable " + id);
                        else {
                            var a = screen.adv(id);
                            if (a != null && !a.isHidden()) GuiUtils.sendCommand("adv togglehidden " + id);
                        }
                    }
                }));
        ov.ctxActions.add(new CtxAct(
                String.format(Component.translatable(LangKeys.BATCH_SHOW).getString(), n),
                () -> {
                    for (String id : screen.selection.multiSel) {
                        if (screen.isVanillaAdvId(id)) GuiUtils.sendCommand("adv vanilla enable " + id);
                        else {
                            var a = screen.adv(id);
                            if (a != null && a.isHidden()) GuiUtils.sendCommand("adv togglehidden " + id);
                        }
                    }
                }));
        ov.current = OverlayType.CTX;
    }

    // ═══════════════ 删除确认 ═══════════════

    void requestDelete(String id) {
        var a = screen.adv(id);
        screen.overlay.confirmText = Component.translatable(LangKeys.CONFIRM_DELETE_PREFIX,
                a != null ? a.getName() : id).getString();
        screen.overlay.confirmAction = () -> GuiUtils.sendCommand("adv delete " + id);
        screen.overlay.current = OverlayType.CONFIRM;
    }

    void requestBatchDelete() {
        screen.overlay.confirmText = Component.translatable(LangKeys.CONFIRM_BATCH_DELETE,
                screen.selection.multiSel.size()).getString();
        screen.overlay.confirmAction = () -> {
            GuiUtils.sendCommand("adv batchdelete " + String.join(",", screen.selection.multiSel));
            screen.selection.clear();
        };
        screen.overlay.current = OverlayType.CONFIRM;
    }

    // ═══════════════ 画布空白右键 / 创建 ═══════════════

    void showCanvasCtx(double mx, double my) {
        var ov = screen.overlay;
        ov.ctxX = (int) mx; ov.ctxY = (int) my; ov.ctxActions.clear();
        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.CREATE_TITLE).getString(),
                () -> screen.openCreateAt(mx, my)));
        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.CREATE_IMAGE).getString(),
                () -> openImageCreator(mx, my)));
        ov.current = OverlayType.CTX;
    }

    // ═══════════════ 图片元素 ═══════════════

    private void openImageCreator(double mx, double my) {
        List<String> files = ImageManager.listImageFiles();
        if (files.isEmpty()) {
            screen.overlay.confirmText = Component.translatable(LangKeys.IMAGE_NO_FILES).getString();
            screen.overlay.confirmAction = null;
            screen.overlay.current = OverlayType.CONFIRM;
            return;
        }
        List<ListSelector.Entry> entries = new ArrayList<>();
        for (String f : files) entries.add(new ListSelector.Entry(f, f));
        screen.showSelector(entries, e -> placeImage(e.id(),
                screen.canvas.toWorldX(mx), screen.canvas.toWorldY(my)));
    }

    private void placeImage(String filename, int worldX, int worldY) {
        String imgId = "img_" + UUID.randomUUID().toString().substring(0, 8);
        ImageElement img = new ImageElement(imgId, filename, worldX, worldY);

        ResourceLocation texId = ImageManager.loadTexture(imgId, filename);
        if (texId != null) {
            img.setTextureId(texId);
            int[] size = ImageManager.getTextureSize(imgId);
            img.setOriginalSize(size[0], size[1]);
            screen.imageElements.add(img);
            ImageManager.save(screen.imageElements);
        } else {
            String error = ImageManager.getLastError();
            screen.overlay.confirmText = Component.translatable(LangKeys.IMAGE_LOAD_FAIL,
                    error != null ? error : Component.translatable(LangKeys.UNKNOWN_ERROR).getString()).getString();
            screen.overlay.confirmAction = null;
            screen.overlay.current = OverlayType.CONFIRM;
        }
    }

    void showImageCtx(double mx, double my, String imageId) {
        var ov = screen.overlay;
        ov.ctxX = (int) mx; ov.ctxY = (int) my; ov.ctxActions.clear();
        ImageElement img = findImageById(imageId);
        if (img == null) return;

        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.IMAGE_SCALE_UP).getString(),
                () -> { img.setScale(img.getScale() * 1.25f); ImageManager.save(screen.imageElements); }));
        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.IMAGE_SCALE_DOWN).getString(),
                () -> { img.setScale(img.getScale() * 0.8f); ImageManager.save(screen.imageElements); }));
        if (img.getScale() != 1.0f) {
            ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.IMAGE_SCALE_RESET).getString(),
                    () -> { img.setScale(1.0f); ImageManager.save(screen.imageElements); }));
        }
        ov.ctxActions.add(new CtxAct(
                Component.translatable(img.isLocked() ? LangKeys.IMAGE_UNLOCK : LangKeys.IMAGE_LOCK).getString(),
                () -> { img.setLocked(!img.isLocked()); ImageManager.save(screen.imageElements); }));

        int idx = screen.imageElements.indexOf(img);
        if (idx < screen.imageElements.size() - 1) {
            ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.IMAGE_TO_FRONT).getString(),
                    () -> { screen.imageElements.remove(img); screen.imageElements.add(img); ImageManager.save(screen.imageElements); }));
        }
        if (idx > 0) {
            ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.IMAGE_TO_BACK).getString(),
                    () -> { screen.imageElements.remove(img); screen.imageElements.add(0, img); ImageManager.save(screen.imageElements); }));
        }

        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.IMAGE_DELETE).getString(),
                () -> requestImageDelete(imageId)));
        ov.current = OverlayType.CTX;
    }

    void requestImageDelete(String imageId) {
        ImageElement img = findImageById(imageId);
        if (img == null) return;
        screen.overlay.confirmText = Component.translatable(LangKeys.CONFIRM_IMAGE_DELETE,
                img.getPath()).getString();
        screen.overlay.confirmAction = () -> {
            screen.imageElements.removeIf(i -> i.getId().equals(imageId));
            if (imageId.equals(screen.selectedImageId)) screen.selectedImageId = null;
            ImageManager.save(screen.imageElements);
        };
        screen.overlay.current = OverlayType.CONFIRM;
    }

    // ═══════════════ 查看模式右键 / 详情 ═══════════════

    void showViewCtx(double mx, double my, String id) {
        var ov = screen.overlay;
        ov.ctxX = (int) mx; ov.ctxY = (int) my; ov.ctxActions.clear();
        ov.ctxActions.add(new CtxAct(Component.translatable(LangKeys.DETAIL_TITLE).getString(),
                () -> openDetail(id)));
        ov.current = OverlayType.CTX;
    }

    private void openDetail(String id) {
        screen.selection.select(id);
        screen.overlay.detailId = id;
        screen.overlay.detailOpenTime = System.currentTimeMillis();
        screen.overlay.current = OverlayType.DETAIL;
    }

    // ═══════════════ 图片元素查找 ═══════════════

    ImageElement findImageById(String id) {
        for (ImageElement img : screen.imageElements)
            if (img.getId().equals(id)) return img;
        return null;
    }

    ImageElement imageAt(double mx, double my) {
        var c = screen.canvas;
        for (int i = screen.imageElements.size() - 1; i >= 0; i--) {
            ImageElement img = screen.imageElements.get(i);
            if (img.getTextureId() == null) continue;
            int sx = c.toScreenX(img.getX());
            int sy = c.toScreenY(img.getY());
            int sw = (int) (img.getRenderWidth() * c.zoom);
            int sh = (int) (img.getRenderHeight() * c.zoom);
            if (mx >= sx && mx < sx + sw && my >= sy && my < sy + sh) return img;
        }
        return null;
    }

    // ═══════════════ 标签管理 ═══════════════

    void openTabManage() {
        screen.overlay.current = OverlayType.TAB_MANAGE;
    }

    /**
     * 级联删除标签：删除标签下所有自定义成就，原版成就回到原版标签并禁用。
     */
    void cascadeDeleteTab(String tabName) {
        ClientDataStore cs = ClientDataStore.getInstance();

        // 1. 删除该标签下的自定义成就
        List<String> customToDelete = new ArrayList<>();
        for (var adv : cs.getAdvancements().values()) {
            if (tabName.equals(adv.getTab())) customToDelete.add(adv.getId());
        }
        if (!customToDelete.isEmpty()) {
            GuiUtils.sendCommand("adv batchdelete " + String.join(",", customToDelete));
        }

        // 2. 清除原版/模组成就的标签分配并禁用
        for (var va : screen.vanillaAdvs) {
            VanillaAdvMeta meta = cs.getVanillaMeta(va.id());
            if (meta != null && tabName.equals(meta.getTab())) {
                GuiUtils.sendCommand("adv vanilla cleartab " + va.id());
                GuiUtils.sendCommand("adv vanilla disable " + va.id());
            }
        }

        // 3. 删除标签
        GuiUtils.sendCommand("adv tab delete " + tabName);
    }
}
