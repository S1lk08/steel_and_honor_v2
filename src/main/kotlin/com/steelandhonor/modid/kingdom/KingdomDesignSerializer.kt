package com.steelandhonor.modid.kingdom

import net.minecraft.network.PacketByteBuf
import net.minecraft.util.DyeColor
import net.minecraft.util.Identifier

object KingdomDesignSerializer {
    fun write(buf: PacketByteBuf, design: KingdomDesign) {
        buf.writeVarInt(design.primaryColor.id)
        buf.writeVarInt(design.accentColor.id)
        buf.writeVarInt(design.layers.size)
        design.layers.forEach {
            buf.writeIdentifier(it.patternId)
            buf.writeVarInt(it.color.id)
        }
    }

    fun read(buf: PacketByteBuf): KingdomDesign {
        val primary = DyeColor.byId(buf.readVarInt()) ?: DyeColor.WHITE
        val accent = DyeColor.byId(buf.readVarInt()) ?: DyeColor.BLACK
        val count = buf.readVarInt()
        val layers = mutableListOf<BannerLayer>()
        repeat(count) {
            val pattern = buf.readIdentifier()
            val color = DyeColor.byId(buf.readVarInt()) ?: accent
            layers.add(BannerLayer(pattern, color))
        }
        return KingdomDesign(primary, accent, layers)
    }
}
