// Reloadable settings. CWI Core owns ticking, interactions and Create spout execution.
ServerEvents.recipes(event => {
    event.recipes.cwi.item_impact('minecraft:glass', 2, [
        { type: 'sound', id: 'minecraft:block.glass.break' },
        { type: 'particle', id: 'minecraft:block', block: 'minecraft:glass', count: 20, dx: 0.3, dy: 0.3, dz: 0.3, speed: 0.1 },
        { type: 'discard' }
    ]).id('cwi:example/fragile_glass')

    event.recipes.cwi.inventory_hazard('#cwi:hot_items', 1)
        .damageType('minecraft:in_fire').interval(10).id('cwi:example/hot_items')
    event.recipes.cwi.fluid_hazard('minecraft:lava', 7)
        .damageType('minecraft:lava').fireTicks(60).interval(10)
        .effects([{ id: 'minecraft:slowness', duration: 10, amplifier: 6 }])
        .id('cwi:example/molten_contact')

    // Missing optional effect IDs remain dormant; exposure still accumulates and decays.
    event.recipes.cwi.toxin_exposure('#cwi:neurotoxin', 'clanginghowl:neurotoxin')
        .additionalInput('#cwi:neurotoxin_lv2').additionalWeight(9)
        .retention(0.85).scale(0.02).threshold(1.5)
        .meleeChance(0.2).strongMeleeChance(0.4).id('cwi:example/neurotoxin')

    event.recipes.cwi.lockpicking('minecraft:iron_pickaxe', ['minecraft:chest', 'minecraft:barrel'], 0.5)
        .durability(3).lootOnly(true).silverfishChance(0.1).id('cwi:example/lockpick')
    event.recipes.cwi.block_spouting(['minecraft:mud'], 'minecraft:dirt', Fluid.of('minecraft:water', 1000))
        .id('cwi:example/mud')
    event.recipes.cwi.budding_catalysis('minecraft:budding_amethyst', Fluid.of('minecraft:lava', 50), [
        'minecraft:small_amethyst_bud', 'minecraft:medium_amethyst_bud',
        'minecraft:large_amethyst_bud', 'minecraft:amethyst_cluster'
    ]).chance(0.75).attempts(2).id('cwi:example/budding')

    // Register these blocks with the cwi:drying builder in startup_scripts first.
    // event.recipes.cwi.drying('kubejs:pressed_wet_web', 'kubejs:wet_web', 0.02)
    //     .id('cwi:example/drying')
})
