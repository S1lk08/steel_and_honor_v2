package com.steelandhonor.modid.client.mixin

import net.fabricmc.loader.api.FabricLoader
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

class SteelAndHonorMixinPlugin : IMixinConfigPlugin {
    private val isXaeroWorldMapLoaded: Boolean = FabricLoader.getInstance().isModLoaded("xaeroworldmap")

    override fun onLoad(mixinPackage: String) = Unit

    override fun getRefMapperConfig(): String? = null

    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean {
        return if (mixinClassName == XAERO_WORLDMAP_MIXIN) {
            isXaeroWorldMapLoaded
        } else {
            true
        }
    }

    override fun acceptTargets(myTargets: MutableSet<String>, otherTargets: MutableSet<String>) = Unit

    override fun getMixins(): MutableList<String>? = null

    override fun preApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo
    ) = Unit

    override fun postApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo
    ) = Unit

    companion object {
        private const val XAERO_WORLDMAP_MIXIN =
            "com.steelandhonor.modid.client.mixin.XaeroWorldMapSessionMixin"
    }
}
