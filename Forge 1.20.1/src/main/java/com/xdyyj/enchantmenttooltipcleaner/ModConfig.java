package com.xdyyj.enchantmenttooltipcleaner; // (你的包名)

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List; 
import java.util.Collections; 

public class ModConfig {
    
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // --- 我们的配置选项 ---
    public static final ForgeConfigSpec.BooleanValue GLOBAL_ENABLE;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE; 
    
    // --- 类别开关 ---
    public static final ForgeConfigSpec.BooleanValue REMOVE_ENCHANTMENTS;
    public static final ForgeConfigSpec.BooleanValue REMOVE_DESCRIPTIONS;
    public static final ForgeConfigSpec.BooleanValue REMOVE_ATTRIBUTES; 
    public static final ForgeConfigSpec.BooleanValue REMOVE_POTION_EFFECTS; 

    // --- 【新】5个自定义列表的总开关 ---
    public static final ForgeConfigSpec.BooleanValue ENABLE_TEXT_CONTAINS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TEXT_EXACT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TEXT_REGEX;
    public static final ForgeConfigSpec.BooleanValue ENABLE_KEY_CONTAINS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_KEY_EXACT;

    // --- 【新】5个自定义列表 ---
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REMOVE_LINES_CONTAINING; 
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REMOVE_LINES_EXACT;    
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REMOVE_LINES_REGEX;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REMOVE_KEYS_CONTAINING;  
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REMOVE_KEYS_EXACT;       

    // --- 【新】排除功能 ---
    public static final ForgeConfigSpec.BooleanValue ENABLE_EXCLUSIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_MODS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_ITEMS;       

    // --- 悬浮小窗与按钮配置 ---
    public static final ForgeConfigSpec.BooleanValue ENABLE_INVENTORY_BUTTON;
    public static final ForgeConfigSpec.IntValue OVERLAY_POS_X;
    public static final ForgeConfigSpec.IntValue OVERLAY_POS_Y;

    static {
        // --- 1. 全局与调试 (根目录) ---
        GLOBAL_ENABLE = BUILDER
                .translation("config.enchantmenttooltipcleaner.globalEnable") 
                .define("globalEnable", true); 
        DEBUG_MODE = BUILDER
                .translation("config.enchantmenttooltipcleaner.debugMode") 
                .define("debugMode", false); 

        // --- 悬浮窗与按钮 (根目录) ---
        BUILDER.translation("config.enchantmenttooltipcleaner.cat.ui").push("ui");
        ENABLE_INVENTORY_BUTTON = BUILDER
                .translation("config.enchantmenttooltipcleaner.enableInventoryButton")
                .comment("Whether to render a mini gear button in inventory screens to open the quick settings overlay. Default: false")
                .define("enableInventoryButton", false);
        OVERLAY_POS_X = BUILDER
                .defineInRange("overlayPosX", -1, -1, 10000);
        OVERLAY_POS_Y = BUILDER
                .defineInRange("overlayPosY", -1, -1, 10000);
        BUILDER.pop(); 

        // --- 2. 简单开关 (独立块) ---
        BUILDER.translation("config.enchantmenttooltipcleaner.cat.simple_toggles").push("simple_toggles");
        REMOVE_ENCHANTMENTS = BUILDER
                .translation("config.enchantmenttooltipcleaner.removeEnchantments") 
                .define("removeEnchantments", true); 
        REMOVE_DESCRIPTIONS = BUILDER
                .translation("config.enchantmenttooltipcleaner.removeDescriptions") 
                .define("removeEnchantmentDescriptions", true); 
        REMOVE_ATTRIBUTES = BUILDER 
                .translation("config.enchantmenttooltipcleaner.removeAttributes") 
                .define("removeAttributes", false); 
        REMOVE_POTION_EFFECTS = BUILDER 
                .translation("config.enchantmenttooltipcleaner.removePotionEffects") 
                .define("removePotionEffects", false); 
        BUILDER.pop(); 

        // --- 3. 排除功能 (独立块) ---
        BUILDER.translation("config.enchantmenttooltipcleaner.cat.exclusions").push("exclusions");
        ENABLE_EXCLUSIONS = BUILDER
                .translation("config.enchantmenttooltipcleaner.excl.enabled")
                .define("enabled", false);
        EXCLUDED_MODS = BUILDER
                .translation("config.enchantmenttooltipcleaner.excl.mods")
                .defineList("mods", java.util.ArrayList::new, (obj) -> true);
        EXCLUDED_ITEMS = BUILDER
                .translation("config.enchantmenttooltipcleaner.excl.items")
                .defineList("items", java.util.ArrayList::new, (obj) -> true);
        BUILDER.pop();

        // --- 4. 高级过滤器 (全部拆分为独立顶级块) ---
        
        BUILDER.translation("config.enchantmenttooltipcleaner.cat.text_contains").push("filter_text_contains");
        ENABLE_TEXT_CONTAINS = BUILDER.translation("config.enchantmenttooltipcleaner.filterTextContains.enabled").define("enabled", true);
        REMOVE_LINES_CONTAINING = BUILDER.translation("config.enchantmenttooltipcleaner.filterTextContains.list").defineList("list", java.util.ArrayList::new, (obj) -> true);
        BUILDER.pop();

        BUILDER.translation("config.enchantmenttooltipcleaner.cat.text_exact").push("filter_text_exact");
        ENABLE_TEXT_EXACT = BUILDER.translation("config.enchantmenttooltipcleaner.filterTextExact.enabled").define("enabled", true);
        REMOVE_LINES_EXACT = BUILDER.translation("config.enchantmenttooltipcleaner.filterTextExact.list").defineList("list", java.util.ArrayList::new, (obj) -> true);
        BUILDER.pop();
        
        BUILDER.translation("config.enchantmenttooltipcleaner.cat.text_regex").push("filter_text_regex");
        ENABLE_TEXT_REGEX = BUILDER.translation("config.enchantmenttooltipcleaner.filterTextRegex.enabled").define("enabled", true);
        REMOVE_LINES_REGEX = BUILDER.translation("config.enchantmenttooltipcleaner.filterTextRegex.list").defineList("list", java.util.ArrayList::new, (obj) -> true);
        BUILDER.pop();

        BUILDER.translation("config.enchantmenttooltipcleaner.cat.key_contains").push("filter_key_contains");
        ENABLE_KEY_CONTAINS = BUILDER.translation("config.enchantmenttooltipcleaner.filterKeyContains.enabled").define("enabled", true);
        REMOVE_KEYS_CONTAINING = BUILDER.translation("config.enchantmenttooltipcleaner.filterKeyContains.list").defineList("list", java.util.ArrayList::new, (obj) -> true);
        BUILDER.pop();
        
        BUILDER.translation("config.enchantmenttooltipcleaner.cat.key_exact").push("filter_key_exact");
        ENABLE_KEY_EXACT = BUILDER.translation("config.enchantmenttooltipcleaner.filterKeyExact.enabled").define("enabled", true);
        REMOVE_KEYS_EXACT = BUILDER.translation("config.enchantmenttooltipcleaner.filterKeyExact.list").defineList("list", java.util.ArrayList::new, (obj) -> true);
        BUILDER.pop();


        SPEC = BUILDER.build(); 
    }

    public static void saveConfig() {
        try {
            if (SPEC.isLoaded()) {
                SPEC.save();
            }
        } catch (Exception ignored) {}
    }
}