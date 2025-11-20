package com.steelandhonor.modid.client.city

import com.steelandhonor.modid.network.KingdomNetworking
import com.steelandhonor.modid.util.DyeColorUtils
import net.minecraft.util.DyeColor
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.UUID

object ClientCityData {
    data class City(
        val id: UUID,
        val name: String,
        val dimension: Identifier,
        val center: BlockPos,
        val radius: Int,
        val primaryColor: FloatArray,
        val kingdomColor: FloatArray,
        val kingdomName: String,
        val capital: Boolean,
        val minChunkX: Int,
        val maxChunkX: Int,
        val minChunkZ: Int,
        val maxChunkZ: Int,
        val minRegionX: Int,
        val maxRegionX: Int,
        val minRegionZ: Int,
        val maxRegionZ: Int
    )

    private val citiesByDimension: MutableMap<Identifier, MutableList<City>> = mutableMapOf()

    @Synchronized
    fun update(snapshots: List<KingdomNetworking.CityBorderData>) {
        citiesByDimension.clear()
        snapshots.forEach { snapshot ->
            val dimension = snapshot.dimension
            val radius = snapshot.radius
            val center = snapshot.center
            val minChunkX = snapshot.minChunkX
            val maxChunkX = snapshot.maxChunkX
            val minChunkZ = snapshot.minChunkZ
            val maxChunkZ = snapshot.maxChunkZ
            val minRegionX = minChunkX shr 5
            val maxRegionX = maxChunkX shr 5
            val minRegionZ = minChunkZ shr 5
            val maxRegionZ = maxChunkZ shr 5
            val dye = DyeColor.byId(snapshot.colorId) ?: DyeColor.WHITE
            val colorComponents = DyeColorUtils.componentsFor(dye)
            val kingdomColor = colorComponents.copyOf()
            val cityColor = if (snapshot.capital) floatArrayOf(1f, 1f, 0f) else floatArrayOf(1f, 1f, 1f)
            val city = City(
                id = snapshot.id,
                name = snapshot.name,
                dimension = dimension,
                center = center,
                radius = radius,
                primaryColor = cityColor,
                kingdomColor = kingdomColor,
                kingdomName = snapshot.kingdomName,
                capital = snapshot.capital,
                minChunkX = minChunkX,
                maxChunkX = maxChunkX,
                minChunkZ = minChunkZ,
                maxChunkZ = maxChunkZ,
                minRegionX = minRegionX,
                maxRegionX = maxRegionX,
                minRegionZ = minRegionZ,
                maxRegionZ = maxRegionZ
            )
            citiesByDimension.getOrPut(dimension) { mutableListOf() }.add(city)
        }
    }

    @Synchronized
    fun citiesFor(dimension: Identifier): List<City> {
        return citiesByDimension[dimension]?.toList() ?: emptyList()
    }

    @Synchronized
    fun allCities(): List<City> = citiesByDimension.values.flatten()

    @Synchronized
    fun hasCityInRegion(dimension: Identifier, regionX: Int, regionZ: Int): Boolean {
        val list = citiesByDimension[dimension] ?: return false
        return list.any {
            regionX in it.minRegionX..it.maxRegionX && regionZ in it.minRegionZ..it.maxRegionZ
        }
    }

    @Synchronized
    fun cityAt(dimension: Identifier, chunkX: Int, chunkZ: Int): City? {
        val list = citiesByDimension[dimension] ?: return null
        return list.firstOrNull {
            chunkX in it.minChunkX..it.maxChunkX &&
                chunkZ in it.minChunkZ..it.maxChunkZ
        }
    }

}
