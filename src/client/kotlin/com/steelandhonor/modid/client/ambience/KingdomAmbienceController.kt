package com.steelandhonor.modid.client.ambience

import com.steelandhonor.modid.client.city.ClientCityData
import com.steelandhonor.modid.client.gui.WarHudOverlay
import com.steelandhonor.modid.util.SoundUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.particle.DustColorTransitionParticleEffect
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.math.random.Random
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.roundToInt

object KingdomAmbienceController {
    private const val PANEL_WIDTH = 196
    private const val PANEL_HEIGHT = 44
    private const val FADE_IN_TICKS = 12
    private const val HOLD_TICKS = 70
    private const val FADE_OUT_TICKS = 24
    private const val TOTAL_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS

    private var overlayTicks = 0
    private var cachedCityId: UUID? = null
    private var overlayTitle: Text = Text.empty()
    private var overlaySubtitle: Text = Text.empty()
    private var overlayColor: Int = 0xFFFFFF
    private var previousCityName: String? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            val world = client.world ?: return@register
            val city = ClientCityData.cityAt(world.registryKey.value, player.chunkPos.x, player.chunkPos.z)
            if (city?.id != cachedCityId || (city == null && cachedCityId != null)) {
                onCityChanged(client, world, player, city)
            }
            if (overlayTicks > 0) {
                overlayTicks++
                if (overlayTicks > TOTAL_TICKS) {
                    overlayTicks = 0
                }
            }
        }

        HudRenderCallback.EVENT.register { context, _ ->
            renderOverlay(context)
        }
    }

    private fun onCityChanged(
        client: MinecraftClient,
        world: ClientWorld,
        player: ClientPlayerEntity,
        city: ClientCityData.City?
    ) {
        cachedCityId = city?.id
        previousCityName = city?.name ?: previousCityName
        overlayTicks = 1

        if (city != null) {
            overlayTitle = Text.translatable("text.steel_and_honor.hud.kingdom", city.kingdomName)
            overlaySubtitle = if (city.capital) {
                Text.translatable("text.steel_and_honor.hud.capital", city.name)
            } else {
                Text.translatable("text.steel_and_honor.hud.city", city.name)
            }
            overlayColor = colorFrom(city.kingdomColor)
            playArrivalCue(player, world, city)
        } else {
            overlayTitle = Text.translatable("text.steel_and_honor.hud.wilderness_title")
            overlaySubtitle = previousCityName?.let {
                Text.translatable("text.steel_and_honor.hud.departing", it)
            } ?: Text.translatable("text.steel_and_honor.hud.wilderness_subtitle")
            overlayColor = 0x8A8793
            playDepartureCue(player, world)
        }

        client.inGameHud.setOverlayMessage(overlaySubtitle, false)
    }

    private fun playArrivalCue(player: ClientPlayerEntity, world: ClientWorld, city: ClientCityData.City) {
        player.playSound(SoundUtil.resolve(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), 0.7f, 1f)
        spawnAura(player, world, player.x, player.y + 1.0, player.z, city.kingdomColor, city.capital, world.random)
    }

    private fun playDepartureCue(player: ClientPlayerEntity, world: ClientWorld) {
        player.playSound(SoundUtil.resolve(SoundEvents.UI_BUTTON_CLICK), 0.55f, 1.2f)
        spawnAura(player, world, player.x, player.y + 1.0, player.z, floatArrayOf(0.8f, 0.8f, 0.8f), false, world.random)
    }

    private fun spawnAura(
        player: ClientPlayerEntity,
        world: ClientWorld,
        x: Double,
        y: Double,
        z: Double,
        baseColor: FloatArray,
        capital: Boolean,
        random: Random
    ) {
        val start = Vector3f(baseColor.componentOr(0), baseColor.componentOr(1), baseColor.componentOr(2))
        val accent = if (capital) {
            Vector3f(1f, 0.94f, 0.56f)
        } else {
            Vector3f(
                (baseColor.componentOr(0) + 0.15f).coerceAtMost(1f),
                (baseColor.componentOr(1) + 0.15f).coerceAtMost(1f),
                (baseColor.componentOr(2) + 0.15f).coerceAtMost(1f)
            )
        }
        val particle = DustColorTransitionParticleEffect(start, accent, if (capital) 1.2f else 0.85f)
        val count = if (capital) 64 else 32
        repeat(count) {
            val offsetX = (random.nextDouble() - 0.5) * 2.2
            val offsetY = random.nextDouble() * 1.6
            val offsetZ = (random.nextDouble() - 0.5) * 2.2
            world.addParticle(
                particle,
                x + offsetX,
                y + offsetY,
                z + offsetZ,
                0.0,
                0.02 + random.nextDouble() * 0.02,
                0.0
            )
        }
        val sound = if (capital) SoundEvents.BLOCK_BEACON_ACTIVATE else SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME
        player.playSound(SoundUtil.resolve(sound), if (capital) 0.8f else 0.5f, if (capital) 0.92f else 1.15f)
    }

    private fun renderOverlay(context: DrawContext) {
        if (overlayTicks <= 0) return
        val alpha = overlayAlpha()
        if (alpha <= 0f) return
        val minecraft = MinecraftClient.getInstance()
        val textRenderer = minecraft.textRenderer
        val panelX = (context.scaledWindowWidth - PANEL_WIDTH) / 2
        // Adjust Y position if war overlay is active to avoid overlap
        val baseY = 30
        val warOverlayBottom = if (WarHudOverlay.isWarActive()) WarHudOverlay.getWarOverlayBottom() else 0
        val panelY = if (warOverlayBottom > 0 && warOverlayBottom >= baseY) {
            warOverlayBottom + 4 // Add small gap between overlays
        } else {
            baseY
        }
        val topColor = applyAlpha(0xF0101010u.toInt(), alpha)
        val bottomColor = applyAlpha(0xF0000000u.toInt(), alpha)
        context.fillGradient(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, topColor, bottomColor)
        val accent = applyAlpha(overlayColor, alpha)
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, accent)
        val titleColor = applyAlpha(0xFFFFFF, alpha)
        val subtitleColor = applyAlpha(0xCBD4FF, alpha * 0.9f)
        context.drawText(textRenderer, overlayTitle, panelX + 10, panelY + 10, titleColor, false)
        context.drawText(textRenderer, overlaySubtitle, panelX + 10, panelY + 25, subtitleColor, false)
    }

    private fun overlayAlpha(): Float {
        val ticks = overlayTicks
        if (ticks <= 0) return 0f
        return when {
            ticks <= FADE_IN_TICKS -> ticks.toFloat() / FADE_IN_TICKS.toFloat()
            ticks <= FADE_IN_TICKS + HOLD_TICKS -> 1f
            else -> {
                val fadeTicks = ticks - (FADE_IN_TICKS + HOLD_TICKS)
                1f - (fadeTicks / FADE_OUT_TICKS.toFloat())
            }
        }.coerceIn(0f, 1f)
    }

    private fun colorFrom(components: FloatArray): Int {
        val r = toChannel(components.componentOr(0))
        val g = toChannel(components.componentOr(1))
        val b = toChannel(components.componentOr(2))
        return (r shl 16) or (g shl 8) or b
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val channel = (alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        return (channel shl 24) or (color and 0xFFFFFF)
    }

    private fun toChannel(value: Float): Int {
        return (value.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    }

    private fun FloatArray.componentOr(index: Int, fallback: Float = 1f): Float {
        return if (index in indices) this[index].coerceIn(0f, 1f) else fallback
    }
}
