package com.steelandhonor.modid.client

import com.steelandhonor.modid.client.ambience.KingdomAmbienceController
import com.steelandhonor.modid.client.gui.WarHudOverlay
import com.steelandhonor.modid.client.gui.WarResultClient
import com.steelandhonor.modid.client.input.SteelKeyBindings
import com.steelandhonor.modid.client.network.ClientKingdomNetworking
import com.steelandhonor.modid.client.render.CityBorderRenderer
import com.steelandhonor.modid.network.KingdomNetworking
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

class SteelAndHonorClient : ClientModInitializer {
    override fun onInitializeClient() {

        // Networking (HUD, suggestions, borders, war results)
        ClientKingdomNetworking.register()

        // *** NEW: handle war end result → celebration + result menu ***
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncWarResultPayload.ID) { payload, context ->
            context.client().execute {
                WarResultClient.onWarResult(payload.result)
            }
        }

        // Registers the tick handler for celebration + automatic UI popup.
        WarResultClient.register()

        // Existing features
        CityBorderRenderer.register()
        KingdomAmbienceController.register()
        WarHudOverlay.register()
        SteelKeyBindings.register()
    }
}
