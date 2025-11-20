package com.steelandhonor.modid.client.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.item.ItemStack

class BannerPreviewWidget(
    x: Int, y: Int,
    private val banner: ItemStack
) : ClickableWidget(x, y, 20, 40, banner.name) {

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (!banner.isEmpty) {
            context.drawItem(banner, x, y)
        }
    }
}
