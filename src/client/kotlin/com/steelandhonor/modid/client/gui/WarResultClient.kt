package com.steelandhonor.modid.client.gui

import com.steelandhonor.modid.network.KingdomNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.particle.ParticleTypes
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.text.Text
import kotlin.random.Random

object WarResultClient {
    private const val CELEBRATION_TICKS = 20 * 5 // ~5 seconds

    private var pendingResult: KingdomNetworking.WarResultEntry? = null
    private var celebrationTicks: Int = 0
    private val random = Random.Default

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            tick(client)
        }
    }

    fun onWarResult(result: KingdomNetworking.WarResultEntry) {
        val client = MinecraftClient.getInstance() ?: return

        client.execute {
            pendingResult = result
            celebrationTicks = CELEBRATION_TICKS

            val player = client.player ?: return@execute

            // Goat horn sound
            val horn = SoundEvent.of(Identifier.of("minecraft", "item.goat_horn.sound.0"))
            player.playSound(horn, 1f, 1f)

            // Big title
            val winnerText = when (result.winnerSide) {
                1 -> Text.literal("${result.attackerName} Wins!")
                2 -> Text.literal("${result.defenderName} Wins!")
                else -> Text.literal("War Drawn")
            }
            val subtitle = Text.literal("${result.attackerName} vs ${result.defenderName}")

            client.inGameHud.setTitle(winnerText)
            client.inGameHud.setSubtitle(subtitle)
        }
    }

    private fun tick(client: MinecraftClient) {
        if (celebrationTicks <= 0) return
        val player = client.player ?: return
        val world = client.world ?: return

        celebrationTicks--

        // Particle fireworks ring
        repeat(12) {
            val angle = random.nextDouble() * Math.PI * 2.0
            val radius = 1.5
            val dx = Math.cos(angle) * radius
            val dz = Math.sin(angle) * radius
            val x = player.x + dx
            val y = player.y + 0.5 + random.nextDouble() * 0.8
            val z = player.z + dz
            world.addParticle(
                ParticleTypes.FIREWORK,
                x, y, z,
                0.0, 0.02, 0.0
            )
        }

        if (celebrationTicks == 0) {
            // Clear title and open result screen
            client.inGameHud.setTitle(Text.empty())
            client.inGameHud.setSubtitle(Text.empty())

            val result = pendingResult ?: return
            pendingResult = null
            client.setScreen(WarResultScreen(result))
        }
    }
}
