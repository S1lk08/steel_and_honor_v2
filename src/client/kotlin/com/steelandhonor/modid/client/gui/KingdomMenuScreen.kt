package com.steelandhonor.modid.client.gui

import com.steelandhonor.modid.command.KingdomCommand
import com.steelandhonor.modid.kingdom.KingdomManager
import com.steelandhonor.modid.kingdom.KingdomRole
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class KingdomMenuScreen : Screen(Text.translatable("screen.steel_and_honor.menu.title")) {
    private var currentPage = MenuPage.HOME
    private val infoParagraphs: MutableList<Text> = mutableListOf()
    private val fields: MutableList<TextFieldWidget> = mutableListOf()
    private val fieldTypes: MutableMap<TextFieldWidget, SuggestionType> = mutableMapOf()
    private val scrollableControls: MutableList<ScrollableControl> = mutableListOf()
    private var paragraphPixelHeight = 0
    private var controlsMaxBaseY = 0
    private var scrollOffset = 0
    private var maxScroll = 0
    private var currentSuggestionWidget: SuggestionWidget? = null
    private var showingSuggestions = false

    override fun init() {
        super.init()
        rebuildUi()
    }

    private fun rebuildUi() {
        infoParagraphs.clear()
        paragraphPixelHeight = 0
        controlsMaxBaseY = controlBaseY()
        fields.clear()
        fieldTypes.clear()
        scrollableControls.clear()
        hideSuggestions()
        clearChildren()
        addNavigation()

        when (currentPage) {
            MenuPage.HOME -> buildHomePage()
            MenuPage.STATUS -> buildStatusPage()
            MenuPage.MANAGEMENT -> buildManagementPage()
            MenuPage.CITIZENS -> buildCitizensPage()
            MenuPage.WAR -> buildWarPage()
            MenuPage.HELP -> buildHelpPage()
            MenuPage.FLAG -> buildFlagPage()
        }

        updateScrollBounds()
    }

    private fun addNavigation() {
        val pages = MenuPage.entries
        val navWidth = navButtonWidth()
        val startX = navStart(navWidth, pages.size)
        val y = navY()
        pages.forEachIndexed { index, page ->
            val button = ButtonWidget.builder(Text.translatable(page.navKey)) {
                currentPage = page
                rebuildUi()
            }.dimensions(
                startX + index * (navWidth + NAV_BUTTON_SPACING),
                y,
                navWidth,
                NAV_BUTTON_HEIGHT
            ).build()
            button.active = currentPage != page
            addDrawableChild(button)
        }
    }

    private fun buildHomePage() {
        addParagraph("screen.steel_and_honor.menu.home.info")
        addParagraph("screen.steel_and_honor.menu.home.tip")
    }

    private fun buildStatusPage() {
        addParagraph("screen.steel_and_honor.menu.status.info")
        val baseY = sectionTop() + 12
        val button = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.status.refresh")
        ) {
            runCommand("kingdom info")
        }.dimensions(centerX() - 75, baseY, 150, 20).build()
        addDrawableChild(button)
        registerControl(button, baseY)
    }

    private fun buildManagementPage() {
        addParagraph("screen.steel_and_honor.menu.management.info")
        val renameBase = sectionTop() + 10
        val renameField = textField(
            leftColumnX(),
            renameBase,
            columnWidth(),
            "screen.steel_and_honor.menu.management.rename"
        )
        val renameButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.management.rename.button")
        ) {
            val value = renameField.text.trim()
            if (value.isNotEmpty()) {
                runCommand("kingdom rename $value")
            }
        }.dimensions(rightColumnX(), renameBase, columnWidth(), 20).build()
        addDrawableChild(renameButton)
        registerControl(renameButton, renameBase)

        val colorBase = sectionTop() + 40
        val colorField = textField(
            leftColumnX(),
            colorBase,
            columnWidth(),
            "screen.steel_and_honor.menu.management.color",
            SuggestionType.COLOR
        )
        val colorButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.management.color.button")
        ) {
            val value = colorField.text.trim()
            if (value.isNotEmpty()) {
                runCommand("kingdom color $value")
            }
        }.dimensions(rightColumnX(), colorBase, columnWidth(), 20).build()
        addDrawableChild(colorButton)
        registerControl(colorButton, colorBase)

        val createNameField = textField(
            leftColumnX(),
            sectionTop() + 85,
            columnWidth(),
            "screen.steel_and_honor.menu.management.create.name"
        )
        val createColorField = textField(
            leftColumnX(),
            sectionTop() + 115,
            columnWidth(),
            "screen.steel_and_honor.menu.management.create.color",
            SuggestionType.COLOR
        )
        val createButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.management.create.button")
        ) {
            val name = createNameField.text.trim()
            val color = createColorField.text.trim()
            if (name.isNotEmpty() && color.isNotEmpty()) {
                runCommand("kingdom create $name $color")
            }
        }.dimensions(rightColumnX(), sectionTop() + 100, columnWidth(), 20).build()
        addDrawableChild(createButton)
        registerControl(createButton, sectionTop() + 100)
    }

    private fun buildCitizensPage() {
        addParagraph("screen.steel_and_honor.menu.citizens.info")
        val inviteBase = sectionTop() + 10
        val inviteField = textField(
            leftColumnX(),
            inviteBase,
            columnWidth(),
            "screen.steel_and_honor.menu.citizens.invite",
            SuggestionType.PLAYER_NAME
        )
        val inviteButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.citizens.invite.button")
        ) {
            val value = inviteField.text.trim()
            if (value.isNotEmpty()) {
                runCommand("kingdom invite $value")
            }
        }.dimensions(rightColumnX(), inviteBase, columnWidth(), 20).build()
        addDrawableChild(inviteButton)
        registerControl(inviteButton, inviteBase)

        val joinBase = sectionTop() + 55
        val joinField = textField(
            leftColumnX(),
            joinBase,
            columnWidth(),
            "screen.steel_and_honor.menu.citizens.join",
            SuggestionType.INVITE_TARGET
        )
        val joinButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.citizens.join.button")
        ) {
            val value = joinField.text.trim()
            if (value.isNotEmpty()) {
                runCommand("kingdom join $value")
            }
        }.dimensions(rightColumnX(), joinBase, columnWidth(), 20).build()
        addDrawableChild(joinButton)
        registerControl(joinButton, joinBase)

        val leaveBase = min(
            sectionTop() + 90,
            contentBottom() - 35
        )

        val leaveWidth = columnWidth() * 2 + COLUMN_SPACING
        val leaveButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.citizens.leave.button")
        ) {
            runCommand("kingdom leave")
        }.dimensions(centerX() - leaveWidth / 2, leaveBase, leaveWidth, 20).build()
        addDrawableChild(leaveButton)
        registerControl(leaveButton, leaveBase)
    }

    private fun buildWarPage() {
        addParagraph("screen.steel_and_honor.menu.war.info")

        // Declare War
        val declareBase = sectionTop() + 10
        val declareField = textField(
            leftColumnX(),
            declareBase,
            columnWidth(),
            "screen.steel_and_honor.menu.war.declare",
            SuggestionType.WAR_TARGET
        )
        val declareButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.war.declare.button")
        ) {
            val target = declareField.text.trim()
            if (target.isNotEmpty()) {
                runCommand("kingdom war declare $target")
            }
        }.dimensions(rightColumnX(), declareBase, columnWidth(), 20).build()
        addDrawableChild(declareButton)
        registerControl(declareButton, declareBase)

        // Assistance Requests
        val requestBase = sectionTop() + 55
        val requestField = textField(
            leftColumnX(),
            requestBase,
            columnWidth(),
            "screen.steel_and_honor.menu.war.request",
            SuggestionType.KINGDOM_NAME
        )
        val requestButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.war.request.button")
        ) {
            val target = requestField.text.trim()
            if (target.isNotEmpty()) {
                runCommand("kingdom war request $target")
            }
        }.dimensions(rightColumnX(), requestBase, columnWidth(), 20).build()
        addDrawableChild(requestButton)
        registerControl(requestButton, requestBase)

        // Approve / Deny Requests
        val responseBase = sectionTop() + 100
        val responseField = textField(
            leftColumnX(),
            responseBase,
            columnWidth(),
            "screen.steel_and_honor.menu.war.approvals",
            SuggestionType.WAR_REQUEST_TARGET
        )

        val approveButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.war.approve.button")
        ) {
            val target = responseField.text.trim()
            if (target.isNotEmpty()) {
                runCommand("kingdom war approve $target")
            }
        }.dimensions(rightColumnX(), responseBase - 5, columnWidth(), 20).build()

        val denyButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.war.deny.button")
        ) {
            val target = responseField.text.trim()
            if (target.isNotEmpty()) {
                runCommand("kingdom war deny $target")
            }
        }.dimensions(rightColumnX(), responseBase + 25, columnWidth(), 20).build()

        addDrawableChild(approveButton)
        addDrawableChild(denyButton)
        registerControl(approveButton, responseBase - 5)
        registerControl(denyButton, responseBase + 25)

        // SURRENDER BUTTON WITH ROLE CHECK + ANIMATION
        val mc = MinecraftClient.getInstance()
        val player = mc.player
        val uuid = player?.uuid ?: return

        if (!KingdomManager.isInWar(uuid)) return

        val surrenderBase = responseBase + 70
        val surrenderWidth = columnWidth() * 2 + COLUMN_SPACING

        val role = KingdomManager.getRole(uuid)
        val isLeader = role == KingdomRole.LEADER

        val surrenderButton = PulsingButtonWidget(
            centerX() - surrenderWidth / 2,
            surrenderBase,
            surrenderWidth,
            20,
            Text.translatable("screen.steel_and_honor.menu.war.surrender.button")
        ) {
            if (isLeader) {
                mc.setScreen(SurrenderConfirmScreen(this))
            }
        }

        surrenderButton.active = isLeader
        addDrawableChild(surrenderButton)
        registerControl(surrenderButton, surrenderBase)
    }

    private fun buildFlagPage() {
        addParagraph("screen.steel_and_honor.menu.flag.info")

        val client = MinecraftClient.getInstance()
        val player = client.player ?: return

        val bannerStack = player.mainHandStack
        val previewY = sectionTop() + 10
        val previewX = centerX() - 10

        val preview = BannerPreviewWidget(
            x = previewX,
            y = previewY,
            banner = bannerStack
        )
        addDrawableChild(preview)
        registerControl(preview, previewY)

        val buttonBaseY = previewY + 50
        val flagButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.flag.button")
        ) {
            runCommand("kingdom flag")
        }.dimensions(centerX() - 75, buttonBaseY, 150, 20).build()

        addDrawableChild(flagButton)
        registerControl(flagButton, buttonBaseY)
    }

    private fun buildHelpPage() {
        addParagraph("screen.steel_and_honor.menu.help.info")
        KingdomCommand.helpEntries.forEach { entry ->
            val text = Text.translatable(
                "screen.steel_and_honor.menu.help.entry",
                entry.usage,
                Text.translatable(entry.descriptionKey).string
            )
            infoParagraphs.add(text)
            paragraphPixelHeight += paragraphHeight(text)
        }
        val helpBase = paragraphsBottom() + 10
        val helpButton = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.help.view")
        ) {
            runCommand("kingdom help")
        }.dimensions(centerX() - 75, helpBase, 150, 20).build()
        addDrawableChild(helpButton)
        registerControl(helpButton, helpBase)
    }

    private fun addParagraph(key: String) {
        val text = Text.translatable(key)
        infoParagraphs.add(text)
        paragraphPixelHeight += paragraphHeight(text)
    }

    private fun paragraphHeight(text: Text): Int {
        val wrappedLines = textRenderer.wrapLines(text, paragraphWrapWidth())
        return wrappedLines.size * PARAGRAPH_LINE_HEIGHT + PARAGRAPH_SPACING
    }

    private fun textField(
        x: Int,
        y: Int,
        width: Int,
        placeholderKey: String,
        type: SuggestionType? = null
    ): TextFieldWidget {
        val field = TextFieldWidget(
            textRenderer,
            x,
            y,
            width,
            20,
            Text.translatable(placeholderKey)
        )
        field.setPlaceholder(Text.translatable(placeholderKey))
        field.setMaxLength(64)
        fields.add(field)
        if (type != null) {
            fieldTypes[field] = type
        }
        addDrawableChild(field)
        registerControl(field, y)
        return field
    }

    private fun paragraphWrapWidth(): Int =
        (panelWidth() - SIDE_PADDING * 2).coerceAtLeast(40)

    private fun columnWidth(): Int =
        ((panelWidth() - SIDE_PADDING * 2 - COLUMN_SPACING).coerceAtLeast(0)) / 2

    private fun panelWidth(): Int {
        val target = (width * 0.72f).roundToInt()
        val available = (width - PANEL_HORIZONTAL_MARGIN * 2).coerceAtLeast(1)
        val minAllowed = MIN_PANEL_WIDTH.coerceAtMost(available)
        val maxAllowed = MAX_PANEL_WIDTH.coerceAtMost(available)
        return target.coerceIn(minAllowed, maxAllowed)
    }

    private fun panelHeight(): Int {
        val target = (height * 0.72f).roundToInt()
        val available = (height - PANEL_VERTICAL_MARGIN * 2).coerceAtLeast(1)
        val minAllowed = MIN_PANEL_HEIGHT.coerceAtMost(available)
        val maxAllowed = MAX_PANEL_HEIGHT.coerceAtMost(available)
        return target.coerceIn(minAllowed, maxAllowed)
    }

    private fun panelLeft(): Int = (width - panelWidth()) / 2
    private fun panelTop(): Int = max(PANEL_VERTICAL_MARGIN, (height - panelHeight()) / 2)
    private fun panelCenterX(): Int = panelLeft() + panelWidth() / 2
    private fun centerX(): Int = panelCenterX()

    private fun navButtonWidth(): Int {
        val pages = MenuPage.entries.size.coerceAtLeast(1)
        val available = panelWidth() - SIDE_PADDING * 2
        val spacing = NAV_BUTTON_SPACING * (pages - 1)
        val widthPerButton = (available - spacing).coerceAtLeast(0) / pages
        val minComfortable = MIN_NAV_BUTTON_WIDTH.coerceAtMost(widthPerButton)
        return widthPerButton.coerceIn(minComfortable, MAX_NAV_BUTTON_WIDTH)
    }

    private fun navStart(navWidth: Int, pageCount: Int): Int {
        val totalWidth = pageCount * navWidth + (pageCount - 1) * NAV_BUTTON_SPACING
        return panelLeft() + (panelWidth() - totalWidth) / 2
    }

    private fun navY(): Int = panelTop() + HEADER_HEIGHT - NAV_BUTTON_HEIGHT

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // If focus left a suggestible field, hide suggestions
        val focusedField = fields.firstOrNull { it.isFocused() }
        if (showingSuggestions && (focusedField == null || !fieldTypes.containsKey(focusedField))) {
            hideSuggestions()
        }

        drawBackdrop(context)
        drawContentPanel(context)
        updateControlPositions()

        val left = panelLeft()
        val right = left + panelWidth()
        val top = contentTop()
        val bottom = contentBottom()

        context.enableScissor(left, top, right, bottom)
        drawParagraphs(context)
        context.disableScissor()

        super.render(context, mouseX, mouseY, delta)

        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable(currentPage.titleKey),
            centerX(),
            panelTop() + 8,
            0xFFE7E7FF.toInt()
        )

// Render suggestions overlay (3-arg render)
if (showingSuggestions && currentSuggestionWidget != null) {
    currentSuggestionWidget?.render(context, mouseX, mouseY)
}
    }

    override fun resize(client: MinecraftClient?, width: Int, height: Int) {
        super.resize(client, width, height)
        rebuildUi()
    }

    override fun applyBlur(delta: Float) {
        // Disable Minecraft's default blur; custom dimming already applied
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (showingSuggestions && currentSuggestionWidget != null) {
            when (keyCode) {
                GLFW.GLFW_KEY_TAB -> {
                    val suggestion = currentSuggestionWidget?.getSelectedSuggestion()
                    if (suggestion != null) {
                        val focusedField = fields.firstOrNull { it.isFocused() }
                        focusedField?.setText(suggestion)
                        hideSuggestions()
                        return true
                    }
                }
                GLFW.GLFW_KEY_UP -> {
                    currentSuggestionWidget?.moveSelection(-1)
                    return true
                }
                GLFW.GLFW_KEY_DOWN -> {
                    currentSuggestionWidget?.moveSelection(1)
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    val suggestion = currentSuggestionWidget?.getSelectedSuggestion()
                    if (suggestion != null) {
                        val focusedField = fields.firstOrNull { it.isFocused() }
                        focusedField?.setText(suggestion)
                        hideSuggestions()
                        return true
                    }
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    hideSuggestions()
                    return true
                }
            }
        }

        if (keyCode == GLFW.GLFW_KEY_TAB && !showingSuggestions) {
            val focusedField = fields.firstOrNull { it.isFocused() }
            if (focusedField != null && fieldTypes.containsKey(focusedField)) {
                showSuggestions(focusedField)
                return true
            }
        }

        if (super.keyPressed(keyCode, scanCode, modifiers)) return true

        fields.forEach { field ->
            if (field.keyPressed(keyCode, scanCode, modifiers)) {
                if (showingSuggestions && field.isFocused()) {
                    updateSuggestions(field)
                }
                return true
            }
        }
        return false
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        fields.forEach { field ->
            if (field.charTyped(chr, modifiers)) {
                if (showingSuggestions && field.isFocused()) {
                    updateSuggestions(field)
                }
                return true
            }
        }
        return super.charTyped(chr, modifiers)
    }

    private fun showSuggestions(field: TextFieldWidget) {
        val type = fieldTypes[field] ?: return
        val currentText = field.text
        val suggestions = SuggestionProvider.getSuggestions(type, currentText)
        if (suggestions.isNotEmpty()) {
            currentSuggestionWidget = SuggestionWidget(field, suggestions)
            showingSuggestions = true
        }
    }

    private fun updateSuggestions(field: TextFieldWidget) {
        if (!showingSuggestions) return
        val type = fieldTypes[field] ?: return
        val currentText = field.text
        val suggestions = SuggestionProvider.getSuggestions(type, currentText)
        if (suggestions.isNotEmpty()) {
            currentSuggestionWidget = SuggestionWidget(field, suggestions)
            currentSuggestionWidget?.resetSelection()
        } else {
            hideSuggestions()
        }
    }

    private fun hideSuggestions() {
        currentSuggestionWidget = null
        showingSuggestions = false
    }

    private fun runCommand(command: String) {
        val player = MinecraftClient.getInstance().player ?: return
        player.networkHandler.sendCommand(command)
    }

    companion object {
        private const val NAV_BUTTON_HEIGHT = 20
        private const val NAV_BUTTON_SPACING = 4
        private const val MIN_NAV_BUTTON_WIDTH = 54
        private const val MAX_NAV_BUTTON_WIDTH = 78
        private const val MIN_PANEL_WIDTH = 380
        private const val MAX_PANEL_WIDTH = 440
        private const val MIN_PANEL_HEIGHT = 230
        private const val MAX_PANEL_HEIGHT = 280
        private const val PANEL_HORIZONTAL_MARGIN = 16
        private const val PANEL_VERTICAL_MARGIN = 24
        private const val SIDE_PADDING = 20
        private const val COLUMN_SPACING = 20
        private const val HEADER_HEIGHT = 38
        private const val HEADER_GAP = 12
        private const val CONTENT_TOP_PADDING = HEADER_HEIGHT + HEADER_GAP
        private const val CONTENT_BOTTOM_PADDING = 12
        private const val PARAGRAPH_TOP_PADDING = 8
        private const val PARAGRAPH_LINE_HEIGHT = 11
        private const val PARAGRAPH_SPACING = 5
        private const val MIN_CONTROL_START_OFFSET = 42
        private const val SECTION_PARAGRAPH_GAP = 14
        private const val ACCENT_LIGHT = 0xFF6F74FF.toInt()
        private const val ACCENT_DARK = 0xFF434A8C.toInt()
        private const val TEXT_COLOR = 0xFFF2F4FF.toInt()
    }

    private fun drawBackdrop(context: DrawContext) {
        context.fill(0, 0, width, height, 0xA0000000.toInt())
    }

    private fun drawContentPanel(context: DrawContext) {
        val left = panelLeft()
        val top = panelTop()
        val right = left + panelWidth()
        val bottom = top + panelHeight()
        val headerBottom = top + HEADER_HEIGHT
        val contentTop = contentTop()
        val innerLeft = left + 3
        val innerRight = right - 3
        val innerTop = top + 3
        val innerBottom = bottom - 3

        context.fill(left - 4, top - 4, right + 4, bottom + 4, 0x60000000.toInt())
        context.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF08080B.toInt())
        context.fill(left, top, right, bottom, 0xFF12121A.toInt())
        context.fill(innerLeft, innerTop, innerRight, innerBottom, 0xFF191A24.toInt())
        context.fill(innerLeft + 2, innerTop + 2, innerRight - 2, headerBottom, 0xFF232538.toInt())
        context.fill(
            innerLeft + 2,
            headerBottom,
            innerRight - 2,
            contentTop - 2,
            0xFF1C1E2C.toInt()
        )
        context.fill(
            innerLeft + 2,
            contentTop - 2,
            innerRight - 2,
            innerBottom - 2,
            0xFF141620.toInt()
        )
        context.fill(innerLeft + 4, contentTop - 3, innerRight - 4, contentTop - 2, ACCENT_LIGHT)
        context.fill(innerLeft + 4, contentTop - 2, innerRight - 4, contentTop - 1, ACCENT_DARK)

        val pages = MenuPage.entries
        if (pages.isNotEmpty()) {
            val navWidth = navButtonWidth()
            val totalWidth = pages.size * navWidth + (pages.size - 1) * NAV_BUTTON_SPACING
            val navStart = navStart(navWidth, pages.size)
            val navY = navY()
            val navTop = navY - 2
            val navBottom = navY + NAV_BUTTON_HEIGHT + 2
            val activeIndex = pages.indexOf(currentPage)
            if (activeIndex >= 0) {
                val activeLeft = navStart + activeIndex * (navWidth + NAV_BUTTON_SPACING) - 3
                val activeRight = activeLeft + navWidth + 6
                context.fill(activeLeft, navTop, activeRight, navBottom, 0x20FFFFFF)
                context.fill(activeLeft, navBottom - 2, activeRight, navBottom - 1, ACCENT_LIGHT)
            }
        }
    }

    private fun drawParagraphs(context: DrawContext) {
        val left = panelLeft() + SIDE_PADDING
        val maxWidth = paragraphWrapWidth()
        val top = contentTop()
        val bottom = contentBottom()
        var cursorY = paragraphStartY() - scrollOffset
        infoParagraphs.forEach { text ->
            val wrapped = textRenderer.wrapLines(text, maxWidth)
            wrapped.forEach { ordered ->
                if (cursorY in top..bottom) {
                    context.drawTextWithShadow(textRenderer, ordered, left, cursorY, TEXT_COLOR)
                }
                cursorY += PARAGRAPH_LINE_HEIGHT
            }
            cursorY += PARAGRAPH_SPACING
        }
    }

    private fun paragraphStartY(): Int = contentTop() + PARAGRAPH_TOP_PADDING
    private fun paragraphsBottom(): Int = paragraphStartY() + paragraphPixelHeight
    private fun controlBaseY(): Int =
        max(paragraphsBottom(), contentTop() + MIN_CONTROL_START_OFFSET)
    private fun sectionTop(): Int = controlBaseY() + SECTION_PARAGRAPH_GAP
    private fun contentTop(): Int = panelTop() + CONTENT_TOP_PADDING
    private fun contentBottom(): Int = panelTop() + panelHeight() - CONTENT_BOTTOM_PADDING
    private fun leftColumnX(): Int = panelLeft() + SIDE_PADDING
    private fun rightColumnX(): Int = leftColumnX() + columnWidth() + COLUMN_SPACING

    private fun registerControl(widget: ClickableWidget, baseY: Int) {
        scrollableControls.add(ScrollableControl(widget, baseY))
        controlsMaxBaseY = max(controlsMaxBaseY, baseY + widget.height)
    }

    private fun updateScrollBounds() {
        val top = contentTop()
        val contentExtent = max(
            paragraphsBottom() + 10,
            max(controlsMaxBaseY, controlBaseY()) + 20
        )
        val total = contentExtent - top
        val visible = contentBottom() - top
        maxScroll = max(0, total - visible)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)
        updateControlPositions()
    }

    private fun updateControlPositions() {
        val visibleTop = contentTop()
        val visibleBottom = contentBottom()
        scrollableControls.forEach { entry ->
            val adjustedY = entry.baseY - scrollOffset
            entry.widget.y = adjustedY
            entry.widget.visible =
                adjustedY + entry.widget.height >= visibleTop && adjustedY <= visibleBottom
        }
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontal: Double,
        vertical: Double
    ): Boolean {
        if (showingSuggestions && currentSuggestionWidget != null &&
            currentSuggestionWidget!!.isMouseOver(mouseX, mouseY)
        ) {
            return true
        }

        val left = panelLeft()
        val right = left + panelWidth()
        val top = contentTop().toDouble()
        val bottom = contentBottom().toDouble()
        if (mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom && maxScroll > 0) {
            scrollOffset = (scrollOffset - vertical * 12).roundToInt().coerceIn(0, maxScroll)
            updateControlPositions()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (showingSuggestions && currentSuggestionWidget != null) {
            val index = currentSuggestionWidget!!.getSuggestionAt(mouseX, mouseY)
            if (index != null) {
                currentSuggestionWidget!!.setSelectedIndex(index)
                val suggestion = currentSuggestionWidget!!.getSelectedSuggestion()
                if (suggestion != null) {
                    val focusedField = fields.firstOrNull { it.isFocused() }
                    focusedField?.setText(suggestion)
                    hideSuggestions()
                    return true
                }
            } else if (!currentSuggestionWidget!!.isMouseOver(mouseX, mouseY)) {
                hideSuggestions()
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private data class ScrollableControl(val widget: ClickableWidget, val baseY: Int)
}

enum class MenuPage(val titleKey: String, val navKey: String) {
    HOME("screen.steel_and_honor.menu.home.title", "screen.steel_and_honor.menu.nav.home"),
    STATUS("screen.steel_and_honor.menu.status.title", "screen.steel_and_honor.menu.nav.status"),
    MANAGEMENT(
        "screen.steel_and_honor.menu.management.title",
        "screen.steel_and_honor.menu.nav.management"
    ),
    CITIZENS("screen.steel_and_honor.menu.citizens.title", "screen.steel_and_honor.menu.nav.citizens"),
    WAR("screen.steel_and_honor.menu.war.title", "screen.steel_and_honor.menu.nav.war"),
    HELP("screen.steel_and_honor.menu.help.title", "screen.steel_and_honor.menu.nav.help"),
    FLAG("screen.steel_and_honor.menu.flag.title", "screen.steel_and_honor.menu.nav.flag")
}

class PulsingButtonWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Text,
    onPress: () -> Unit
) : ButtonWidget(x, y, width, height, message, { onPress() }, DEFAULT_NARRATION_SUPPLIER) {

    private var tick = 0f

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        tick++

        val pulse = ((Math.sin(tick.toDouble()) + 1.0) / 2.0).toFloat()
        val alpha = if (active) (60 + pulse * 60).toInt() else 40
        val color = (alpha shl 24) or 0xFFAA4444.toInt()

        // Draw pulsing background
        context.fill(x - 2, y - 2, x + width + 2, y + height + 2, color)

        // Correct super call for 1.21.3
        super.renderWidget(context, mouseX, mouseY, delta)
    }
}

class SurrenderConfirmScreen(private val parent: Screen) :
    Screen(Text.translatable("screen.steel_and_honor.menu.war.surrender.confirm.title")) {

    override fun init() {
        val centerX = width / 2
        val baseY = height / 2 - 10

        val confirmBtn = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.war.surrender.confirm")
        ) {
            val player = MinecraftClient.getInstance().player
            player?.networkHandler?.sendCommand("kingdom war surrender")
            MinecraftClient.getInstance().setScreen(parent)
        }.dimensions(centerX - 80, baseY, 160, 20).build()

        val cancelBtn = ButtonWidget.builder(
            Text.translatable("screen.steel_and_honor.menu.war.surrender.cancel")
        ) {
            MinecraftClient.getInstance().setScreen(parent)
        }.dimensions(centerX - 80, baseY + 30, 160, 20).build()

        addDrawableChild(confirmBtn)
        addDrawableChild(cancelBtn)
    }

override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
    this.renderBackground(context)

    // FIXED: added missing delta argument
    super.render(context, mouseX, mouseY, delta)

    context.drawCenteredTextWithShadow(
        textRenderer,
        Text.translatable("screen.steel_and_honor.menu.war.surrender.prompt"),
        width / 2,
        height / 2 - 40,
        0xFFFFFF
    )
}

    override fun shouldPause() = false
}

