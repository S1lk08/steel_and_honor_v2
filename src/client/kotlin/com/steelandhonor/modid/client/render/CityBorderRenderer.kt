package com.steelandhonor.modid.client.render

import com.steelandhonor.modid.client.city.ClientCityData
import com.steelandhonor.modid.network.KingdomNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.particle.DustColorTransitionParticleEffect
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.random.Random
import net.minecraft.world.Heightmap
import org.joml.Vector3f

object CityBorderRenderer {
    private const val COLUMN_HEIGHT = 40.0
    private const val COLUMN_SEGMENTS = 24
    private const val EDGE_STEP = 4.0
    private val CAPITAL_COLOR = floatArrayOf(1f, 1f, 0f)
    private val CITY_COLOR = floatArrayOf(1f, 1f, 1f)

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val world = client.world ?: return@register
            val time = world.time + getTickDelta(client)
            spawnParticles(world, time)
        }
    }

    private fun getTickDelta(client: MinecraftClient): Double {
        return runCatching {
            val field = client::class.java.getDeclaredField("renderTickCounter")
            field.isAccessible = true
            val counter = field.get(client)
            val tickField = counter.javaClass.getDeclaredField("tickDelta")
            tickField.isAccessible = true
            (tickField.get(counter) as? Float)?.toDouble()
        }.getOrElse { 0.0 } ?: 0.0
    }

    fun update(cities: List<KingdomNetworking.CityBorderData>) {
        ClientCityData.update(cities)
    }

    private fun spawnParticles(world: ClientWorld, time: Double) {
        val cameraDim = world.registryKey.value
        val random = world.random
        ClientCityData.citiesFor(cameraDim).forEach { border ->
            spawnPerimeter(world, border, random)
        }
    }

    private fun spawnPerimeter(world: ClientWorld, border: ClientCityData.City, random: Random) {
        val minX = border.minChunkX * 16.0
        val maxX = (border.maxChunkX + 1) * 16.0
        val minZ = border.minChunkZ * 16.0
        val maxZ = (border.maxChunkZ + 1) * 16.0
        drawBorder(world, border, minX, maxX, minZ, maxZ, border.kingdomColor, random, 0.0, 0.0)
        val cityColor = if (border.capital) CAPITAL_COLOR else CITY_COLOR
        drawBorder(world, border, minX, maxX, minZ, maxZ, cityColor, random, 0.6, 0.02)
    }

    private fun drawBorder(
        world: ClientWorld,
        border: ClientCityData.City,
        startX: Double,
        endX: Double,
        startZ: Double,
        endZ: Double,
        color: FloatArray,
        random: Random,
        inset: Double,
        swirlSpeed: Double
    ) {
        val minX = startX + inset
        val maxX = endX - inset
        val minZ = startZ + inset
        val maxZ = endZ - inset
        if (minX >= maxX || minZ >= maxZ) return
        spawnEdge(world, border, minX, maxX, minZ, true, color, random, swirlSpeed, true)
        spawnEdge(world, border, minX, maxX, maxZ, true, color, random, swirlSpeed, false)
        spawnEdge(world, border, minZ, maxZ, minX, false, color, random, swirlSpeed, true)
        spawnEdge(world, border, minZ, maxZ, maxX, false, color, random, swirlSpeed, false)
    }

    private fun spawnEdge(
        world: ClientWorld,
        border: ClientCityData.City,
        start: Double,
        end: Double,
        fixed: Double,
        horizontal: Boolean,
        color: FloatArray,
        random: Random,
        swirlSpeed: Double,
        positiveDirection: Boolean
    ) {
        var pos = start
        val direction = if (positiveDirection) 1 else -1
        val velocityX = if (horizontal) swirlSpeed * direction else 0.0
        val velocityZ = if (!horizontal) swirlSpeed * direction else 0.0
        while (pos <= end + 0.001) {
            val x = if (horizontal) pos else fixed
            val z = if (horizontal) fixed else pos
            spawnColumn(world, border, x + 0.5, z + 0.5, color, random, velocityX, velocityZ)
            pos += EDGE_STEP
        }
    }

    private fun spawnColumn(
        world: ClientWorld,
        border: ClientCityData.City,
        x: Double,
        z: Double,
        color: FloatArray,
        random: Random,
        velocityX: Double,
        velocityZ: Double
    ) {
        val groundY = getGroundY(world, x, z, border.center.y.toDouble())
        val particle = glowParticle(color, if (velocityX != 0.0 || velocityZ != 0.0) 0.85f else 1.0f)
        for (segment in 0..COLUMN_SEGMENTS) {
            val height = groundY + (segment / COLUMN_SEGMENTS.toDouble()) * COLUMN_HEIGHT
            world.addParticle(
                particle,
                x,
                height,
                z,
                velocityX,
                0.01 + random.nextDouble() * 0.01,
                velocityZ
            )
        }
    }

    private fun getGroundY(world: ClientWorld, x: Double, z: Double, fallback: Double): Double {
        val blockX = MathHelper.floor(x)
        val blockZ = MathHelper.floor(z)
        val chunkX = blockX shr 4
        val chunkZ = blockZ shr 4
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return fallback
        }
        val topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ)
        return topY.coerceAtLeast(world.bottomY).toDouble() + 0.1
    }

    private fun glowParticle(color: FloatArray, scale: Float): DustColorTransitionParticleEffect {
        val start = Vector3f(color[0], color[1], color[2])
        val end = Vector3f((color[0] + 0.2f).coerceAtMost(1f), (color[1] + 0.2f).coerceAtMost(1f), (color[2] + 0.2f).coerceAtMost(1f))
        return DustColorTransitionParticleEffect(start, end, scale)
    }

}
