package com.steelandhonor.modid.client.input

import com.steelandhonor.modid.client.gui.KingdomMenuScreen
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW

object SteelKeyBindings {
    lateinit var openMenu: KeyBinding
        private set

    fun register() {
        openMenu = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.steel_and_honor.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "category.steel_and_honor"
            )
        )
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.isPaused) return@register
            while (openMenu.wasPressed()) {
                if (client.currentScreen !is KingdomMenuScreen) {
                    client.setScreen(KingdomMenuScreen())
                }
            }
        }
    }
}
