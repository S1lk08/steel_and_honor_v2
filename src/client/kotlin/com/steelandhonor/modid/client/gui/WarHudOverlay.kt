package com.steelandhonor.modid.client.gui

import com.steelandhonor.modid.network.KingdomNetworking
import com.steelandhonor.modid.util.DyeColorUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.DyeColor
import kotlin.math.roundToInt

object WarHudOverlay {
    private const val PANEL_WIDTH = 240
    private const val PANEL_HEIGHT = 30
    private const val TICKS_PER_SECOND = 20

    private val wars: MutableList<ClientWar> = mutableListOf()
    private var tickCounter = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!client.isPaused) tick()
        }
        HudRenderCallback.EVENT.register { context, _ ->
            render(context)
        }
    }

    fun update(entries: List<KingdomNetworking.WarStatusEntry>) {
        synchronized(wars) {
            wars.clear()
            entries.forEach { wars.add(ClientWar(it.attackerName, it.defenderName, it.attackerColorId, it.defenderColorId, it.attackerKills, it.defenderKills, it.secondsRemaining, it.prepSecondsRemaining)) }
        }
    }

    private fun tick() {
        tickCounter++
        // Only update timers once per second (every 20 ticks) to match server update rate
        if (tickCounter < TICKS_PER_SECOND) return
        tickCounter = 0
        
        synchronized(wars) {
            for (i in wars.indices) {
                val war = wars[i]
                var updated = war
                if (war.prepSecondsRemaining > 0) {
                    updated = updated.copy(prepSecondsRemaining = (war.prepSecondsRemaining - 1).coerceAtLeast(0))
                } else if (war.secondsRemaining > 0) {
                    updated = updated.copy(secondsRemaining = (war.secondsRemaining - 1).coerceAtLeast(0))
                }
                wars[i] = updated
            }
        }
    }

    fun isWarActive(): Boolean {
        return synchronized(wars) { wars.isNotEmpty() }
    }
    
    fun getWarOverlayBottom(): Int {
        if (!isWarActive()) return 0
        return 6 + PANEL_HEIGHT // y position + height
    }
    
    private fun render(context: DrawContext) {
        val war = synchronized(wars) { wars.firstOrNull() } ?: return
        val client = MinecraftClient.getInstance() ?: return
        val textRenderer = client.textRenderer
        val x = (context.scaledWindowWidth - PANEL_WIDTH) / 2
        val y = 6
        val background = 0xC0101010.toInt()
        val border = 0xFF0A0A0A.toInt()
        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, background)
        context.fill(x, y, x + PANEL_WIDTH, y + 2, border)
        context.fill(x, y + PANEL_HEIGHT - 2, x + PANEL_WIDTH, y + PANEL_HEIGHT, border)

        val attackerColor = toColor(war.attackerColorId)
        val defenderColor = toColor(war.defenderColorId)
        val header = Text.translatable("text.steel_and_honor.hud.war.title", war.attackerName, war.defenderName)
        val kills = Text.translatable("text.steel_and_honor.hud.war.kills", war.attackerKills, war.defenderKills)
        
        val prepTimerText = Text.translatable("text.steel_and_honor.hud.war.prep_timer", formatTime(war.prepSecondsRemaining))
        val warTimerText = Text.translatable("text.steel_and_honor.hud.war.timer", formatTime(war.secondsRemaining))
        val combinedTimer = Text.literal("${prepTimerText.string} | ${warTimerText.string}")

        context.drawText(textRenderer, header, x + 10, y + 6, 0xFFFFFF, false)
        context.drawText(textRenderer, kills, x + 10, y + 18, attackerColor, false)
        val timerWidth = textRenderer.getWidth(combinedTimer)
        context.drawText(textRenderer, combinedTimer, x + PANEL_WIDTH - timerWidth - 12, y + 18, defenderColor, false)
    }

    private fun formatTime(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val minutes = safeSeconds / 60
        val remaining = safeSeconds % 60
        return "%02d:%02d".format(minutes, remaining)
    }

    private fun toColor(colorId: Int): Int {
        val dye = DyeColor.byId(colorId) ?: DyeColor.WHITE
        val components = DyeColorUtils.componentsFor(dye)
        val r = (components.getOrNull(0)?.coerceIn(0f, 1f) ?: 1f)
        val g = (components.getOrNull(1)?.coerceIn(0f, 1f) ?: 1f)
        val b = (components.getOrNull(2)?.coerceIn(0f, 1f) ?: 1f)
        val ri = (r * 255f).roundToInt().coerceIn(0, 255)
        val gi = (g * 255f).roundToInt().coerceIn(0, 255)
        val bi = (b * 255f).roundToInt().coerceIn(0, 255)
        return (ri shl 16) or (gi shl 8) or bi
    }

    private data class ClientWar(
        val attackerName: String,
        val defenderName: String,
        val attackerColorId: Int,
        val defenderColorId: Int,
        val attackerKills: Int,
        val defenderKills: Int,
        val secondsRemaining: Int,
        val prepSecondsRemaining: Int
    )
}
