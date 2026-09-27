package com.xdyyj.enchantmenttooltipcleaner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RuleListEditScreen extends Screen {

    private final Screen parent;

    // 当前选中的类别索引:
    // 0: 文本-包含, 1: 文本-精准, 2: 文本-正则, 3: 键-包含, 4: 键-精准, 5: 排除物品/Tag, 6: 排除Mod
    private int currentCategory = 0;
    private static final String[] CATEGORY_KEYS = {
        "gui.enchantmenttooltipcleaner.rules.cat.text_contains",
        "gui.enchantmenttooltipcleaner.rules.cat.text_exact",
        "gui.enchantmenttooltipcleaner.rules.cat.text_regex",
        "gui.enchantmenttooltipcleaner.rules.cat.key_contains",
        "gui.enchantmenttooltipcleaner.rules.cat.key_exact",
        "gui.enchantmenttooltipcleaner.rules.cat.excluded_items",
        "gui.enchantmenttooltipcleaner.rules.cat.excluded_mods"
    };
    private static final String[] CATEGORY_TOOLTIP_KEYS = {
        "gui.enchantmenttooltipcleaner.rules.cat.text_contains.tooltip",
        "gui.enchantmenttooltipcleaner.rules.cat.text_exact.tooltip",
        "gui.enchantmenttooltipcleaner.rules.cat.text_regex.tooltip",
        "gui.enchantmenttooltipcleaner.rules.cat.key_contains.tooltip",
        "gui.enchantmenttooltipcleaner.rules.cat.key_exact.tooltip",
        "gui.enchantmenttooltipcleaner.rules.cat.excluded_items.tooltip",
        "gui.enchantmenttooltipcleaner.rules.cat.excluded_mods.tooltip"
    };

    // 搜索过滤与输入组件
    private EditBox searchBox;
    private String searchQuery = "";
    private EditBox inputField;
    private Button actionButton;
    private int scrollOffset = 0;
    private int editingIndex = -1; // -1 表示添加模式，>=0 表示当前正在修改的原列表索引
    private static final int ITEM_HEIGHT = 19;
    private static final int LIST_HEADER_HEIGHT = 16;

    // 首次启动时释出的示范预设资源 (随模组 jar 一同打包)
    private static final String DEFAULT_PRESET_RESOURCE = "/assets/enchantmenttooltipcleaner/presets/纯净体验预设.json";
    private static final String DEFAULT_PRESET_FILE_NAME = "纯净体验预设.json";

    // 单行规则删除两步确认
    private int pendingDeleteIndex = -1;

    // 悬浮提示文本
    private String screenHoveredTooltip = null;

    // 状态与异常反馈
    private String statusMessage = null;
    private long statusExpiry = 0L;
    private int statusColor = 0xFF55FF55;
    private String regexFeedbackMessage = null;
    private int regexFeedbackColor = 0xFF55FF55;

    // 预设选择弹窗状态与磁盘预设列表
    private boolean isPresetDialogOpen = false;
    private int presetScrollOffset = 0;
    private List<PresetInfo> diskPresets = new ArrayList<>();

    // 二级确认对话框状态
    private enum ConfirmType {
        NONE,
        APPLY_PRESET,
        DELETE_PRESET,
        CLEAR_CATEGORY
    }
    private ConfirmType pendingConfirmType = ConfirmType.NONE;
    private PresetInfo pendingConfirmPreset = null;

    // 预设命名模式状态 (新建预设 或 重命名预设)
    private boolean isNamingPreset = false;
    private boolean isNewPreset = false;
    private PresetInfo namingTargetPreset = null;
    private EditBox presetNameBox = null;

    // 滚动条鼠标拖拽状态
    private boolean isDraggingScrollbar = false;

    public static class FilteredEntry {
        public final int originalIndex;
        public final String text;
        public FilteredEntry(int originalIndex, String text) {
            this.originalIndex = originalIndex;
            this.text = text;
        }
    }

    public static class PresetInfo {
        public final String displayName;
        public final File file;
        public final int totalRules;

        public PresetInfo(String displayName, File file, int totalRules) {
            this.displayName = displayName;
            this.file = file;
            this.totalRules = totalRules;
        }
    }

    public RuleListEditScreen(Screen parent) {
        super(tc("gui.enchantmenttooltipcleaner.rules.title"));
        this.parent = parent;
    }

    // 统一翻译键取值，保证英文/繁体环境下呈现地道语言
    private static Component tc(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // 原版按钮点击音效
    private void playClickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int centerX = this.width / 2;

        // 1. Tab 类别切换栏
        int tabW = Math.min(54, (this.width - 24) / CATEGORY_KEYS.length - 2);
        int tabH = 16;
        int totalTabs = CATEGORY_KEYS.length;
        int startX = centerX - (totalTabs * (tabW + 2)) / 2;

        for (int i = 0; i < totalTabs; i++) {
            final int catIndex = i;
            boolean active = (catIndex == currentCategory);
            this.addRenderableWidget(Button.builder(
                Component.literal(active ? "§a§l" : "§7").append(tc(CATEGORY_KEYS[i])),
                btn -> {
                    this.currentCategory = catIndex;
                    this.scrollOffset = 0;
                    this.editingIndex = -1;
                    this.pendingDeleteIndex = -1;
                    this.regexFeedbackMessage = null;
                    this.init();
                }
            ).bounds(startX + i * (tabW + 2), 22, tabW, tabH).build());
        }

        // 2. 顶部工具栏 (搜索过滤 + 预设 + 导入导出)
        int barY = 42;
        int listLeft = centerX - 195;
        int listRight = centerX + 205;

        // 搜索输入框
        this.searchBox = new EditBox(this.font, listLeft, barY, 150, 16, tc("gui.enchantmenttooltipcleaner.rules.search_box"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(tc("gui.enchantmenttooltipcleaner.rules.search_hint"));
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(val -> {
            this.searchQuery = val;
            this.scrollOffset = 0;
            this.pendingDeleteIndex = -1;
        });
        this.addRenderableWidget(this.searchBox);

        // 清空搜索 [X]
        this.addRenderableWidget(Button.builder(
            Component.literal("§7X"),
            btn -> {
                this.searchQuery = "";
                this.searchBox.setValue("");
                this.scrollOffset = 0;
                this.pendingDeleteIndex = -1;
                this.init();
            }
        ).bounds(listLeft + 152, barY, 16, 16).build());

        // 工具按钮（居右对齐，无 emoji）
        int btnW = 68;
        int toolStartX = listRight - (btnW * 3 + 6);

        // [规则预设]
        this.addRenderableWidget(Button.builder(
            Component.literal("§6").append(tc("gui.enchantmenttooltipcleaner.rules.btn.presets")),
            btn -> {
                this.refreshDiskPresets();
                this.presetScrollOffset = 0;
                this.pendingConfirmType = ConfirmType.NONE;
                this.isNamingPreset = false;
                this.isPresetDialogOpen = true;
            }
        ).bounds(toolStartX, barY, btnW, 16).build());

        // [导出规则]
        this.addRenderableWidget(Button.builder(
            Component.literal("§b").append(tc("gui.enchantmenttooltipcleaner.rules.btn.export")),
            btn -> exportRulesToClipboard()
        ).bounds(toolStartX + btnW + 3, barY, btnW, 16).build());

        // [导入规则]
        this.addRenderableWidget(Button.builder(
            Component.literal("§a").append(tc("gui.enchantmenttooltipcleaner.rules.btn.import")),
            btn -> importRulesFromClipboard()
        ).bounds(toolStartX + (btnW + 3) * 2, barY, btnW, 16).build());

        // 3. 底部规则输入与主操作栏
        int bottomY = this.height - 46;
        int inputW = 230;
        int inputH = 18;
        boolean isEditing = (editingIndex >= 0);
        int inputX = centerX - (inputW + 54 + (isEditing ? 44 : 0)) / 2;

        String prevText = (this.inputField != null) ? this.inputField.getValue() : "";
        this.inputField = new EditBox(this.font, inputX, bottomY, inputW, inputH, tc("gui.enchantmenttooltipcleaner.rules.input_box"));
        this.inputField.setMaxLength(256);

        String hintKey;
        if (currentCategory == 2) {
            hintKey = "gui.enchantmenttooltipcleaner.rules.input_hint.regex";
        } else if (currentCategory == 5) {
            hintKey = "gui.enchantmenttooltipcleaner.rules.input_hint.items";
        } else if (currentCategory == 6) {
            hintKey = "gui.enchantmenttooltipcleaner.rules.input_hint.mods";
        } else {
            hintKey = "gui.enchantmenttooltipcleaner.rules.input_hint.default";
        }
        this.inputField.setHint(tc(hintKey));
        this.inputField.setValue(prevText);
        this.inputField.setResponder(val -> updateRegexFeedback(val));
        this.addRenderableWidget(this.inputField);

        // [添加 / 保存] 按钮
        this.actionButton = Button.builder(
            Component.literal(isEditing ? "§e" : "§a").append(tc(isEditing
                ? "gui.enchantmenttooltipcleaner.rules.btn.save"
                : "gui.enchantmenttooltipcleaner.rules.btn.add")),
            btn -> {
                String text = inputField.getValue().trim();
                if (!text.isEmpty()) {
                    if (currentCategory == 2) {
                        try {
                            Pattern.compile(text);
                            regexFeedbackMessage = null;
                        } catch (PatternSyntaxException e) {
                            regexFeedbackMessage = tr("gui.enchantmenttooltipcleaner.rules.status.regex_cannot_add", e.getDescription());
                            regexFeedbackColor = 0xFFFF5555;
                            showStatus(regexFeedbackMessage, 0xFFFF5555);
                            return;
                        }
                    }
                    if (editingIndex >= 0) {
                        modifyRule(editingIndex, text);
                        editingIndex = -1;
                        showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.saved"), 0xFF55FF55);
                    } else {
                        addRule(text);
                        showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.added"), 0xFF55FF55);
                    }
                    inputField.setValue("");
                    pendingDeleteIndex = -1;
                    this.init();
                }
            }
        ).bounds(inputX + inputW + 4, bottomY, 52, inputH).build();
        this.addRenderableWidget(this.actionButton);

        // 如果在编辑状态，提供 [取消] 按钮
        if (isEditing) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§7").append(tc("gui.enchantmenttooltipcleaner.rules.btn.cancel")),
                btn -> {
                    this.editingIndex = -1;
                    this.inputField.setValue("");
                    this.init();
                }
            ).bounds(inputX + inputW + 60, bottomY, 44, inputH).build());
        }

        // 4. 最底部“完成并返回”按钮
        this.addRenderableWidget(Button.builder(
            CommonComponents.GUI_BACK,
            btn -> this.onClose()
        ).bounds(centerX - 80, this.height - 23, 160, 18).build());

        // 5. 规则条目 [编辑] 与 [删除/确认] 按钮
        if (!isPresetDialogOpen) {
            List<FilteredEntry> filtered = getFilteredEntries();
            int listTop = 60;
            int listBottom = bottomY - 6;
            int itemStartY = listTop + LIST_HEADER_HEIGHT;
            int maxVisible = (listBottom - itemStartY) / ITEM_HEIGHT;

            int renderCount = Math.min(maxVisible, Math.max(0, filtered.size() - scrollOffset));
            for (int i = 0; i < renderCount; i++) {
                FilteredEntry entry = filtered.get(scrollOffset + i);
                int rowY = itemStartY + i * ITEM_HEIGHT + 1;
                int editX = listRight - 94;
                int delX = listRight - 56;

                boolean isCurrentItemEditing = (editingIndex == entry.originalIndex);
                boolean isPendingDelete = (pendingDeleteIndex == entry.originalIndex);

                // [编辑] 按钮
                this.addRenderableWidget(Button.builder(
                    Component.literal(isCurrentItemEditing ? "§e" : "§7").append(tc(isCurrentItemEditing
                        ? "gui.enchantmenttooltipcleaner.rules.btn.editing"
                        : "gui.enchantmenttooltipcleaner.rules.btn.edit")),
                    btn -> {
                        this.editingIndex = entry.originalIndex;
                        this.pendingDeleteIndex = -1;
                        this.inputField.setValue(entry.text);
                        this.init();
                    }
                ).bounds(editX, rowY + 1, 35, 15).build());

                // [删除 / 确认?] 按钮 (两步确认机制)
                this.addRenderableWidget(Button.builder(
                    Component.literal("§c").append(tc(isPendingDelete
                        ? "gui.enchantmenttooltipcleaner.rules.btn.confirm_delete"
                        : "gui.enchantmenttooltipcleaner.rules.btn.delete")),
                    btn -> {
                        if (isPendingDelete) {
                            if (editingIndex == entry.originalIndex) {
                                editingIndex = -1;
                                inputField.setValue("");
                            }
                            removeRule(entry.originalIndex);
                            pendingDeleteIndex = -1;
                            showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.deleted"), 0xFFFFAA00);
                            this.init();
                        } else {
                            pendingDeleteIndex = entry.originalIndex;
                            this.init();
                        }
                    }
                ).bounds(delX, rowY + 1, 42, 15).build());
            }
        }

        // 6. 若预设命名模式处于激活状态，恢复预设名称输入框
        if (isPresetDialogOpen && isNamingPreset && this.presetNameBox != null) {
            int centerY = this.height / 2;
            int modalY = centerY - 55;
            String val = this.presetNameBox.getValue();
            this.presetNameBox = new EditBox(this.font, centerX - 100, modalY + 44, 200, 18, tc("gui.enchantmenttooltipcleaner.rules.preset.naming_box"));
            this.presetNameBox.setMaxLength(64);
            this.presetNameBox.setValue(val);
            this.presetNameBox.setFocused(true);
            this.presetNameBox.setCanLoseFocus(false);
            this.setFocused(this.presetNameBox);
        }
    }

    private void updateRegexFeedback(String val) {
        if (currentCategory == 2 && !val.trim().isEmpty()) {
            try {
                Pattern.compile(val.trim());
                regexFeedbackMessage = tr("gui.enchantmenttooltipcleaner.rules.status.regex_valid");
                regexFeedbackColor = 0xFF55FF55;
            } catch (PatternSyntaxException e) {
                regexFeedbackMessage = tr("gui.enchantmenttooltipcleaner.rules.status.regex_invalid", e.getDescription());
                regexFeedbackColor = 0xFFFF5555;
            }
        } else {
            regexFeedbackMessage = null;
        }
    }

    private List<FilteredEntry> getFilteredEntries() {
        List<String> raw = getCurrentList();
        List<FilteredEntry> result = new ArrayList<>();
        String query = (searchQuery != null) ? searchQuery.trim().toLowerCase(Locale.ROOT) : "";
        for (int i = 0; i < raw.size(); i++) {
            String item = raw.get(i);
            if (query.isEmpty() || item.toLowerCase(Locale.ROOT).contains(query)) {
                result.add(new FilteredEntry(i, item));
            }
        }
        return result;
    }

    private void showStatus(String message, int color) {
        this.statusMessage = message;
        this.statusExpiry = System.currentTimeMillis() + 3000L;
        this.statusColor = color;
    }

    private void modifyRule(int originalIndex, String newRule) {
        List<String> list = getCurrentList();
        if (originalIndex >= 0 && originalIndex < list.size()) {
            list.set(originalIndex, newRule);
            saveCurrentList(list);
        }
    }

    private List<String> getCurrentList() {
        return switch (currentCategory) {
            case 0 -> new ArrayList<>(ModConfig.REMOVE_LINES_CONTAINING.get());
            case 1 -> new ArrayList<>(ModConfig.REMOVE_LINES_EXACT.get());
            case 2 -> new ArrayList<>(ModConfig.REMOVE_LINES_REGEX.get());
            case 3 -> new ArrayList<>(ModConfig.REMOVE_KEYS_CONTAINING.get());
            case 4 -> new ArrayList<>(ModConfig.REMOVE_KEYS_EXACT.get());
            case 5 -> new ArrayList<>(ModConfig.EXCLUDED_ITEMS.get());
            case 6 -> new ArrayList<>(ModConfig.EXCLUDED_MODS.get());
            default -> new ArrayList<>();
        };
    }

    private void saveCurrentList(List<String> list) {
        switch (currentCategory) {
            case 0 -> ModConfig.REMOVE_LINES_CONTAINING.set(list);
            case 1 -> ModConfig.REMOVE_LINES_EXACT.set(list);
            case 2 -> ModConfig.REMOVE_LINES_REGEX.set(list);
            case 3 -> ModConfig.REMOVE_KEYS_CONTAINING.set(list);
            case 4 -> ModConfig.REMOVE_KEYS_EXACT.set(list);
            case 5 -> ModConfig.EXCLUDED_ITEMS.set(list);
            case 6 -> ModConfig.EXCLUDED_MODS.set(list);
        }
        ModConfig.saveConfig();
        ClientEvents.refreshConfig();
    }

    private void addRule(String rule) {
        List<String> list = getCurrentList();
        if (!list.contains(rule)) {
            list.add(rule);
            saveCurrentList(list);
        }
    }

    private void removeRule(int originalIndex) {
        List<String> list = getCurrentList();
        if (originalIndex >= 0 && originalIndex < list.size()) {
            list.remove(originalIndex);
            saveCurrentList(list);
        }
    }

    // --- 剪贴板导入与导出 ---
    private JsonObject buildFullRulesJsonObject() {
        JsonObject root = new JsonObject();
        root.addProperty("generator", "EnchantmentTooltipCleaner");
        root.addProperty("version", 1);

        root.add("lines_containing", toJsonArray(ModConfig.REMOVE_LINES_CONTAINING.get()));
        root.add("lines_exact", toJsonArray(ModConfig.REMOVE_LINES_EXACT.get()));
        root.add("lines_regex", toJsonArray(ModConfig.REMOVE_LINES_REGEX.get()));
        root.add("keys_containing", toJsonArray(ModConfig.REMOVE_KEYS_CONTAINING.get()));
        root.add("keys_exact", toJsonArray(ModConfig.REMOVE_KEYS_EXACT.get()));
        root.add("excluded_items", toJsonArray(ModConfig.EXCLUDED_ITEMS.get()));
        root.add("excluded_mods", toJsonArray(ModConfig.EXCLUDED_MODS.get()));
        return root;
    }

    private void exportRulesToClipboard() {
        try {
            JsonObject root = buildFullRulesJsonObject();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(root);
            Minecraft.getInstance().keyboardHandler.setClipboard(json);
            showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.exported"), 0xFF55FF55);
        } catch (Exception e) {
            EnchantmentTooltipCleanerMod.LOGGER.debug("Failed to export rules to clipboard", e);
            showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.export_failed", String.valueOf(e.getMessage())), 0xFFFF5555);
        }
    }

    private JsonArray toJsonArray(List<? extends String> list) {
        JsonArray array = new JsonArray();
        for (String s : list) {
            array.add(s);
        }
        return array;
    }

    private void importRulesFromClipboard() {
        try {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip == null || clip.trim().isEmpty()) {
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.clip_empty"), 0xFFFF5555);
                return;
            }

            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(clip.trim());
            } catch (Exception notJson) {
                parsed = null;
            }

            if (parsed == null || !parsed.isJsonObject()) {
                String[] lines = clip.split("\\r?\\n");
                List<String> list = getCurrentList();
                int added = 0;
                for (String line : lines) {
                    String clean = line.trim();
                    if (!clean.isEmpty() && !list.contains(clean)) {
                        list.add(clean);
                        added++;
                    }
                }
                if (added > 0) {
                    saveCurrentList(list);
                    showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.imported_lines", added), 0xFF55FF55);
                    this.init();
                    return;
                }
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.clip_no_rules"), 0xFFFF5555);
                return;
            }

            JsonObject obj = parsed.getAsJsonObject();
            int totalAdded = mergeJsonObjectIntoConfig(obj);

            ModConfig.saveConfig();
            ClientEvents.refreshConfig();
            showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.imported_merged", totalAdded), 0xFF55FF55);
            this.init();
        } catch (Exception e) {
            EnchantmentTooltipCleanerMod.LOGGER.debug("Failed to import rules from clipboard", e);
            showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.import_corrupt"), 0xFFFF5555);
        }
    }

    private int mergeJsonObjectIntoConfig(JsonObject obj) {
        int totalAdded = 0;
        totalAdded += mergeInto(ModConfig.REMOVE_LINES_CONTAINING::get, ModConfig.REMOVE_LINES_CONTAINING::set, obj.getAsJsonArray("lines_containing"));
        totalAdded += mergeInto(ModConfig.REMOVE_LINES_EXACT::get, ModConfig.REMOVE_LINES_EXACT::set, obj.getAsJsonArray("lines_exact"));
        totalAdded += mergeInto(ModConfig.REMOVE_LINES_REGEX::get, ModConfig.REMOVE_LINES_REGEX::set, obj.getAsJsonArray("lines_regex"));
        totalAdded += mergeInto(ModConfig.REMOVE_KEYS_CONTAINING::get, ModConfig.REMOVE_KEYS_CONTAINING::set, obj.getAsJsonArray("keys_containing"));
        totalAdded += mergeInto(ModConfig.REMOVE_KEYS_EXACT::get, ModConfig.REMOVE_KEYS_EXACT::set, obj.getAsJsonArray("keys_exact"));
        totalAdded += mergeInto(ModConfig.EXCLUDED_ITEMS::get, ModConfig.EXCLUDED_ITEMS::set, obj.getAsJsonArray("excluded_items"));
        totalAdded += mergeInto(ModConfig.EXCLUDED_MODS::get, ModConfig.EXCLUDED_MODS::set, obj.getAsJsonArray("excluded_mods"));
        return totalAdded;
    }

    private int mergeInto(Supplier<List<? extends String>> getter, Consumer<List<String>> setter, JsonArray arr) {
        if (arr == null) return 0;
        List<String> list = new ArrayList<>(getter.get());
        int count = 0;
        for (JsonElement el : arr) {
            String val = el.getAsString();
            if (val != null && !val.trim().isEmpty() && !list.contains(val.trim())) {
                list.add(val.trim());
                count++;
            }
        }
        setter.accept(list);
        return count;
    }

    // --- 自定义磁盘规则预设读取与管理 ---
    private File getPresetsDirectory() {
        File dir = new File(Minecraft.getInstance().gameDirectory, "config/enchantmenttooltipcleaner/presets");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private void refreshDiskPresets() {
        diskPresets.clear();
        File dir = getPresetsDirectory();
        ensureDefaultPresetsExist(dir);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                int count = countRulesInJsonFile(file);
                String name = file.getName();
                if (name.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    name = name.substring(0, name.length() - 5);
                }
                diskPresets.add(new PresetInfo(name, file, count));
            }
        }
    }

    // 预设目录为空时，从模组 jar 资源中释出一份示范预设，降低新用户配置门槛
    private void ensureDefaultPresetsExist(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        File[] existing = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (existing != null && existing.length > 0) {
            return;
        }
        try (InputStream in = getClass().getResourceAsStream(DEFAULT_PRESET_RESOURCE)) {
            if (in != null) {
                Files.copy(in, new File(dir, DEFAULT_PRESET_FILE_NAME).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            EnchantmentTooltipCleanerMod.LOGGER.debug("Failed to extract the bundled default preset", e);
        }
    }

    private int countRulesInJsonFile(File file) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (el != null && el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                int total = 0;
                String[] keys = {"lines_containing", "lines_exact", "lines_regex", "keys_containing", "keys_exact", "excluded_items", "excluded_mods"};
                for (String k : keys) {
                    if (obj.has(k) && obj.get(k).isJsonArray()) {
                        total += obj.getAsJsonArray(k).size();
                    }
                }
                return total;
            }
        } catch (Exception e) {
            EnchantmentTooltipCleanerMod.LOGGER.debug("Failed to read preset rule count: {}", file.getName(), e);
        }
        return 0;
    }

    private void applyPresetFromFile(File file) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (el != null && el.isJsonObject()) {
                int added = mergeJsonObjectIntoConfig(el.getAsJsonObject());
                ModConfig.saveConfig();
                ClientEvents.refreshConfig();
                String name = file.getName().replace(".json", "");
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_applied", name, added), 0xFF55FF55);
                this.init();
            } else {
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_corrupt"), 0xFFFF5555);
            }
        } catch (Exception e) {
            EnchantmentTooltipCleanerMod.LOGGER.debug("Failed to apply preset file: {}", file.getName(), e);
            showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_read_failed", String.valueOf(e.getMessage())), 0xFFFF5555);
        }
    }

    private void openPresetNamingDialog(boolean isNew, PresetInfo target) {
        this.isNamingPreset = true;
        this.isNewPreset = isNew;
        this.namingTargetPreset = target;
        this.pendingConfirmType = ConfirmType.NONE;

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int modalY = centerY - 55;

        this.presetNameBox = new EditBox(this.font, centerX - 100, modalY + 44, 200, 18, tc("gui.enchantmenttooltipcleaner.rules.preset.naming_box"));
        this.presetNameBox.setMaxLength(64);

        String initialName;
        if (isNew) {
            int index = diskPresets.size() + 1;
            File dir = getPresetsDirectory();
            while (new File(dir, tr("gui.enchantmenttooltipcleaner.rules.preset.custom_name_prefix") + index + ".json").exists()) {
                index++;
            }
            initialName = tr("gui.enchantmenttooltipcleaner.rules.preset.custom_name_prefix") + index;
        } else {
            initialName = (target != null) ? target.displayName : tr("gui.enchantmenttooltipcleaner.rules.preset.default_name");
        }
        this.presetNameBox.setValue(initialName);
        this.presetNameBox.setFocused(true);
        this.presetNameBox.setCanLoseFocus(false);
        this.setFocused(this.presetNameBox);
    }

    private void closePresetNamingDialog() {
        this.isNamingPreset = false;
        this.isNewPreset = false;
        this.namingTargetPreset = null;
        this.presetNameBox = null;
        this.setFocused(null);
    }

    private void confirmPresetNaming() {
        if (presetNameBox == null) return;
        String cleanName = presetNameBox.getValue().trim();
        if (cleanName.isEmpty()) {
            showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_name_empty"), 0xFFFF5555);
            return;
        }
        if (cleanName.matches(".*[\\\\/:*?\"<>|].*")) {
            showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_name_invalid"), 0xFFFF5555);
            return;
        }

        File dir = getPresetsDirectory();
        File targetFile = new File(dir, cleanName + ".json");

        if (isNewPreset) {
            if (targetFile.exists()) {
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_name_exists"), 0xFFFF5555);
                return;
            }
            try {
                JsonObject root = buildFullRulesJsonObject();
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
                    gson.toJson(root, writer);
                }
                refreshDiskPresets();
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_saved", cleanName), 0xFF55FF55);
                closePresetNamingDialog();
            } catch (Exception e) {
                EnchantmentTooltipCleanerMod.LOGGER.debug("Failed to save preset file: {}", targetFile.getName(), e);
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_save_failed", String.valueOf(e.getMessage())), 0xFFFF5555);
            }
        } else {
            if (namingTargetPreset == null) return;
            if (targetFile.exists() && !targetFile.equals(namingTargetPreset.file)) {
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_name_exists"), 0xFFFF5555);
                return;
            }
            boolean success = namingTargetPreset.file.renameTo(targetFile);
            if (success) {
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_renamed", cleanName), 0xFF55FF55);
                closePresetNamingDialog();
                refreshDiskPresets();
            } else {
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_rename_failed"), 0xFFFF5555);
            }
        }
    }

    private void openPresetsFolderInExplorer() {
        try {
            File dir = getPresetsDirectory();
            Util.getPlatform().openFile(dir);
        } catch (Exception e) {
            EnchantmentTooltipCleanerMod.LOGGER.debug("Failed to open the presets folder in the file explorer", e);
        }
    }

    private void clearCurrentCategoryRules() {
        int count = getCurrentList().size();
        saveCurrentList(Collections.emptyList());
        this.scrollOffset = 0;
        this.editingIndex = -1;
        this.pendingDeleteIndex = -1;
        showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.category_cleared"), 0xFFFF5555);
        this.init();
    }

    private void executeConfirmedAction() {
        if (pendingConfirmType == ConfirmType.APPLY_PRESET && pendingConfirmPreset != null) {
            applyPresetFromFile(pendingConfirmPreset.file);
            pendingConfirmType = ConfirmType.NONE;
            pendingConfirmPreset = null;
            isPresetDialogOpen = false;
            this.init();
        } else if (pendingConfirmType == ConfirmType.DELETE_PRESET && pendingConfirmPreset != null) {
            String name = pendingConfirmPreset.displayName;
            if (pendingConfirmPreset.file.delete()) {
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_deleted", name), 0xFFFFAA00);
            } else {
                showStatus(tr("gui.enchantmenttooltipcleaner.rules.status.preset_delete_failed"), 0xFFFF5555);
            }
            pendingConfirmType = ConfirmType.NONE;
            pendingConfirmPreset = null;
            refreshDiskPresets();
        } else if (pendingConfirmType == ConfirmType.CLEAR_CATEGORY) {
            pendingConfirmType = ConfirmType.NONE;
            isPresetDialogOpen = false;
            clearCurrentCategoryRules();
        }
    }

    // --- 容器与胶囊徽章绘制 ---
    private void renderCard(GuiGraphics graphics, int x, int y, int w, int h, int borderColor) {
        graphics.fill(x + 1, y + 1, x + w + 1, y + h + 1, 0x40000000);
        graphics.fill(x, y, x + w, y + h, 0xF2161619);
        graphics.hLine(x + 1, x + w - 2, y + 1, 0x1AFFFFFF);
        graphics.renderOutline(x, y, w, h, borderColor);
    }

    private void renderBadge(GuiGraphics graphics, int x, int y, String text, int bg, int fg) {
        int badgeW = 34;
        int badgeH = 12;
        graphics.fill(x, y, x + badgeW, y + badgeH, bg);
        graphics.renderOutline(x, y, badgeW, badgeH, fg);
        graphics.drawCenteredString(this.font, text, x + badgeW / 2, y + 2, fg);
    }

    private String getCategoryBadgeText(int cat) {
        return switch (cat) {
            case 0 -> tr("gui.enchantmenttooltipcleaner.rules.badge.contains");
            case 1 -> tr("gui.enchantmenttooltipcleaner.rules.badge.exact");
            case 2 -> tr("gui.enchantmenttooltipcleaner.rules.badge.regex");
            case 3 -> tr("gui.enchantmenttooltipcleaner.rules.badge.key_contains");
            case 4 -> tr("gui.enchantmenttooltipcleaner.rules.badge.key_exact");
            case 5 -> tr("gui.enchantmenttooltipcleaner.rules.badge.item");
            case 6 -> tr("gui.enchantmenttooltipcleaner.rules.badge.mod");
            default -> tr("gui.enchantmenttooltipcleaner.rules.badge.key");
        };
    }

    private int getCategoryBadgeBg(int cat) {
        return switch (cat) {
            case 0 -> 0x331E88E5;
            case 1 -> 0x33F57C00;
            case 2 -> 0x338E24AA;
            case 3 -> 0x3300897B;
            case 4 -> 0x3300ACC1;
            case 5 -> 0x33E53935;
            case 6 -> 0x33D81B60;
            default -> 0x33555555;
        };
    }

    private int getCategoryBadgeFg(int cat) {
        return switch (cat) {
            case 0 -> 0xFF64B5F6;
            case 1 -> 0xFFFFB74D;
            case 2 -> 0xFFCE93D8;
            case 3 -> 0xFF4DB6AC;
            case 4 -> 0xFF4DD0E1;
            case 5 -> 0xFFEF9A9A;
            case 6 -> 0xFFF48FB1;
            default -> 0xFFAAAAAA;
        };
    }

    // --- 预设弹窗与二级确认弹窗渲染 ---
    private void renderPresetDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int dialogW = 320;
        int dialogH = 186;
        int dialogX = centerX - dialogW / 2;
        int dialogY = centerY - dialogH / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);

        // 1. 全屏半透明遮罩
        graphics.fill(0, 0, this.width, this.height, 0x99000000);

        // 2. 主卡片容器
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xFF16161B);
        graphics.hLine(dialogX + 1, dialogX + dialogW - 2, dialogY + 1, 0x25FFFFFF);
        graphics.renderOutline(dialogX, dialogY, dialogW, dialogH, 0xFFFFAA00);

        // 3. 标题与说明
        graphics.drawCenteredString(this.font, "§6§l" + tr("gui.enchantmenttooltipcleaner.rules.preset.title"), centerX, dialogY + 8, 0xFFFFFFFF);
        graphics.hLine(dialogX + 12, dialogX + dialogW - 12, dialogY + 20, 0x33FFFFFF);
        graphics.drawCenteredString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.rules.preset.storage_hint"), centerX, dialogY + 23, 0x888888);

        // 4. 内容展示：若是二级确认中
        if (pendingConfirmType != ConfirmType.NONE) {
            renderConfirmModal(graphics, mouseX, mouseY, centerX, centerY);
        } else if (isNamingPreset) {
            renderNamingModal(graphics, mouseX, mouseY, centerX, centerY);
        } else {
            // 预设列表展示
            int listY = dialogY + 36;
            int itemH = 20;
            int maxShow = 4;

            if (diskPresets.isEmpty()) {
                graphics.drawCenteredString(this.font, "§7" + tr("gui.enchantmenttooltipcleaner.rules.preset.empty"), centerX, listY + 22, 0xAAAAAA);
                graphics.drawCenteredString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.rules.preset.empty_hint"), centerX, listY + 36, 0x666666);
            } else {
                int renderCount = Math.min(maxShow, diskPresets.size() - presetScrollOffset);
                for (int i = 0; i < renderCount; i++) {
                    PresetInfo preset = diskPresets.get(presetScrollOffset + i);
                    int optY = listY + i * itemH;
                    int optX = dialogX + 12;
                    int optW = dialogW - 24;

                    boolean rowHovered = mouseX >= optX && mouseX <= optX + optW && mouseY >= optY && mouseY <= optY + itemH - 2;
                    graphics.fill(optX, optY, optX + optW, optY + itemH - 2, rowHovered ? 0xFF282830 : 0xFF1D1D22);
                    graphics.renderOutline(optX, optY, optW, itemH - 2, rowHovered ? 0xFFFFAA00 : 0xFF35353D);

                    // 预设名称 (截短保护)
                    String nameDisp = (rowHovered ? "§e> " : "§7- ") + preset.displayName;
                    if (this.font.width(nameDisp) > 135) {
                        nameDisp = this.font.plainSubstrByWidth(nameDisp, 125) + "...";
                    }
                    graphics.drawString(this.font, nameDisp, optX + 6, optY + 5, rowHovered ? 0xFFFFFFFF : 0xFFE0E0E0, false);

                    // 规则数量微标签
                    String countTag = tr("gui.enchantmenttooltipcleaner.rules.preset.rules_count", preset.totalRules);
                    int tagW = this.font.width(countTag) + 6;
                    int tagX = optX + optW - 110 - tagW;
                    graphics.fill(tagX, optY + 3, tagX + tagW, optY + itemH - 5, 0x2500AAFF);
                    graphics.renderOutline(tagX, optY + 3, tagW, itemH - 8, 0xFF4DB6AC);
                    graphics.drawCenteredString(this.font, countTag, tagX + tagW / 2, optY + 4, 0xFF80CBC4);

                    // [应用] 按钮
                    int appBtnX = optX + optW - 105;
                    renderSmallButton(graphics, this.font, mouseX, mouseY, appBtnX, optY + 2, 32, 14, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.apply"), 0xFF55FF55, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.apply_tooltip"));

                    // [改名] 按钮
                    int renBtnX = optX + optW - 70;
                    renderSmallButton(graphics, this.font, mouseX, mouseY, renBtnX, optY + 2, 32, 14, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.rename"), 0xFFFFB74D, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.rename_tooltip"));

                    // [删除] 按钮
                    int delBtnX = optX + optW - 35;
                    renderSmallButton(graphics, this.font, mouseX, mouseY, delBtnX, optY + 2, 32, 14, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.delete"), 0xFFFF5555, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.delete_tooltip"));
                }

                if (diskPresets.size() > maxShow) {
                    graphics.drawCenteredString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.rules.preset.scroll_hint", presetScrollOffset + 1, presetScrollOffset + renderCount), centerX, listY + maxShow * itemH + 2, 0x777777);
                }
            }

            // 底部 4 个操作按钮
            int toolY1 = dialogY + dialogH - 46;
            int toolY2 = dialogY + dialogH - 24;
            int btnW = 140;
            int btnH = 17;
            int b1X = dialogX + 14;
            int b2X = dialogX + dialogW - 14 - btnW;

            renderPresetDialogButton(graphics, this.font, mouseX, mouseY, b1X, toolY1, btnW, btnH, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.new"), 0xFF81C784, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.new_tooltip"));
            renderPresetDialogButton(graphics, this.font, mouseX, mouseY, b2X, toolY1, btnW, btnH, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.open_folder"), 0xFF64B5F6, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.open_folder_tooltip"));
            renderPresetDialogButton(graphics, this.font, mouseX, mouseY, b1X, toolY2, btnW, btnH, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.clear_cat"), 0xFFE57373, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.clear_cat_tooltip"));
            renderPresetDialogButton(graphics, this.font, mouseX, mouseY, b2X, toolY2, btnW, btnH, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.close"), 0xFFAAAAAA, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.close_tooltip"));
        }

        graphics.pose().popPose();
    }

    // 二级确认卡片弹窗
    private void renderConfirmModal(GuiGraphics graphics, int mouseX, int mouseY, int centerX, int centerY) {
        int modalW = 260;
        int modalH = 110;
        int modalX = centerX - modalW / 2;
        int modalY = centerY - modalH / 2;

        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF1C1C22);
        graphics.renderOutline(modalX, modalY, modalW, modalH, 0xFFFF5555);

        String title;
        String line1;
        String line2;
        int confirmColor;

        if (pendingConfirmType == ConfirmType.APPLY_PRESET && pendingConfirmPreset != null) {
            title = "§e" + tr("gui.enchantmenttooltipcleaner.rules.preset.confirm_apply_title");
            line1 = "§f" + tr("gui.enchantmenttooltipcleaner.rules.preset.confirm_apply_line1", "§e" + pendingConfirmPreset.displayName);
            line2 = "§7" + tr("gui.enchantmenttooltipcleaner.rules.preset.confirm_apply_desc", "§b" + pendingConfirmPreset.totalRules);
            confirmColor = 0xFF55FF55;
        } else if (pendingConfirmType == ConfirmType.DELETE_PRESET && pendingConfirmPreset != null) {
            title = "§c" + tr("gui.enchantmenttooltipcleaner.rules.preset.confirm_delete_title");
            line1 = "§f" + tr("gui.enchantmenttooltipcleaner.rules.preset.confirm_delete_line1", "§c" + pendingConfirmPreset.file.getName());
            line2 = "§c" + tr("gui.enchantmenttooltipcleaner.rules.preset.confirm_delete_desc");
            confirmColor = 0xFFFF5555;
        } else {
            title = "§c" + tr("gui.enchantmenttooltipcleaner.rules.preset.confirm_clear_title");
            line1 = "§f" + tr("gui.enchantmenttooltipcleaner.rules.preset.confirm_clear_line1", "§e" + tr(CATEGORY_KEYS[currentCategory]));
            line2 = "§c" + tr("gui.enchantmenttooltipcleaner.rules.preset.confirm_clear_desc", "§e" + getCurrentList().size());
            confirmColor = 0xFFFF5555;
        }

        graphics.drawCenteredString(this.font, title, centerX, modalY + 12, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, line1, centerX, modalY + 34, 0xFFE0E0E0);
        graphics.drawCenteredString(this.font, line2, centerX, modalY + 48, 0xAAAAAA);

        int btnW = 84;
        int btnH = 18;
        int btnY = modalY + modalH - 28;
        int okX = centerX - btnW - 8;
        int cancelX = centerX + 8;

        renderPresetDialogButton(graphics, this.font, mouseX, mouseY, okX, btnY, btnW, btnH, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.confirm_exec"), confirmColor, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.confirm_exec_tooltip"));
        renderPresetDialogButton(graphics, this.font, mouseX, mouseY, cancelX, btnY, btnW, btnH, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.confirm_cancel"), 0xFFAAAAAA, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.confirm_cancel_tooltip"));
    }

    // 新建 / 重命名预设卡片弹窗
    private void renderNamingModal(GuiGraphics graphics, int mouseX, int mouseY, int centerX, int centerY) {
        int modalW = 260;
        int modalH = 110;
        int modalX = centerX - modalW / 2;
        int modalY = centerY - modalH / 2;

        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF1C1C22);
        graphics.renderOutline(modalX, modalY, modalW, modalH, isNewPreset ? 0xFF81C784 : 0xFFFFB74D);

        String title = isNewPreset
            ? "§a" + tr("gui.enchantmenttooltipcleaner.rules.preset.naming_new_title")
            : "§e" + tr("gui.enchantmenttooltipcleaner.rules.preset.naming_rename_title");
        String subtitle = isNewPreset
            ? "§7" + tr("gui.enchantmenttooltipcleaner.rules.preset.naming_new_sub")
            : "§8" + tr("gui.enchantmenttooltipcleaner.rules.preset.naming_rename_sub", namingTargetPreset != null ? namingTargetPreset.file.getName() : "");

        graphics.drawCenteredString(this.font, title, centerX, modalY + 12, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, subtitle, centerX, modalY + 28, 0xAAAAAA);

        if (presetNameBox != null) {
            presetNameBox.render(graphics, mouseX, mouseY, 0);
        }

        int btnW = 84;
        int btnH = 18;
        int btnY = modalY + modalH - 28;
        int okX = centerX - btnW - 8;
        int cancelX = centerX + 8;

        String okText = isNewPreset
            ? tr("gui.enchantmenttooltipcleaner.rules.preset.btn.confirm_create")
            : tr("gui.enchantmenttooltipcleaner.rules.preset.btn.confirm_save");
        int okColor = isNewPreset ? 0xFF81C784 : 0xFF55FF55;
        renderPresetDialogButton(graphics, this.font, mouseX, mouseY, okX, btnY, btnW, btnH, okText, okColor, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.confirm_save_tooltip"));
        renderPresetDialogButton(graphics, this.font, mouseX, mouseY, cancelX, btnY, btnW, btnH, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.confirm_cancel"), 0xFFAAAAAA, tr("gui.enchantmenttooltipcleaner.rules.preset.btn.confirm_cancel_tooltip"));
    }

    private void renderSmallButton(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, int w, int h, String text, int color, String tooltip) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        graphics.fill(x, y, x + w, y + h, hovered ? 0xFF353540 : 0xFF24242A);
        graphics.renderOutline(x, y, w, h, hovered ? color : 0xFF444450);
        graphics.drawCenteredString(font, text, x + w / 2, y + 3, hovered ? 0xFFFFFFFF : color);
        if (hovered && tooltip != null) {
            screenHoveredTooltip = tooltip;
        }
    }

    private void renderPresetDialogButton(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, int w, int h, String text, int textColor, String tooltip) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        graphics.fill(x, y, x + w, y + h, hovered ? 0xFF35353C : 0xFF222226);
        graphics.renderOutline(x, y, w, h, hovered ? 0xFFFFAA00 : 0xFF44444C);
        graphics.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, hovered ? 0xFFFFFFFF : textColor);
        if (hovered && tooltip != null) {
            screenHoveredTooltip = tooltip;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isPresetDialogOpen) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int dialogW = 320;
            int dialogH = 186;
            int dialogX = centerX - dialogW / 2;
            int dialogY = centerY - dialogH / 2;

            // 1. 处理二级确认点击
            if (pendingConfirmType != ConfirmType.NONE) {
                int modalW = 260;
                int modalH = 110;
                int modalY = centerY - modalH / 2;
                int btnW = 84;
                int btnH = 18;
                int btnY = modalY + modalH - 28;
                int okX = centerX - btnW - 8;
                int cancelX = centerX + 8;

                if (isInside(mouseX, mouseY, okX, btnY, btnW, btnH)) {
                    playClickSound();
                    executeConfirmedAction();
                    return true;
                }
                if (isInside(mouseX, mouseY, cancelX, btnY, btnW, btnH)) {
                    playClickSound();
                    pendingConfirmType = ConfirmType.NONE;
                    pendingConfirmPreset = null;
                    return true;
                }
                return true;
            }

            // 2. 处理预设命名点击 (新建/改名)
            if (isNamingPreset && presetNameBox != null) {
                int modalW = 260;
                int modalH = 110;
                int modalY = centerY - modalH / 2;
                if (presetNameBox.mouseClicked(mouseX, mouseY, button)) {
                    presetNameBox.setFocused(true);
                    this.setFocused(presetNameBox);
                    return true;
                }
                int btnW = 84;
                int btnH = 18;
                int btnY = modalY + modalH - 28;
                int okX = centerX - btnW - 8;
                int cancelX = centerX + 8;

                if (isInside(mouseX, mouseY, okX, btnY, btnW, btnH)) {
                    playClickSound();
                    confirmPresetNaming();
                    return true;
                }
                if (isInside(mouseX, mouseY, cancelX, btnY, btnW, btnH)) {
                    playClickSound();
                    closePresetNamingDialog();
                    return true;
                }
                return true;
            }

            // 3. 处理预设列表条目操作
            int listY = dialogY + 36;
            int itemH = 20;
            int maxShow = 4;
            int renderCount = Math.min(maxShow, diskPresets.size() - presetScrollOffset);

            for (int i = 0; i < renderCount; i++) {
                PresetInfo preset = diskPresets.get(presetScrollOffset + i);
                int optY = listY + i * itemH;
                int optX = dialogX + 12;
                int optW = dialogW - 24;

                int appBtnX = optX + optW - 105;
                int renBtnX = optX + optW - 70;
                int delBtnX = optX + optW - 35;

                // [应用]
                if (isInside(mouseX, mouseY, appBtnX, optY + 2, 32, 14)) {
                    playClickSound();
                    pendingConfirmType = ConfirmType.APPLY_PRESET;
                    pendingConfirmPreset = preset;
                    return true;
                }
                // [改名]
                if (isInside(mouseX, mouseY, renBtnX, optY + 2, 32, 14)) {
                    playClickSound();
                    openPresetNamingDialog(false, preset);
                    return true;
                }
                // [删除]
                if (isInside(mouseX, mouseY, delBtnX, optY + 2, 32, 14)) {
                    playClickSound();
                    pendingConfirmType = ConfirmType.DELETE_PRESET;
                    pendingConfirmPreset = preset;
                    return true;
                }
            }

            // 底部 4 个按钮
            int toolY1 = dialogY + dialogH - 46;
            int toolY2 = dialogY + dialogH - 24;
            int btnW = 140;
            int btnH = 17;
            int b1X = dialogX + 14;
            int b2X = dialogX + dialogW - 14 - btnW;

            // [+ 保存为新预设]
            if (isInside(mouseX, mouseY, b1X, toolY1, btnW, btnH)) {
                playClickSound();
                openPresetNamingDialog(true, null);
                return true;
            }
            // [打开预设文件夹]
            if (isInside(mouseX, mouseY, b2X, toolY1, btnW, btnH)) {
                playClickSound();
                openPresetsFolderInExplorer();
                return true;
            }
            // [清空当前分类规则]
            if (isInside(mouseX, mouseY, b1X, toolY2, btnW, btnH)) {
                playClickSound();
                pendingConfirmType = ConfirmType.CLEAR_CATEGORY;
                return true;
            }
            // [关闭并返回] 或点击遮罩外区域
            if (isInside(mouseX, mouseY, b2X, toolY2, btnW, btnH) || !isInside(mouseX, mouseY, dialogX, dialogY, dialogW, dialogH)) {
                playClickSound();
                isPresetDialogOpen = false;
                this.init();
                return true;
            }
            return true;
        }

        // 如果点击了非确认按钮区域，取消单条规则删除确认
        pendingDeleteIndex = -1;

        // 检查点击右侧视觉滚动条 (点击直接跳转/开始拖拽)
        List<FilteredEntry> filtered = getFilteredEntries();
        int listTop = 60;
        int listBottom = this.height - 52;
        int itemStartY = listTop + LIST_HEADER_HEIGHT;
        int maxVisible = (listBottom - itemStartY) / ITEM_HEIGHT;
        int listRight = this.width / 2 + 205;
        if (!isPresetDialogOpen && filtered.size() > maxVisible && maxVisible > 0) {
            int scrollTrackX = listRight - 9;
            int scrollTrackY = itemStartY + 2;
            int scrollTrackW = 6;
            int scrollTrackH = (listBottom - 4) - scrollTrackY;

            if (button == 0 && mouseX >= scrollTrackX - 3 && mouseX <= scrollTrackX + scrollTrackW + 3 &&
                mouseY >= scrollTrackY && mouseY <= scrollTrackY + scrollTrackH) {
                this.isDraggingScrollbar = true;
                updateScrollbarFromMouse(mouseY, scrollTrackY, scrollTrackH, maxVisible, filtered.size());
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isDraggingScrollbar) {
            this.isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.isDraggingScrollbar) {
            List<FilteredEntry> filtered = getFilteredEntries();
            int listTop = 60;
            int listBottom = this.height - 52;
            int itemStartY = listTop + LIST_HEADER_HEIGHT;
            int maxVisible = (listBottom - itemStartY) / ITEM_HEIGHT;
            int scrollTrackY = itemStartY + 2;
            int scrollTrackH = (listBottom - 4) - scrollTrackY;
            updateScrollbarFromMouse(mouseY, scrollTrackY, scrollTrackH, maxVisible, filtered.size());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateScrollbarFromMouse(double mouseY, int trackY, int trackH, int maxVisible, int totalItems) {
        int maxOffset = totalItems - maxVisible;
        if (maxOffset <= 0) return;
        int thumbH = Math.max(16, (int) ((float) maxVisible / totalItems * trackH));
        double relY = Math.max(0, Math.min(trackH - thumbH, mouseY - trackY - thumbH / 2.0));
        int newOffset = (int) Math.round(relY / (trackH - thumbH) * maxOffset);
        if (newOffset != this.scrollOffset) {
            this.scrollOffset = Math.max(0, Math.min(maxOffset, newOffset));
            this.pendingDeleteIndex = -1;
            this.init();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isPresetDialogOpen) {
            if (isNamingPreset && presetNameBox != null) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    confirmPresetNaming();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    closePresetNamingDialog();
                    return true;
                }
                return presetNameBox.keyPressed(keyCode, scanCode, modifiers);
            }
            if (pendingConfirmType != ConfirmType.NONE) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    executeConfirmedAction();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    pendingConfirmType = ConfirmType.NONE;
                    pendingConfirmPreset = null;
                    return true;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                isPresetDialogOpen = false;
                this.init();
                return true;
            }
            return true;
        }

        // 底部输入框按回车直接触发添加/保存规则
        if (this.inputField != null && this.inputField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (this.actionButton != null) {
                    this.actionButton.onPress();
                    return true;
                }
            }
        }

        // 搜索框按 ESC 清空搜索内容，按回车退出输入焦点
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE && !this.searchBox.getValue().isEmpty()) {
                this.searchBox.setValue("");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.searchBox.setFocused(false);
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isPresetDialogOpen && isNamingPreset && presetNameBox != null) {
            return presetNameBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isPresetDialogOpen) {
            int maxShow = 4;
            int maxOffset = Math.max(0, diskPresets.size() - maxShow);
            if (scrollY > 0 && presetScrollOffset > 0) {
                presetScrollOffset--;
                return true;
            } else if (scrollY < 0 && presetScrollOffset < maxOffset) {
                presetScrollOffset++;
                return true;
            }
            return true;
        }

        List<FilteredEntry> filtered = getFilteredEntries();
        int listTop = 60;
        int listBottom = this.height - 52;
        int maxVisible = (listBottom - (listTop + LIST_HEADER_HEIGHT)) / ITEM_HEIGHT;
        int maxOffset = Math.max(0, filtered.size() - maxVisible);

        if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
            pendingDeleteIndex = -1;
            this.init();
            return true;
        } else if (scrollY < 0 && scrollOffset < maxOffset) {
            scrollOffset++;
            pendingDeleteIndex = -1;
            this.init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        screenHoveredTooltip = null;

        // 顶部标题或动态状态提醒
        if (statusMessage != null && System.currentTimeMillis() < statusExpiry) {
            graphics.drawCenteredString(this.font, statusMessage, centerX, 7, statusColor);
        } else {
            graphics.drawCenteredString(this.font, "§f" + this.title.getString(), centerX, 7, 0xFFFFFFFF);
        }

        // 检查类别 Tab 悬停提示
        if (!isPresetDialogOpen) {
            int tabW = Math.min(54, (this.width - 24) / CATEGORY_KEYS.length - 2);
            int tabH = 16;
            int totalTabs = CATEGORY_KEYS.length;
            int startX = centerX - (totalTabs * (tabW + 2)) / 2;
            for (int i = 0; i < totalTabs; i++) {
                if (isInside(mouseX, mouseY, startX + i * (tabW + 2), 22, tabW, tabH)) {
                    screenHoveredTooltip = tr(CATEGORY_TOOLTIP_KEYS[i]);
                    break;
                }
            }
        }

        // 列表区域坐标
        int listTop = 60;
        int listBottom = this.height - 52;
        int listLeft = centerX - 195;
        int listRight = centerX + 205;
        int listW = listRight - listLeft;
        int listH = listBottom - listTop;

        // 绘制列表底框
        renderCard(graphics, listLeft, listTop, listW, listH, 0xFF4A4A52);

        // 表头区域与表头文字
        graphics.fill(listLeft + 1, listTop + 1, listRight - 1, listTop + LIST_HEADER_HEIGHT, 0xFF202025);
        graphics.hLine(listLeft + 1, listRight - 2, listTop + LIST_HEADER_HEIGHT, 0xFF35353C);
        graphics.drawString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.rules.header.num"), listLeft + 8, listTop + 4, 0x888888, false);
        graphics.drawString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.rules.header.type"), listLeft + 27, listTop + 4, 0x888888, false);
        graphics.drawString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.rules.header.content"), listLeft + 62, listTop + 4, 0x888888, false);
        graphics.drawString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.rules.header.actions"), listRight - 76, listTop + 4, 0x888888, false);

        // 渲染规则条目
        List<FilteredEntry> filtered = getFilteredEntries();
        List<String> rawAll = getCurrentList();
        int itemStartY = listTop + LIST_HEADER_HEIGHT;
        int maxVisible = (listBottom - itemStartY) / ITEM_HEIGHT;

        if (!isPresetDialogOpen && filtered.isEmpty()) {
            if (rawAll.isEmpty()) {
                graphics.drawCenteredString(this.font, "§7" + tr("gui.enchantmenttooltipcleaner.rules.empty"), centerX, itemStartY + (listH - LIST_HEADER_HEIGHT) / 2 - 8, 0xAAAAAA);
                graphics.drawCenteredString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.rules.empty_hint"), centerX, itemStartY + (listH - LIST_HEADER_HEIGHT) / 2 + 4, 0x666666);
            } else {
                graphics.drawCenteredString(this.font, "§7" + tr("gui.enchantmenttooltipcleaner.rules.no_match", "§e" + searchQuery), centerX, itemStartY + (listH - LIST_HEADER_HEIGHT) / 2 - 8, 0xAAAAAA);
                graphics.drawCenteredString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.rules.no_match_hint"), centerX, itemStartY + (listH - LIST_HEADER_HEIGHT) / 2 + 4, 0x666666);
            }
        } else if (!isPresetDialogOpen) {
            int renderCount = Math.min(maxVisible, Math.max(0, filtered.size() - scrollOffset));

            for (int i = 0; i < renderCount; i++) {
                FilteredEntry entry = filtered.get(scrollOffset + i);
                int rowY = itemStartY + i * ITEM_HEIGHT + 1;
                boolean isCurrentItemEditing = (editingIndex == entry.originalIndex);
                boolean rowHovered = (mouseX >= listLeft + 1 && mouseX <= listRight - 1 && mouseY >= rowY && mouseY < rowY + ITEM_HEIGHT);

                // 行背景高亮
                if (isCurrentItemEditing) {
                    graphics.fill(listLeft + 1, rowY, listRight - 1, rowY + ITEM_HEIGHT - 1, 0x30FFAA00);
                    graphics.fill(listLeft + 1, rowY, listLeft + 3, rowY + ITEM_HEIGHT - 1, 0xFFFFAA00);
                } else if (rowHovered) {
                    graphics.fill(listLeft + 1, rowY, listRight - 1, rowY + ITEM_HEIGHT - 1, 0x22FFFFFF);
                    graphics.fill(listLeft + 1, rowY, listLeft + 3, rowY + ITEM_HEIGHT - 1, 0xFF55FF55);
                } else if (i % 2 == 1) {
                    graphics.fill(listLeft + 1, rowY, listRight - 1, rowY + ITEM_HEIGHT - 1, 0x0EFFFFFF);
                }

                // 1. 序号
                String numStr = String.valueOf(entry.originalIndex + 1);
                graphics.drawString(this.font, "§7" + numStr, listLeft + 8, rowY + 5, 0x888888, false);

                // 2. 类型胶囊徽章
                renderBadge(graphics, listLeft + 26, rowY + 3, getCategoryBadgeText(currentCategory), getCategoryBadgeBg(currentCategory), getCategoryBadgeFg(currentCategory));

                // 3. 规则内容
                String display = entry.text;
                int maxTextW = (listRight - 98) - (listLeft + 62);
                if (this.font.width(display) > maxTextW) {
                    display = this.font.plainSubstrByWidth(display, maxTextW - 10) + "...";
                }
                int textColor = isCurrentItemEditing ? 0xFFFFD54F : (rowHovered ? 0xFFFFFFFF : 0xFFDDDDDD);
                graphics.drawString(this.font, display, listLeft + 62, rowY + 5, textColor, false);
            }

            // 规则统计与翻页指示
            String countText = searchQuery.isEmpty()
                ? "§8" + tr("gui.enchantmenttooltipcleaner.rules.stats_total", rawAll.size())
                : "§a" + tr("gui.enchantmenttooltipcleaner.rules.stats_filtered", filtered.size(), rawAll.size());
            graphics.drawString(this.font, countText, listLeft + 4, listTop - 11, 0x999999, false);

            if (filtered.size() > maxVisible) {
                graphics.drawString(this.font, "§8" + tr("gui.enchantmenttooltipcleaner.rules.scroll_hint", scrollOffset + 1, scrollOffset + renderCount), listRight - 96, listTop - 11, 0x888888, false);
            }
        }

        // 正则表达式/校验动态微反馈
        if (regexFeedbackMessage != null) {
            graphics.drawCenteredString(this.font, regexFeedbackMessage, centerX, this.height - 58, regexFeedbackColor);
        }

        // 渲染注册控件
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(graphics, isPresetDialogOpen ? -999 : mouseX, isPresetDialogOpen ? -999 : mouseY, partialTick);
        }

        // 视觉滚动条 (在所有控件之后渲染，保证拥有绝对顶层层级，绝不被任何按钮遮挡)
        if (!isPresetDialogOpen && filtered.size() > maxVisible && maxVisible > 0) {
            int scrollTrackX = listRight - 9;
            int scrollTrackY = itemStartY + 2;
            int scrollTrackW = 6;
            int scrollTrackH = (listBottom - 4) - scrollTrackY;

            // 1. 滑轨背景槽与柔和描边
            graphics.fill(scrollTrackX, scrollTrackY, scrollTrackX + scrollTrackW, scrollTrackY + scrollTrackH, 0x66000000);
            graphics.renderOutline(scrollTrackX, scrollTrackY, scrollTrackW, scrollTrackH, 0x30FFFFFF);

            // 2. 滑动块计算
            int maxOffset = filtered.size() - maxVisible;
            int thumbH = Math.max(16, (int) ((float) maxVisible / filtered.size() * scrollTrackH));
            int thumbY = scrollTrackY + (int) ((float) scrollOffset / maxOffset * (scrollTrackH - thumbH));

            boolean thumbHovered = (mouseX >= scrollTrackX - 2 && mouseX <= scrollTrackX + scrollTrackW + 2 &&
                                   mouseY >= thumbY && mouseY <= thumbY + thumbH);
            int thumbColor = (isDraggingScrollbar || thumbHovered) ? 0xFFFFFFFF : 0xFFA0A0B0;
            int thumbBorder = (isDraggingScrollbar || thumbHovered) ? 0xFFFFAA00 : 0x50FFFFFF;

            graphics.fill(scrollTrackX + 1, thumbY + 1, scrollTrackX + scrollTrackW - 1, thumbY + thumbH - 1, thumbColor);
            graphics.renderOutline(scrollTrackX, thumbY, scrollTrackW, thumbH, thumbBorder);
        }

        // 预设弹窗顶层遮罩渲染
        if (isPresetDialogOpen) {
            renderPresetDialog(graphics, mouseX, mouseY);
        }

        // 悬浮说明文字展示 (顶层渲染，抬升 Z 轴至 900，杜绝任何图层与按钮穿透遮挡)
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
