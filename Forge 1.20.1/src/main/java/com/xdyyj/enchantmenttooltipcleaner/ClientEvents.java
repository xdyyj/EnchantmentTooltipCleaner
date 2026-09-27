package com.xdyyj.enchantmenttooltipcleaner;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Mod.EventBusSubscriber(modid = "enchantmenttooltipcleaner", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {

    private static final TextColor GRAY_TEXT_COLOR = TextColor.fromLegacyFormat(ChatFormatting.GRAY);
    private static final TextColor DARK_GRAY_TEXT_COLOR = TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY);
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\s\\h\\p{Z}]+");
    private static final Map<String, String> MOD_DISPLAY_NAME_CACHE = new ConcurrentHashMap<>();
    
    private static volatile ConfigContext currentContext;

    // --- ReDoS 防御：带超时与字符访问限制的安全 CharSequence ---
    public static class TimeoutCharSequence implements CharSequence {
        private final CharSequence inner;
        private final long maxTimeNanos;
        private final long startTimeNanos;
        private int checkCounter = 0;

        public TimeoutCharSequence(CharSequence inner, long timeoutMillis) {
            this.inner = inner;
            this.maxTimeNanos = timeoutMillis * 1_000_000L;
            this.startTimeNanos = System.nanoTime();
        }

        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public char charAt(int index) {
            // 每 64 次字符访问采样一次系统时钟，将时钟读取开销降低 98% 以上，同时保持毫秒级防御精度
            if ((++checkCounter & 0x3F) == 0 && (System.nanoTime() - startTimeNanos) > maxTimeNanos) {
                throw new RegexTimeoutException("Regex evaluation exceeded timeout limit");
            }
            return inner.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            long elapsed = System.nanoTime() - startTimeNanos;
            long remaining = Math.max(1, (maxTimeNanos - elapsed) / 1_000_000L);
            return new TimeoutCharSequence(inner.subSequence(start, end), remaining);
        }

        @Override
        public String toString() {
            return inner.toString();
        }

        public static class RegexTimeoutException extends RuntimeException {
            public RegexTimeoutException(String message) {
                super(message);
            }
        }
    }

    // --- 缓存数据结构 ---
    public static class BakedConfig {
        public static volatile boolean globalEnable = true;
        public static volatile boolean debugMode = false;

        public static volatile boolean removeEnchantments = true;
        public static volatile boolean removeDescriptions = true;
        public static volatile boolean removeAttributes = true;
        public static volatile boolean removePotionEffects = true;

        public static volatile boolean enableTextContains = true;
        public static volatile boolean enableTextExact = true;
        public static volatile boolean enableTextRegex = true;
        public static volatile boolean enableKeyContains = true;
        public static volatile boolean enableKeyExact = true;

        public static volatile Set<String> customLinesContaining = Collections.emptySet();
        public static volatile Set<String> customLinesExact = Collections.emptySet();
        public static volatile List<Pattern> regexPatterns = Collections.emptyList();
        public static volatile Set<String> customKeysToContain = Collections.emptySet();
        public static volatile Set<String> customKeysExact = Collections.emptySet();

        public static volatile boolean enableExclusions = false;
        public static volatile Set<String> excludedMods = Collections.emptySet();
        public static volatile Set<String> excludedItems = Collections.emptySet();
        public static volatile Set<String> excludedTags = Collections.emptySet();
    }

    // --- 供外部调用的刷新方法 ---
    public static void refreshConfig() {
        if (!ModConfig.SPEC.isLoaded()) {
            return;
        }
        BakedConfig.globalEnable = ModConfig.GLOBAL_ENABLE.get();
        BakedConfig.debugMode = ModConfig.DEBUG_MODE.get();

        BakedConfig.removeEnchantments = ModConfig.REMOVE_ENCHANTMENTS.get();
        BakedConfig.removeDescriptions = ModConfig.REMOVE_DESCRIPTIONS.get();
        BakedConfig.removeAttributes = ModConfig.REMOVE_ATTRIBUTES.get();
        BakedConfig.removePotionEffects = ModConfig.REMOVE_POTION_EFFECTS.get();

        BakedConfig.enableTextContains = ModConfig.ENABLE_TEXT_CONTAINS.get();
        BakedConfig.enableTextExact = ModConfig.ENABLE_TEXT_EXACT.get();
        BakedConfig.enableTextRegex = ModConfig.ENABLE_TEXT_REGEX.get();
        BakedConfig.enableKeyContains = ModConfig.ENABLE_KEY_CONTAINS.get();
        BakedConfig.enableKeyExact = ModConfig.ENABLE_KEY_EXACT.get();

        Set<String> linesCont = new HashSet<>();
        for (String s : ModConfig.REMOVE_LINES_CONTAINING.get()) {
            if (s != null && !s.trim().isEmpty()) linesCont.add(s.trim());
        }
        BakedConfig.customLinesContaining = linesCont;

        Set<String> linesExact = new HashSet<>();
        for (String s : ModConfig.REMOVE_LINES_EXACT.get()) {
            if (s != null && !s.trim().isEmpty()) linesExact.add(s.trim());
        }
        BakedConfig.customLinesExact = linesExact;

        Set<String> keysCont = new HashSet<>();
        for (String s : ModConfig.REMOVE_KEYS_CONTAINING.get()) {
            if (s != null && !s.trim().isEmpty()) keysCont.add(s.trim());
        }
        BakedConfig.customKeysToContain = keysCont;

        Set<String> keysExact = new HashSet<>();
        for (String s : ModConfig.REMOVE_KEYS_EXACT.get()) {
            if (s != null && !s.trim().isEmpty()) keysExact.add(s.trim());
        }
        BakedConfig.customKeysExact = keysExact;

        BakedConfig.enableExclusions = ModConfig.ENABLE_EXCLUSIONS.get();
        
        Set<String> mods = new HashSet<>();
        for (String s : ModConfig.EXCLUDED_MODS.get()) {
            if (s != null && !s.trim().isEmpty()) mods.add(s.trim().toLowerCase(Locale.ROOT));
        }
        BakedConfig.excludedMods = mods;
        
        Set<String> items = new HashSet<>();
        Set<String> tags = new HashSet<>();
        for (String s : ModConfig.EXCLUDED_ITEMS.get()) {
            if (s != null && !s.trim().isEmpty()) {
                String clean = s.trim().toLowerCase(Locale.ROOT);
                if (clean.startsWith("#")) {
                    tags.add(clean.substring(1));
                    tags.add(clean);
                } else {
                    items.add(clean);
                }
            }
        }
        BakedConfig.excludedItems = items;
        BakedConfig.excludedTags = tags;

        List<Pattern> newPatterns = new ArrayList<>();
        for (String s : ModConfig.REMOVE_LINES_REGEX.get()) {
            if (s == null || s.isEmpty()) continue;
            try {
                newPatterns.add(Pattern.compile(s));
            } catch (PatternSyntaxException ignored) {}
        }
        BakedConfig.regexPatterns = newPatterns;
        
        MOD_DISPLAY_NAME_CACHE.clear();
        currentContext = ConfigContext.capture();
    }

    // 统一翻译键取值，保证英文/繁体环境下呈现地道语言
    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String getModDisplayName(String modId) {
        return MOD_DISPLAY_NAME_CACHE.computeIfAbsent(modId, id ->
            ModList.get().getModContainerById(id)
                .map(container -> container.getModInfo().getDisplayName().toLowerCase(Locale.ROOT))
                .orElse("")
        );
    }

    // 用于单次事件处理的本地配置快照，聚合实际生效条件
    public record ConfigContext(
        boolean removeEnch, boolean removeDesc, boolean removeAttr, boolean removePot,
        boolean textContActive, boolean textExactActive, boolean textRegexActive,
        boolean keyContActive, boolean keyExactActive,
        Set<String> customLinesCont, Set<String> customLinesEx, List<Pattern> regexPats,
        Set<String> customKeysCont, Set<String> customKeysEx,
        boolean enableExcl, Set<String> exclMods, Set<String> exclItems, Set<String> exclTags
    ) {
        public static ConfigContext capture() {
            boolean textCont = BakedConfig.enableTextContains && !BakedConfig.customLinesContaining.isEmpty();
            boolean textExact = BakedConfig.enableTextExact && !BakedConfig.customLinesExact.isEmpty();
            boolean textRegex = BakedConfig.enableTextRegex && !BakedConfig.regexPatterns.isEmpty();
            boolean keyCont = BakedConfig.enableKeyContains && !BakedConfig.customKeysToContain.isEmpty();
            boolean keyExact = BakedConfig.enableKeyExact && !BakedConfig.customKeysExact.isEmpty();

            return new ConfigContext(
                BakedConfig.removeEnchantments, BakedConfig.removeDescriptions,
                BakedConfig.removeAttributes, BakedConfig.removePotionEffects,
                textCont, textExact, textRegex,
                keyCont, keyExact,
                BakedConfig.customLinesContaining, BakedConfig.customLinesExact, BakedConfig.regexPatterns,
                BakedConfig.customKeysToContain, BakedConfig.customKeysExact,
                BakedConfig.enableExclusions, BakedConfig.excludedMods, BakedConfig.excludedItems, BakedConfig.excludedTags
            );
        }

        public boolean hasAnyFilter() {
            return removeEnch || removeDesc || removeAttr || removePot ||
                   textContActive || textExactActive || textRegexActive || keyContActive || keyExactActive;
        }

        public boolean needsTextInspection() {
            return textExactActive || textContActive || textRegexActive;
        }
    }
    
    // --- 核心递归检查逻辑 (单次遍历) ---
    private static boolean shouldRemoveRecursively(Component component, ConfigContext ctx) {
        if (component == null) return false;
        
        if (component.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            if (key != null && !key.isEmpty()) {
                // 1. 内置 Key 检查：支持 startsWith 与 contains
                if (ctx.removeEnch() && (key.startsWith("enchantment.") || key.contains(".enchantment."))) return true;
                if (ctx.removeAttr() && key.startsWith("attribute.modifier.")) return true;
                if (ctx.removePot() && (key.startsWith("effect.minecraft.") || key.startsWith("potion.withDuration") || key.contains(".effect.") || key.startsWith("jade.potion") || key.contains("potion_effects"))) return true;
                if (ctx.removeDesc() && (key.contains("description.") || key.contains(".desc") || key.contains("enchantment_description"))) return true;
                
                // 2. 自定义 Key 检查
                if (ctx.keyExactActive() && ctx.customKeysEx().contains(key)) return true;
                if (ctx.keyContActive()) {
                    for (String frag : ctx.customKeysCont()) {
                        if (key.contains(frag)) return true;
                    }
                }
            }
            
            // 递归检查参数 (args)
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component argComp && shouldRemoveRecursively(argComp, ctx)) return true;
            }
        }
        
        // 递归检查子组件 (siblings)
        List<Component> siblings = component.getSiblings();
        if (!siblings.isEmpty()) {
            for (int i = 0, size = siblings.size(); i < size; i++) {
                if (shouldRemoveRecursively(siblings.get(i), ctx)) return true;
            }
        }
        
        return false;
    }

    // 使用 StackWalker 高性能惰性扫描栈帧（零全量数组堆分配开销）
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    // 检查调用栈是否来自搜索索引（如 JEI、REI、EMI 等后台构建搜索字典抓取 tooltip）
    private static boolean isSearchIndexingCall() {
        // 1. 物品提示的渲染必然发生在渲染主线程；不在主线程即必定是后台检索模组的索引收集线程，
        //    直接判定为索引调用，完全免去 StackWalker 的分配与遍历开销
        if (!RenderSystem.isOnRenderThread()) {
            return true;
        }

        // 2. 限制最大遍历深度为 20 帧，消除深层调用栈带来的性能抖动
        return STACK_WALKER.walk(frames -> frames.limit(20).anyMatch(frame -> {
            String className = frame.getClassName();
            // 必须是搜索/索引相关核心类（如 JEI IngredientFilter、EMI 搜索构建等）
            if (className.contains("IngredientFilter") ||
                className.contains("SearchIndex") ||
                className.contains("ElementSearch") ||
                className.contains("PrefixInfo") ||
                className.contains(".search.") ||
                className.contains(".indexing.")) {
                // 排除明确的 GUI 悬停渲染操作，确保在 JEI/EMI 界面看物品时正常清理！
                return !className.contains(".gui.") && !className.contains(".screen.") && !className.contains(".overlay.");
            }
            return false;
        }));
    }

    private static void collectTranslatableKeys(Component component, List<String> keys) {
        if (component == null) return;
        if (component.getContents() instanceof TranslatableContents tc) {
            String k = tc.getKey();
            if (k != null && !k.isEmpty() && !keys.contains(k)) {
                keys.add(k);
            }
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component ac) {
                    collectTranslatableKeys(ac, keys);
                }
            }
        }
        for (Component sib : component.getSiblings()) {
            collectTranslatableKeys(sib, keys);
        }
    }

    private static void extractKeysToString(Component comp, StringBuilder sb, String indent) {
        if (comp == null) return;
        if (comp.getContents() instanceof TranslatableContents tc) {
            sb.append(indent).append(tr("gui.enchantmenttooltipcleaner.debug.key_name")).append(tc.getKey());
            if (tc.getArgs() != null && tc.getArgs().length > 0) {
                sb.append(tr("gui.enchantmenttooltipcleaner.debug.key_args", Arrays.toString(tc.getArgs())));
            }
            sb.append("\n");
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component ac) {
                    extractKeysToString(ac, sb, indent + "    ");
                }
            }
        }
        for (Component sib : comp.getSiblings()) {
            extractKeysToString(sib, sb, indent);
        }
    }

    public static void copyItemDebugInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = id.toString();
        StringBuilder sb = new StringBuilder();
        sb.append(tr("gui.enchantmenttooltipcleaner.debug.header.item")).append("\n");
        sb.append(tr("gui.enchantmenttooltipcleaner.debug.item_id", itemId)).append("\n");
        sb.append(tr("gui.enchantmenttooltipcleaner.debug.mod_id", id.getNamespace())).append("\n");
        sb.append(tr("gui.enchantmenttooltipcleaner.debug.item_name", stack.getHoverName().getString())).append("\n");

        List<String> tags = stack.getTags().map(t -> "#" + t.location().toString()).toList();
        if (!tags.isEmpty()) {
            sb.append(tr("gui.enchantmenttooltipcleaner.debug.tags", String.join(", ", tags))).append("\n");
        }

        sb.append("\n").append(tr("gui.enchantmenttooltipcleaner.debug.header.tooltip")).append("\n");
        Minecraft mc = Minecraft.getInstance();
        List<Component> tooltip = Screen.getTooltipFromItem(mc, stack);
        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            String text = ChatFormatting.stripFormatting(line.getString()).trim();
            if (!text.isEmpty()) {
                sb.append("[L").append(i).append("] ").append(text).append("\n");
                extractKeysToString(line, sb, "    ");
            }
        }

        String result = sb.toString();
        Minecraft.getInstance().keyboardHandler.setClipboard(result);
        Minecraft.getInstance().gui.setOverlayMessage(
            Component.literal("§a").append(Component.translatable("gui.enchantmenttooltipcleaner.overlay.copied")), false
        );
    }

    // --- 主事件 ---
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltipShow(ItemTooltipEvent event) {
        if (event == null || event.getToolTip() == null) {
            return;
        }

        if (!BakedConfig.globalEnable) return;

        // 如果是 JEI 等搜索模组在后台收集 tooltip 构建附魔/NBT 索引，则放行不修改，保证 # 和 $ 检索 100% 正常
        if (isSearchIndexingCall()) {
            return;
        }

        ItemStack stack = event.getItemStack();

        // 排除逻辑检查
        if (BakedConfig.enableExclusions && !stack.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String registryName = id.toString();
            String modId = id.getNamespace();
            
            // 1. 检查物品 ID 是否被直接排除
            if (BakedConfig.excludedItems.contains(registryName)) {
                return;
            }

            // 2. 检查 Mod ID 是否被排除
            if (BakedConfig.excludedMods.contains(modId)) {
                return;
            }
            
            // 3. 检查 Mod 显示名称是否被排除
            String displayName = getModDisplayName(modId);
            if (!displayName.isEmpty() && BakedConfig.excludedMods.contains(displayName)) {
                return;
            }

            // 4. 检查物品标签 (Tags) 是否被排除 (支持形如 #c:tools 或 #minecraft:enchantable)
            if (!BakedConfig.excludedTags.isEmpty()) {
                boolean tagExcluded = stack.getTags().anyMatch(tag -> {
                    String tagPath = tag.location().toString();
                    return BakedConfig.excludedTags.contains(tagPath) || BakedConfig.excludedTags.contains("#" + tagPath);
                });
                if (tagExcluded) {
                    return;
                }
            }
        }
        
        // 捕获本地上下文并提前退出
        ConfigContext ctx = currentContext;
        if (ctx == null) {
            ctx = ConfigContext.capture();
            currentContext = ctx;
        }
        if (!ctx.hasAnyFilter() && !BakedConfig.debugMode) return;

        List<Component> toolTip = event.getToolTip();
        if (toolTip.size() < 2) return;

        if (BakedConfig.debugMode) {
            List<Component> debugLinesToAdd = new ArrayList<>();
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            debugLinesToAdd.add(Component.literal("§6").append(Component.translatable("gui.enchantmenttooltipcleaner.debug.tooltip.item", itemId)));
            debugLinesToAdd.add(Component.literal("§8").append(Component.translatable("gui.enchantmenttooltipcleaner.debug.tooltip.copy_hint")));

            for (int i = 0; i < toolTip.size(); i++) {
                Component component = toolTip.get(i);
                List<String> keys = new ArrayList<>();
                collectTranslatableKeys(component, keys);
                if (!keys.isEmpty()) {
                    debugLinesToAdd.add(Component.literal("§d").append(Component.translatable("gui.enchantmenttooltipcleaner.debug.tooltip.line_keys", i, String.join(", ", keys))));
                }
            }
            toolTip.addAll(debugLinesToAdd);
            return;
        }

        // 提取该物品实际具有的附魔名称集合（用于突破模组自定义附魔渲染格式）
        Set<String> itemEnchantmentNames = Collections.emptySet();
        if (ctx.removeEnch() && (stack.isEnchanted() || stack.getItem() instanceof EnchantedBookItem)) {
            itemEnchantmentNames = new HashSet<>();
            Map<Enchantment, Integer> enchs = EnchantmentHelper.getEnchantments(stack);
            for (Map.Entry<Enchantment, Integer> entry : enchs.entrySet()) {
                Enchantment ench = entry.getKey();
                int level = entry.getValue();
                // 附魔全名（如 "锋利 V"）
                itemEnchantmentNames.add(ench.getFullname(level).getString().trim());
                // 附魔单名（如 "锋利"）
                itemEnchantmentNames.add(Component.translatable(ench.getDescriptionId()).getString().trim());
            }
        }

        Iterator<Component> iterator = toolTip.iterator();
        if (iterator.hasNext()) iterator.next(); // 跳过标题

        boolean prevLineWasEnchantment = false;

        while (iterator.hasNext()) {
            Component component = iterator.next();
            boolean removeThisLine = false;

            // 1. 基于 Key 与组件结构的检查
            if (shouldRemoveRecursively(component, ctx)) {
                removeThisLine = true;
                prevLineWasEnchantment = true;
            }

            // 2. 基于真实附魔名称的比对 (严密比对，杜绝包含'力量'/'Power'误杀正常Lore)
            if (!removeThisLine && ctx.removeEnch() && !itemEnchantmentNames.isEmpty()) {
                String lineStr = ChatFormatting.stripFormatting(component.getString());
                if (lineStr != null && !lineStr.isEmpty()) {
                    String trimmedLine = lineStr.trim();
                    // 去除行首可能存在的修饰符号（如 ★, ◆, *, -, •, [+] 等）
                    String cleanEnchLine = trimmedLine.replaceFirst("^[\\s*★◆■•\\-\\[\\]+]+", "").trim();
                    for (String enchName : itemEnchantmentNames) {
                        if (cleanEnchLine.equals(enchName) || cleanEnchLine.startsWith(enchName + " ") || cleanEnchLine.startsWith(enchName + "§")) {
                            removeThisLine = true;
                            prevLineWasEnchantment = true;
                            break;
                        }
                    }
                }
            }

            // 检查该行是否为属性修饰符段落或Lore，避免被附魔说明误删
            boolean isAttributeOrLore = false;
            String plainText = ChatFormatting.stripFormatting(component.getString());
            if (plainText != null) {
                String trimmed = plainText.trim();
                if (trimmed.startsWith("When in ") || trimmed.startsWith("在主手时") || trimmed.startsWith("在副手时") ||
                    trimmed.startsWith("在躯干时") || trimmed.startsWith("在脚部时") || trimmed.startsWith("在头部时") ||
                    trimmed.startsWith("在腿部时") || trimmed.contains("攻击伤害") || trimmed.contains("攻击速度") ||
                    trimmed.contains("Attack Damage") || trimmed.contains("Attack Speed")) {
                    isAttributeOrLore = true;
                }
            }

            // 3. 附魔说明（Descriptions）检查
            if (!removeThisLine && ctx.removeDesc() && !isAttributeOrLore) {
                TextColor color = component.getStyle().getColor();
                boolean isGray = color != null && (color.equals(GRAY_TEXT_COLOR) || color.equals(DARK_GRAY_TEXT_COLOR));
                String rawText = component.getString();
                String plain = ChatFormatting.stripFormatting(rawText);
                
                if (plain != null) {
                    boolean hasIndent = plain.startsWith(" ") || plain.startsWith("\t") || plain.startsWith("- ") || plain.startsWith("— ");
                    if ((prevLineWasEnchantment && isGray) || (prevLineWasEnchantment && hasIndent)) {
                        removeThisLine = true;
                    }
                }
            }

            // 4. 药水占位符/未格式化清理 (兼容 Jade 实体药水 HUD 遗留格式如 "%s %s (%s)" 或 "%s (%s)")
            if (!removeThisLine && ctx.removePot()) {
                String rawStr = component.getString();
                if (rawStr != null) {
                    String clean = ChatFormatting.stripFormatting(rawStr).trim();
                    if (clean.equals("%s %s (%s)") || clean.equals("%s (%s)") || clean.startsWith("%s %s") || clean.startsWith("%s (")) {
                        removeThisLine = true;
                    }
                }
            }

            // 状态机重置：若未删除该行，或该行是属性修饰符，立即切断 prevLineWasEnchantment
            if (!removeThisLine || isAttributeOrLore) {
                prevLineWasEnchantment = false;
            }
            
            // 5. 基于用户自定义文本内容的检查
            if (!removeThisLine && ctx.needsTextInspection()) {
                String text = component.getString();
                if (text != null && !text.isEmpty()) {
                    if (ctx.textExactActive() && ctx.customLinesEx().contains(text)) {
                        removeThisLine = true;
                    } else {
                        String noColorText = ChatFormatting.stripFormatting(text);
                        String cleanText = (noColorText != null)
                            ? WHITESPACE_PATTERN.matcher(noColorText).replaceAll(" ").trim()
                            : text.trim();
                        
                        if (ctx.textExactActive() && ctx.customLinesEx().contains(cleanText)) {
                            removeThisLine = true;
                        }
                        if (!removeThisLine && ctx.textContActive()) {
                            for (String s : ctx.customLinesCont()) {
                                if (cleanText.contains(s) || text.contains(s)) {
                                    removeThisLine = true;
                                    break;
                                }
                            }
                        }
                        if (!removeThisLine && ctx.textRegexActive() && !ctx.regexPats().isEmpty()) {
                            TimeoutCharSequence safeSeq = new TimeoutCharSequence(cleanText, 25L);
                            for (Pattern p : ctx.regexPats()) {
                                try {
                                    if (p.matcher(safeSeq).find()) {
                                        removeThisLine = true;
                                        break;
                                    }
                                } catch (TimeoutCharSequence.RegexTimeoutException e) {
                                    EnchantmentTooltipCleanerMod.LOGGER.warn("[EnchantmentTooltipCleaner] ReDoS defense triggered: regex matching exceeded the timeout (>25ms) and was aborted: {}", p.pattern());
                                    break;
                                } catch (Exception ignored) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (removeThisLine) {
                iterator.remove();
            }
        }
    }

    // --- 容器界面悬浮窗渲染与事件监听 ---

    private static Object emiExclusionProxy = null;
    private static boolean emiChecked = false;
    private static int lastSyncedEmiX = -999;
    private static int lastSyncedEmiY = -999;
    private static boolean lastSyncedEmiVisible = false;

    private static Field hoveredSlotField = null;
    private static boolean hoveredSlotFieldChecked = false;

    public static void clearHoveredSlot(AbstractContainerScreen<?> screen) {
        if (screen == null) return;
        if (!hoveredSlotFieldChecked) {
            hoveredSlotFieldChecked = true;
            for (String fieldName : new String[]{"hoveredSlot", "f_97734_", "field_2787"}) {
                try {
                    Field f = AbstractContainerScreen.class.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    hoveredSlotField = f;
                    break;
                } catch (Throwable ignored) {}
            }
        }
        if (hoveredSlotField != null) {
            try {
                hoveredSlotField.set(screen, null);
            } catch (Throwable ignored) {}
        }
    }

    public static void triggerEmiRecalculate() {
        try {
            if (ModList.get().isLoaded("emi")) {
                Class<?> emiScreenManagerClass = Class.forName("dev.emi.emi.screen.EmiScreenManager");
                Method forceRecalculate = emiScreenManagerClass.getMethod("forceRecalculate");
                forceRecalculate.invoke(null);
            }
        } catch (Throwable ignored) {}
    }

    public static void checkAndSyncEmiExclusion() {
        boolean curVis = DraggableOverlayPanel.isVisible;
        int curX = DraggableOverlayPanel.getPanelX();
        int curY = DraggableOverlayPanel.getPanelY();
        if (curVis != lastSyncedEmiVisible || curX != lastSyncedEmiX || curY != lastSyncedEmiY) {
            lastSyncedEmiVisible = curVis;
            lastSyncedEmiX = curX;
            lastSyncedEmiY = curY;
            triggerEmiRecalculate();
        }
    }

    @SuppressWarnings("unchecked")
    public static void tryRegisterEmiExclusion() {
        if (emiChecked && emiExclusionProxy == null) return;
        try {
            if (!ModList.get().isLoaded("emi")) {
                emiChecked = true;
                return;
            }
            Class<?> emiExclusionAreasClass = Class.forName("dev.emi.emi.registry.EmiExclusionAreas");
            Field genericField = emiExclusionAreasClass.getField("generic");
            List<Object> genericList = (List<Object>) genericField.get(null);
            if (genericList == null) return;

            if (emiExclusionProxy == null) {
                Class<?> emiExclusionAreaInterface = Class.forName("dev.emi.emi.api.EmiExclusionArea");
                Class<?> boundsClass = Class.forName("dev.emi.emi.api.widget.Bounds");
                Constructor<?> boundsCtor = boundsClass.getConstructor(int.class, int.class, int.class, int.class);

                emiExclusionProxy = Proxy.newProxyInstance(
                    emiExclusionAreaInterface.getClassLoader(),
                    new Class<?>[]{emiExclusionAreaInterface},
                    (proxy, method, args) -> {
                        String mName = method.getName();
                        if ("addExclusionArea".equals(mName) && args != null && args.length >= 2) {
                            if (DraggableOverlayPanel.isVisible && DraggableOverlayPanel.getPanelX() >= 0) {
                                Consumer<Object> consumer = (Consumer<Object>) args[1];
                                Object bounds = boundsCtor.newInstance(
                                    DraggableOverlayPanel.getPanelX(),
                                    DraggableOverlayPanel.getPanelY(),
                                    DraggableOverlayPanel.PANEL_WIDTH,
                                    DraggableOverlayPanel.PANEL_HEIGHT
                                );
                                consumer.accept(bounds);
                            }
                            return null;
                        }
                        if ("equals".equals(mName) && args != null && args.length == 1) {
                            return proxy == args[0];
                        }
                        if ("hashCode".equals(mName)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("toString".equals(mName)) {
                            return "DraggableOverlayPanelEmiExclusionArea";
                        }
                        return null;
                    }
                );
            }

            if (!genericList.contains(emiExclusionProxy)) {
                genericList.add(emiExclusionProxy);
                triggerEmiRecalculate();
            }
            emiChecked = true;
        } catch (Throwable ignored) {
            emiChecked = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            DraggableOverlayPanel.ensurePosition(containerScreen.width, containerScreen.height,
                containerScreen.getGuiLeft(), containerScreen.getGuiTop(), containerScreen.getXSize());
            tryRegisterEmiExclusion();
            if (DraggableOverlayPanel.isVisible) {
                triggerEmiRecalculate();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            DraggableOverlayPanel.updateMousePosition(event.getMouseX(), event.getMouseY());
            DraggableOverlayPanel.ensurePosition(containerScreen.width, containerScreen.height,
                containerScreen.getGuiLeft(), containerScreen.getGuiTop(), containerScreen.getXSize());
            tryRegisterEmiExclusion();
            checkAndSyncEmiExclusion();
            if (DraggableOverlayPanel.isVisible && DraggableOverlayPanel.isMouseOverPanel(event.getMouseX(), event.getMouseY())) {
                clearHoveredSlot(containerScreen);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            if (DraggableOverlayPanel.isVisible && DraggableOverlayPanel.isMouseOverPanel(event.getMouseX(), event.getMouseY())) {
                clearHoveredSlot(containerScreen);
            } else {
                DraggableOverlayPanel.updateHoveredSlot(containerScreen.getSlotUnderMouse());
            }
            // 仅渲染悬浮面板，彻底移除背包上的齿轮图标
            DraggableOverlayPanel.renderPanel(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), containerScreen);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        // 核心守卫：判断当前玩家实际鼠标位置是否处于悬浮面板区域内，而非检查被偏移后的提示框顶点坐标！
        if (DraggableOverlayPanel.isVisible && DraggableOverlayPanel.isMouseOverPanel()) {
            if (!DraggableOverlayPanel.isRenderingOurOwnTooltip) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            DraggableOverlayPanel.ensurePosition(containerScreen.width, containerScreen.height,
                containerScreen.getGuiLeft(), containerScreen.getGuiTop(), containerScreen.getXSize());
            if (DraggableOverlayPanel.isVisible && DraggableOverlayPanel.isMouseOverPanel(event.getMouseX(), event.getMouseY())) {
                DraggableOverlayPanel.mouseScrolled(event.getMouseX(), event.getMouseY());
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            DraggableOverlayPanel.ensurePosition(containerScreen.width, containerScreen.height,
                containerScreen.getGuiLeft(), containerScreen.getGuiTop(), containerScreen.getXSize());
            if (DraggableOverlayPanel.isVisible && DraggableOverlayPanel.isMouseOverPanel(event.getMouseX(), event.getMouseY())) {
                DraggableOverlayPanel.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton(), containerScreen);
                clearHoveredSlot(containerScreen);
                event.setCanceled(true);
                return;
            }

            // Ctrl + 左键点击槽位：复制物品信息与调试翻译键并弹出 Action Bar 提示
            if (event.getButton() == 0 && Screen.hasControlDown()) {
                net.minecraft.world.inventory.Slot slot = containerScreen.getSlotUnderMouse();
                if (slot != null && slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    copyItemDebugInfo(stack);
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            DraggableOverlayPanel.ensurePosition(containerScreen.width, containerScreen.height,
                containerScreen.getGuiLeft(), containerScreen.getGuiTop(), containerScreen.getXSize());
            if (DraggableOverlayPanel.isVisible && (DraggableOverlayPanel.isDragging() || DraggableOverlayPanel.isMouseOverPanel(event.getMouseX(), event.getMouseY()))) {
                boolean wasDragging = DraggableOverlayPanel.isDragging();
                DraggableOverlayPanel.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton());
                if (wasDragging) {
                    checkAndSyncEmiExclusion();
                }
                clearHoveredSlot(containerScreen);
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            DraggableOverlayPanel.ensurePosition(containerScreen.width, containerScreen.height,
                containerScreen.getGuiLeft(), containerScreen.getGuiTop(), containerScreen.getXSize());
            if (DraggableOverlayPanel.isVisible && (DraggableOverlayPanel.isDragging() || DraggableOverlayPanel.isMouseOverPanel(event.getMouseX(), event.getMouseY()))) {
                DraggableOverlayPanel.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY());
                clearHoveredSlot(containerScreen);
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            // 用户正在搜索框/文本框内打字时，不拦截任何快捷键，保证字符正常上屏
            if (containerScreen.getFocused() != null && containerScreen.getFocused().isFocused()) {
                return;
            }
            if (ModKeyBindings.TOGGLE_OVERLAY_KEY.matches(event.getKeyCode(), event.getScanCode())) {
                DraggableOverlayPanel.toggleVisibility();
                checkAndSyncEmiExclusion();
                event.setCanceled(true);
                return;
            }
            if (DraggableOverlayPanel.handleKeyPressed(event.getKeyCode(), event.getScanCode(), containerScreen)) {
                event.setCanceled(true);
                return;
            }
            // 当鼠标位于悬浮面板内部时，如果按下的键是数字键(1-9)、丢弃键(Q)等，必须拦截并清除 hoveredSlot，杜绝触发背景物品槽位快捷操作！
            if (DraggableOverlayPanel.isVisible && DraggableOverlayPanel.isMouseOverPanel()) {
                clearHoveredSlot(containerScreen);
                Minecraft mc = Minecraft.getInstance();
                if (mc.options.keyDrop.matches(event.getKeyCode(), event.getScanCode())) {
                    event.setCanceled(true);
                    return;
                }
                for (var hotbarKey : mc.options.keyHotbarSlots) {
                    if (hotbarKey.matches(event.getKeyCode(), event.getScanCode())) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }
    }
}