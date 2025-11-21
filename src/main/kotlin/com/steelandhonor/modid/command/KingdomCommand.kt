package com.steelandhonor.modid.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.steelandhonor.modid.kingdom.KingdomManager
import com.steelandhonor.modid.kingdom.KingdomOperationException
import com.steelandhonor.modid.kingdom.KingdomRole
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.DyeColor

object KingdomCommand {
    private val colorSuggestions = SuggestionProvider<ServerCommandSource> { _, builder ->
        DyeColor.entries.forEach { builder.suggest(it.getName()) }
        builder.buildFuture()
    }
    private val roleSuggestions = SuggestionProvider<ServerCommandSource> { _, builder ->
        KingdomRole.entries.forEach { builder.suggest(it.name.lowercase()) }
        builder.buildFuture()
    }
    private val memberSuggestions = SuggestionProvider<ServerCommandSource> { context, builder ->
        val player = context.source.player
        if (player != null) {
            KingdomManager.getMemberNameSuggestions(player).forEach { builder.suggest(it) }
        }
        builder.buildFuture()
    }
    private val inviteSuggestions = SuggestionProvider<ServerCommandSource> { context, builder ->
        val player = context.source.player
        if (player != null) {
            KingdomManager.pendingInviteNames(player).forEach { builder.suggest(it) }
        }
        builder.buildFuture()
    }
    private val rivalKingdomSuggestions = SuggestionProvider<ServerCommandSource> { context, builder ->
        val requester = context.source.player
        KingdomManager.getRivalKingdomNames(requester?.uuid).forEach { builder.suggest(it) }
        builder.buildFuture()
    }
    private val kingdomNameSuggestions = SuggestionProvider<ServerCommandSource> { _, builder ->
        KingdomManager.allKingdomNames().forEach { builder.suggest(it) }
        builder.buildFuture()
    }
    private val warTargetSuggestions = SuggestionProvider<ServerCommandSource> { _, builder ->
        KingdomManager.warEligibleTargets().forEach { builder.suggest(it) }
        builder.buildFuture()
    }
    private val warRequestSuggestions = SuggestionProvider<ServerCommandSource> { context, builder ->
        val player = context.source.player
        if (player != null) {
            KingdomManager.pendingWarRequestNames(player).forEach { builder.suggest(it) }
        }
        builder.buildFuture()
    }
    val helpEntries = listOf(
        HelpEntry("/kingdom create <name> <color>", "command.steel_and_honor.kingdom.help.create"),
        HelpEntry("/kingdom rename <name>", "command.steel_and_honor.kingdom.help.rename"),
        HelpEntry("/kingdom color <color>", "command.steel_and_honor.kingdom.help.color"),
        HelpEntry("/kingdom flag", "command.steel_and_honor.kingdom.help.flag"),
        HelpEntry("/kingdom role set <player> <role>", "command.steel_and_honor.kingdom.help.role"),
        HelpEntry("/kingdom invite <player>", "command.steel_and_honor.kingdom.help.invite"),
        HelpEntry("/kingdom join <kingdom>", "command.steel_and_honor.kingdom.help.join"),
        HelpEntry("/kingdom leave", "command.steel_and_honor.kingdom.help.leave"),
        HelpEntry("/kingdom war declare <kingdom>", "command.steel_and_honor.kingdom.help.war"),
        HelpEntry("/kingdom info", "command.steel_and_honor.kingdom.help.info")
    )

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                CommandManager.literal("kingdom")
                    .requires { source -> source.entity is ServerPlayerEntity }
                    .then(createNode())
                    .then(renameNode())
                    .then(colorNode())
                    .then(roleNode())
                    .then(inviteNode())
                    .then(joinNode())
                    .then(forceJoinNode())
                    .then(leaveNode())
                    .then(flagNode())
                    .then(warNode())
                    .then(infoNode())
                    .then(helpNode())
            )
        }
    }

    private fun createNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("create")
            .then(CommandManager.argument("name", StringArgumentType.string())
                .then(CommandManager.argument("color", StringArgumentType.word())
                    .suggests(colorSuggestions)
                    .executes { ctx ->
                        val player = ctx.source.playerOrThrow
                        runOp(ctx) {
                            val color = parseColor(StringArgumentType.getString(ctx, "color"))
                            KingdomManager.createKingdom(player, StringArgumentType.getString(ctx, "name"), color)
                            ctx.source.sendFeedback(
                                { Text.translatable("command.steel_and_honor.kingdom.create.success", color.getName()) },
                                true
                            )
                            1
                        }
                    }))
    }

    private fun roleNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("role")
            .then(
                CommandManager.literal("set")
                    .then(
                        CommandManager.argument("player", EntityArgumentType.player())
                            .suggests(memberSuggestions)
                            .then(
                                CommandManager.argument("role", StringArgumentType.word())
                                    .suggests(roleSuggestions)
                                    .executes { ctx ->
                                val executor = ctx.source.playerOrThrow
                                val target = EntityArgumentType.getPlayer(ctx, "player")
                                runOp(ctx) {
                                            val role = KingdomRole.fromName(StringArgumentType.getString(ctx, "role"))
                                                ?: throw KingdomOperationException("command.steel_and_honor.kingdom.role.unknown")
                                            KingdomManager.assignRole(executor, target, role)
                                            ctx.source.sendFeedback(
                                                { Text.translatable("command.steel_and_honor.kingdom.role.set", target.gameProfile.name, role.name) },
                                                true
                                            )
                                            1
                                        }
                                    }
                            )
                    )
            )
    }

    private fun inviteNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("invite")
            .then(
                CommandManager.argument("player", EntityArgumentType.player())
                    .executes { ctx ->
                        val executor = ctx.source.playerOrThrow
                        val target = EntityArgumentType.getPlayer(ctx, "player")
                        runOp(ctx) {
                            KingdomManager.invitePlayer(executor, target)
                            ctx.source.sendFeedback(
                                { Text.translatable("command.steel_and_honor.kingdom.invite.sent", target.gameProfile.name) },
                                true
                            )
                            1
                        }
                    }
            )
    }

    private fun joinNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("join")
            .then(
                CommandManager.argument("kingdom", StringArgumentType.greedyString())
                    .suggests(inviteSuggestions)
                    .executes { ctx ->
                        val player = ctx.source.playerOrThrow
                        runOp(ctx) {
                            KingdomManager.joinKingdom(player, StringArgumentType.getString(ctx, "kingdom"))
                            1
                        }
                    }
            )
    }

    private fun forceJoinNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("forcejoin")
            .requires { it.hasPermissionLevel(2) }
            .then(
                CommandManager.argument("player", EntityArgumentType.player())
                    .then(
                        CommandManager.argument("kingdom", StringArgumentType.greedyString())
                            .suggests(kingdomNameSuggestions)
                            .executes { ctx ->
                                val target = EntityArgumentType.getPlayer(ctx, "player")
                                runOp(ctx) {
                                    val assigned = KingdomManager.forceAssignToKingdom(target, StringArgumentType.getString(ctx, "kingdom"))
                                    ctx.source.sendFeedback(
                                        { Text.translatable("command.steel_and_honor.kingdom.forcejoin.success", target.gameProfile.name, assigned) },
                                        true
                                    )
                                    1
                                }
                            }
                    )
            )
    }

    private fun leaveNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("leave")
            .executes { ctx ->
                val player = ctx.source.playerOrThrow
                runOp(ctx) {
                    KingdomManager.leaveKingdom(player)
                    ctx.source.sendFeedback({ Text.translatable("command.steel_and_honor.kingdom.leave.success") }, true)
                    1
                }
            }
    }

    private fun flagNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("flag")
            .executes { ctx ->
                val player = ctx.source.playerOrThrow
                runOp(ctx) {
                    if (KingdomManager.getRole(player.uuid) != KingdomRole.LEADER) {
                        throw KingdomOperationException("command.steel_and_honor.kingdom.flag.no_permission")
                    }
                    KingdomManager.updateDesignFromBanner(player)
                    ctx.source.sendFeedback({ Text.translatable("command.steel_and_honor.kingdom.flag.updated") }, true)
                    1
                }
            }
    }

private fun warNode(): ArgumentBuilder<ServerCommandSource, *> {
    return CommandManager.literal("war")
        .then(
            CommandManager.literal("declare")
                .then(CommandManager.argument("kingdom", StringArgumentType.string())
                    .suggests(rivalKingdomSuggestions)
                    .executes { ctx ->
                        val player = ctx.source.playerOrThrow
                        runOp(ctx) {
                            val target = StringArgumentType.getString(ctx, "kingdom")
                            KingdomManager.declareWar(player, target)
                            ctx.source.sendFeedback(
                                { Text.translatable("command.steel_and_honor.kingdom.war.declared", target) },
                                true
                            )
                            1
                        }
                    })
        )
        .then(warRequestNode())
        .then(warApproveNode())
        .then(warDenyNode())

        // ⭐ NEW LINE: Add surrender node
        .then(warSurrenderNode())
}

private fun warSurrenderNode(): ArgumentBuilder<ServerCommandSource, *> {
    return CommandManager.literal("surrender")
        .executes { ctx ->
            val player = ctx.source.playerOrThrow
            runOp(ctx) {
                KingdomManager.surrender(player)
                ctx.source.sendFeedback(
                    { Text.translatable("command.steel_and_honor.kingdom.war.surrender.success") },
                    true
                )
                1
            }
        }
}

    private fun warRequestNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("request")
            .then(
                CommandManager.argument("kingdom", StringArgumentType.greedyString())
                    .suggests(warTargetSuggestions)
                    .executes { ctx ->
                        val player = ctx.source.playerOrThrow
                        runOp(ctx) {
                            val target = KingdomManager.requestWarAssistance(player, StringArgumentType.getString(ctx, "kingdom"))
                            ctx.source.sendFeedback(
                                { Text.translatable("command.steel_and_honor.kingdom.war.request.confirm", target) },
                                true
                            )
                            1
                        }
                    }
            )
    }

    private fun warApproveNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("approve")
            .then(
                CommandManager.argument("kingdom", StringArgumentType.greedyString())
                    .suggests(warRequestSuggestions)
                    .executes { ctx ->
                        val player = ctx.source.playerOrThrow
                        runOp(ctx) {
                            val (requester, _) = KingdomManager.respondToWarRequest(player, StringArgumentType.getString(ctx, "kingdom"), true)
                            ctx.source.sendFeedback(
                                { Text.translatable("command.steel_and_honor.kingdom.war.request.approve.confirm", requester) },
                                true
                            )
                            1
                        }
                    }
            )
    }

    private fun warDenyNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("deny")
            .then(
                CommandManager.argument("kingdom", StringArgumentType.greedyString())
                    .suggests(warRequestSuggestions)
                    .executes { ctx ->
                        val player = ctx.source.playerOrThrow
                        runOp(ctx) {
                            val (requester, _) = KingdomManager.respondToWarRequest(player, StringArgumentType.getString(ctx, "kingdom"), false)
                            ctx.source.sendFeedback(
                                { Text.translatable("command.steel_and_honor.kingdom.war.request.deny.confirm", requester) },
                                true
                            )
                            1
                        }
                    }
            )
    }

    private fun colorNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("color")
            .then(
                CommandManager.argument("color", StringArgumentType.word())
                    .suggests(colorSuggestions)
                    .executes { ctx ->
                        val player = ctx.source.playerOrThrow
                        runOp(ctx) {
                            val color = parseColor(StringArgumentType.getString(ctx, "color"))
                            val applied = KingdomManager.updateKingdomColor(player, color)
                            ctx.source.sendFeedback(
                                { Text.translatable("command.steel_and_honor.kingdom.color.updated", applied.getName()) },
                                true
                            )
                            1
                        }
                    }
            )
    }

    private fun renameNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("rename")
            .then(
                CommandManager.argument("name", StringArgumentType.greedyString())
                    .executes { ctx ->
                        val player = ctx.source.playerOrThrow
                        runOp(ctx) {
                            val newName = KingdomManager.renameKingdom(player, StringArgumentType.getString(ctx, "name"))
                            ctx.source.sendFeedback(
                                { Text.translatable("command.steel_and_honor.kingdom.rename.success", newName) },
                                true
                            )
                            1
                        }
                    }
            )
    }

    private fun infoNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("info")
            .executes { ctx ->
                val player = ctx.source.playerOrThrow
                val role = KingdomManager.getRole(player.uuid)
                val status = KingdomManager.statusTextFor(player.uuid)
                ctx.source.sendFeedback({ Text.translatable("command.steel_and_honor.kingdom.info", status, role.name) }, false)
                1
            }
    }

    private fun parseColor(input: String): DyeColor {
        return DyeColor.byName(input.lowercase(), null) ?: throw KingdomOperationException("command.steel_and_honor.kingdom.invalid_color")
    }

    private fun runOp(context: CommandContext<ServerCommandSource>, block: () -> Int): Int {
        return try {
            block()
        } catch (ex: KingdomOperationException) {
            context.source.sendError(Text.translatable(ex.translationKey))
            0
        }
    }

    private fun helpNode(): ArgumentBuilder<ServerCommandSource, *> {
        return CommandManager.literal("help")
            .executes { ctx ->
                ctx.source.sendFeedback({ Text.translatable("command.steel_and_honor.kingdom.help.header") }, false)
                helpEntries.forEach { entry ->
                    val description = Text.translatable(entry.descriptionKey)
                    ctx.source.sendFeedback(
                        { Text.translatable("command.steel_and_honor.kingdom.help.entry", entry.usage, description) },
                        false
                    )
                }
                1
            }
    }

    data class HelpEntry(val usage: String, val descriptionKey: String)
}
