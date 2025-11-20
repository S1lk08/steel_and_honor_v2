package com.steelandhonor.modid.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.item.ItemStack

class BannerPreviewWidget(
    x: Int, y: Int,
    private val banner: ItemStack
) : ClickableWidget(x, y, 20, 40, banner.name) {

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (!banner.isEmpty) {
            val itemRenderer = MinecraftClient.getInstance().itemRenderer
            itemRenderer.renderInGui(context, banner, x, y)
        }
        super.render(context, mouseX, mouseY, delta)
    }
}