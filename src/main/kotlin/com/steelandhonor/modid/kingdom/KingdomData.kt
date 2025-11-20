package com.steelandhonor.modid.kingdom

import net.minecraft.registry.RegistryKey
import net.minecraft.util.DyeColor
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

data class KingdomData(
    val owner: UUID,
    var name: String,
    var color: DyeColor,
    var design: KingdomDesign = KingdomDesign.DEFAULT,
    val members: MutableSet<UUID> = mutableSetOf(owner),
    val roles: MutableMap<UUID, KingdomRole> = mutableMapOf(owner to KingdomRole.LEADER),
    var partyId: UUID? = null,
    var claimCount: Int = 0
) {
    fun isKingdom(): Boolean {
        return claimCount >= MINIMUM_CLAIM_COUNT
    }

    fun roleOf(member: UUID): KingdomRole {
        return roles[member] ?: KingdomRole.CITIZEN
    }

    companion object {
        private const val MINIMUM_CLAIM_COUNT = 9
    }
}

data class CityBorderSnapshot(
    val id: UUID,
    val name: String,
    val center: BlockPos,
    val radius: Int,
    val dimension: RegistryKey<World>,
    val capital: Boolean,
    val kingdomColor: DyeColor,
    val kingdomName: String,
    val minChunkX: Int,
    val maxChunkX: Int,
    val minChunkZ: Int,
    val maxChunkZ: Int
)
