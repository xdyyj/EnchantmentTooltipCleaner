package com.xdyyj.enchantmenttooltipcleaner;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.bus.api.IEventBus;

@Mod("enchantmenttooltipcleaner")
public class EnchantmentTooltipCleanerMod {

    // 统一日志出口 (SLF4J / LogUtils)
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public EnchantmentTooltipCleanerMod(IEventBus modEventBus, ModContainer modContainer) {
        // 1. 注册配置
        modContainer.registerConfig(
            ModConfig.Type.CLIENT, 
            com.xdyyj.enchantmenttooltipcleaner.ModConfig.SPEC, 
            "enchantmenttooltipcleaner-client.toml"
        );

        // 2. 注册配置重载监听器
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);
        
        // 3. 注册按键绑定
        modEventBus.addListener(ModKeyBindings::register);

        // 4. 注册模组列表配置界面拓展点 (IConfigScreenFactory)
        modContainer.registerExtensionPoint(
            net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
            (container, parent) -> new ModConfigScreen(parent)
        );
    }

    // 当配置首次加载时
    public void onConfigLoad(ModConfigEvent.Loading event) {
        ClientEvents.refreshConfig();
    }

    // 当配置被重载时
    public void onConfigReload(ModConfigEvent.Reloading event) {
        ClientEvents.refreshConfig();
    }
}