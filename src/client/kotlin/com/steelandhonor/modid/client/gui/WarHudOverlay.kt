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
    private const val PANEL_HEIGHT = 42
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
            entries.forEach {
                wars.add(
                    ClientWar(
                        attackerName = it.attackerName,
                        defenderName = it.defenderName,
                        attackerColorId = it.attackerColorId,
                        defenderColorId = it.defenderColorId,
                        attackerKills = it.attackerKills,
                        defenderKills = it.defenderKills,
                        secondsRemaining = it.secondsRemaining,
                        prepSecondsRemaining = it.prepSecondsRemaining,
                        attackerScore = it.attackerScore,
                        defenderScore = it.defenderScore,
                        activeCityName = it.activeCityName,
                        captureProgress = it.captureProgress
                    )
                )
            }
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
                val updated = if (war.prepSecondsRemaining > 0) {
                    war.copy(prepSecondsRemaining = (war.prepSecondsRemaining - 1).coerceAtLeast(0))
                } else if (war.secondsRemaining > 0) {
                    war.copy(secondsRemaining = (war.secondsRemaining - 1).coerceAtLeast(0))
                } else {
                    war
                }
                wars[i] = updated
            }
        }
    }

    fun isWarActive(): Boolean =
        synchronized(wars) { wars.isNotEmpty() }

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

        // Panel background + borders
        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, background)
        context.fill(x, y, x + PANEL_WIDTH, y + 2, border)
        context.fill(x, y + PANEL_HEIGHT - 2, x + PANEL_WIDTH, y + PANEL_HEIGHT, border)

        val attackerColor = toColor(war.attackerColorId)
        val defenderColor = toColor(war.defenderColorId)
        val header = Text.translatable("text.steel_and_honor.hud.war.title", war.attackerName, war.defenderName)
        val kills = Text.translatable("text.steel_and_honor.hud.war.kills", war.attackerKills, war.defenderKills)

        val prepTimerText =
            Text.translatable("text.steel_and_honor.hud.war.prep_timer", formatTime(war.prepSecondsRemaining))
        val warTimerText =
            Text.translatable("text.steel_and_honor.hud.war.timer", formatTime(war.secondsRemaining))
        val combinedTimer = Text.literal("${prepTimerText.string} | ${warTimerText.string}")

        context.drawText(textRenderer, header, x + 10, y + 4, 0xFFFFFF, false)
        context.drawText(textRenderer, kills, x + 10, y + 16, attackerColor, false)

        val timerWidth = textRenderer.getWidth(combinedTimer)
        context.drawText(textRenderer, combinedTimer, x + PANEL_WIDTH - timerWidth - 12, y + 16, defenderColor, false)

        // Capture bar under the panel if there is an active city
        if (war.activeCityName.isNotEmpty() && war.captureProgress > 0f) {
            val barX = x + 10
            val barY = y + PANEL_HEIGHT - 8
            val barWidth = PANEL_WIDTH - 20
            val filled = (barWidth * war.captureProgress.coerceIn(0f, 1f)).roundToInt()

            context.fill(barX, barY, barX + barWidth, barY + 3, 0xFF202020.toInt())
            context.fill(barX, barY, barX + filled, barY + 3, 0xFFAA0000.toInt())

            val label = Text.literal("Capturing ${war.activeCityName}")
            val labelWidth = textRenderer.getWidth(label)
            context.drawText(textRenderer, label, barX + (barWidth - labelWidth) / 2, barY - 9, 0xFFFFFF, false)
        }
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
        val prepSecondsRemaining: Int,
        val attackerScore: Int,
        val defenderScore: Int,
        val activeCityName: String,
        val captureProgress: Float
    )
}
