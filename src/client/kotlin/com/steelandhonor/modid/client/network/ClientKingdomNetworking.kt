package com.steelandhonor.modid.client.network

import com.steelandhonor.modid.client.render.CityBorderRenderer
import com.steelandhonor.modid.client.gui.WarHudOverlay
import com.steelandhonor.modid.client.gui.SuggestionProvider
import com.steelandhonor.modid.network.KingdomNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

object ClientKingdomNetworking {
    fun register() {
        KingdomNetworking.registerCommonPayloads()
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncCityBordersPayload.ID) { payload, context ->
            val client = context.client()
            client.execute {
                CityBorderRenderer.update(payload.cities)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncWarStatusPayload.ID) { payload, context ->
            val client = context.client()
            client.execute {
                WarHudOverlay.update(payload.wars)
            }
        }
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
    }
}
