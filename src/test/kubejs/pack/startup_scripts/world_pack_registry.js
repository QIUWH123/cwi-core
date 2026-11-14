// Test-only optional IDs; no implementation or assets from optional mods.
const registries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries')
const rl = Java.loadClass('net.minecraft.resources.ResourceLocation')
StartupEvents.registry('block', event => {
    for (const id of ["geode_plus:budding_nether_quartz","geode_plus:small_nether_quartz_bud","geode_plus:medium_nether_quartz_bud","geode_plus:large_nether_quartz_bud","geode_plus:nether_quartz_crystal"]) {
        if (!registries.BLOCK.containsKey(rl.parse(id))) event.create(id)
    }
})
StartupEvents.registry('item', event => {
    for (const id of ["kubejs:ash","kubejs:dark_ash","create:polished_rose_quartz","kubejs:damaged_polished_rose_quartz","create:precision_mechanism","kubejs:damaged_precision_mechanism","naturescompass:naturescompass","kubejs:damaged_natures_compass","create_connected:control_chip","kubejs:damaged_control_chip","create:electron_tube","kubejs:damaged_electron_tube","kubejs:devitrified_glass","kubejs:cracked_glass","ae2:tiny_tnt","kubejs:rusted_iron_sword","kubejs:rusted_iron_axe","kubejs:rusted_iron_pickaxe","kubejs:rusted_iron_shovel","kubejs:rusted_iron_hoe","kubejs:lead_sword","kubejs:lead_axe","kubejs:lead_pickaxe","kubejs:lead_shovel","kubejs:lead_hoe","kubejs:copper_sword","kubejs:copper_axe","kubejs:copper_pickaxe","kubejs:copper_shovel","kubejs:copper_hoe","kubejs:zinc_sword","kubejs:zinc_axe","kubejs:zinc_pickaxe","kubejs:zinc_shovel","kubejs:zinc_hoe","kubejs:brass_sword","kubejs:brass_axe","kubejs:brass_pickaxe","kubejs:brass_shovel","kubejs:brass_hoe","kubejs:bronze_sword","kubejs:bronze_axe","kubejs:bronze_pickaxe","kubejs:bronze_shovel","kubejs:bronze_hoe","kubejs:steel_sword","kubejs:steel_axe","kubejs:steel_pickaxe","kubejs:steel_shovel","kubejs:steel_hoe","kubejs:stainless_steel_sword","kubejs:stainless_steel_axe","kubejs:stainless_steel_pickaxe","kubejs:stainless_steel_shovel","kubejs:stainless_steel_hoe","ae2:nether_quartz_sword","ae2:nether_quartz_axe","ae2:nether_quartz_pickaxe","ae2:nether_quartz_shovel","ae2:nether_quartz_hoe","ae2:certus_quartz_sword","ae2:certus_quartz_axe","ae2:certus_quartz_pickaxe","ae2:certus_quartz_shovel","ae2:certus_quartz_hoe","ae2:fluix_sword","ae2:fluix_axe","ae2:fluix_pickaxe","ae2:fluix_shovel","ae2:fluix_hoe","clanginghowl:advanced_chainsword"]) {
        if (!registries.ITEM.containsKey(rl.parse(id))) event.create(id)
    }
})
StartupEvents.registry('fluid', event => {
    for (const id of ["kubejs:molten_aluminum","kubejs:molten_vanadium","kubejs:molten_tin","kubejs:molten_silver","kubejs:molten_andesite_alloy","kubejs:molten_zinc","kubejs:molten_gold","kubejs:molten_iron","kubejs:molten_pig_iron","kubejs:molten_undeoxidized_steel","kubejs:molten_cast_iron","kubejs:molten_copper","kubejs:molten_brass","kubejs:molten_lead","kubejs:molten_cobalt","kubejs:molten_nickel","kubejs:molten_constantan","kubejs:molten_bronze","kubejs:molten_electrum","kubejs:molten_slag","kubejs:liquid_silicon","kubejs:molten_glass","kubejs:redstone_acid","kubejs:concentrated_sulfuric_acid","kubejs:nitric_acid","kubejs:muriatic_acid","kubejs:acetic_acid","kubejs:propionic_acid","kubejs:lactic_acid","kubejs:wood_vinegar","kubejs:humic_acid_slurry","kubejs:sulfuric_acid","kubejs:nitrogen_fertilizer","kubejs:caustic_soda"]) {
        if (!registries.FLUID.containsKey(rl.parse(id))) event.create(id)
    }
})
