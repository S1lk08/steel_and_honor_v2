package com.steelandhonor.modid.util

import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.sound.SoundEvent

object SoundUtil {
    fun resolve(sound: Any): SoundEvent {
        return when (sound) {
            is SoundEvent -> sound
            is RegistryEntry<*> -> (sound as RegistryEntry<SoundEvent>).value()
            else -> throw IllegalArgumentException("Unsupported sound type: ${sound::class.java}")
        }
    }
}
