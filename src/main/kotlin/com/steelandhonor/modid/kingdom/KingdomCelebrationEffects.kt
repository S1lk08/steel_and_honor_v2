package com.steelandhonor.modid.kingdom

import com.steelandhonor.modid.util.DyeColorUtils
import com.steelandhonor.modid.util.SoundUtil
import net.minecraft.particle.DustColorTransitionParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.DyeColor
import org.joml.Vector3f

object KingdomCelebrationEffects {
    fun realmFounded(player: ServerPlayerEntity, color: DyeColor) {
        val world = player.serverWorld
        val components = DyeColorUtils.componentsFor(color)
        val accent = Vector3f(1f, 0.92f, 0.7f)
        val swirl = DustColorTransitionParticleEffect(
            Vector3f(components[0], components[1], components[2]),
            accent,
            1.15f
        )
        val pos = player.pos
        world.spawnParticles(swirl, pos.x, pos.y + 1.2, pos.z, 120, 1.4, 0.9, 1.4, 0.02)
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y + 1.4, pos.z, 6, 0.4, 0.4, 0.4, 0.0)
        world.playSound(
            player,
            pos.x,
            pos.y,
            pos.z,
            SoundUtil.resolve(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE),
            SoundCategory.PLAYERS,
            1.0f,
            1.1f
        )
    }

    fun warDeclared(server: MinecraftServer, attacker: KingdomData, defender: KingdomData) {
        blastFor(server, attacker, 0.92f)
        blastFor(server, defender, 1.1f)
    }

    private fun blastFor(server: MinecraftServer, kingdom: KingdomData, pitch: Float) {
        val components = DyeColorUtils.componentsFor(kingdom.color)
        val base = Vector3f(components[0], components[1], components[2])
        val accent = Vector3f(
            (components[0] + 0.2f).coerceAtMost(1f),
            (components[1] + 0.2f).coerceAtMost(1f),
            (components[2] + 0.2f).coerceAtMost(1f)
        )
        val dust = DustColorTransitionParticleEffect(base, accent, 1.05f)
        kingdom.members
            .mapNotNull { server.playerManager.getPlayer(it) }
            .forEach { player ->
                val world = player.serverWorld
                val pos = player.pos
                world.spawnParticles(dust, pos.x, pos.y + 1.0, pos.z, 90, 0.8, 0.7, 0.8, 0.015)
                world.spawnParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 1.4, pos.z, 1, 0.0, 0.0, 0.0, 0.0)
                world.playSound(
                    player,
                    pos.x,
                    pos.y,
                    pos.z,
                    SoundUtil.resolve(SoundEvents.EVENT_RAID_HORN),
                    SoundCategory.PLAYERS,
                    1.2f,
                    pitch
                )
            }
    }
}
