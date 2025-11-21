package com.steelandhonor.modid.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.TextFieldWidget
import kotlin.math.max
import kotlin.math.min

class SuggestionWidget(
    private val textField: TextFieldWidget,
    private val suggestions: List<String>,
    private val maxVisible: Int = 5
) {
    private var selectedIndex = 0
    private var scrollOffset = 0
    private val itemHeight = 12
    private val padding = 2
    private val borderColor = 0xFF808080.toInt()
    private val backgroundColor = 0xFF000000.toInt()
    private val selectedColor = 0xFF3B3B3B.toInt()
    private val textColor = 0xFFFFFF

    fun getSelectedSuggestion(): String? {
        if (suggestions.isEmpty() || selectedIndex < 0 || selectedIndex >= suggestions.size) {
            return null
        }
        return suggestions[selectedIndex]
    }

    fun moveSelection(delta: Int) {
        if (suggestions.isEmpty()) return
        selectedIndex = (selectedIndex + delta).coerceIn(0, suggestions.size - 1)
        updateScroll()
    }

    fun resetSelection() {
        selectedIndex = 0
        scrollOffset = 0
    }

    private fun updateScroll() {
        val visibleCount = min(maxVisible, suggestions.size)
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex
        } else if (selectedIndex >= scrollOffset + visibleCount) {
            scrollOffset = selectedIndex - visibleCount + 1
        }
    }

    // NOTE: 3-argument render – matches how we call it from KingdomMenuScreen
    fun render(context: DrawContext, mouseX: Int, mouseY: Int) {
        if (suggestions.isEmpty()) return

        val client = MinecraftClient.getInstance()
        val textRenderer = client.textRenderer

        val visibleCount = min(maxVisible, suggestions.size)
        val width = textField.width
        val x = textField.x
        val y = textField.y + textField.height + 2

        val totalHeight = visibleCount * itemHeight + padding * 2
        val maxWidth = suggestions.maxOfOrNull { textRenderer.getWidth(it) } ?: width
        val actualWidth = max(width, min(maxWidth + padding * 2, 200))

        // Background + border
        context.fill(x, y, x + actualWidth, y + totalHeight, backgroundColor)
        context.fill(x, y, x + actualWidth, y + 1, borderColor)
        context.fill(x, y + totalHeight - 1, x + actualWidth, y + totalHeight, borderColor)
        context.fill(x, y, x + 1, y + totalHeight, borderColor)
        context.fill(x + actualWidth - 1, y, x + actualWidth, y + totalHeight, borderColor)

        // Suggestions
        val startIndex = scrollOffset
        val endIndex = min(startIndex + visibleCount, suggestions.size)

        for (i in startIndex until endIndex) {
            val suggestion = suggestions[i]
            val itemY = y + padding + (i - startIndex) * itemHeight
            val isSelected = i == selectedIndex

            if (isSelected) {
                context.fill(
                    x + 1,
                    itemY - 1,
                    x + actualWidth - 1,
                    itemY + itemHeight - 1,
                    selectedColor
                )
            }

            val textX = x + padding
            val textY = itemY + (itemHeight - 9) / 2
            context.drawTextWithShadow(textRenderer, suggestion, textX, textY, textColor)
        }
    }

    fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        if (suggestions.isEmpty()) return false

        val visibleCount = min(maxVisible, suggestions.size)
        val width = textField.width
        val x = textField.x.toDouble()
        val y = (textField.y + textField.height + 2).toDouble()
        val totalHeight = visibleCount * itemHeight + padding * 2
        val maxWidth = suggestions.maxOfOrNull {
            MinecraftClient.getInstance().textRenderer.getWidth(it)
        } ?: width
        val actualWidth = max(width, min(maxWidth + padding * 2, 200)).toDouble()

        return mouseX >= x && mouseX <= x + actualWidth &&
               mouseY >= y && mouseY <= y + totalHeight
    }

    fun getSuggestionAt(mouseX: Double, mouseY: Double): Int? {
        if (!isMouseOver(mouseX, mouseY)) return null

        val y = (textField.y + textField.height + 2).toDouble()
        val itemY = mouseY - y - padding
        val index = (itemY / itemHeight).toInt() + scrollOffset

        return if (index >= 0 && index < suggestions.size) index else null
    }

    fun setSelectedIndex(index: Int) {
        if (index >= 0 && index < suggestions.size) {
            selectedIndex = index
            updateScroll()
        }
    }
}

