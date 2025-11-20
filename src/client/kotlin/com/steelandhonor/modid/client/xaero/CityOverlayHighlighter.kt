package com.steelandhonor.modid.client.xaero

import com.steelandhonor.modid.client.city.ClientCityData
import net.minecraft.registry.RegistryKey
import net.minecraft.text.Text
import net.minecraft.world.World
import xaero.map.highlight.ChunkHighlighter
import kotlin.math.roundToInt

class CityOverlayHighlighter : ChunkHighlighter(true) {
    override fun calculateRegionHash(
        dimension: RegistryKey<World>,
        regionX: Int,
        regionZ: Int
    ): Int {
        val regionMinChunkX = regionX shl 5
        val regionMinChunkZ = regionZ shl 5
        val regionMaxChunkX = regionMinChunkX + 31
        val regionMaxChunkZ = regionMinChunkZ + 31
        val cities = ClientCityData.citiesFor(dimension.value)
        if (cities.isEmpty()) return 0
        var hash = 31 * dimension.hashCode() + regionX
        hash = 31 * hash + regionZ
        cities.forEach { city ->
            if (intersects(regionMinChunkX, regionMaxChunkX, regionMinChunkZ, regionMaxChunkZ, city)) {
                hash = 31 * hash + city.id.hashCode()
                hash = 31 * hash + city.minChunkX
                hash = 31 * hash + city.minChunkZ
            }
        }
        return hash
    }

    override fun regionHasHighlights(
        dimension: RegistryKey<World>,
        regionX: Int,
        regionZ: Int
    ): Boolean {
        val regionMinChunkX = regionX shl 5
        val regionMinChunkZ = regionZ shl 5
        val regionMaxChunkX = regionMinChunkX + 31
        val regionMaxChunkZ = regionMinChunkZ + 31
        return ClientCityData.citiesFor(dimension.value).any {
            intersects(regionMinChunkX, regionMaxChunkX, regionMinChunkZ, regionMaxChunkZ, it)
        }
    }

    override fun chunkIsHighlit(
        dimension: RegistryKey<World>,
        chunkX: Int,
        chunkZ: Int
    ): Boolean {
        return ClientCityData.cityAt(dimension.value, chunkX, chunkZ) != null
    }

    override fun getChunkHighlightSubtleTooltip(
        dimension: RegistryKey<World>,
        chunkX: Int,
        chunkZ: Int
    ): Text? {
        val city = ClientCityData.cityAt(dimension.value, chunkX, chunkZ) ?: return null
        return Text.literal(city.name)
    }

    override fun getChunkHighlightBluntTooltip(
        dimension: RegistryKey<World>,
        chunkX: Int,
        chunkZ: Int
    ): Text? {
        val city = ClientCityData.cityAt(dimension.value, chunkX, chunkZ) ?: return null
        val label = "${city.name} (${city.kingdomName})"
        return Text.literal(label)
    }

    override fun getBlockHighlightSubtleTooltip(
        dimension: RegistryKey<World>,
        blockX: Int,
        blockZ: Int
    ): Text? {
        val city = ClientCityData.cityAt(dimension.value, blockX shr 4, blockZ shr 4) ?: return null
        return Text.literal(city.name)
    }

    override fun getBlockHighlightBluntTooltip(
        dimension: RegistryKey<World>,
        blockX: Int,
        blockZ: Int
    ): Text? {
        val city = ClientCityData.cityAt(dimension.value, blockX shr 4, blockZ shr 4) ?: return null
        return Text.literal("${city.kingdomName} Wilderness")
    }

    override fun addMinimapBlockHighlightTooltips(
        tooltips: MutableList<Text>,
        dimension: RegistryKey<World>,
        blockX: Int,
        blockZ: Int,
        y: Int
    ) {
        val city = ClientCityData.cityAt(dimension.value, blockX shr 4, blockZ shr 4) ?: return
        tooltips.clear()
        tooltips.add(Text.literal(city.name))
        tooltips.add(Text.literal("${city.kingdomName} Wilderness"))
    }

    override fun getColors(
        dimension: RegistryKey<World>,
        chunkX: Int,
        chunkZ: Int
    ): IntArray? {
        val city = ClientCityData.cityAt(dimension.value, chunkX, chunkZ) ?: return null
        val fill = colorFrom(city.kingdomColor, if (city.capital) 0xB0 else 0x80)
        val accent = colorFrom(city.primaryColor, 0xFF)
        val highlight = if (isEdgeChunk(city, chunkX, chunkZ)) accent else fill
        resultStore[0] = fill
        resultStore[1] = highlight
        resultStore[2] = highlight
        resultStore[3] = highlight
        resultStore[4] = highlight
        return resultStore
    }

    private fun isEdgeChunk(city: ClientCityData.City, chunkX: Int, chunkZ: Int): Boolean {
        return chunkX == city.minChunkX || chunkX == city.maxChunkX || chunkZ == city.minChunkZ || chunkZ == city.maxChunkZ
    }

    private fun colorFrom(rgb: FloatArray, alpha: Int): Int {
        val r = toChannel(rgb.componentOr(0))
        val g = toChannel(rgb.componentOr(1))
        val b = toChannel(rgb.componentOr(2))
        val clampedAlpha = alpha.coerceIn(0, 255)
        return (clampedAlpha shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun toChannel(value: Float): Int {
        return (value.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    }

    private fun FloatArray.componentOr(index: Int, fallback: Float = 1f): Float {
        return if (index in indices) this[index] else fallback
    }
}
    private fun intersects(
        regionMinX: Int,
        regionMaxX: Int,
        regionMinZ: Int,
        regionMaxZ: Int,
        city: ClientCityData.City
    ): Boolean {
        return city.minChunkX <= regionMaxX &&
            city.maxChunkX >= regionMinX &&
            city.minChunkZ <= regionMaxZ &&
            city.maxChunkZ >= regionMinZ
    }
