package com.steelandhonor.modid.client

import com.steelandhonor.modid.client.ambience.KingdomAmbienceController
import com.steelandhonor.modid.client.gui.WarHudOverlay
import com.steelandhonor.modid.client.input.SteelKeyBindings
import com.steelandhonor.modid.client.network.ClientKingdomNetworking
import com.steelandhonor.modid.client.render.CityBorderRenderer
import net.fabricmc.api.ClientModInitializer

class SteelAndHonorClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientKingdomNetworking.register()
        CityBorderRenderer.register()
        KingdomAmbienceController.register()
        WarHudOverlay.register()
        SteelKeyBindings.register()
    }
}
