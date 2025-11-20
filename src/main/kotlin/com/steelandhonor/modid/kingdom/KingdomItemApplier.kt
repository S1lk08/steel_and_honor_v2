package com.steelandhonor.modid.kingdom

import net.minecraft.block.entity.BannerPattern
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.BannerPatternsComponent
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.ShieldItem
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.network.ServerPlayerEntity

object KingdomItemApplier {
    fun applyToPlayer(player: ServerPlayerEntity, design: KingdomDesign) {
        val patternLookup = player.server.registryManager.getWrapperOrThrow(RegistryKeys.BANNER_PATTERN)
        val enchantLookup = player.server.registryManager.getWrapperOrThrow(RegistryKeys.ENCHANTMENT)
        val templateShield = buildShield(design, patternLookup, enchantLookup)
        var replaced = replaceInList(player.inventory.main, templateShield)
        replaced = replaceInList(player.inventory.offHand, templateShield) || replaced
        EquipmentSlot.values().forEach { slot ->
            val stack = player.getEquippedStack(slot)
            if (stack.item is ShieldItem) {
                player.equipStack(slot, templateShield.copy())
                replaced = true
            }
        }
        if (!replaced) {
            player.giveItemStack(templateShield.copy())
        }
    }

    private fun replaceInList(list: MutableList<ItemStack>, template: ItemStack): Boolean {
        var replaced = false
        for (i in list.indices) {
            if (list[i].item is ShieldItem) {
                list[i] = template.copy()
                replaced = true
            }
        }
        return replaced
    }

    private fun buildShield(
        design: KingdomDesign,
        patternLookup: RegistryWrapper<BannerPattern>,
        enchantLookup: RegistryWrapper<net.minecraft.enchantment.Enchantment>
    ): ItemStack {
        val stack = ItemStack(Items.SHIELD)
        stack.set(DataComponentTypes.BASE_COLOR, design.primaryColor)
        val builder = BannerPatternsComponent.Builder()
        design.layers.forEach { layer ->
            val key = RegistryKey.of(RegistryKeys.BANNER_PATTERN, layer.patternId)
            builder.add(patternLookup, key, layer.color)
        }
        val component = builder.build()
        if (component.layers().isNotEmpty()) {
            stack.set(DataComponentTypes.BANNER_PATTERNS, component)
        } else {
            stack.remove(DataComponentTypes.BANNER_PATTERNS)
        }
        val enchantments = ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT)
        enchantments.add(enchantLookup.getOrThrow(Enchantments.UNBREAKING), 5)
        enchantments.add(enchantLookup.getOrThrow(Enchantments.MENDING), 1)
        stack.set(DataComponentTypes.ENCHANTMENTS, enchantments.build())
        stack.remove(DataComponentTypes.BLOCK_ENTITY_DATA)
        return stack
    }
}
