package com.steelandhonor.modid.kingdom

import com.steelandhonor.modid.SteelAndHonorMod
import com.steelandhonor.modid.network.KingdomNetworking
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.BannerItem
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtSizeTracker
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.DyeColor
import net.minecraft.util.Identifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

object KingdomManager {
    private val kingdoms: MutableMap<UUID, KingdomData> = mutableMapOf()
    private val memberLookup: MutableMap<UUID, UUID> = mutableMapOf()
    private val wars: MutableSet<WarState> = mutableSetOf()
    private val warJoinRequests: MutableList<WarJoinRequest> = mutableListOf()
    private val invites: MutableMap<UUID, MutableSet<UUID>> = mutableMapOf()

    private var savePath: Path? = null
    private var dirty = false
    private var autosaveTimer = 0

    private const val AUTOSAVE_INTERVAL = 20 * 60 * 5
    private const val BORDER_SYNC_DELAY_TICKS = 40
    private const val MIN_NAME_LENGTH = 3
    private const val MAX_NAME_LENGTH = 32
    private val KINGDOM_NAME_PATTERN = Regex("^[A-Za-z0-9 .,'-]+$")
    private const val GENERIC_PERMISSION_KEY = "command.steel_and_honor.kingdom.no_permission"

    // 20 minutes prep, 90 minutes war
    const val WAR_PREP_TIME_TICKS = 20 * 60 * 20L  // 20 min
    const val WAR_DURATION_TICKS = 20 * 60 * 90L   // 90 min

    // You can surrender 25 minutes after war begins (post-prep)
    const val MIN_ACTIVE_WAR_TICKS_BEFORE_SURRENDER = 20 * 60 * 25L

    // Scoring
    const val CITY_CAPTURE_POINTS = 1000

    // Capture / city control logic
    private const val WAR_HUD_INTERVAL_TICKS = 20
    private const val CAPTURE_TICKS_INTERVAL = 20      // once per second
    private const val CAPTURE_RATE_PER_SECOND = 0.02   // 0 -> 1 in ~50s
    private const val CAPTURE_DECAY_PER_SECOND = 0.02

    private var borderDirty = true
    private var borderDelay = 0
    private var warHudDelay = 0

    fun initialize(server: MinecraftServer) {
        val basePath = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("steel_and_honor")
        Files.createDirectories(basePath)
        savePath = basePath.resolve("kingdoms.dat")
        load()

        kingdoms.values.forEach { data ->
            if (data.partyId == null) {
                data.partyId = OpenPacClaimService.resolvePartyId(server, data.owner)
            }
            OpenPacClaimService.ensurePartyForKingdom(server, data)
            OpenPacClaimService.syncMembersToParty(server, data)
        }

        OpenPacClaimService.initialize(server) { notifyClaimsChanged() }

        // Restore wars + initialize cities for each war based on current claims
        wars.forEach { war ->
            if (war.startTick == 0L) {
                war.startTick = server.overworld.time
            }
            val attackerData = kingdoms[war.attacker]
            val defenderData = kingdoms[war.defender]
            if (attackerData != null && defenderData != null) {
                initializeWarCities(server, war, attackerData, defenderData)
            }
        }

        borderDirty = true
        borderDelay = 0
    }

    fun tick(server: MinecraftServer) {
        if (dirty) {
            autosaveTimer++
            if (autosaveTimer >= AUTOSAVE_INTERVAL) {
                saveNow()
                autosaveTimer = 0
            }
        }

        updateWars(server)

        warHudDelay++
        if (warHudDelay >= WAR_HUD_INTERVAL_TICKS) {
            sendWarHudUpdates(server)
            warHudDelay = 0
        }

        if (borderDelay > 0) {
            borderDelay--
        }
        if (borderDirty && borderDelay <= 0) {
            broadcastBorders(server)
        }
    }

    // ------------------------------
    // CLAIM / BORDER SYNC
    // ------------------------------

    private fun broadcastBorders(server: MinecraftServer) {
        val players = server.playerManager.playerList
        if (players.isEmpty()) {
            borderDirty = false
            return
        }
        val snapshots = collectCitySnapshots(server)
        KingdomNetworking.broadcastCityBorders(players, snapshots)
        borderDirty = false
    }

    private fun collectCitySnapshots(server: MinecraftServer): List<CityBorderSnapshot> {
        val perOwner = OpenPacClaimService.collectSnapshots(server, kingdoms.values) { owner, claimCount ->
            kingdoms[owner]?.claimCount = claimCount
        }
        val snapshots = mutableListOf<CityBorderSnapshot>()
        val occupied = mutableListOf<ClaimedArea>()

        perOwner.entries.sortedBy { kingdoms[it.key]?.name ?: it.key.toString() }.forEach { (owner, list) ->
            val kingdom = kingdoms[owner] ?: return@forEach
            val allowed = allowedClaimZones(kingdom.members.size)
            val trimmed = if (list.size <= allowed) list else list.take(allowed)
            if (list.size > allowed) {
                sendClaimLimitWarning(server, kingdom, allowed, list.size)
            }
            trimmed.forEach { snapshot ->
                val area = ClaimedArea(
                    owner,
                    snapshot.dimension.value,
                    snapshot.minChunkX,
                    snapshot.maxChunkX,
                    snapshot.minChunkZ,
                    snapshot.maxChunkZ
                )
                if (occupied.any { it.dimension == area.dimension && it.owner != owner && it.intersects(area) }) {
                    sendOverlapWarning(server, kingdom, snapshot.dimension.value)
                    return@forEach
                }
                occupied.add(area)
                snapshots.add(snapshot.copy(name = kingdom.name))
            }
        }
        return snapshots
    }

    private fun markBordersDirty() {
        borderDirty = true
        borderDelay = BORDER_SYNC_DELAY_TICKS
    }

    private fun notifyClaimsChanged() {
        markBordersDirty()
    }

    fun sendBorderDataTo(player: ServerPlayerEntity) {
        KingdomNetworking.sendCityBorders(player, collectCitySnapshots(player.server))
    }

    // ------------------------------
    // SUGGESTIONS / NETWORK
    // ------------------------------

    fun sendSuggestionsTo(player: ServerPlayerEntity) {
        val kingdomNames = allKingdomNames()
        val playerNames = getMemberNameSuggestions(player)
        val warTargets = warEligibleTargets()
        val inviteTargets = pendingInviteNames(player)
        val warRequestTargets = pendingWarRequestNames(player)
        val suggestions = KingdomNetworking.SuggestionData(
            kingdomNames = kingdomNames,
            playerNames = playerNames,
            warTargets = warTargets,
            inviteTargets = inviteTargets,
            warRequestTargets = warRequestTargets
        )
        KingdomNetworking.sendSuggestions(player, suggestions)
    }

    // ------------------------------
    // SAVE / LOAD
    // ------------------------------

    fun saveNow() {
        val path = savePath ?: return
        val nbt = NbtCompound()

        val kingdomList = NbtList()
        kingdoms.forEach { (owner, data) ->
            val entry = NbtCompound()
            entry.putUuid("Owner", owner)
            entry.putString("Name", data.name)
            entry.putString("Color", data.color.getName())
            entry.put("Design", data.design.toNbt())

            val members = NbtList()
            data.members.forEach { memberId ->
                val memberTag = NbtCompound()
                memberTag.putUuid("Id", memberId)
                members.add(memberTag)
            }
            entry.put("Members", members)

            val roles = NbtList()
            data.roles.forEach { (memberId, role) ->
                val roleTag = NbtCompound()
                roleTag.putUuid("Id", memberId)
                roleTag.putString("Role", role.name)
                roles.add(roleTag)
            }
            entry.put("Roles", roles)

            data.partyId?.let { entry.putUuid("PartyId", it) }
            entry.putInt("ClaimCount", data.claimCount)
            kingdomList.add(entry)
        }
        nbt.put("Kingdoms", kingdomList)

        val warList = NbtList()
        wars.forEach { war ->
            val tag = NbtCompound()
            tag.putUuid("Attacker", war.attacker)
            tag.putUuid("Defender", war.defender)

            val attackerAllies = NbtList()
            war.attackerAllies.forEach { ally ->
                val allyTag = NbtCompound()
                allyTag.putUuid("Id", ally)
                attackerAllies.add(allyTag)
            }
            val defenderAllies = NbtList()
            war.defenderAllies.forEach { ally ->
                val allyTag = NbtCompound()
                allyTag.putUuid("Id", ally)
                defenderAllies.add(allyTag)
            }
            tag.put("AttackerAllies", attackerAllies)
            tag.put("DefenderAllies", defenderAllies)

            tag.putInt("AttackerKills", war.attackerKills)
            tag.putInt("DefenderKills", war.defenderKills)
            tag.putInt("AttackerScore", war.attackerScore)
            tag.putInt("DefenderScore", war.defenderScore)
            tag.putLong("StartTick", war.startTick)

            warList.add(tag)
        }
        nbt.put("Wars", warList)

        val requestList = NbtList()
        warJoinRequests.forEach { request ->
            val requestTag = NbtCompound()
            requestTag.putUuid("Requester", request.requester)
            requestTag.putUuid("Attacker", request.attacker)
            requestTag.putUuid("Defender", request.defender)
            requestTag.putString("Side", request.side.name)
            requestList.add(requestTag)
        }
        nbt.put("WarRequests", requestList)

        val inviteList = NbtList()
        invites.forEach { (playerId, owners) ->
            owners.forEach { ownerId ->
                val inviteTag = NbtCompound()
                inviteTag.putUuid("Player", playerId)
                inviteTag.putUuid("Kingdom", ownerId)
                inviteList.add(inviteTag)
            }
        }
        nbt.put("Invites", inviteList)

        Files.newOutputStream(path).use { output ->
            NbtIo.writeCompressed(nbt, output)
        }
        dirty = false
        SteelAndHonorMod.LOGGER.info("Saved {} kingdoms and {} wars.", kingdoms.size, wars.size)
    }

    private fun load() {
        kingdoms.clear()
        memberLookup.clear()
        wars.clear()
        warJoinRequests.clear()

        val path = savePath ?: return
        if (!Files.exists(path)) {
            dirty = false
            return
        }

        runCatching {
            Files.newInputStream(path).use { input ->
                val nbt = NbtIo.readCompressed(input, NbtSizeTracker.ofUnlimitedBytes())

                // Kingdoms
                val list = nbt.getList("Kingdoms", NbtElement.COMPOUND_TYPE.toInt())
                list.forEach { element ->
                    if (element !is NbtCompound || !element.containsUuid("Owner")) return@forEach
                    val owner = element.getUuid("Owner")
                    val name = element.getString("Name")
                    val color = DyeColor.byName(element.getString("Color"), DyeColor.WHITE) ?: DyeColor.WHITE
                    val design = if (element.contains("Design", NbtElement.COMPOUND_TYPE.toInt())) {
                        KingdomDesign.fromNbt(element.getCompound("Design"))
                    } else KingdomDesign.DEFAULT
                    val members = mutableSetOf<UUID>()
                    element.getList("Members", NbtElement.COMPOUND_TYPE.toInt()).forEach { memberTag ->
                        if (memberTag is NbtCompound && memberTag.containsUuid("Id")) {
                            members.add(memberTag.getUuid("Id"))
                        }
                    }
                    if (members.isEmpty()) members.add(owner)

                    val roles = mutableMapOf<UUID, KingdomRole>()
                    element.getList("Roles", NbtElement.COMPOUND_TYPE.toInt()).forEach { roleTag ->
                        if (roleTag is NbtCompound && roleTag.containsUuid("Id")) {
                            val role = KingdomRole.fromName(roleTag.getString("Role")) ?: KingdomRole.CITIZEN
                            roles[roleTag.getUuid("Id")] = role
                        }
                    }
                    if (roles[owner] == null) {
                        roles[owner] = KingdomRole.LEADER
                    }
                    val partyId =
                        if (element.containsUuid("PartyId")) element.getUuid("PartyId") else null
                    val claimCount =
                        if (element.contains("ClaimCount", NbtElement.INT_TYPE.toInt())) element.getInt("ClaimCount") else 0

                    val data = KingdomData(
                        owner = owner,
                        name = name,
                        color = color,
                        design = design,
                        members = members,
                        roles = roles,
                        partyId = partyId,
                        claimCount = claimCount
                    )
                    kingdoms[owner] = data
                    members.forEach { memberLookup[it] = owner }
                }

                // Wars
                nbt.getList("Wars", NbtElement.COMPOUND_TYPE.toInt()).forEach { warTag ->
                    if (warTag is NbtCompound && warTag.containsUuid("Attacker") && warTag.containsUuid("Defender")) {
                        val attackerAllies = mutableSetOf<UUID>()
                        warTag.getList("AttackerAllies", NbtElement.COMPOUND_TYPE.toInt()).forEach { allyTag ->
                            if (allyTag is NbtCompound && allyTag.containsUuid("Id")) {
                                attackerAllies.add(allyTag.getUuid("Id"))
                            }
                        }
                        val defenderAllies = mutableSetOf<UUID>()
                        warTag.getList("DefenderAllies", NbtElement.COMPOUND_TYPE.toInt()).forEach { allyTag ->
                            if (allyTag is NbtCompound && allyTag.containsUuid("Id")) {
                                defenderAllies.add(allyTag.getUuid("Id"))
                            }
                        }
                        val attackerKills =
                            if (warTag.contains("AttackerKills", NbtElement.INT_TYPE.toInt()))
                                warTag.getInt("AttackerKills") else 0
                        val defenderKills =
                            if (warTag.contains("DefenderKills", NbtElement.INT_TYPE.toInt()))
                                warTag.getInt("DefenderKills") else 0
                        val attackerScore =
                            if (warTag.contains("AttackerScore", NbtElement.INT_TYPE.toInt()))
                                warTag.getInt("AttackerScore") else 0
                        val defenderScore =
                            if (warTag.contains("DefenderScore", NbtElement.INT_TYPE.toInt()))
                                warTag.getInt("DefenderScore") else 0
                        val startTick =
                            if (warTag.contains("StartTick", NbtElement.LONG_TYPE.toInt()))
                                warTag.getLong("StartTick") else 0L

                        wars.add(
                            WarState(
                                attacker = warTag.getUuid("Attacker"),
                                defender = warTag.getUuid("Defender"),
                                attackerAllies = attackerAllies,
                                defenderAllies = defenderAllies,
                                attackerKills = attackerKills,
                                defenderKills = defenderKills,
                                attackerScore = attackerScore,
                                defenderScore = defenderScore,
                                startTick = startTick
                            )
                        )
                    }
                }

                // War requests
                nbt.getList("WarRequests", NbtElement.COMPOUND_TYPE.toInt()).forEach { requestTag ->
                    if (requestTag is NbtCompound &&
                        requestTag.containsUuid("Requester") &&
                        requestTag.containsUuid("Attacker") &&
                        requestTag.containsUuid("Defender") &&
                        requestTag.contains("Side", NbtElement.STRING_TYPE.toInt())
                    ) {
                        val side =
                            runCatching { WarSide.valueOf(requestTag.getString("Side")) }.getOrNull()
                                ?: return@forEach
                        warJoinRequests.add(
                            WarJoinRequest(
                                requester = requestTag.getUuid("Requester"),
                                attacker = requestTag.getUuid("Attacker"),
                                defender = requestTag.getUuid("Defender"),
                                side = side
                            )
                        )
                    }
                }

                // Invites
                invites.clear()
                nbt.getList("Invites", NbtElement.COMPOUND_TYPE.toInt()).forEach { inviteTag ->
                    if (inviteTag is NbtCompound &&
                        inviteTag.containsUuid("Player") &&
                        inviteTag.containsUuid("Kingdom")
                    ) {
                        val list = invites.getOrPut(inviteTag.getUuid("Player")) { mutableSetOf() }
                        list.add(inviteTag.getUuid("Kingdom"))
                    }
                }
            }

            SteelAndHonorMod.LOGGER.info("Loaded {} kingdoms and {} wars.", kingdoms.size, wars.size)
        }.onFailure { throwable ->
            SteelAndHonorMod.LOGGER.error("Failed to load kingdom data", throwable)
        }

        dirty = false
    }

    // ------------------------------
    // KINGDOM MANAGEMENT
    // ------------------------------

    fun createKingdom(player: ServerPlayerEntity, name: String, color: DyeColor) {
        if (memberLookup[player.uuid] != null) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.create.must_leave")
        }
        val sanitized = sanitizeKingdomName(name)
        if (kingdoms.values.any { it.name.equals(sanitized, ignoreCase = true) }) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.create.name_exists")
        }
        val data = KingdomData(
            owner = player.uuid,
            name = sanitized,
            color = color
        )
        data.members.add(player.uuid)
        data.roles[player.uuid] = KingdomRole.LEADER
        data.partyId = OpenPacClaimService.resolvePartyId(player.server, player.uuid)
        kingdoms[player.uuid] = data
        memberLookup[player.uuid] = player.uuid
        invites.remove(player.uuid)
        OpenPacClaimService.ensurePartyForKingdom(player.server, data)
        OpenPacClaimService.syncMembersToParty(player.server, data)
        dirty = true
        saveNow()
        KingdomCelebrationEffects.realmFounded(player, color)
    }

    fun assignRole(executor: ServerPlayerEntity, target: ServerPlayerEntity, role: KingdomRole) {
        val owner = memberLookup[executor.uuid]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        val executorRole = getRole(executor.uuid)
        if (!executorRole.isCommandRank) throw KingdomOperationException(GENERIC_PERMISSION_KEY)
        val kingdom = kingdoms[owner]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (!kingdom.members.contains(target.uuid)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.member.unknown")
        }
        if (role == KingdomRole.LEADER && target.uuid != kingdom.owner) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.role.transfer")
        }
        kingdom.roles[target.uuid] = if (target.uuid == owner) KingdomRole.LEADER else role
        dirty = true
    }

    fun getMemberNameSuggestions(player: ServerPlayerEntity): List<String> {
        val owner = memberLookup[player.uuid] ?: return emptyList()
        val kingdom = kingdoms[owner] ?: return emptyList()
        return kingdom.members.mapNotNull { memberId ->
            player.server.lookupName(memberId)
        }.distinct().sortedBy { it.lowercase(Locale.ROOT) }
    }

    fun invitePlayer(executor: ServerPlayerEntity, target: ServerPlayerEntity) {
        val owner = memberLookup[executor.uuid]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (executor.uuid == target.uuid) throw KingdomOperationException("command.steel_and_honor.kingdom.invite.self")
        val kingdom = kingdoms[owner]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (!getRole(executor.uuid).canInvite) throw KingdomOperationException(GENERIC_PERMISSION_KEY)
        if (memberLookup[target.uuid] != null) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.invite.already_member")
        }
        val set = invites.getOrPut(target.uuid) { mutableSetOf() }
        if (!set.add(owner)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.invite.exists")
        }
        target.sendMessage(
            Text.translatable(
                "command.steel_and_honor.kingdom.invite.notify",
                kingdom.name
            ),
            false
        )
        dirty = true
    }

    fun pendingInviteNames(player: ServerPlayerEntity): List<String> {
        val pending = invites[player.uuid] ?: return emptyList()
        return pending.mapNotNull { ownerId -> kingdoms[ownerId]?.name }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }
    }

    fun joinKingdom(player: ServerPlayerEntity, kingdomName: String) {
        if (memberLookup[player.uuid] != null) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.join.already_member")
        }
        val entry = kingdoms.entries.firstOrNull { it.value.name.equals(kingdomName, ignoreCase = true) }
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.join.unknown")
        val pending =
            invites[player.uuid] ?: throw KingdomOperationException("command.steel_and_honor.kingdom.join.no_invite")
        if (!pending.remove(entry.key)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.join.no_invite")
        }
        if (pending.isEmpty()) {
            invites.remove(player.uuid)
        }
        entry.value.members.add(player.uuid)
        entry.value.roles[player.uuid] = KingdomRole.CITIZEN
        memberLookup[player.uuid] = entry.key
        OpenPacClaimService.ensurePartyForKingdom(player.server, entry.value)
        OpenPacClaimService.addMemberToParty(player.server, entry.value, player.uuid)
        dirty = true
        markBordersDirty()
        player.sendMessage(
            Text.translatable(
                "command.steel_and_honor.kingdom.join.success",
                entry.value.name
            ),
            false
        )
        notifyMembers(
            player.server,
            entry.value,
            Text.translatable(
                "command.steel_and_honor.kingdom.join.announce",
                player.gameProfile.name,
                entry.value.name
            )
        )
        applyToPlayer(player)
    }

    fun leaveKingdom(player: ServerPlayerEntity) {
        val owner = memberLookup[player.uuid]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        val kingdom = kingdoms[owner]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (player.uuid == owner && kingdom.members.size > 1) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.leave.leader_has_members")
        }
        OpenPacClaimService.removeMemberFromParty(player.server, kingdom, player.uuid)
        memberLookup.remove(player.uuid)
        kingdom.members.remove(player.uuid)
        kingdom.roles.remove(player.uuid)
        invites.remove(player.uuid)
        if (player.uuid == owner) {
            removeInvitesForOwner(owner)
            kingdoms.remove(owner)
        }
        dirty = true
        markBordersDirty()
    }

    private fun removeInvitesForOwner(owner: UUID) {
        invites.values.forEach { it.remove(owner) }
        invites.entries.removeIf { it.value.isEmpty() }
    }

    fun renameKingdom(player: ServerPlayerEntity, requestedName: String): String {
        val owner = memberLookup[player.uuid]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        val kingdom = kingdoms[owner]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (player.uuid != kingdom.owner) throw KingdomOperationException(GENERIC_PERMISSION_KEY)
        val sanitized = sanitizeKingdomName(requestedName)
        if (kingdom.name.equals(sanitized, ignoreCase = true)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.rename.no_change")
        }
        kingdom.name = sanitized
        dirty = true
        markBordersDirty()
        return kingdom.name
    }

    fun updateKingdomColor(player: ServerPlayerEntity, color: DyeColor): DyeColor {
        val owner = memberLookup[player.uuid]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        val kingdom = kingdoms[owner]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (!getRole(player.uuid).isCommandRank) throw KingdomOperationException(GENERIC_PERMISSION_KEY)
        if (kingdom.color == color) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.color.same")
        }
        kingdom.color = color
        dirty = true
        markBordersDirty()
        return kingdom.color
    }

    fun getRivalKingdomNames(requester: UUID?): List<String> {
        val excludedOwner = requester?.let { memberLookup[it] }
        return kingdoms
            .filter { it.value.isKingdom() && it.key != excludedOwner }
            .values
            .map { it.name }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }
    }

    fun allKingdomNames(): List<String> {
        return kingdoms
            .values
            .filter { it.isKingdom() }
            .map { it.name }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }
    }

    // ------------------------------
    // WAR DECLARATION + REQUESTS
    // ------------------------------

    fun warEligibleTargets(): List<String> {
        val names = mutableSetOf<String>()
        wars.forEach { war ->
            kingdoms[war.attacker]?.name?.let { names.add(it) }
            kingdoms[war.defender]?.name?.let { names.add(it) }
        }
        return names.sortedBy { it.lowercase(Locale.ROOT) }
    }

    fun pendingWarRequestNames(player: ServerPlayerEntity): List<String> {
        val owner = memberLookup[player.uuid] ?: return emptyList()
        return warJoinRequests
            .filter { it.targetOwner() == owner }
            .mapNotNull { kingdoms[it.requester]?.name }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }
    }

    fun declareWar(executor: ServerPlayerEntity, targetName: String) {
        val attackerOwner = memberLookup[executor.uuid]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        val attacker = kingdoms[attackerOwner]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (!attacker.isKingdom()) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.only_kingdom")
        }

        val defenderEntry = kingdoms.entries.firstOrNull { it.value.name.equals(targetName, true) }
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.war.unknown")
        if (!defenderEntry.value.isKingdom()) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.only_kingdom")
        }
        if (defenderEntry.key == attackerOwner) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.self")
        }
        if (isInWar(attackerOwner)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.in_progress")
        }
        if (isInWar(defenderEntry.key)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.target_busy")
        }
        if (isAtWar(attackerOwner, defenderEntry.key)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.exists")
        }

        val war = WarState(attackerOwner, defenderEntry.key, startTick = executor.server.overworld.time)

        // Build city lists for attacker/defender
        initializeWarCities(executor.server, war, attacker, defenderEntry.value)

        wars.add(war)
        dirty = true
        KingdomCelebrationEffects.warDeclared(executor.server, attacker, defenderEntry.value)
    }

    fun requestWarAssistance(player: ServerPlayerEntity, allyName: String): String {
        val requesterOwner = memberLookup[player.uuid]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        val requesterKingdom = kingdoms[requesterOwner]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (!requesterKingdom.isKingdom()) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.only_kingdom")
        }
        if (isInWar(requesterOwner)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.request.ineligible")
        }
        val allyEntry = kingdoms.entries.firstOrNull { it.value.name.equals(allyName, true) }
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.war.unknown")
        if (allyEntry.key == requesterOwner) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.request.self")
        }
        val war = wars.firstOrNull { it.attacker == allyEntry.key || it.defender == allyEntry.key }
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.war.request.none")
        val side = if (war.attacker == allyEntry.key) WarSide.ATTACKER else WarSide.DEFENDER
        if (war.includes(requesterOwner)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.request.ineligible")
        }
        if (warJoinRequests.any {
                it.requester == requesterOwner &&
                    it.attacker == war.attacker &&
                    it.defender == war.defender
            }
        ) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.request.pending")
        }

        warJoinRequests.add(WarJoinRequest(requesterOwner, war.attacker, war.defender, side))
        dirty = true
        notifyMembers(
            player.server,
            allyEntry.value,
            Text.translatable(
                "command.steel_and_honor.kingdom.war.request.notify",
                requesterKingdom.name
            )
        )
        notifyMembers(
            player.server,
            requesterKingdom,
            Text.translatable(
                "command.steel_and_honor.kingdom.war.request.sent",
                allyEntry.value.name
            )
        )
        return allyEntry.value.name
    }

    fun respondToWarRequest(
        player: ServerPlayerEntity,
        requesterName: String,
        approve: Boolean
    ): Pair<String, String> {
        val owner = memberLookup[player.uuid]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        val responderKingdom = kingdoms[owner]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (!getRole(player.uuid).isCommandRank) {
            throw KingdomOperationException(GENERIC_PERMISSION_KEY)
        }
        val request = warJoinRequests.firstOrNull {
            it.targetOwner() == owner &&
                kingdoms[it.requester]?.name?.equals(requesterName, true) == true
        } ?: throw KingdomOperationException("command.steel_and_honor.kingdom.war.request.not_found")

        val requesterKingdom = kingdoms[request.requester]
        if (requesterKingdom == null) {
            warJoinRequests.remove(request)
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.request.not_found")
        }

        if (!approve) {
            warJoinRequests.remove(request)
            dirty = true
            notifyMembers(
                player.server,
                requesterKingdom,
                Text.translatable(
                    "command.steel_and_honor.kingdom.war.request.denied",
                    responderKingdom.name
                )
            )
            return requesterKingdom.name to responderKingdom.name
        }

        if (isInWar(request.requester)) {
            warJoinRequests.remove(request)
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.request.ineligible")
        }

        val war = wars.firstOrNull { it.attacker == request.attacker && it.defender == request.defender }
            ?: run {
                warJoinRequests.remove(request)
                throw KingdomOperationException("command.steel_and_honor.kingdom.war.request.none")
            }

        war.addAlly(request.requester, request.side)
        warJoinRequests.remove(request)
        dirty = true

        val opponentName = kingdoms[war.opposingPrimary(request.side)]?.name ?: ""

        notifyMembers(
            player.server,
            requesterKingdom,
            Text.translatable(
                "command.steel_and_honor.kingdom.war.request.approved",
                responderKingdom.name,
                opponentName
            )
        )
        notifyMembers(
            player.server,
            responderKingdom,
            Text.translatable(
                "command.steel_and_honor.kingdom.war.request.helper_joined",
                requesterKingdom.name
            )
        )

        return requesterKingdom.name to responderKingdom.name
    }

    // ------------------------------
    // FORCE ASSIGN / DESIGN
    // ------------------------------

    fun forceAssignToKingdom(target: ServerPlayerEntity, kingdomName: String): String {
        val targetEntry = kingdoms.entries.firstOrNull { it.value.name.equals(kingdomName, true) }
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.join.unknown")
        val destination = targetEntry.value
        val currentOwner = memberLookup[target.uuid]
        if (currentOwner == targetEntry.key) {
            return destination.name
        }
        if (currentOwner != null) {
            val currentKingdom = kingdoms[currentOwner]
            if (currentKingdom != null) {
                if (currentKingdom.owner == target.uuid) {
                    throw KingdomOperationException("command.steel_and_honor.kingdom.forcejoin.leader")
                }
                currentKingdom.members.remove(target.uuid)
                currentKingdom.roles.remove(target.uuid)
                OpenPacClaimService.removeMemberFromParty(target.server, currentKingdom, target.uuid)
            }
        }
        memberLookup[target.uuid] = targetEntry.key
        destination.members.add(target.uuid)
        destination.roles.putIfAbsent(target.uuid, KingdomRole.CITIZEN)
        OpenPacClaimService.ensurePartyForKingdom(target.server, destination)
        OpenPacClaimService.addMemberToParty(target.server, destination, target.uuid)
        invites[target.uuid]?.clear()
        dirty = true
        markBordersDirty()
        applyToPlayer(target)
        target.sendMessage(
            Text.translatable(
                "command.steel_and_honor.kingdom.forcejoin.success",
                destination.name
            ),
            false
        )
        return destination.name
    }

    fun updateDesign(player: ServerPlayerEntity, transform: (KingdomDesign) -> KingdomDesign): Boolean {
        val ownerId = memberLookup[player.uuid]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        val kingdom = kingdoms[ownerId]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (kingdom.owner != player.uuid && !getRole(player.uuid).isCommandRank) {
            return false
        }
        kingdom.design = transform(kingdom.design)
        dirty = true
        applyToMembers(player.server, kingdom)
        saveNow()
        SteelAndHonorMod.LOGGER.info("Updated kingdom banner for {}", player.gameProfile.name)
        return true
    }

    fun updateDesignFromBanner(player: ServerPlayerEntity): Boolean {
        val stack = player.mainHandStack
        if (stack.isEmpty || stack.item !is BannerItem) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.flag.no_banner")
        }
        val baseColor = stack.get(DataComponentTypes.BASE_COLOR) ?: (stack.item as BannerItem).color
        val patternsComponent = stack.get(DataComponentTypes.BANNER_PATTERNS)
        val layers = patternsComponent?.layers() ?: emptyList()
        val accent = layers.firstOrNull()?.color ?: baseColor
        val bannerLayers = layers.mapNotNull {
            val id = it.pattern.value().assetId
            if (id != null) BannerLayer(id, it.color) else null
        }
        return updateDesign(player) {
            KingdomDesign(
                primaryColor = baseColor,
                accentColor = accent,
                layers = bannerLayers
            )
        }
    }

    fun getDesign(playerId: UUID): KingdomDesign {
        val owner = memberLookup[playerId]
        return if (owner != null) {
            kingdoms[owner]?.design ?: KingdomDesign.DEFAULT
        } else {
            KingdomDesign.DEFAULT
        }
    }

    fun applyToPlayer(player: ServerPlayerEntity) {
        val owner = memberLookup[player.uuid] ?: return
        val kingdom = kingdoms[owner] ?: return
        KingdomItemApplier.applyToPlayer(player, kingdom.design)
        RoyalRegaliaManager.sync(player, owner == player.uuid)
    }

    fun getRole(playerId: UUID): KingdomRole {
        val owner = memberLookup[playerId] ?: return KingdomRole.CITIZEN
        val kingdom = kingdoms[owner] ?: return KingdomRole.CITIZEN
        return kingdom.roleOf(playerId)
    }

    fun statusTextFor(playerId: UUID): Text {
        val owner = memberLookup[playerId]
            ?: return Text.translatable("command.steel_and_honor.kingdom.status.unaligned")
        val data = kingdoms[owner]
            ?: return Text.translatable("command.steel_and_honor.kingdom.status.unaligned")
        return Text.translatable("command.steel_and_honor.kingdom.status.kingdom", data.name)
    }

    // ------------------------------
    // KILL SCORING
    // ------------------------------

    fun recordKill(killer: ServerPlayerEntity, victim: ServerPlayerEntity) {
        val killerOwner = memberLookup[killer.uuid] ?: return
        val victimOwner = memberLookup[victim.uuid] ?: return
        if (killerOwner == victimOwner) return

        val war = wars.firstOrNull { it.includes(killerOwner) && it.includes(victimOwner) } ?: return
        val killerSide = war.sideOf(killerOwner) ?: return
        val victimSide = war.sideOf(victimOwner) ?: return
        if (killerSide == victimSide) return

        val now = killer.server.overworld.time
        // Don't count kills during prep time
        if (war.prepTimeRemaining(now) > 0L) return

        val victimRole = getRole(victim.uuid)
        // Citizens do not count
        if (victimRole == KingdomRole.CITIZEN) return

        val points = when (victimRole) {
            KingdomRole.MILITARY -> 25
            KingdomRole.POLITICIAN -> 50
            KingdomRole.OFFICER -> 150
            KingdomRole.LEADER -> 500
            else -> 0
        }

        war.incrementKill(killerSide)
        if (points > 0) {
            when (killerSide) {
                WarSide.ATTACKER -> war.attackerScore += points
                WarSide.DEFENDER -> war.defenderScore += points
            }
        }
        dirty = true
    }

    // ------------------------------
    // NAME SANITIZATION / HELPERS
    // ------------------------------

    private fun sanitizeKingdomName(value: String): String {
        val name = value.trim()
        if (name.length < MIN_NAME_LENGTH) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.rename.too_short")
        }
        if (name.length > MAX_NAME_LENGTH) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.rename.too_long")
        }
        if (!KINGDOM_NAME_PATTERN.matches(name)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.rename.invalid")
        }
        return name
    }

    private fun applyToMembers(server: MinecraftServer, kingdom: KingdomData) {
        kingdom.members.forEach { uuid ->
            server.playerManager.getPlayer(uuid)?.let {
                KingdomItemApplier.applyToPlayer(it, kingdom.design)
            }
        }
    }

    private fun isAtWar(first: UUID, second: UUID): Boolean {
        return wars.any { it.matches(first, second) }
    }

    private fun isInWar(owner: UUID): Boolean {
        return wars.any { it.includes(owner) }
    }

    // ------------------------------
    // WAR TICK + CITY CAPTURE
    // ------------------------------

    private fun updateWars(server: MinecraftServer) {
        val now = server.overworld.time
        val concluded = mutableListOf<WarState>()

        wars.forEach { war ->
            if (war.startTick == 0L) {
                war.startTick = now
                dirty = true
            }

            // Capture tick
            if (now % CAPTURE_TICKS_INTERVAL == 0L) {
                updateCityCapture(server, war, now)
            }

            val autoWinner = war.autoWinSide()
            if (autoWinner != null || war.timeRemaining(now) <= 0L) {
                concluded.add(war)
            }
        }

        concluded.forEach { war ->
            val nowWinner = war.autoWinSide()
            resolveWar(server, war, forcedWinner = nowWinner, surrendered = false)
        }
    }

    /**
     * Build WarCity entries from current claim snapshots for both sides.
     */
    private fun initializeWarCities(
        server: MinecraftServer,
        war: WarState,
        attacker: KingdomData,
        defender: KingdomData
    ) {
        war.cities.clear()
        war.cities.addAll(buildWarCitiesFor(server, attacker, WarSide.ATTACKER))
        war.cities.addAll(buildWarCitiesFor(server, defender, WarSide.DEFENDER))
    }

    private fun buildWarCitiesFor(
        server: MinecraftServer,
        kingdom: KingdomData,
        side: WarSide
    ): List<WarCity> {
        val perOwner = OpenPacClaimService.collectSnapshots(server, listOf(kingdom)) { ownerId, claimCount ->
            kingdoms[ownerId]?.claimCount = claimCount
        }
        val snapshots = perOwner[kingdom.owner] ?: return emptyList()
        return snapshots.map { snapshot ->
            WarCity(
                id = snapshot.id,
                originalOwner = kingdom.owner,
                originalSide = side,
                name = snapshot.name,
                dimension = snapshot.dimension.value,
                centerX = snapshot.center.x,
                centerY = snapshot.center.y,
                centerZ = snapshot.center.z,
                minChunkX = snapshot.minChunkX,
                maxChunkX = snapshot.maxChunkX,
                minChunkZ = snapshot.minChunkZ,
                maxChunkZ = snapshot.maxChunkZ
            )
        }
    }

    private fun updateCityCapture(server: MinecraftServer, war: WarState, now: Long) {
        // No capture during prep
        if (war.prepTimeRemaining(now) > 0L) {
            war.cities.forEach {
                it.progress = 0.0
                it.capturingSide = null
            }
            return
        }

        val players = server.playerManager.playerList
        if (players.isEmpty() || war.cities.isEmpty()) return

        val attackersInCity = mutableMapOf<UUID, MutableList<ServerPlayerEntity>>()
        val defendersInCity = mutableMapOf<UUID, MutableList<ServerPlayerEntity>>()

        // Map which players are in which city zones
        for (player in players) {
            val owner = memberLookup[player.uuid] ?: continue
            val side = war.sideOf(owner) ?: continue
            val city = cityContainingPlayer(war, player) ?: continue
            val map = if (side == WarSide.ATTACKER) attackersInCity else defendersInCity
            map.getOrPut(city.id) { mutableListOf() }.add(player)
        }

        // Only one active city at a time
        var active = war.cities.firstOrNull { it.capturingSide != null && it.capturedBy == null }

        if (active == null) {
            // Prefer attacker capturing defender city, then defender capturing attacker city
            active = war.cities.firstOrNull { city ->
                city.capturedBy == null &&
                    city.originalSide == WarSide.DEFENDER &&
                    (attackersInCity[city.id]?.isNotEmpty() == true)
            } ?: war.cities.firstOrNull { city ->
                city.capturedBy == null &&
                    city.originalSide == WarSide.ATTACKER &&
                    (defendersInCity[city.id]?.isNotEmpty() == true)
            }

            if (active != null) {
                active.capturingSide = if (attackersInCity[active.id]?.isNotEmpty() == true) {
                    WarSide.ATTACKER
                } else {
                    WarSide.DEFENDER
                }
                active.progress = 0.0
            }
        }

        if (active == null) return

        val capturers = if (active.capturingSide == WarSide.ATTACKER) {
            attackersInCity[active.id] ?: emptyList()
        } else {
            defendersInCity[active.id] ?: emptyList()
        }

        val opponents = if (active.capturingSide == WarSide.ATTACKER) {
            defendersInCity[active.id] ?: emptyList()
        } else {
            attackersInCity[active.id] ?: emptyList()
        }

        when {
            // Capturers alone in zone: progress toward capture
            capturers.isNotEmpty() && opponents.isEmpty() -> {
                active.progress = (active.progress + CAPTURE_RATE_PER_SECOND).coerceAtMost(1.0)
            }
            // Nobody in zone: decay back toward 0
            capturers.isEmpty() && opponents.isEmpty() -> {
                active.progress = (active.progress - CAPTURE_DECAY_PER_SECOND).coerceAtLeast(0.0)
                if (active.progress <= 0.0) {
                    active.capturingSide = null
                }
            }
            // Contested: freeze
        }

        // Visual: red ring (flames for now) around the 4-chunk capture square
        if (active.progress > 0.0 && active.capturingSide != null) {
            spawnCaptureParticles(server, active)
        }

        // Capture complete
        if (active.progress >= 1.0 && active.capturingSide != null) {
            val winnerSide = active.capturingSide!!
            active.capturedBy = winnerSide
            active.capturingSide = null
            active.progress = 1.0

            war.addCityCapturePoints(winnerSide)
            notifyCityCaptured(server, war, active, winnerSide)
            transferCityClaims(server, war, active, winnerSide)
            markBordersDirty()
        }
    }

    private fun cityContainingPlayer(war: WarState, player: ServerPlayerEntity): WarCity? {
        val worldId = player.world.registryKey.value
        val pos = player.blockPos
        return war.cities.firstOrNull { city ->
            if (city.dimension != worldId || city.capturedBy != null) return@firstOrNull false
            val dx = pos.x - city.centerX
            val dz = pos.z - city.centerZ
            abs(dx) <= city.captureRadiusBlocks && abs(dz) <= city.captureRadiusBlocks
        }
    }

    private fun notifyCityCaptured(
        server: MinecraftServer,
        war: WarState,
        city: WarCity,
        side: WarSide
    ) {
        val winnerOwner = war.primaryOwner(side)
        val winnerKingdom = kingdoms[winnerOwner] ?: return
        val loserKingdom = kingdoms[war.opposingPrimary(side)] ?: return
        val message = Text.translatable(
            "command.steel_and_honor.kingdom.war.city_captured",
            winnerKingdom.name,
            city.name
        )
        notifyMembers(server, winnerKingdom, message)
        notifyMembers(server, loserKingdom, message)
    }

    private fun transferCityClaims(
        server: MinecraftServer,
        war: WarState,
        city: WarCity,
        winnerSide: WarSide
    ) {
        val fromOwner = if (winnerSide == WarSide.ATTACKER) war.defender else war.attacker
        val toOwner = if (winnerSide == WarSide.ATTACKER) war.attacker else war.defender
        val fromKingdom = kingdoms[fromOwner] ?: return
        val toKingdom = kingdoms[toOwner] ?: return
        // Hook into OPAC (you need to implement this helper in OpenPacClaimService)
        OpenPacClaimService.transferCityClaims(server, fromKingdom, toKingdom, city)
    }

    private fun spawnCaptureParticles(server: MinecraftServer, city: WarCity) {
        val worldKey = net.minecraft.registry.RegistryKey.of(
            net.minecraft.registry.RegistryKeys.WORLD,
            city.dimension
        )
        val world = server.getWorld(worldKey) ?: return

        val y = (city.centerY + 1).toDouble()
        val radius = city.captureRadiusBlocks.toDouble()
        val step = 4.0 // every 4 blocks along the ring

        var x = city.centerX - radius
        while (x <= city.centerX + radius) {
            world.spawnParticles(
                net.minecraft.particle.ParticleTypes.FLAME,
                x, y, city.centerZ - radius,
                1, 0.0, 0.0, 0.0, 0.0
            )
            world.spawnParticles(
                net.minecraft.particle.ParticleTypes.FLAME,
                x, y, city.centerZ + radius,
                1, 0.0, 0.0, 0.0, 0.0
            )
            x += step
        }

        var z = city.centerZ - radius
        while (z <= city.centerZ + radius) {
            world.spawnParticles(
                net.minecraft.particle.ParticleTypes.FLAME,
                city.centerX - radius, y, z,
                1, 0.0, 0.0, 0.0, 0.0
            )
            world.spawnParticles(
                net.minecraft.particle.ParticleTypes.FLAME,
                city.centerX + radius, y, z,
                1, 0.0, 0.0, 0.0, 0.0
            )
            z += step
        }
    }

    // ------------------------------
    // RESOLVING WARS / ABSORPTION / HUD
    // ------------------------------

    private fun resolveWar(
        server: MinecraftServer,
        war: WarState,
        forcedWinner: WarSide? = null,
        surrendered: Boolean = false
    ) {
        val attacker = kingdoms[war.attacker]
        val defender = kingdoms[war.defender]
        val attackerName = attacker?.name ?: "Unknown"
        val defenderName = defender?.name ?: "Unknown"

        // Auto-win (all enemy cities captured) or forced winner (surrender) beats score
        val autoWinner = forcedWinner ?: war.autoWinSide()
        val winner = autoWinner ?: when {
            war.attackerScore > war.defenderScore -> WarSide.ATTACKER
            war.defenderScore > war.attackerScore -> WarSide.DEFENDER
            else -> null
        }

        val message = when (winner) {
            WarSide.ATTACKER -> Text.translatable(
                "command.steel_and_honor.kingdom.war.victory",
                attackerName,
                defenderName,
                war.attackerScore,
                war.defenderScore
            )
            WarSide.DEFENDER -> Text.translatable(
                "command.steel_and_honor.kingdom.war.victory",
                defenderName,
                attackerName,
                war.defenderScore,
                war.attackerScore
            )
            null -> Text.translatable(
                "command.steel_and_honor.kingdom.war.draw",
                attackerName,
                defenderName,
                war.attackerScore,
                war.defenderScore
            )
        }

        attacker?.let { notifyMembers(server, it, message) }
        defender?.let { notifyMembers(server, it, message) }
        war.attackerAllies.forEach { allyOwner ->
            kingdoms[allyOwner]?.let { notifyMembers(server, it, message) }
        }
        war.defenderAllies.forEach { allyOwner ->
            kingdoms[allyOwner]?.let { notifyMembers(server, it, message) }
        }

        // Full elimination only on surrender or auto-win
        if (winner != null && (surrendered || autoWinner != null)) {
            val winnerOwner = war.primaryOwner(winner)
            val loserOwner = war.opposingPrimary(winner)
            absorbKingdom(server, winnerOwner, loserOwner)
        }

        wars.remove(war)
        dirty = true
    }

private fun sendWarHudUpdates(server: MinecraftServer) {
    val now = server.overworld.time
    val players = server.playerManager.playerList
    if (players.isEmpty()) return

    players.forEach { player ->
        val owner = memberLookup[player.uuid] ?: return@forEach

        val payloadWars = wars
            .filter { it.includes(owner) }
            .mapNotNull { war ->
                val attackerData = kingdoms[war.attacker]
                val defenderData = kingdoms[war.defender]
                if (attackerData == null || defenderData == null) return@mapNotNull null

                val warSeconds = (war.warTimeRemaining(now) / 20L).coerceAtLeast(0)
                val prepSeconds = (war.prepTimeRemaining(now) / 20L).coerceAtLeast(0)

                // Find the currently active city for this war
                val activeCity = war.cities.firstOrNull { city ->
                    city.capturingSide != null && city.capturedBy == null
                }

                val activeCityName = activeCity?.name ?: ""
                val captureProgress = (activeCity?.progress ?: 0.0)
                    .toFloat()
                    .coerceIn(0f, 1f) // HUD expects 0.0–1.0

                KingdomNetworking.WarStatusEntry(
                    attackerName = attackerData.name,
                    defenderName = defenderData.name,
                    attackerColorId = attackerData.color.id,
                    defenderColorId = defenderData.color.id,
                    attackerKills = war.attackerKills,
                    defenderKills = war.defenderKills,
                    secondsRemaining = warSeconds.toInt(),
                    prepSecondsRemaining = prepSeconds.toInt(),
                    attackerScore = war.attackerScore,
                    defenderScore = war.defenderScore,
                    activeCityName = activeCityName,
                    captureProgress = captureProgress
                )
            }

        KingdomNetworking.sendWarStatus(player, payloadWars)
    }
}

    private fun allowedClaimZones(memberCount: Int): Int {
        return when {
            memberCount <= 2 -> 1
            memberCount <= 4 -> 2
            memberCount <= 7 -> 3
            else -> 4
        }
    }

    private fun sendClaimLimitWarning(
        server: MinecraftServer,
        kingdom: KingdomData,
        allowed: Int,
        attempted: Int
    ) {
        val message = Text.translatable(
            "command.steel_and_honor.kingdom.claim.limit",
            allowed,
            attempted,
            kingdom.name
        )
        notifyMembers(server, kingdom, message)
    }

    private fun sendOverlapWarning(
        server: MinecraftServer,
        kingdom: KingdomData,
        dimension: Identifier
    ) {
        val message = Text.translatable(
            "command.steel_and_honor.kingdom.claim.overlap",
            kingdom.name,
            dimension.toString()
        )
        notifyMembers(server, kingdom, message)
    }

    private fun notifyMembers(
        server: MinecraftServer,
        kingdom: KingdomData,
        message: Text
    ) {
        kingdom.members.mapNotNull { server.playerManager.getPlayer(it) }.forEach { player ->
            player.sendMessage(message, false)
        }
    }

    // ------------------------------
    // ABSORPTION / SURRENDER
    // ------------------------------

    private fun absorbKingdom(server: MinecraftServer, winnerOwner: UUID, loserOwner: UUID) {
        val winner = kingdoms[winnerOwner] ?: return
        val loser = kingdoms[loserOwner] ?: return
        if (winnerOwner == loserOwner) return

        val loserMembers = loser.members.toList()
        loserMembers.forEach { memberId ->
            if (!winner.members.contains(memberId)) {
                winner.members.add(memberId)
                winner.roles.putIfAbsent(memberId, KingdomRole.CITIZEN)
                memberLookup[memberId] = winnerOwner

                server.playerManager.getPlayer(memberId)?.let { player ->
                    OpenPacClaimService.addMemberToParty(server, winner, memberId)
                    applyToPlayer(player)
                }
            }
        }

        // Move all claims loser -> winner
        OpenPacClaimService.transferAllClaimsTo(server, loser, winner)

        kingdoms.remove(loserOwner)
        removeInvitesForOwner(loserOwner)

        dirty = true
        markBordersDirty()
    }

    fun surrender(player: ServerPlayerEntity) {
        val owner = memberLookup[player.uuid]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        val kingdom = kingdoms[owner]
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.not_member")
        if (kingdom.owner != player.uuid) {
            throw KingdomOperationException(GENERIC_PERMISSION_KEY)
        }

        val war = wars.firstOrNull { it.includes(owner) }
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.war.none")
        val now = player.server.overworld.time
        if (!war.canSurrender(now)) {
            throw KingdomOperationException("command.steel_and_honor.kingdom.war.surrender.too_early")
        }

        val side = war.sideOf(owner)
            ?: throw KingdomOperationException("command.steel_and_honor.kingdom.war.none")
        val winner = if (side == WarSide.ATTACKER) WarSide.DEFENDER else WarSide.ATTACKER

        resolveWar(player.server, war, forcedWinner = winner, surrendered = true)
    }
}

// ------------------------------
// DATA CLASSES
// ------------------------------

data class WarState(
    val attacker: UUID,
    val defender: UUID,
    val attackerAllies: MutableSet<UUID> = mutableSetOf(),
    val defenderAllies: MutableSet<UUID> = mutableSetOf(),
    var attackerKills: Int = 0,
    var defenderKills: Int = 0,
    var attackerScore: Int = 0,
    var defenderScore: Int = 0,
    var startTick: Long = 0L,
    val cities: MutableList<WarCity> = mutableListOf()
) {
    fun matches(first: UUID, second: UUID): Boolean {
        return (attacker == first && defender == second) ||
            (attacker == second && defender == first)
    }

    fun includes(uuid: UUID): Boolean {
        return attacker == uuid ||
            defender == uuid ||
            attackerAllies.contains(uuid) ||
            defenderAllies.contains(uuid)
    }

    fun sideOf(uuid: UUID): WarSide? {
        return when {
            attacker == uuid || attackerAllies.contains(uuid) -> WarSide.ATTACKER
            defender == uuid || defenderAllies.contains(uuid) -> WarSide.DEFENDER
            else -> null
        }
    }

    fun addAlly(uuid: UUID, side: WarSide) {
        when (side) {
            WarSide.ATTACKER -> attackerAllies.add(uuid)
            WarSide.DEFENDER -> defenderAllies.add(uuid)
        }
    }

    fun primaryOwner(side: WarSide): UUID {
        return if (side == WarSide.ATTACKER) attacker else defender
    }

    fun opposingPrimary(side: WarSide): UUID {
        return if (side == WarSide.ATTACKER) defender else attacker
    }

    fun incrementKill(side: WarSide) {
        when (side) {
            WarSide.ATTACKER -> attackerKills++
            WarSide.DEFENDER -> defenderKills++
        }
    }

    fun addCityCapturePoints(side: WarSide) {
        when (side) {
            WarSide.ATTACKER -> attackerScore += KingdomManager.CITY_CAPTURE_POINTS
            WarSide.DEFENDER -> defenderScore += KingdomManager.CITY_CAPTURE_POINTS
        }
    }

    fun timeRemaining(now: Long): Long {
        if (startTick == 0L) return KingdomManager.WAR_PREP_TIME_TICKS + KingdomManager.WAR_DURATION_TICKS
        val elapsed = (now - startTick).coerceAtLeast(0)
        val totalTime = KingdomManager.WAR_PREP_TIME_TICKS + KingdomManager.WAR_DURATION_TICKS
        return (totalTime - elapsed).coerceAtLeast(0)
    }

    fun prepTimeRemaining(now: Long): Long {
        if (startTick == 0L) return KingdomManager.WAR_PREP_TIME_TICKS
        val elapsed = (now - startTick).coerceAtLeast(0)
        return (KingdomManager.WAR_PREP_TIME_TICKS - elapsed).coerceAtLeast(0)
    }

    fun warTimeRemaining(now: Long): Long {
        if (startTick == 0L) return 0L
        val elapsed = (now - startTick).coerceAtLeast(0)
        val prepElapsed = elapsed - KingdomManager.WAR_PREP_TIME_TICKS
        if (prepElapsed < 0) return 0L
        return (KingdomManager.WAR_DURATION_TICKS - prepElapsed).coerceAtLeast(0)
    }

    fun canSurrender(now: Long): Boolean {
        if (startTick == 0L) return false
        val elapsed = (now - startTick).coerceAtLeast(0)
        if (elapsed <= KingdomManager.WAR_PREP_TIME_TICKS) return false
        val activeElapsed = elapsed - KingdomManager.WAR_PREP_TIME_TICKS
        return activeElapsed >= KingdomManager.MIN_ACTIVE_WAR_TICKS_BEFORE_SURRENDER
    }

    /**
     * Returns the side that has captured all enemy cities, or null if nobody has.
     */
    fun autoWinSide(): WarSide? {
        val attackerCities = cities.filter { it.originalSide == WarSide.DEFENDER }
        val defenderCities = cities.filter { it.originalSide == WarSide.ATTACKER }
        val attackerHasAll = attackerCities.isNotEmpty() && attackerCities.all { it.capturedBy == WarSide.ATTACKER }
        val defenderHasAll = defenderCities.isNotEmpty() && defenderCities.all { it.capturedBy == WarSide.DEFENDER }

        return when {
            attackerHasAll && !defenderHasAll -> WarSide.ATTACKER
            defenderHasAll && !attackerHasAll -> WarSide.DEFENDER
            else -> null
        }
    }
}

data class WarCity(
    val id: UUID,
    val originalOwner: UUID,
    val originalSide: WarSide,
    val name: String,
    val dimension: Identifier,
    val centerX: Int,
    val centerY: Int,
    val centerZ: Int,
    val minChunkX: Int,
    val maxChunkX: Int,
    val minChunkZ: Int,
    val maxChunkZ: Int,
    val captureRadiusChunks: Int = 4,
    var capturedBy: WarSide? = null,
    var capturingSide: WarSide? = null,
    var progress: Double = 0.0
) {
    val captureRadiusBlocks: Int get() = captureRadiusChunks * 16
}

data class WarJoinRequest(
    val requester: UUID,
    val attacker: UUID,
    val defender: UUID,
    val side: WarSide
) {
    fun targetOwner(): UUID = if (side == WarSide.ATTACKER) attacker else defender
}

enum class WarSide {
    ATTACKER,
    DEFENDER
}

class KingdomOperationException(val translationKey: String) : RuntimeException()

private fun MinecraftServer.lookupName(id: UUID): String? {
    playerManager.getPlayer(id)?.gameProfile?.name?.let { return it }
    val cached = userCache?.getByUuid(id)?.orElse(null)
    return cached?.name
}

private data class ClaimedArea(
    val owner: UUID,
    val dimension: Identifier,
    val minChunkX: Int,
    val maxChunkX: Int,
    val minChunkZ: Int,
    val maxChunkZ: Int
) {
    fun intersects(other: ClaimedArea): Boolean {
        return dimension == other.dimension &&
            minChunkX <= other.maxChunkX &&
            maxChunkX >= other.minChunkX &&
            minChunkZ <= other.maxChunkZ &&
            maxChunkZ >= other.minChunkZ
    }
}
