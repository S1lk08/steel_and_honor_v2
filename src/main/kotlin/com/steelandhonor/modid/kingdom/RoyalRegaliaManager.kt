package com.steelandhonor.modid.kingdom

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.component.type.UnbreakableComponent
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.Identifier

object RoyalRegaliaManager {
    private val CROWN_ID: Identifier = Identifier.of("epic_knights", "crown_decoration")
    private val CROWN_NAME: Text = Text.translatable("item.steel_and_honor.royal_crown")

    fun sync(player: ServerPlayerEntity, isLeader: Boolean) {
        val crownItem = Registries.ITEM.getOrEmpty(CROWN_ID).orElse(null) ?: return
        if (!isLeader) {
            removeRoyalCrowns(player, crownItem)
            return
        }
        val existing = findRoyalCrown(player, crownItem)
        if (existing != null) {
            decorate(existing, player)
            return
        }
        val newStack = ItemStack(crownItem)
        decorate(newStack, player)
        if (!player.giveItemStack(newStack.copy())) {
            player.dropItem(newStack, false)
        }
    }

    private fun decorate(stack: ItemStack, player: ServerPlayerEntity) {
        stack.set(DataComponentTypes.CUSTOM_NAME, CROWN_NAME)
        stack.set(DataComponentTypes.UNBREAKABLE, UnbreakableComponent(true))
        val enchantLookup = player.server.registryManager.getWrapperOrThrow(RegistryKeys.ENCHANTMENT)
        val enchantments = ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT)
        enchantments.add(enchantLookup.getOrThrow(Enchantments.VANISHING_CURSE), 1)
        stack.set(DataComponentTypes.ENCHANTMENTS, enchantments.build())
    }

    private fun findRoyalCrown(player: ServerPlayerEntity, crownItem: Item): ItemStack? {
        player.inventory.armor.forEach { stack ->
            if (isRoyalCrown(stack, crownItem)) {
                return stack
            }
        }
        player.inventory.main.forEach { stack ->
            if (isRoyalCrown(stack, crownItem)) {
                return stack
            }
        }
        player.inventory.offHand.forEach { stack ->
            if (isRoyalCrown(stack, crownItem)) {
                return stack
            }
        }
        val equipped = player.getEquippedStack(EquipmentSlot.HEAD)
        if (isRoyalCrown(equipped, crownItem)) {
            return equipped
        }
        return null
    }

    private fun removeRoyalCrowns(player: ServerPlayerEntity, crownItem: Item) {
        for (i in player.inventory.main.indices) {
            if (isRoyalCrown(player.inventory.main[i], crownItem)) {
                player.inventory.main[i] = ItemStack.EMPTY
            }
        }
        for (i in player.inventory.armor.indices) {
            if (isRoyalCrown(player.inventory.armor[i], crownItem)) {
                player.inventory.armor[i] = ItemStack.EMPTY
            }
        }
        for (i in player.inventory.offHand.indices) {
            if (isRoyalCrown(player.inventory.offHand[i], crownItem)) {
                player.inventory.offHand[i] = ItemStack.EMPTY
            }
        }
        if (isRoyalCrown(player.getEquippedStack(EquipmentSlot.HEAD), crownItem)) {
            player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY)
        }
    }

    private fun isRoyalCrown(stack: ItemStack, crownItem: Item): Boolean {
        if (!stack.isOf(crownItem)) return false
        val customName = stack.get(DataComponentTypes.CUSTOM_NAME) ?: return false
        val content = customName.content
        return content is TranslatableTextContent && content.key == "item.steel_and_honor.royal_crown"
    }
}
