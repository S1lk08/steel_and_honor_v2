package com.steelandhonor.modid.kingdom

enum class KingdomRole {
    LEADER,
    OFFICER,
    POLITICIAN,
    MILITARY,
    CITIZEN;

    val isCommandRank: Boolean
        get() = this == LEADER || this == OFFICER

    val canInvite: Boolean
        get() = this == LEADER || this == POLITICIAN

    companion object {
        fun fromName(name: String): KingdomRole? {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}
