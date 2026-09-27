package com.xdyyj.enchantmenttooltipcleaner;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("enchantmenttooltipcleaner")
public class EnchantmentTooltipCleanerMod {

    // 统一日志出口 (SLF4J / LogUtils)
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    @SuppressWarnings("removal")
    public EnchantmentTooltipCleanerMod() {
        // 1. 注册配置
        ModLoadingContext.get().registerConfig(
            ModConfig.Type.CLIENT, 
            com.xdyyj.enchantmenttooltipcleaner.ModConfig.SPEC, 
            "enchantmenttooltipcleaner-client.toml"
        );

        // 2. 注册配置重载监听器
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigLoad);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);
        
        // 3. 注册按键绑定
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ModKeyBindings::register);

        // 4. 注册模组列表配置界面拓展点 (ConfigScreenHandler)
        ModLoadingContext.get().registerExtensionPoint(
            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                (mc, parentScreen) -> new ModConfigScreen(parentScreen)
            )
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