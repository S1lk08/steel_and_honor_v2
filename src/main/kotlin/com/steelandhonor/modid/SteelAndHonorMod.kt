package com.steelandhonor.modid

import com.steelandhonor.modid.command.KingdomCommand
import com.steelandhonor.modid.kingdom.KingdomManager
import com.steelandhonor.modid.network.KingdomNetworking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.text.Text
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Main mod class for Steel & Honor.
 * 
 * Steel & Honor is a comprehensive medieval kingdom management mod that allows players to:
 * - Create and manage kingdoms with role-based permissions
 * - Claim and defend territories with visual borders
 * - Engage in strategic warfare with preparation and active phases
 * - Customize kingdom appearance with banners and colors
 * 
 * @author S1lk08
 * @since 1.0.0
 */
class SteelAndHonorMod : ModInitializer {
    override fun onInitialize() {
        LOGGER.info("[Steel & Honor] Initializing mod...")

        ServerLifecycleEvents.SERVER_STARTED.register { server -> KingdomManager.initialize(server) }
        ServerLifecycleEvents.SERVER_STOPPING.register { server -> KingdomManager.saveNow() }
        ServerTickEvents.END_SERVER_TICK.register { server -> KingdomManager.tick(server) }
        KingdomNetworking.registerServer()
        KingdomCommand.register()
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            val killer = source.attacker as? ServerPlayerEntity ?: return@register
            val victim = entity as? ServerPlayerEntity ?: return@register
            KingdomManager.recordKill(killer, victim)
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            KingdomManager.applyToPlayer(handler.player)
            KingdomManager.sendBorderDataTo(handler.player)
            KingdomManager.sendSuggestionsTo(handler.player)
            handler.player.sendMessage(Text.translatable("text.steel_and_honor.keybind.tip"), false)
        }

        LOGGER.info("[Steel & Honor] Initialization complete!")
    }

    companion object {
        const val MOD_ID = "steel_and_honor"
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
