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
        return 6 + PANEL_HEIGHT
    }

private fun render(context: DrawContext) {
    val war = synchronized(wars) { wars.firstOrNull() } ?: return
    val client = MinecraftClient.getInstance() ?: return
    val textRenderer = client.textRenderer

    val x = (context.scaledWindowWidth - PANEL_WIDTH) / 2
    val y = 6
    val background = 0xC0101010.toInt()
    val border = 0xFF0A0A0A.toInt()

    // Background + borders
    context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, background)
    context.fill(x, y, x + PANEL_WIDTH, y + 2, border)
    context.fill(x, y + PANEL_HEIGHT - 2, x + PANEL_WIDTH, y + PANEL_HEIGHT, border)

    val attackerColor = toColor(war.attackerColorId)
    val defenderColor = toColor(war.defenderColorId)

    val header = Text.literal("${war.attackerName} vs ${war.defenderName}")
    context.drawText(textRenderer, header, x + 10, y + 4, 0xFFFFFF, false)

    // ---- NEW: SCORE DISPLAY ----
    val scoreText = Text.literal("Score: ${war.attackerScore} - ${war.defenderScore}")
    val scoreWidth = textRenderer.getWidth(scoreText)
    context.drawText(
        textRenderer,
        scoreText,
        x + PANEL_WIDTH - scoreWidth - 12,
        y + 4,
        0xFFCCCCCC.toInt(),
        false
    )

    // Timers
    val prepTimerText =
        "Prep: ${formatTime(war.prepSecondsRemaining)}"
    val warTimerText =
        "War: ${formatTime(war.secondsRemaining)}"

    val combinedTimer = Text.literal("$prepTimerText | $warTimerText")

    val timerWidth = textRenderer.getWidth(combinedTimer)
    context.drawText(
        textRenderer,
        combinedTimer,
        x + PANEL_WIDTH - timerWidth - 12,
        y + 18,
        defenderColor,
        false
    )

    // Capture bar
    if (war.activeCityName.isNotEmpty() && war.captureProgress > 0f) {
        val barX = x + 10
        val barY = y + PANEL_HEIGHT - 8
        val barWidth = PANEL_WIDTH - 20
        val filled = (barWidth * war.captureProgress.coerceIn(0f, 1f)).roundToInt()

        context.fill(barX, barY, barX + barWidth, barY + 3, 0xFF202020.toInt())
        context.fill(barX, barY, barX + filled, barY + 3, 0xFFAA0000.toInt())

        val label = Text.literal("Capturing ${war.activeCityName}")
        val labelWidth = textRenderer.getWidth(label)
        context.drawText(
            textRenderer,
            label,
            barX + (barWidth - labelWidth) / 2,
            barY - 9,
            0xFFFFFF,
            false
        )
    }
}

    private fun formatTime(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val m = safe / 60
        val s = safe % 60
        return "%02d:%02d".format(m, s)
    }

    private fun toColor(colorId: Int): Int {
        val dye = DyeColor.byId(colorId) ?: DyeColor.WHITE
        val comp = DyeColorUtils.componentsFor(dye)
        val r = (comp.getOrNull(0) ?: 1f).coerceIn(0f, 1f)
        val g = (comp.getOrNull(1) ?: 1f).coerceIn(0f, 1f)
        val b = (comp.getOrNull(2) ?: 1f).coerceIn(0f, 1f)
        return ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
    }

    private data class ClientWar(
        val attackerName: String,
        val defenderName: String,
        val attackerColorId: Int,
        val defenderColorId: Int,
        val secondsRemaining: Int,
        val prepSecondsRemaining: Int,
        val attackerScore: Int,
        val defenderScore: Int,
        val activeCityName: String,
        val captureProgress: Float
    )
}
