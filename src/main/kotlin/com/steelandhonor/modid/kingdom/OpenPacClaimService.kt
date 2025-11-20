package com.steelandhonor.modid.kingdom

import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.DyeColor
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI
import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI
import xaero.pac.common.server.api.OpenPACServerAPI
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI
import xaero.pac.common.server.parties.party.api.IServerPartyAPI
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI
import xaero.pac.common.parties.party.member.PartyMemberRank
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object OpenPacClaimService {
    private val registeredServers = Collections.newSetFromMap(ConcurrentHashMap<MinecraftServer, Boolean>())

    fun initialize(server: MinecraftServer, onClaimsChanged: () -> Unit) {
        val api = OpenPACServerAPI.get(server) ?: return
        if (registeredServers.add(server)) {
            api.serverClaimsManager.tracker.register(object : IClaimsManagerListenerAPI {
                override fun onWholeRegionChange(dimension: Identifier, regionX: Int, regionZ: Int) = onClaimsChanged()
                override fun onChunkChange(dimension: Identifier, chunkX: Int, chunkZ: Int, claim: xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI?) = onClaimsChanged()
                override fun onDimensionChange(dimension: Identifier) = onClaimsChanged()
            })
        }
    }

fun transferCityClaims(
    server: MinecraftServer,
    from: KingdomData,
    to: KingdomData,
    city: WarCity
) {
    // TODO: Implement real claim transfer via OpenPAC
}

fun transferAllClaimsTo(
    server: MinecraftServer,
    from: KingdomData,
    to: KingdomData
) {
    // TODO: Implement full kingdom claim transfer via OpenPAC
}


    fun collectSnapshots(
        server: MinecraftServer,
        kingdoms: Collection<KingdomData>,
        onClaimCountUpdated: (UUID, Int) -> Unit
    ): Map<UUID, List<CityBorderSnapshot>> {
        val api = OpenPACServerAPI.get(server) ?: return emptyMap()
        val partyManager = api.partyManager
        val claimsManager = api.serverClaimsManager
        val snapshots = mutableMapOf<UUID, MutableList<CityBorderSnapshot>>()
        kingdoms.forEach { kingdom ->
            val memberIds = resolvePartyMembers(kingdom, partyManager)
            var totalClaims = 0
            var capitalAssigned = false
            memberIds.forEach { memberId ->
                val info = claimsManager.getPlayerInfo(memberId) ?: return@forEach
                totalClaims += info.claimCount
                val playerSnapshots = buildSnapshotsForPlayer(info, kingdom, allowCapital = !capitalAssigned)
                if (playerSnapshots.isNotEmpty() && !capitalAssigned) {
                    capitalAssigned = true
                }
                val list = snapshots.getOrPut(kingdom.owner) { mutableListOf() }
                list.addAll(playerSnapshots)
            }
            onClaimCountUpdated(kingdom.owner, totalClaims)
        }
        return snapshots
    }

    fun resolvePartyId(server: MinecraftServer, owner: UUID): UUID? {
        val api = OpenPACServerAPI.get(server) ?: return null
        return api.partyManager.getPartyByOwner(owner)?.id
    }

    fun ensurePartyForKingdom(server: MinecraftServer, kingdom: KingdomData): IServerPartyAPI? {
        val api = OpenPACServerAPI.get(server) ?: return null
        val partyManager = api.partyManager
        var party = kingdom.partyId?.let { partyManager.getPartyById(it) }
            ?: partyManager.getPartyByOwner(kingdom.owner)
        if (party == null) {
            val owner = server.playerManager.getPlayer(kingdom.owner) ?: return null
            party = partyManager.createPartyForOwner(owner)
        }
        kingdom.partyId = party?.id
        return party
    }

    fun syncMembersToParty(server: MinecraftServer, kingdom: KingdomData) {
        val party = ensurePartyForKingdom(server, kingdom) ?: return
        kingdom.members.forEach { memberId ->
            ensureMember(server, party, memberId)
        }
        purgeNonMembers(party, kingdom)
    }

    fun addMemberToParty(server: MinecraftServer, kingdom: KingdomData, memberId: UUID) {
        val party = ensurePartyForKingdom(server, kingdom) ?: return
        ensureMember(server, party, memberId)
    }

    fun removeMemberFromParty(server: MinecraftServer, kingdom: KingdomData, memberId: UUID) {
        val api = OpenPACServerAPI.get(server) ?: return
        val party = kingdom.partyId?.let { api.partyManager.getPartyById(it) } ?: return
        val info = party.getMemberInfo(memberId) ?: return
        if (info.isOwner) return
        party.removeMember(memberId)
    }

    private fun ensureMember(server: MinecraftServer, party: IServerPartyAPI, memberId: UUID) {
        if (party.getMemberInfo(memberId) != null) return
        val name = lookupName(server, memberId) ?: return
        party.addMember(memberId, PartyMemberRank.MEMBER, name)
    }

    private fun purgeNonMembers(party: IServerPartyAPI, kingdom: KingdomData) {
        party.memberInfoStream.use { stream ->
            val iterator = stream.iterator()
            iterator.forEachRemaining { member ->
                if (!member.isOwner && !kingdom.members.contains(member.uuid)) {
                    party.removeMember(member.uuid)
                }
            }
        }
    }

    private fun resolvePartyMembers(kingdom: KingdomData, partyManager: IPartyManagerAPI): Set<UUID> {
        val party = kingdom.partyId?.let { partyManager.getPartyById(it) }
            ?: partyManager.getPartyByOwner(kingdom.owner)
        if (party != null) {
            kingdom.partyId = party.id
            val ids = mutableSetOf<UUID>()
            party.memberInfoStream.use { stream ->
                val iterator = stream.iterator()
                iterator.forEachRemaining { ids.add(it.uuid) }
            }
            return ids
        }
        return kingdom.members.toSet()
    }

    private fun buildSnapshotsForPlayer(
        info: IServerPlayerClaimInfoAPI,
        kingdom: KingdomData,
        allowCapital: Boolean
    ): List<CityBorderSnapshot> {
        val snapshots = mutableListOf<CityBorderSnapshot>()
        var capitalAvailable = allowCapital
        info.stream.use { dimensionStream ->
            val iterator = dimensionStream.iterator()
            iterator.forEachRemaining { entry ->
                val dimensionKey = RegistryKey.of(RegistryKeys.WORLD, entry.key)
                snapshots.addAll(
                    buildSnapshotsForDimension(
                        info,
                        kingdom,
                        dimensionKey,
                        entry.value,
                        capitalAvailable
                    ).also { created ->
                        if (capitalAvailable && created.isNotEmpty()) {
                            capitalAvailable = false
                        }
                    }
                )
            }
        }
        return snapshots
    }

    private fun buildSnapshotsForDimension(
        info: IServerPlayerClaimInfoAPI,
        kingdom: KingdomData,
        dimension: RegistryKey<World>,
        dimensionClaims: IPlayerDimensionClaimsAPI,
        allowCapital: Boolean
    ): List<CityBorderSnapshot> {
        val snapshots = mutableListOf<CityBorderSnapshot>()
        var capitalAvailable = allowCapital
        var index = 0
        dimensionClaims.stream.use { claimStream ->
            val iterator = claimStream.iterator()
            iterator.forEachRemaining { posList ->
                val chunks = posList.toChunkList()
                if (chunks.isEmpty()) return@forEachRemaining
                val snapshot = createSnapshot(
                    info = info,
                    kingdom = kingdom,
                    dimension = dimension,
                    chunks = chunks,
                    index = index,
                    isCapital = capitalAvailable && index == 0
                )
                snapshots.add(snapshot)
                index++
            }
        }
        return snapshots
    }

    private fun createSnapshot(
        info: IServerPlayerClaimInfoAPI,
        kingdom: KingdomData,
        dimension: RegistryKey<World>,
        chunks: List<ChunkPos>,
        index: Int,
        isCapital: Boolean
    ): CityBorderSnapshot {
        val minChunkX = chunks.minOf { it.x }
        val maxChunkX = chunks.maxOf { it.x }
        val minChunkZ = chunks.minOf { it.z }
        val maxChunkZ = chunks.maxOf { it.z }
        val (centerX, centerZ) = computeCenter(minChunkX, maxChunkX, minChunkZ, maxChunkZ)
        val radius = computeRadius(centerX, centerZ, chunks)
        val dye: DyeColor = kingdom.color
        return CityBorderSnapshot(
            id = UUID.nameUUIDFromBytes("${info.playerId}-${dimension.value}-$index".toByteArray()),
            name = kingdom.name,
            center = BlockPos(centerX, 64, centerZ),
            radius = radius,
            dimension = dimension,
            capital = isCapital,
            kingdomColor = dye,
            kingdomName = kingdom.name,
            minChunkX = minChunkX,
            maxChunkX = maxChunkX,
            minChunkZ = minChunkZ,
            maxChunkZ = maxChunkZ
        )
    }

    private fun computeCenter(minChunkX: Int, maxChunkX: Int, minChunkZ: Int, maxChunkZ: Int): Pair<Int, Int> {
        val avgX = ((minChunkX + maxChunkX + 1) * 8).toDouble()
        val avgZ = ((minChunkZ + maxChunkZ + 1) * 8).toDouble()
        return avgX.roundToInt() to avgZ.roundToInt()
    }

    private fun computeRadius(centerX: Int, centerZ: Int, chunks: List<ChunkPos>): Int {
        var maxDistance = 0.0
        chunks.forEach { chunk ->
            val x = chunk.startX + 8
            val z = chunk.startZ + 8
            val dx = (x - centerX).toDouble()
            val dz = (z - centerZ).toDouble()
            maxDistance = max(maxDistance, sqrt(dx.pow(2) + dz.pow(2)))
        }
        return (maxDistance + 24.0).roundToInt().coerceAtLeast(32)
    }

    private fun IPlayerClaimPosListAPI.toChunkList(): List<ChunkPos> {
        val chunks = mutableListOf<ChunkPos>()
        stream.use { chunkStream ->
            val iterator = chunkStream.iterator()
            iterator.forEachRemaining { chunks.add(it) }
        }
        return chunks
    }

    private fun lookupName(server: MinecraftServer, id: UUID): String? {
        server.playerManager.getPlayer(id)?.gameProfile?.name?.let { return it }
        val cached = server.userCache?.getByUuid(id)?.orElse(null)
        return cached?.name
    }
}
