package com.steelandhonor.modid.client.gui

import com.steelandhonor.modid.kingdom.KingdomRole
import net.minecraft.util.DyeColor

enum class SuggestionType {
    COLOR,
    ROLE,
    KINGDOM_NAME,
    PLAYER_NAME,
    WAR_TARGET,
    INVITE_TARGET,
    WAR_REQUEST_TARGET
}

object SuggestionProvider {
    private var cachedKingdomNames: List<String> = emptyList()
    private var cachedPlayerNames: List<String> = emptyList()
    private var cachedWarTargets: List<String> = emptyList()
    private var cachedInviteTargets: List<String> = emptyList()
    private var cachedWarRequestTargets: List<String> = emptyList()
    
    fun getSuggestions(type: SuggestionType, currentText: String): List<String> {
        val allSuggestions = when (type) {
            SuggestionType.COLOR -> DyeColor.entries.map { it.getName() }
            SuggestionType.ROLE -> KingdomRole.entries.map { it.name.lowercase() }
            SuggestionType.KINGDOM_NAME -> cachedKingdomNames
            SuggestionType.PLAYER_NAME -> cachedPlayerNames
            SuggestionType.WAR_TARGET -> cachedWarTargets
            SuggestionType.INVITE_TARGET -> cachedInviteTargets
            SuggestionType.WAR_REQUEST_TARGET -> cachedWarRequestTargets
        }
        
        if (currentText.isEmpty()) {
            return allSuggestions
        }
        
        val lowerText = currentText.lowercase()
        return allSuggestions.filter { it.lowercase().startsWith(lowerText) }
    }
    
    fun updateKingdomNames(names: List<String>) {
        cachedKingdomNames = names
    }
    
    fun updatePlayerNames(names: List<String>) {
        cachedPlayerNames = names
    }
    
    fun updateWarTargets(targets: List<String>) {
        cachedWarTargets = targets
    }
    
    fun updateInviteTargets(targets: List<String>) {
        cachedInviteTargets = targets
    }
    
    fun updateWarRequestTargets(targets: List<String>) {
        cachedWarRequestTargets = targets
    }
}

