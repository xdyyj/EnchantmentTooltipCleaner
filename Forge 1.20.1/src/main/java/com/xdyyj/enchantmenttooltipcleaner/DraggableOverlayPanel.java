package com.xdyyj.enchantmenttooltipcleaner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DraggableOverlayPanel {

    public static boolean isVisible = false;

    // 面板尺寸与位置 (高度 166，精确贴合原版容器 imageHeight=166，控件零重叠)
    public static final int PANEL_WIDTH = 146;
    public static final int PANEL_HEIGHT = 166;
    public static final int TITLE_BAR_HEIGHT = 18;
    public static final int TAB_BAR_HEIGHT = 15;

    private static int panelX = -1;
    private static int panelY = -1;
    private static boolean isDragging = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;

    // 当前选中的 Tab: 0 = 常规, 1 = 过滤, 2 = 排除/标签
    public static int currentTab = 0;

    // 记忆最后悬停的物品
    private static ItemStack lockedStack = ItemStack.EMPTY;

    // 当前选中的物品标签索引 (物品拥有多个标签时可用右键轮转切换)
    private static int selectedTagIndex = 0;

    // 状态反馈提示文本与计时
    private static String statusMessage = null;
    private static long statusMessageExpiry = 0L;

    // 当前悬停说明文本 (悬浮显示说明)
    private static String hoveredTooltip = null;

    // 工具提示专属渲染标记 (用于 RenderTooltipEvent.Pre 区分自身与背景容器槽位穿透)
    public static volatile boolean isRenderingOurOwnTooltip = false;

    public static boolean isDragging() {
        return isDragging;
    }

    public static void toggleVisibility() {
        isVisible = !isVisible;
        ClientEvents.checkAndSyncEmiExclusion();
    }

    public static void resetPosition() {
        panelX = -1;
        panelY = -1;
    }

    public static void updateHoveredSlot(Slot slot) {
        if (slot != null && !slot.getItem().isEmpty()) {
            // 切换到不同物品时，标签轮转索引归零
            if (!ItemStack.isSameItemSameTags(lockedStack, slot.getItem())) {
                selectedTagIndex = 0;
            }
            lockedStack = slot.getItem().copy();
        }
    }

    // --- 原版按钮点击音效 ---
    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    // 播放音效并返回 true，供所有有效点击分支复用
    private static boolean click() {
        playClickSound();
        return true;
    }

    // 收集当前锁定物品的全部标签，用于右键轮转选择
    private static List<TagKey<Item>> getLockedTags() {
        if (lockedStack.isEmpty()) {
            return Collections.emptyList();
        }
        List<TagKey<Item>> tags = new ArrayList<>();
        lockedStack.getTags().forEach(tags::add);
        return tags;
    }

    private static TagKey<Item> getSelectedTag() {
        List<TagKey<Item>> tags = getLockedTags();
        if (tags.isEmpty()) {
            return null;
        }
        if (selectedTagIndex < 0 || selectedTagIndex >= tags.size()) {
            selectedTagIndex = 0;
        }
        return tags.get(selectedTagIndex);
    }

    // 统一翻译键取值，保证繁体/英文环境下呈现地道语言
    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    public static void ensurePosition(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            ensurePosition(containerScreen.width, containerScreen.height,
                containerScreen.getGuiLeft(), containerScreen.getGuiTop(), containerScreen.getXSize());
        } else if (screen != null) {
            ensurePosition(screen.width, screen.height, 0, 0, screen.width);
        }
    }

    public static void ensurePosition(int screenWidth, int screenHeight, int guiLeft, int guiTop, int xSize) {
        if (panelX == -1 || panelY == -1) {
            int savedX = ModConfig.OVERLAY_POS_X.get();
            int savedY = ModConfig.OVERLAY_POS_Y.get();
            if (savedX >= 0 && savedY >= 0) {
                panelX = savedX;
                panelY = savedY;
            } else {
                panelX = guiLeft - PANEL_WIDTH - 2;
                if (panelX < 0) {
                    panelX = guiLeft + xSize + 2;
                }
                panelY = guiTop;
            }
        }
        panelX = Math.max(0, Math.min(panelX, screenWidth - PANEL_WIDTH));
        panelY = Math.max(0, Math.min(panelY, screenHeight - PANEL_HEIGHT));
    }

    public static int getPanelX() {
        return panelX;
    }

    public static int getPanelY() {
        return panelY;
    }

    private static double lastMouseX = -1;
    private static double lastMouseY = -1;

    public static void updateMousePosition(double mx, double my) {
        lastMouseX = mx;
        lastMouseY = my;
    }

    public static boolean isMouseOverPanel() {
        if (!isVisible) return false;
        if (panelX < 0 || panelY < 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                ensurePosition(mc.screen);
            }
        }
        if (panelX < 0 || panelY < 0) return false;
        if (isMouseOverPanel(lastMouseX, lastMouseY)) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler != null && mc.getWindow() != null) {
            double mx = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
            return isMouseOverPanel(mx, my);
        }
        return false;
    }

    public static boolean isMouseOverPanel(double mouseX, double mouseY) {
        if (!isVisible) return false;
        if (panelX < 0 || panelY < 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                ensurePosition(mc.screen);
            }
        }
        if (panelX < 0 || panelY < 0) return false;
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH &&
               mouseY >= panelY && mouseY <= panelY + PANEL_HEIGHT;
    }

    // --- 渲染悬浮面板 ---
    public static void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, AbstractContainerScreen<?> screen) {
        if (!isVisible) return;

        updateMousePosition(mouseX, mouseY);
        ensurePosition(screen.width, screen.height, screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize());
        Font font = Minecraft.getInstance().font;
        hoveredTooltip = null;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);

        // 1. 面板主体柔和阴影与原版石板深色质感底色
        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, 0x60000000);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF81C1C20);
        graphics.hLine(panelX + 1, panelX + PANEL_WIDTH - 2, panelY + 1, 0x20FFFFFF);
        graphics.renderOutline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF3A3A42);

        // 2. 标题栏 (无任何 emoji，统一利落纯净风格)
        boolean titleHovered = mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH &&
                               mouseY >= panelY && mouseY <= panelY + TITLE_BAR_HEIGHT;
        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + TITLE_BAR_HEIGHT, titleHovered ? 0xFF28282E : 0xFF202024);
        graphics.hLine(panelX + 1, panelX + PANEL_WIDTH - 2, panelY + TITLE_BAR_HEIGHT, 0xFF323238);

        // 标题栏内嵌操作动态反馈：状态生效时以亮绿文本呈现，2秒后自动淡出恢复默认标题
        if (statusMessage != null && System.currentTimeMillis() < statusMessageExpiry) {
            graphics.drawString(font, "§a" + statusMessage, panelX + 7, panelY + 5, 0xFF55FF55, false);
        } else {
            graphics.drawString(font, tr("gui.enchantmenttooltipcleaner.overlay.title"), panelX + 7, panelY + 5, 0xFFEDEDED, false);
        }

        // 关闭按钮 [X] (无 emoji，纯英文字母 X)
        int closeX = panelX + PANEL_WIDTH - 14;
        int closeY = panelY + 4;
        boolean closeHovered = mouseX >= closeX - 2 && mouseX <= closeX + 10 && mouseY >= closeY - 1 && mouseY <= closeY + 11;
        if (closeHovered) {
            graphics.fill(closeX - 2, closeY - 1, closeX + 10, closeY + 11, 0x40FF5555);
            graphics.renderOutline(closeX - 2, closeY - 1, 12, 12, 0xFFFF5555);
            hoveredTooltip = tr("gui.enchantmenttooltipcleaner.overlay.closeTooltip");
        }
        graphics.drawString(font, "X", closeX + 2, closeY + 1, closeHovered ? 0xFFFF5555 : 0xFF888888, false);

        // 3. Tab 分页栏 (每个选项按钮均有独立完整 4 边边框与左右隔断，选中底边不消失)
        int tabY = panelY + TITLE_BAR_HEIGHT + 2;
        int tabH = 15;
        int tabW = 44;
        renderTabButton(graphics, font, mouseX, mouseY, panelX + 5, tabY, tabW, tabH, tr("gui.enchantmenttooltipcleaner.tab.basic"), currentTab == 0);
        renderTabButton(graphics, font, mouseX, mouseY, panelX + 51, tabY, tabW, tabH, tr("gui.enchantmenttooltipcleaner.tab.filters"), currentTab == 1);
        renderTabButton(graphics, font, mouseX, mouseY, panelX + 97, tabY, tabW, tabH, tr("gui.enchantmenttooltipcleaner.tab.exclusions"), currentTab == 2);

        // Tab 下方分割横线
        graphics.hLine(panelX + 4, panelX + PANEL_WIDTH - 5, tabY + tabH + 2, 0xFF35353E);

        // Tab 悬停说明
        if (mouseY >= tabY && mouseY <= tabY + tabH) {
            if (mouseX >= panelX + 5 && mouseX <= panelX + 49) {
                hoveredTooltip = tr("gui.enchantmenttooltipcleaner.overlay.tab.basicTooltip");
            } else if (mouseX >= panelX + 51 && mouseX <= panelX + 95) {
                hoveredTooltip = tr("gui.enchantmenttooltipcleaner.overlay.tab.filtersTooltip");
            } else if (mouseX >= panelX + 97 && mouseX <= panelX + 141) {
                hoveredTooltip = tr("gui.enchantmenttooltipcleaner.overlay.tab.exclusionsTooltip");
            }
        }

        int contentStartY = tabY + tabH + 5;

        // 4. Tab 内容渲染
        if (currentTab == 0) {
            renderBasicTab(graphics, font, mouseX, mouseY, contentStartY);
        } else if (currentTab == 1) {
            renderFiltersTab(graphics, font, mouseX, mouseY, contentStartY);
        } else {
            renderExclusionsTab(graphics, font, mouseX, mouseY, contentStartY);
        }

        // 5. 底部跳转到全屏设置面板按钮 (面板高166，位于 Y=149，完全杜绝与Tab2重叠)
        int fullCfgY = panelY + PANEL_HEIGHT - 17;
        int fullCfgW = PANEL_WIDTH - 12;
        int fullCfgH = 13;
        boolean fullCfgHovered = mouseX >= panelX + 6 && mouseX <= panelX + 6 + fullCfgW &&
                                 mouseY >= fullCfgY && mouseY <= fullCfgY + fullCfgH;

        graphics.fill(panelX + 6, fullCfgY, panelX + 6 + fullCfgW, fullCfgY + fullCfgH, fullCfgHovered ? 0xFF35353D : 0xFF222226);
        graphics.renderOutline(panelX + 6, fullCfgY, fullCfgW, fullCfgH, fullCfgHovered ? 0xFF888894 : 0xFF38383F);
        graphics.drawCenteredString(font, tr("gui.enchantmenttooltipcleaner.overlay.openFullSettings"), panelX + 6 + fullCfgW / 2, fullCfgY + 3, fullCfgHovered ? 0xFFFFFFFF : 0xFFAAAAAA);
        if (fullCfgHovered) {
            hoveredTooltip = tr("gui.enchantmenttooltipcleaner.overlay.openFullSettingsTooltip");
        }

        graphics.pose().popPose();

        // 6. 悬浮说明展示 (提高至最高 Z=900，杜绝被背包或 EMI 覆盖)
        if (hoveredTooltip != null) {
            isRenderingOurOwnTooltip = true;
            try {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 900.0F);
                graphics.renderTooltip(font, net.minecraft.network.chat.Component.literal("§7" + hoveredTooltip), mouseX, mouseY);
                graphics.pose().popPose();
            } finally {
                isRenderingOurOwnTooltip = false;
            }
        }
    }

    private static void renderTabButton(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, int w, int h, String text, boolean active) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = active ? 0xFF282832 : (hovered ? 0xFF222228 : 0xFF17171B);
        int border = active ? 0xFF55FF55 : (hovered ? 0xFF656575 : 0xFF383842);
        graphics.fill(x, y, x + w, y + h, bg);
        // 渲染完整 4 边边框，左右独立隔断，底边永不消失
        graphics.renderOutline(x, y, w, h, border);
        String label = (active ? "§a§l" : (hovered ? "§f" : "§7")) + text;
        graphics.drawCenteredString(font, label, x + w / 2, y + 4, 0xFFFFFFFF);
    }

    // --- Tab 0: 常规基础开关 ---
    private static void renderBasicTab(GuiGraphics graphics, Font font, int mouseX, int mouseY, int startY) {
        int itemH = 14;
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 0 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.toggle.global"), ModConfig.GLOBAL_ENABLE.get(), tr("gui.enchantmenttooltipcleaner.overlay.toggle.global.tooltip"));
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 1 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.toggle.enchantments"), ModConfig.REMOVE_ENCHANTMENTS.get(), tr("gui.enchantmenttooltipcleaner.overlay.toggle.enchantments.tooltip"));
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 2 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.toggle.descriptions"), ModConfig.REMOVE_DESCRIPTIONS.get(), tr("gui.enchantmenttooltipcleaner.overlay.toggle.descriptions.tooltip"));
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 3 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.toggle.attributes"), ModConfig.REMOVE_ATTRIBUTES.get(), tr("gui.enchantmenttooltipcleaner.overlay.toggle.attributes.tooltip"));
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 4 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.toggle.potionEffects"), ModConfig.REMOVE_POTION_EFFECTS.get(), tr("gui.enchantmenttooltipcleaner.overlay.toggle.potionEffects.tooltip"));
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 5 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.toggle.debug"), ModConfig.DEBUG_MODE.get(), tr("gui.enchantmenttooltipcleaner.overlay.toggle.debug.tooltip"));

        graphics.drawString(font, tr("gui.enchantmenttooltipcleaner.overlay.basicHint"), panelX + 8, startY + 6 * itemH + 2, 0x666666, false);
    }

    // --- Tab 1: 5 大高级自定义过滤器 ---
    private static void renderFiltersTab(GuiGraphics graphics, Font font, int mouseX, int mouseY, int startY) {
        int itemH = 15;

        int contCount = ModConfig.REMOVE_LINES_CONTAINING.get().size();
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 0 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.filter.textContains", contCount), ModConfig.ENABLE_TEXT_CONTAINS.get(), tr("gui.enchantmenttooltipcleaner.overlay.filter.textContains.tooltip"));

        int exactCount = ModConfig.REMOVE_LINES_EXACT.get().size();
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 1 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.filter.textExact", exactCount), ModConfig.ENABLE_TEXT_EXACT.get(), tr("gui.enchantmenttooltipcleaner.overlay.filter.textExact.tooltip"));

        int regexCount = ModConfig.REMOVE_LINES_REGEX.get().size();
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 2 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.filter.textRegex", regexCount), ModConfig.ENABLE_TEXT_REGEX.get(), tr("gui.enchantmenttooltipcleaner.overlay.filter.textRegex.tooltip"));

        int keyContCount = ModConfig.REMOVE_KEYS_CONTAINING.get().size();
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 3 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.filter.keyContains", keyContCount), ModConfig.ENABLE_KEY_CONTAINS.get(), tr("gui.enchantmenttooltipcleaner.overlay.filter.keyContains.tooltip"));

        int keyExactCount = ModConfig.REMOVE_KEYS_EXACT.get().size();
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY + 4 * itemH, tr("gui.enchantmenttooltipcleaner.overlay.filter.keyExact", keyExactCount), ModConfig.ENABLE_KEY_EXACT.get(), tr("gui.enchantmenttooltipcleaner.overlay.filter.keyExact.tooltip"));

        graphics.drawString(font, tr("gui.enchantmenttooltipcleaner.overlay.filtersHint"), panelX + 8, startY + 5 * itemH + 2, 0x666666, false);
    }

    // --- Tab 2: 排除名单与标签 (记忆最后悬停物品) ---
    private static void renderExclusionsTab(GuiGraphics graphics, Font font, int mouseX, int mouseY, int startY) {
        checkToggleWithTooltip(graphics, font, mouseX, mouseY, panelX + 8, startY, tr("gui.enchantmenttooltipcleaner.overlay.toggle.exclusions"), ModConfig.ENABLE_EXCLUSIONS.get(), tr("gui.enchantmenttooltipcleaner.overlay.toggle.exclusions.tooltip"));

        int infoY = startY + 14;
        graphics.fill(panelX + 6, infoY, panelX + PANEL_WIDTH - 6, infoY + 36, 0xFF18181B);
        graphics.renderOutline(panelX + 6, infoY, PANEL_WIDTH - 12, 36, 0xFF303036);

        if (!lockedStack.isEmpty()) {
            int slotX = panelX + 9;
            int slotY = infoY + 9;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF0D0D10);
            graphics.hLine(slotX - 1, slotX + 18, slotY - 1, 0xFF2A2A30);
            graphics.vLine(slotX - 1, slotY - 1, slotY + 18, 0xFF2A2A30);
            graphics.hLine(slotX - 1, slotX + 18, slotY + 18, 0xFF4A4A52);
            graphics.vLine(slotX + 18, slotY - 1, slotY + 18, 0xFF4A4A52);

            graphics.renderItem(lockedStack, slotX + 1, slotY + 1);
            graphics.renderItemDecorations(font, lockedStack, slotX + 1, slotY + 1);

            String itemName = lockedStack.getHoverName().getString();
            if (itemName.length() > 9) itemName = itemName.substring(0, 8) + "..";
            graphics.drawString(font, itemName, panelX + 31, infoY + 4, 0xFFFFDD55, false);

            ResourceLocation id = BuiltInRegistries.ITEM.getKey(lockedStack.getItem());
            String itemId = id.toString();
            String modId = id.getNamespace();
            graphics.drawString(font, "§8" + id.getPath(), panelX + 31, infoY + 15, 0xAAAAAA, false);
            graphics.drawString(font, "§b" + modId, panelX + 31, infoY + 25, 0x888888, false);

            // 操作按钮区 (每个按钮高 13px，步进 15px，无缝舒展展开)
            int btnY = infoY + 39;
            int btnW = PANEL_WIDTH - 14;
            int btnH = 13;

            // 1. 排除此物品
            boolean isItemExcluded = ModConfig.EXCLUDED_ITEMS.get().contains(itemId);
            renderActionButton(graphics, font, mouseX, mouseY, panelX + 7, btnY, btnW, btnH,
                isItemExcluded ? tr("gui.enchantmenttooltipcleaner.overlay.excludeItem.done") : tr("gui.enchantmenttooltipcleaner.overlay.excludeItem"),
                isItemExcluded ? 0xFF55FF55 : 0xFFFFD700, tr("gui.enchantmenttooltipcleaner.overlay.excludeItem.tooltip"));

            // 2. 排除整 Mod
            boolean isModExcluded = ModConfig.EXCLUDED_MODS.get().contains(modId);
            renderActionButton(graphics, font, mouseX, mouseY, panelX + 7, btnY + 15, btnW, btnH,
                isModExcluded ? tr("gui.enchantmenttooltipcleaner.overlay.excludeMod.done") : tr("gui.enchantmenttooltipcleaner.overlay.excludeMod", modId),
                isModExcluded ? 0xFF55FF55 : 0xFF55FFFF, tr("gui.enchantmenttooltipcleaner.overlay.excludeMod.tooltip"));

            // 3. 排除 Tag 标签 (左键排除/取消，右键轮转选择下一个标签)
            List<TagKey<Item>> allTags = getLockedTags();
            TagKey<Item> selectedTag = getSelectedTag();
            if (selectedTag != null) {
                String tagStr = "#" + selectedTag.location().toString();
                String displayTag = tagStr.length() > 16 ? tagStr.substring(0, 14) + ".." : tagStr;
                boolean isTagExcluded = ModConfig.EXCLUDED_ITEMS.get().contains(tagStr);
                renderActionButton(graphics, font, mouseX, mouseY, panelX + 7, btnY + 30, btnW, btnH,
                    isTagExcluded ? tr("gui.enchantmenttooltipcleaner.overlay.excludeTag.done") : tr("gui.enchantmenttooltipcleaner.overlay.excludeTag", displayTag),
                    isTagExcluded ? 0xFF55FF55 : 0xFFFFAA55,
                    tr("gui.enchantmenttooltipcleaner.overlay.excludeTag.tooltip", selectedTagIndex + 1, allTags.size()));
            }

            int hintY = (selectedTag != null) ? (btnY + 45) : (btnY + 32);
            if (hintY < panelY + PANEL_HEIGHT - 20) {
                graphics.drawString(font, tr("gui.enchantmenttooltipcleaner.overlay.hoverExcludeHint"), panelX + 8, hintY, 0x777777, false);
            }
        } else {
            graphics.drawCenteredString(font, tr("gui.enchantmenttooltipcleaner.overlay.noItem"), panelX + PANEL_WIDTH / 2, infoY + 9, 0x888888);
            graphics.drawCenteredString(font, tr("gui.enchantmenttooltipcleaner.overlay.noItemHint"), panelX + PANEL_WIDTH / 2, infoY + 20, 0x666666);
        }
    }

    private static void renderActionButton(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, int w, int h, String text, int textColor, String tooltip) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        graphics.fill(x, y, x + w, y + h, hovered ? 0xFF35353C : 0xFF222226);
        graphics.renderOutline(x, y, w, h, hovered ? 0xFF888892 : 0xFF38383F);
        graphics.drawCenteredString(font, text, x + w / 2, y + 2, hovered ? 0xFFFFFFFF : textColor);
        if (hovered && tooltip != null) {
            hoveredTooltip = tooltip;
        }
    }

    private static void checkToggleWithTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, String label, boolean active, String tooltip) {
        boolean hovered = mouseX >= x && mouseX <= x + PANEL_WIDTH - 16 && mouseY >= y && mouseY <= y + 12;
        int boxColor = active ? 0xFF55FF55 : (hovered ? 0xFF888888 : 0xFF555555);
        int textColor = hovered ? 0xFFFFFFFF : (active ? 0xFFE0E0E0 : 0xFF999999);

        // 8x8 细腻复选框
        graphics.fill(x, y + 2, x + 8, y + 10, active ? 0xFF143814 : 0xFF18181A);
        graphics.renderOutline(x, y + 2, 8, 8, boxColor);
        if (active) {
            graphics.fill(x + 2, y + 4, x + 6, y + 8, 0xFF55FF55);
        }

        graphics.drawString(font, label, x + 12, y + 2, textColor, false);

        if (hovered && tooltip != null) {
            hoveredTooltip = tooltip;
        }
    }

    // --- 快捷键排除支持 ---
    public static boolean handleKeyPressed(int keyCode, int scanCode, AbstractContainerScreen<?> screen) {
        if (!isVisible) return false;

        // 如果用户正在界面中的任何文本框/搜索框打字（如背包搜索、JEI搜索），不拦截 X 与 Delete 键！
        if (screen.getFocused() != null && screen.getFocused().isFocused()) {
            return false;
        }

        if (keyCode == GLFW.GLFW_KEY_X || keyCode == GLFW.GLFW_KEY_DELETE) {
            Slot slot = screen.getSlotUnderMouse();
            ItemStack target = (slot != null && !slot.getItem().isEmpty()) ? slot.getItem() : lockedStack;
            if (!target.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(target.getItem());
                toggleItemExclusion(id.toString(), id.getPath());
                return true;
            }
        }
        return false;
    }

    // --- 鼠标点击处理 ---
    public static boolean mouseClicked(double mouseX, double mouseY, int button, AbstractContainerScreen<?> screen) {
        if (!isVisible) return false;
        ensurePosition(screen.width, screen.height, screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize());

        if (isMouseOverPanel(mouseX, mouseY)) {
            // 中键及其他鼠标按键在面板上点击时全部吞掉，100%杜绝穿透
            if (button != 0 && button != 1) return true;

            int contentStartY = panelY + TITLE_BAR_HEIGHT + 2 + TAB_BAR_HEIGHT + 5;

            // 右键：仅在“排除”页的标签按钮上生效，轮转切换下一个标签
            if (button == 1) {
                if (currentTab == 2 && !lockedStack.isEmpty()) {
                    int tagBtnY = contentStartY + 14 + 39 + 30;
                    int tagBtnW = PANEL_WIDTH - 14;
                    int tagBtnH = 13;
                    List<TagKey<Item>> allTags = getLockedTags();
                    if (!allTags.isEmpty() && isInside(mouseX, mouseY, panelX + 7, tagBtnY, tagBtnW, tagBtnH)) {
                        selectedTagIndex = (selectedTagIndex + 1) % allTags.size();
                        setStatusMessage(tr("gui.enchantmenttooltipcleaner.overlay.status.tagSwitched",
                            "#" + allTags.get(selectedTagIndex).location()));
                        return click();
                    }
                }
                // 右键点击面板其他区域时吞掉事件，避免穿透到容器界面
                return true;
            }

            // 点击关闭按钮 [X]
            int closeX = panelX + PANEL_WIDTH - 14;
            int closeY = panelY + 4;
            if (mouseX >= closeX - 2 && mouseX <= closeX + 10 && mouseY >= closeY - 1 && mouseY <= closeY + 11) {
                isVisible = false;
                ClientEvents.checkAndSyncEmiExclusion();
                return click();
            }

            // 点击标题栏开始拖拽
            if (mouseY >= panelY && mouseY <= panelY + TITLE_BAR_HEIGHT) {
                isDragging = true;
                dragOffsetX = (int) mouseX - panelX;
                dragOffsetY = (int) mouseY - panelY;
                return true;
            }

            // 点击 Tab 切换
            int tabY = panelY + TITLE_BAR_HEIGHT + 2;
            int tabH = 15;
            if (mouseY >= tabY && mouseY <= tabY + tabH) {
                if (mouseX >= panelX + 5 && mouseX <= panelX + 49) {
                    currentTab = 0;
                    return click();
                } else if (mouseX >= panelX + 51 && mouseX <= panelX + 95) {
                    currentTab = 1;
                    return click();
                } else if (mouseX >= panelX + 97 && mouseX <= panelX + 141) {
                    currentTab = 2;
                    return click();
                }
            }

            // 点击底部“打开完整设置...”
            int fullCfgY = panelY + PANEL_HEIGHT - 17;
            int fullCfgW = PANEL_WIDTH - 12;
            int fullCfgH = 13;
            if (mouseX >= panelX + 6 && mouseX <= panelX + 6 + fullCfgW && mouseY >= fullCfgY && mouseY <= fullCfgY + fullCfgH) {
                Minecraft.getInstance().setScreen(new ModConfigScreen(screen));
                return click();
            }

            // 处理 Tab 0 点击
            if (currentTab == 0) {
                int itemH = 14;
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 0 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.GLOBAL_ENABLE.set(!ModConfig.GLOBAL_ENABLE.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.toggle.global"), toggleState(ModConfig.GLOBAL_ENABLE.get())));
                    return click();
                }
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 1 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.REMOVE_ENCHANTMENTS.set(!ModConfig.REMOVE_ENCHANTMENTS.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.toggle.enchantments"), toggleState(ModConfig.REMOVE_ENCHANTMENTS.get())));
                    return click();
                }
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 2 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.REMOVE_DESCRIPTIONS.set(!ModConfig.REMOVE_DESCRIPTIONS.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.toggle.descriptions"), toggleState(ModConfig.REMOVE_DESCRIPTIONS.get())));
                    return click();
                }
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 3 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.REMOVE_ATTRIBUTES.set(!ModConfig.REMOVE_ATTRIBUTES.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.toggle.attributes"), toggleState(ModConfig.REMOVE_ATTRIBUTES.get())));
                    return click();
                }
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 4 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.REMOVE_POTION_EFFECTS.set(!ModConfig.REMOVE_POTION_EFFECTS.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.toggle.potionEffects"), toggleState(ModConfig.REMOVE_POTION_EFFECTS.get())));
                    return click();
                }
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 5 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.DEBUG_MODE.set(!ModConfig.DEBUG_MODE.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.toggle.debug"), toggleState(ModConfig.DEBUG_MODE.get())));
                    return click();
                }
            }

            // 处理 Tab 1 点击
            if (currentTab == 1) {
                int itemH = 15;
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 0 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.ENABLE_TEXT_CONTAINS.set(!ModConfig.ENABLE_TEXT_CONTAINS.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.filter.textContains", ModConfig.REMOVE_LINES_CONTAINING.get().size()), toggleState(ModConfig.ENABLE_TEXT_CONTAINS.get())));
                    return click();
                }
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 1 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.ENABLE_TEXT_EXACT.set(!ModConfig.ENABLE_TEXT_EXACT.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.filter.textExact", ModConfig.REMOVE_LINES_EXACT.get().size()), toggleState(ModConfig.ENABLE_TEXT_EXACT.get())));
                    return click();
                }
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 2 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.ENABLE_TEXT_REGEX.set(!ModConfig.ENABLE_TEXT_REGEX.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.filter.textRegex", ModConfig.REMOVE_LINES_REGEX.get().size()), toggleState(ModConfig.ENABLE_TEXT_REGEX.get())));
                    return click();
                }
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 3 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.ENABLE_KEY_CONTAINS.set(!ModConfig.ENABLE_KEY_CONTAINS.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.filter.keyContains", ModConfig.REMOVE_KEYS_CONTAINING.get().size()), toggleState(ModConfig.ENABLE_KEY_CONTAINS.get())));
                    return click();
                }
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY + 4 * itemH, PANEL_WIDTH - 16, 12)) {
                    ModConfig.ENABLE_KEY_EXACT.set(!ModConfig.ENABLE_KEY_EXACT.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.filter.keyExact", ModConfig.REMOVE_KEYS_EXACT.get().size()), toggleState(ModConfig.ENABLE_KEY_EXACT.get())));
                    return click();
                }
            }

            // 处理 Tab 2 点击
            if (currentTab == 2) {
                if (isInside(mouseX, mouseY, panelX + 8, contentStartY, PANEL_WIDTH - 16, 12)) {
                    ModConfig.ENABLE_EXCLUSIONS.set(!ModConfig.ENABLE_EXCLUSIONS.get());
                    saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.toggle", tr("gui.enchantmenttooltipcleaner.overlay.toggle.exclusions"), toggleState(ModConfig.ENABLE_EXCLUSIONS.get())));
                    return click();
                }

                if (!lockedStack.isEmpty()) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(lockedStack.getItem());
                    String itemId = id.toString();
                    String modId = id.getNamespace();

                    int infoY = contentStartY + 14;
                    int btnY = infoY + 39;
                    int btnW = PANEL_WIDTH - 14;
                    int btnH = 13;

                    // 1. 排除物品
                    if (isInside(mouseX, mouseY, panelX + 7, btnY, btnW, btnH)) {
                        toggleItemExclusion(itemId, id.getPath());
                        return click();
                    }
                    // 2. 排除 Mod
                    if (isInside(mouseX, mouseY, panelX + 7, btnY + 15, btnW, btnH)) {
                        toggleModExclusion(modId);
                        return click();
                    }
                    // 3. 排除当前选中的 Tag
                    TagKey<Item> selectedTag = getSelectedTag();
                    if (selectedTag != null && isInside(mouseX, mouseY, panelX + 7, btnY + 30, btnW, btnH)) {
                        String tagStr = "#" + selectedTag.location();
                        toggleItemExclusion(tagStr, tagStr);
                        return click();
                    }
                }
            }

            return true;
        }

        return false;
    }

    private static String toggleState(boolean enabled) {
        return tr(enabled
            ? "gui.enchantmenttooltipcleaner.overlay.status.on"
            : "gui.enchantmenttooltipcleaner.overlay.status.off");
    }

    private static void toggleItemExclusion(String id, String displayName) {
        List<String> items = new ArrayList<>(ModConfig.EXCLUDED_ITEMS.get());
        if (items.contains(id)) {
            items.remove(id);
            ModConfig.EXCLUDED_ITEMS.set(items);
            saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.unexcluded", displayName));
        } else {
            items.add(id);
            ModConfig.EXCLUDED_ITEMS.set(items);
            saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.excluded", displayName));
        }
    }

    private static void toggleModExclusion(String modId) {
        List<String> mods = new ArrayList<>(ModConfig.EXCLUDED_MODS.get());
        if (mods.contains(modId)) {
            mods.remove(modId);
            ModConfig.EXCLUDED_MODS.set(mods);
            saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.modUnexcluded", modId));
        } else {
            mods.add(modId);
            ModConfig.EXCLUDED_MODS.set(mods);
            saveAndRefresh(tr("gui.enchantmenttooltipcleaner.overlay.status.modExcluded", modId));
        }
    }

    private static void saveAndRefresh(String message) {
        ModConfig.saveConfig();
        ClientEvents.refreshConfig();
        setStatusMessage(message);
    }

    private static void setStatusMessage(String message) {
        statusMessage = message;
        statusMessageExpiry = System.currentTimeMillis() + 2000L;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDragging) {
            isDragging = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                panelX = Math.max(0, Math.min(panelX, mc.screen.width - PANEL_WIDTH));
                panelY = Math.max(0, Math.min(panelY, mc.screen.height - PANEL_HEIGHT));
            }
            ModConfig.OVERLAY_POS_X.set(panelX);
            ModConfig.OVERLAY_POS_Y.set(panelY);
            ModConfig.saveConfig();
            ClientEvents.checkAndSyncEmiExclusion();
            return true;
        }
        // 鼠标在悬浮面板内部松开时，吞掉释放事件，杜绝穿透到背景容器槽位
        return isMouseOverPanel(mouseX, mouseY);
    }

    public static boolean mouseScrolled(double mouseX, double mouseY) {
        // 鼠标在悬浮面板内部滚轮滚动时，吞掉滚动事件，杜绝穿透导致背景容器或 JEI/EMI 翻页
        return isMouseOverPanel(mouseX, mouseY);
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && isDragging) {
            panelX = (int) mouseX - dragOffsetX;
            panelY = (int) mouseY - dragOffsetY;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                panelX = Math.max(0, Math.min(panelX, mc.screen.width - PANEL_WIDTH));
                panelY = Math.max(0, Math.min(panelY, mc.screen.height - PANEL_HEIGHT));
            }
            return true;
        }
        return false;
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
