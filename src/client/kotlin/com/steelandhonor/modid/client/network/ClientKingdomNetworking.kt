package com.steelandhonor.modid.client.network

import com.steelandhonor.modid.client.gui.SuggestionProvider
import com.steelandhonor.modid.client.gui.WarHudOverlay
import com.steelandhonor.modid.client.gui.WarResultScreen
import com.steelandhonor.modid.client.gui.WarResultClient
import com.steelandhonor.modid.client.render.CityBorderRenderer
import com.steelandhonor.modid.network.KingdomNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.text.Text

object ClientKingdomNetworking {
    fun register() {
        // Register payload types client-side
        KingdomNetworking.registerCommonPayloads()

        // City borders
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncCityBordersPayload.ID) { payload, context ->
            val client = context.client()
            client.execute {
                CityBorderRenderer.update(payload.cities)
            }
        }

        // War HUD status
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncWarStatusPayload.ID) { payload, context ->
            val client = context.client()
            client.execute {
                WarHudOverlay.update(payload.wars)
            }
        }

        // Suggestions
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncSuggestionsPayload.ID) { payload, context ->
            val client = context.client()
            client.execute {
                val data = payload.suggestions
                SuggestionProvider.updateKingdomNames(data.kingdomNames)
                SuggestionProvider.updatePlayerNames(data.playerNames)
                SuggestionProvider.updateWarTargets(data.warTargets)
                SuggestionProvider.updateInviteTargets(data.inviteTargets)
                SuggestionProvider.updateWarRequestTargets(data.warRequestTargets)
            }
        }

        // War results
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncWarResultPayload.ID) { payload, context ->
            val client = context.client()
            client.execute {
                val result = payload.result

                // Clear war HUD
                WarHudOverlay.update(emptyList())

                // Play horn
                val mc = MinecraftClient.getInstance()
                val player = mc.player ?: return@execute
                val horn = SoundEvent.of(Identifier.of("minecraft", "item.goat_horn.sound.0"))
                player.playSound(horn, 1f, 1f)

                // Show celebration + screen
                WarResultClient.onWarResult(result)
            }
        }
    }
}
