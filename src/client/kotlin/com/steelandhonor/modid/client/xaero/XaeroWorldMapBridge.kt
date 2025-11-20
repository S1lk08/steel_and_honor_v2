package com.steelandhonor.modid.client.xaero

import xaero.map.highlight.HighlighterRegistry

object XaeroWorldMapBridge {
    fun registerCityHighlighters(registry: HighlighterRegistry) {
        registry.register(CityOverlayHighlighter())
    }
}
