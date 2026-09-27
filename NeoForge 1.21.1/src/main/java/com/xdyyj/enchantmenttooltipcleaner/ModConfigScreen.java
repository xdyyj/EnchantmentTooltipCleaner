package com.xdyyj.enchantmenttooltipcleaner;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.File;

public class ModConfigScreen extends Screen {

    private final Screen parent;
    private String screenHoveredTooltip = null;

    public ModConfigScreen(Screen parent) {
        super(Component.translatable("gui.enchantmenttooltipcleaner.screen.title"));
        this.parent = parent;
    }

    // 统一翻译键取值，保证繁体/英文环境下呈现地道语言
    private static Component tc(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int centerX = this.width / 2;
        int btnW = 155;
        int btnH = 19;
        int leftColX = centerX - btnW - 6;
        int rightColX = centerX + 6;

        // --- 分组 1: 核心净化开关 (标题 Y=26, 按钮起始 Y=38) ---
        int y1 = 38;
        // 1. 全局清理
        this.addRenderableWidget(Button.builder(
            getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.global"), ModConfig.GLOBAL_ENABLE.get()),
            btn -> {
                ModConfig.GLOBAL_ENABLE.set(!ModConfig.GLOBAL_ENABLE.get());
                btn.setMessage(getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.global"), ModConfig.GLOBAL_ENABLE.get()));
                ModConfig.saveConfig();
                ClientEvents.refreshConfig();
            }
        ).bounds(leftColX, y1, btnW, btnH).build());

        // 2. 隐藏附魔
        this.addRenderableWidget(Button.builder(
            getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.enchantments"), ModConfig.REMOVE_ENCHANTMENTS.get()),
            btn -> {
                ModConfig.REMOVE_ENCHANTMENTS.set(!ModConfig.REMOVE_ENCHANTMENTS.get());
                btn.setMessage(getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.enchantments"), ModConfig.REMOVE_ENCHANTMENTS.get()));
                ModConfig.saveConfig();
                ClientEvents.refreshConfig();
            }
        ).bounds(rightColX, y1, btnW, btnH).build());

        int y2 = y1 + 22; // 60
        // 3. 隐藏描述
        this.addRenderableWidget(Button.builder(
            getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.descriptions"), ModConfig.REMOVE_DESCRIPTIONS.get()),
            btn -> {
                ModConfig.REMOVE_DESCRIPTIONS.set(!ModConfig.REMOVE_DESCRIPTIONS.get());
                btn.setMessage(getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.descriptions"), ModConfig.REMOVE_DESCRIPTIONS.get()));
                ModConfig.saveConfig();
                ClientEvents.refreshConfig();
            }
        ).bounds(leftColX, y2, btnW, btnH).build());

        // 4. 隐藏属性
        this.addRenderableWidget(Button.builder(
            getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.attributes"), ModConfig.REMOVE_ATTRIBUTES.get()),
            btn -> {
                ModConfig.REMOVE_ATTRIBUTES.set(!ModConfig.REMOVE_ATTRIBUTES.get());
                btn.setMessage(getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.attributes"), ModConfig.REMOVE_ATTRIBUTES.get()));
                ModConfig.saveConfig();
                ClientEvents.refreshConfig();
            }
        ).bounds(rightColX, y2, btnW, btnH).build());

        int y3 = y2 + 22; // 82
        // 5. 隐藏药水
        this.addRenderableWidget(Button.builder(
            getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.potionEffects"), ModConfig.REMOVE_POTION_EFFECTS.get()),
            btn -> {
                ModConfig.REMOVE_POTION_EFFECTS.set(!ModConfig.REMOVE_POTION_EFFECTS.get());
                btn.setMessage(getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.potionEffects"), ModConfig.REMOVE_POTION_EFFECTS.get()));
                ModConfig.saveConfig();
                ClientEvents.refreshConfig();
            }
        ).bounds(leftColX, y3, btnW, btnH).build());

        // 6. 排除列表功能
        this.addRenderableWidget(Button.builder(
            getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.exclusions"), ModConfig.ENABLE_EXCLUSIONS.get()),
            btn -> {
                ModConfig.ENABLE_EXCLUSIONS.set(!ModConfig.ENABLE_EXCLUSIONS.get());
                btn.setMessage(getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.exclusions"), ModConfig.ENABLE_EXCLUSIONS.get()));
                ModConfig.saveConfig();
                ClientEvents.refreshConfig();
            }
        ).bounds(rightColX, y3, btnW, btnH).build());

        // --- 分组 2: 自定义规则管理 (标题 Y=108, 按钮 Y=120) ---
        int yRules = 120;
        int mainBtnW = btnW * 2 + 12;
        this.addRenderableWidget(Button.builder(
            Component.literal("§6§l").append(tc("gui.enchantmenttooltipcleaner.screen.openRules")),
            btn -> Minecraft.getInstance().setScreen(new RuleListEditScreen(this))
        ).bounds(centerX - mainBtnW / 2, yRules, mainBtnW, 20).build());

        // --- 分组 3: 辅助与调试工具 (标题 Y=148, 按钮 Y=160) ---
        int yTools = 160;
        // 8. 调试模式
        this.addRenderableWidget(Button.builder(
            getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.debug"), ModConfig.DEBUG_MODE.get()),
            btn -> {
                ModConfig.DEBUG_MODE.set(!ModConfig.DEBUG_MODE.get());
                btn.setMessage(getToggleComponent(tc("gui.enchantmenttooltipcleaner.screen.toggle.debug"), ModConfig.DEBUG_MODE.get()));
                ModConfig.saveConfig();
                ClientEvents.refreshConfig();
            }
        ).bounds(leftColX, yTools, btnW, btnH).build());

        // 9. 重置悬浮窗位置
        this.addRenderableWidget(Button.builder(
            Component.literal("§7").append(tc("gui.enchantmenttooltipcleaner.screen.resetOverlay")),
            btn -> {
                ModConfig.OVERLAY_POS_X.set(-1);
                ModConfig.OVERLAY_POS_Y.set(-1);
                ModConfig.saveConfig();
                DraggableOverlayPanel.resetPosition();
                btn.setMessage(Component.literal("§a").append(tc("gui.enchantmenttooltipcleaner.overlay.reset")));
            }
        ).bounds(rightColX, yTools, btnW, btnH).build());

        // 10. 打开配置文件目录 (Y=182)
        this.addRenderableWidget(Button.builder(
            Component.literal("§8").append(tc("gui.enchantmenttooltipcleaner.screen.openConfigDir")),
            btn -> {
                try {
                    File configDir = new File(Minecraft.getInstance().gameDirectory, "config");
                    if (configDir.exists()) {
                        Util.getPlatform().openFile(configDir);
                    }
                } catch (Exception ignored) {}
            }
        ).bounds(centerX - mainBtnW / 2, yTools + 22, mainBtnW, 18).build());

        // 底部“完成”按钮
        this.addRenderableWidget(Button.builder(
            CommonComponents.GUI_DONE,
            btn -> this.onClose()
        ).bounds(centerX - 80, this.height - 24, 160, 18).build());
    }

    private static Component getToggleComponent(Component title, boolean state) {
        return tc(state
            ? "gui.enchantmenttooltipcleaner.screen.toggle.on"
            : "gui.enchantmenttooltipcleaner.screen.toggle.off", title);
    }

    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        screenHoveredTooltip = null;

        // 界面主标题
        graphics.drawCenteredString(this.font, "§f" + this.title.getString(), centerX, 10, 0xFFFFFF);

        // 分组标签标题
        graphics.drawCenteredString(this.font, "§6§l" + tr("gui.enchantmenttooltipcleaner.screen.section.core"), centerX, 26, 0xFFFFAA00);
        graphics.drawCenteredString(this.font, "§6§l" + tr("gui.enchantmenttooltipcleaner.screen.section.rules"), centerX, 108, 0xFFFFAA00);
        graphics.drawCenteredString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.screen.section.tools"), centerX, 148, 0x888888);

        // 底部快捷提示
        graphics.drawCenteredString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.screen.hint"), centerX, this.height - 38, 0x888888);

        // 遍历渲染注册控件
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }

        // 检测各按钮的悬浮提示说明
        int btnW = 155;
        int btnH = 19;
        int leftColX = centerX - btnW - 6;
        int rightColX = centerX + 6;
        int y1 = 38;
        int y2 = y1 + 22;
        int y3 = y2 + 22;
        int yRules = 120;
        int mainBtnW = btnW * 2 + 12;
        int yTools = 160;

        if (isInside(mouseX, mouseY, leftColX, y1, btnW, btnH)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.global");
        } else if (isInside(mouseX, mouseY, rightColX, y1, btnW, btnH)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.enchantments");
        } else if (isInside(mouseX, mouseY, leftColX, y2, btnW, btnH)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.descriptions");
        } else if (isInside(mouseX, mouseY, rightColX, y2, btnW, btnH)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.attributes");
        } else if (isInside(mouseX, mouseY, leftColX, y3, btnW, btnH)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.potionEffects");
        } else if (isInside(mouseX, mouseY, rightColX, y3, btnW, btnH)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.exclusions");
        } else if (isInside(mouseX, mouseY, centerX - mainBtnW / 2, yRules, mainBtnW, 20)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.rules");
        } else if (isInside(mouseX, mouseY, leftColX, yTools, btnW, btnH)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.debug");
        } else if (isInside(mouseX, mouseY, rightColX, yTools, btnW, btnH)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.resetOverlay");
        } else if (isInside(mouseX, mouseY, centerX - mainBtnW / 2, yTools + 22, mainBtnW, 18)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.openConfigDir");
        } else if (isInside(mouseX, mouseY, centerX - 80, this.height - 24, 160, 18)) {
            screenHoveredTooltip = tr("gui.enchantmenttooltipcleaner.screen.tooltip.done");
        }

        // 顶层悬浮说明渲染 (Z=900)
        if (screenHoveredTooltip != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 900.0F);
            graphics.renderTooltip(this.font, Component.literal("§7" + screenHoveredTooltip), mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    @Override
    public void onClose() {
        ModConfig.saveConfig();
        ClientEvents.refreshConfig();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
