package com.steelandhonor.modid.kingdom

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.util.DyeColor
import net.minecraft.util.Identifier

data class BannerLayer(val patternId: Identifier, val color: DyeColor)

data class KingdomDesign(
    val primaryColor: DyeColor,
    val accentColor: DyeColor,
    val layers: List<BannerLayer>
) {
    fun toNbt(): NbtCompound {
        val tag = NbtCompound()
        tag.putString("Primary", primaryColor.getName())
        tag.putString("Accent", accentColor.getName())
        val layerList = NbtList()
        layers.forEach { layer ->
            val entry = NbtCompound()
            entry.putString("Pattern", layer.patternId.toString())
            entry.putString("Color", layer.color.getName())
            layerList.add(entry)
        }
        tag.put("Layers", layerList)
        // Legacy support
        val legacy = NbtList()
        layers.forEach {
            val entry = NbtCompound()
            entry.putString("Pattern", it.patternId.toString())
            legacy.add(entry)
        }
        tag.put("Patterns", legacy)
        return tag
    }

    companion object {
        val DEFAULT_LAYERS: List<BannerLayer> = emptyList()

        val DEFAULT = KingdomDesign(
            primaryColor = DyeColor.WHITE,
            accentColor = DyeColor.WHITE,
            layers = DEFAULT_LAYERS
        )

        fun fromNbt(tag: NbtCompound): KingdomDesign {
            val primary = DyeColor.byName(tag.getString("Primary"), DyeColor.WHITE) ?: DyeColor.WHITE
            val accent = DyeColor.byName(tag.getString("Accent"), DyeColor.BLACK) ?: DyeColor.BLACK
            val layers = mutableListOf<BannerLayer>()
            if (tag.contains("Layers", NbtElement.LIST_TYPE.toInt())) {
                val list = tag.getList("Layers", NbtElement.COMPOUND_TYPE.toInt())
                list.forEach { element ->
                    if (element is NbtCompound && element.contains("Pattern", NbtElement.STRING_TYPE.toInt())) {
                        val pattern = Identifier.tryParse(element.getString("Pattern"))
                        val color = DyeColor.byName(element.getString("Color"), accent) ?: accent
                        if (pattern != null) {
                            layers.add(BannerLayer(pattern, color))
                        }
                    }
                }
            } else if (tag.contains("Patterns", NbtElement.LIST_TYPE.toInt())) {
                val list = tag.getList("Patterns", NbtElement.COMPOUND_TYPE.toInt())
                list.forEach { element ->
                    if (element is NbtCompound && element.contains("Pattern", NbtElement.STRING_TYPE.toInt())) {
                        Identifier.tryParse(element.getString("Pattern"))?.let {
                            layers.add(BannerLayer(it, accent))
                        }
                    }
                }
            }
            return KingdomDesign(primary, accent, layers)
        }
    }
}
