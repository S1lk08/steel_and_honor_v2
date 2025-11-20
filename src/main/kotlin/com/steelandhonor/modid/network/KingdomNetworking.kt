package com.steelandhonor.modid.network

import com.steelandhonor.modid.SteelAndHonorMod
import com.steelandhonor.modid.kingdom.CityBorderSnapshot
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.UUID

object KingdomNetworking {
    private var registered = false

    fun registerCommonPayloads() {
        if (registered) return
        registered = true
        PayloadTypeRegistry.playS2C().register(SyncCityBordersPayload.ID, SyncCityBordersPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncWarStatusPayload.ID, SyncWarStatusPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncSuggestionsPayload.ID, SyncSuggestionsPayload.CODEC)
    }

    fun registerServer() {
        registerCommonPayloads()
    }

    fun sendCityBorders(player: ServerPlayerEntity, snapshots: List<CityBorderSnapshot>) {
        val payload = buildCityPayload(snapshots)
        ServerPlayNetworking.send(player, payload)
    }

    fun broadcastCityBorders(players: List<ServerPlayerEntity>, snapshots: List<CityBorderSnapshot>) {
        if (players.isEmpty()) return
        val payload = buildCityPayload(snapshots)
        players.forEach { ServerPlayNetworking.send(it, payload) }
    }

    fun sendWarStatus(player: ServerPlayerEntity, wars: List<WarStatusEntry>) {
        val payload = SyncWarStatusPayload(wars)
        ServerPlayNetworking.send(player, payload)
    }
    
    fun sendSuggestions(player: ServerPlayerEntity, suggestions: SuggestionData) {
        val payload = SyncSuggestionsPayload(suggestions)
        ServerPlayNetworking.send(player, payload)
    }

    private fun buildCityPayload(snapshots: List<CityBorderSnapshot>): SyncCityBordersPayload {
        val cities = snapshots.map {
            CityBorderData(
                id = it.id,
                name = it.name,
                center = it.center,
                radius = it.radius,
                dimension = it.dimension.value,
                capital = it.capital,
                colorId = it.kingdomColor.id,
                kingdomName = it.kingdomName,
                minChunkX = it.minChunkX,
                maxChunkX = it.maxChunkX,
                minChunkZ = it.minChunkZ,
                maxChunkZ = it.maxChunkZ
            )
        }
        return SyncCityBordersPayload(cities)
    }
    data class SyncCityBordersPayload(val cities: List<CityBorderData>) : CustomPayload {
        override fun getId(): CustomPayload.Id<SyncCityBordersPayload> = ID

        companion object {
            val ID: CustomPayload.Id<SyncCityBordersPayload> =
                CustomPayload.Id(Identifier.of(SteelAndHonorMod.MOD_ID, "sync_city_borders"))
            val CODEC: PacketCodec<PacketByteBuf, SyncCityBordersPayload> =
                object : PacketCodec<PacketByteBuf, SyncCityBordersPayload> {
                    override fun encode(buf: PacketByteBuf, value: SyncCityBordersPayload) {
                        buf.writeVarInt(value.cities.size)
                        value.cities.forEach { city ->
                            buf.writeUuid(city.id)
                            buf.writeString(city.name)
                            buf.writeBlockPos(city.center)
                            buf.writeVarInt(city.radius)
                            buf.writeIdentifier(city.dimension)
                            buf.writeBoolean(city.capital)
                            buf.writeVarInt(city.colorId)
                            buf.writeString(city.kingdomName)
                            buf.writeVarInt(city.minChunkX)
                            buf.writeVarInt(city.maxChunkX)
                            buf.writeVarInt(city.minChunkZ)
                            buf.writeVarInt(city.maxChunkZ)
                        }
                    }

                    override fun decode(buf: PacketByteBuf): SyncCityBordersPayload {
                        val size = buf.readVarInt()
                        val entries = mutableListOf<CityBorderData>()
                        repeat(size) {
                            val id = buf.readUuid()
                            val name = buf.readString(64)
                            val center = buf.readBlockPos()
                            val radius = buf.readVarInt()
                            val dimension = buf.readIdentifier()
                            val capital = buf.readBoolean()
                            val colorId = buf.readVarInt()
                            val kingdomName = buf.readString(64)
                            val minChunkX = buf.readVarInt()
                            val maxChunkX = buf.readVarInt()
                            val minChunkZ = buf.readVarInt()
                            val maxChunkZ = buf.readVarInt()
                            entries.add(
                                CityBorderData(
                                    id,
                                    name,
                                    center,
                                    radius,
                                    dimension,
                                    capital,
                                    colorId,
                                    kingdomName,
                                    minChunkX,
                                    maxChunkX,
                                    minChunkZ,
                                    maxChunkZ
                                )
                            )
                        }
                        return SyncCityBordersPayload(entries)
                    }
                }
        }
    }

    data class CityBorderData(
        val id: UUID,
        val name: String,
        val center: BlockPos,
        val radius: Int,
        val dimension: Identifier,
        val capital: Boolean,
        val colorId: Int,
        val kingdomName: String,
        val minChunkX: Int,
        val maxChunkX: Int,
        val minChunkZ: Int,
        val maxChunkZ: Int
    )

    data class WarStatusEntry(
        val attackerName: String,
        val defenderName: String,
        val attackerColorId: Int,
        val defenderColorId: Int,
        val attackerKills: Int,
        val defenderKills: Int,
        val secondsRemaining: Int,
        val prepSecondsRemaining: Int
    )

    data class SyncWarStatusPayload(val wars: List<WarStatusEntry>) : CustomPayload {
        override fun getId(): CustomPayload.Id<SyncWarStatusPayload> = ID

        companion object {
            val ID: CustomPayload.Id<SyncWarStatusPayload> =
                CustomPayload.Id(Identifier.of(SteelAndHonorMod.MOD_ID, "sync_war_status"))
            val CODEC: PacketCodec<PacketByteBuf, SyncWarStatusPayload> =
                object : PacketCodec<PacketByteBuf, SyncWarStatusPayload> {
                    override fun encode(buf: PacketByteBuf, value: SyncWarStatusPayload) {
                        buf.writeVarInt(value.wars.size)
                        value.wars.forEach { war ->
                            buf.writeString(war.attackerName, 64)
                            buf.writeString(war.defenderName, 64)
                            buf.writeVarInt(war.attackerColorId)
                            buf.writeVarInt(war.defenderColorId)
                            buf.writeVarInt(war.attackerKills)
                            buf.writeVarInt(war.defenderKills)
                            buf.writeVarInt(war.secondsRemaining)
                            buf.writeVarInt(war.prepSecondsRemaining)
                        }
                    }

                    override fun decode(buf: PacketByteBuf): SyncWarStatusPayload {
                        val count = buf.readVarInt()
                        val wars = mutableListOf<WarStatusEntry>()
                        repeat(count) {
                            wars.add(
                                WarStatusEntry(
                                    attackerName = buf.readString(64),
                                    defenderName = buf.readString(64),
                                    attackerColorId = buf.readVarInt(),
                                    defenderColorId = buf.readVarInt(),
                                    attackerKills = buf.readVarInt(),
                                    defenderKills = buf.readVarInt(),
                                    secondsRemaining = buf.readVarInt(),
                                    prepSecondsRemaining = buf.readVarInt()
                                )
                            )
                        }
                        return SyncWarStatusPayload(wars)
                    }
                }
        }
    }
    
    data class SuggestionData(
        val kingdomNames: List<String>,
        val playerNames: List<String>,
        val warTargets: List<String>,
        val inviteTargets: List<String>,
        val warRequestTargets: List<String>
    )
    
    data class SyncSuggestionsPayload(val suggestions: SuggestionData) : CustomPayload {
        override fun getId(): CustomPayload.Id<SyncSuggestionsPayload> = ID
        
        companion object {
            val ID: CustomPayload.Id<SyncSuggestionsPayload> =
                CustomPayload.Id(Identifier.of(SteelAndHonorMod.MOD_ID, "sync_suggestions"))
            val CODEC: PacketCodec<PacketByteBuf, SyncSuggestionsPayload> =
                object : PacketCodec<PacketByteBuf, SyncSuggestionsPayload> {
                    override fun encode(buf: PacketByteBuf, value: SyncSuggestionsPayload) {
                        val data = value.suggestions
                        buf.writeCollection(data.kingdomNames) { b, s -> b.writeString(s, 64) }
                        buf.writeCollection(data.playerNames) { b, s -> b.writeString(s, 64) }
                        buf.writeCollection(data.warTargets) { b, s -> b.writeString(s, 64) }
                        buf.writeCollection(data.inviteTargets) { b, s -> b.writeString(s, 64) }
                        buf.writeCollection(data.warRequestTargets) { b, s -> b.writeString(s, 64) }
                    }
                    
                    override fun decode(buf: PacketByteBuf): SyncSuggestionsPayload {
                        val kingdomNames = buf.readCollection({ mutableListOf() }) { it.readString(64) }
                        val playerNames = buf.readCollection({ mutableListOf() }) { it.readString(64) }
                        val warTargets = buf.readCollection({ mutableListOf() }) { it.readString(64) }
                        val inviteTargets = buf.readCollection({ mutableListOf() }) { it.readString(64) }
                        val warRequestTargets = buf.readCollection({ mutableListOf() }) { it.readString(64) }
                        return SyncSuggestionsPayload(
                            SuggestionData(kingdomNames, playerNames, warTargets, inviteTargets, warRequestTargets)
                        )
                    }
                }
        }
    }
}
