package com.steelandhonor.modid.client.network

import com.steelandhonor.modid.client.render.CityBorderRenderer
import com.steelandhonor.modid.client.gui.WarHudOverlay
import com.steelandhonor.modid.client.gui.WarResultScreen
import com.steelandhonor.modid.client.gui.SuggestionProvider
import com.steelandhonor.modid.network.KingdomNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text

object ClientKingdomNetworking {

    fun register() {
        KingdomNetworking.registerCommonPayloads()

        // --- Sync City Borders ---
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncCityBordersPayload.ID) { payload, context ->
            val client = context.client()
            client.execute { CityBorderRenderer.update(payload.cities) }
        }

        // --- Sync War HUD ---
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncWarStatusPayload.ID) { payload, context ->
            val client = context.client()
            client.execute { WarHudOverlay.update(payload.wars) }
        }

        // --- Sync Suggestions ---
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncSuggestionsPayload.ID) { payload, context ->
            val client = context.client()
            client.execute {
                val d = payload.suggestions
                SuggestionProvider.updateKingdomNames(d.kingdomNames)
                SuggestionProvider.updatePlayerNames(d.playerNames)
                SuggestionProvider.updateWarTargets(d.warTargets)
                SuggestionProvider.updateInviteTargets(d.inviteTargets)
                SuggestionProvider.updateWarRequestTargets(d.warRequestTargets)
            }
        }

        // --- Sync War Results (new packet) ---
        ClientPlayNetworking.registerGlobalReceiver(KingdomNetworking.SyncWarResultPayload.ID) { payload, context ->
            val client = context.client()
            val result = payload.result

            client.execute {

                // Clear HUD immediately
                WarHudOverlay.clear()

                val player = MinecraftClient.getInstance().player
                if (player != null) {
                    // Horn
                    player.playSound(SoundEvents.ITEM_GOAT_HORN_SOUND_0, 1f, 1f)

                    // Title splash
                    player.sendMessage(Text.literal("§6§l${result.attackerName} vs ${result.defenderName} — War Complete!"), true)
                }

                // Delay 100 ticks → open results
                client.send {
                    client.setScreen(
                        WarResultScreen(result)
                    )
                }
            }
        }
    }
}
