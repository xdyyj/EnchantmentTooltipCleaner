package com.xdyyj.enchantmenttooltipcleaner;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static final String KEY_CATEGORY = "key.categories.enchantmenttooltipcleaner";
    public static final String KEY_TOGGLE_OVERLAY = "key.enchantmenttooltipcleaner.toggle_overlay";

    public static final KeyMapping TOGGLE_OVERLAY_KEY = new KeyMapping(
        KEY_TOGGLE_OVERLAY,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_H,
        KEY_CATEGORY
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_OVERLAY_KEY);
    }
}
