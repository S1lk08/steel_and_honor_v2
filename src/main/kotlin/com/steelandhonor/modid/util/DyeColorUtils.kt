package com.steelandhonor.modid.util

import net.minecraft.util.DyeColor
import kotlin.math.pow

object DyeColorUtils {
    private val COLOR_COMPONENTS: Map<DyeColor, FloatArray> = mapOf(
        DyeColor.WHITE to floatArrayOf(1.0f, 1.0f, 1.0f),
        DyeColor.ORANGE to floatArrayOf(0.85f, 0.5f, 0.2f),
        DyeColor.MAGENTA to floatArrayOf(0.7f, 0.3f, 0.85f),
        DyeColor.LIGHT_BLUE to floatArrayOf(0.4f, 0.6f, 0.85f),
        DyeColor.YELLOW to floatArrayOf(0.9f, 0.9f, 0.2f),
        DyeColor.LIME to floatArrayOf(0.5f, 0.8f, 0.1f),
        DyeColor.PINK to floatArrayOf(0.95f, 0.5f, 0.65f),
        DyeColor.GRAY to floatArrayOf(0.3f, 0.3f, 0.3f),
        DyeColor.LIGHT_GRAY to floatArrayOf(0.6f, 0.6f, 0.6f),
        DyeColor.CYAN to floatArrayOf(0.3f, 0.5f, 0.6f),
        DyeColor.PURPLE to floatArrayOf(0.5f, 0.25f, 0.7f),
        DyeColor.BLUE to floatArrayOf(0.2f, 0.3f, 0.7f),
        DyeColor.BROWN to floatArrayOf(0.4f, 0.3f, 0.2f),
        DyeColor.GREEN to floatArrayOf(0.4f, 0.5f, 0.2f),
        DyeColor.RED to floatArrayOf(0.6f, 0.2f, 0.2f),
        DyeColor.BLACK to floatArrayOf(0.1f, 0.1f, 0.1f)
    )

    private val DEFAULT_COLOR = COLOR_COMPONENTS[DyeColor.WHITE]!!

    fun componentsFor(color: DyeColor): FloatArray {
        return COLOR_COMPONENTS[color] ?: DEFAULT_COLOR
    }

    fun closestDye(rgb: Int): DyeColor {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        return COLOR_COMPONENTS.minBy { (_, components) ->
            distanceSquared(r, g, b, components)
        }.key
    }

    private fun distanceSquared(r: Float, g: Float, b: Float, components: FloatArray): Float {
        return (r - components[0]).pow(2) + (g - components[1]).pow(2) + (b - components[2]).pow(2)
    }
}
