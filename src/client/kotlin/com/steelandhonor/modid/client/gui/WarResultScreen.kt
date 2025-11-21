package com.steelandhonor.modid.client.gui

import com.steelandhonor.modid.network.KingdomNetworking
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

class WarResultScreen(
    private val result: KingdomNetworking.WarResultEntry
) : Screen(Text.literal("War Results")) {

    companion object {
        private const val PANEL_WIDTH = 260
        private const val PANEL_HEIGHT = 180
        private const val CITY_CAPTURE_POINTS = 1000
    }

    override fun init() {
        super.init()
        val centerX = width / 2
        val panelBottom = height / 2 + PANEL_HEIGHT / 2
        val buttonW = 100
        val buttonH = 20

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Close")) {
                close()
            }.dimensions(
                centerX - buttonW / 2,
                panelBottom - buttonH - 8,
                buttonW,
                buttonH
            ).build()
        )
    }

    override fun close() {
        client?.setScreen(null)
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
    renderBackground(context, mouseX, mouseY, delta)

        val cx = width / 2
        val cy = height / 2
        val left = cx - PANEL_WIDTH / 2
        val top = cy - PANEL_HEIGHT / 2
        val right = cx + PANEL_WIDTH / 2
        val bottom = cy + PANEL_HEIGHT / 2

        // Background panel
        context.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF000000.toInt())
        context.fill(left, top, right, bottom, 0xEE111118.toInt())

        val tr = textRenderer
        var y = top + 10

        // Title
        context.drawCenteredTextWithShadow(tr, Text.literal("War Results"), cx, y, 0xFFFFFF)
        y += 14

        // Header
        val header = "${result.attackerName} vs ${result.defenderName}"
        context.drawCenteredTextWithShadow(tr, Text.literal(header), cx, y, 0xAAAAFF)
        y += 14

        val winnerText = when (result.winnerSide) {
            1 -> "Winner: ${result.attackerName}"
            2 -> "Winner: ${result.defenderName}"
            else -> "Result: Draw"
        }
        context.drawCenteredTextWithShadow(tr, Text.literal(winnerText), cx, y, 0xFFFFFF)
        y += 12

        // Divider line
        context.fill(left + 8, y, right - 8, y + 1, 0xFF333333.toInt())
        y += 6

        // Derived points
        val attackerCityPoints = result.attackerCityCaptures * CITY_CAPTURE_POINTS
        val defenderCityPoints = result.defenderCityCaptures * CITY_CAPTURE_POINTS
        val attackerKillPoints = result.attackerScore - attackerCityPoints
        val defenderKillPoints = result.defenderScore - defenderCityPoints

        val colLeft = left + 14
        val colRight = cx + 6

        y = top + 40

        // Attacker column
        drawSideBlock(
            context,
            colLeft,
            y,
            result.attackerName,
            result.attackerScore,
            result.attackerKills,
            result.attackerCityCaptures,
            attackerKillPoints,
            attackerCityPoints
        )

        // Defender column
        drawSideBlock(
            context,
            colRight,
            y,
            result.defenderName,
            result.defenderScore,
            result.defenderKills,
            result.defenderCityCaptures,
            defenderKillPoints,
            defenderCityPoints
        )

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawSideBlock(
        context: DrawContext,
        x: Int,
        startY: Int,
        name: String,
        totalScore: Int,
        kills: Int,
        cities: Int,
        killPoints: Int,
        cityPoints: Int
    ) {
        val tr = textRenderer
        var y = startY

        context.drawTextWithShadow(tr, Text.literal(name), x, y, 0xFFFFFF)
        y += 10

        context.drawTextWithShadow(
            tr,
            Text.literal("Total Score: $totalScore"),
            x,
            y,
            0xFFFFD37F.toInt()
        )
        y += 12

        val bullet = "\u2022 "

        // Kills
        context.drawTextWithShadow(
            tr,
            Text.literal("$bullet Combat kills (x$kills) - +$killPoints points"),
            x,
            y,
            0xFFEEEEEE.toInt()
        )
        y += 10

        // Cities
        context.drawTextWithShadow(
            tr,
            Text.literal("$bullet City captures (x$cities) - +$cityPoints points"),
            x,
            y,
            0xFFEEEEEE.toInt()
        )
        y += 10
    }
}
