ServerEvents.recipes(event => {
    event.recipes.cwi.item_impact('minecraft:paper', 2, [
        { type: 'replace_item', output: { id: 'minecraft:brick' } }
    ]).id('cwi:contracts/impact_paper')
    event.recipes.cwi.item_impact('minecraft:paper', 4, [
        { type: 'discard' }
    ]).id('cwi:contracts/impact_paper_high')
    event.recipes.cwi.item_impact('minecraft:feather', 2, [
        { type: 'sound', id: 'minecraft:block.sand.place', volume: 2, pitch: 2 },
        { type: 'particle', id: 'minecraft:falling_dust', block: 'minecraft:gravel', count: 16, dx: 0.5, dy: 0.3, dz: 0.5, speed: 0.1 },
        { type: 'discard' }
    ]).id('cwi:contracts/impact_feather')
    event.recipes.cwi.inventory_hazard('minecraft:diamond', 1).damageType('minecraft:in_fire')
        .id('cwi:contracts/hot_diamond')
    event.recipes.cwi.fluid_hazard('minecraft:water', 2).damageType('minecraft:magic')
        .effects([{ id: 'minecraft:slowness', duration: 20, amplifier: 2 }])
        .id('cwi:contracts/acid_water')
    event.recipes.cwi.toxin_exposure(['minecraft:apple', 'minecraft:golden_apple'], 'minecraft:weakness')
        .additionalInput('minecraft:golden_apple').stateKey('cwiTestToxin')
        .meleeChance(1).strongMeleeChance(1)
        .id('cwi:contracts/toxin_apple')
    event.recipes.cwi.lockpicking('minecraft:golden_sword', ['minecraft:chest', 'minecraft:barrel'], 1)
        .silverfishChance(0).id('cwi:contracts/lock_success')
    event.recipes.cwi.lockpicking('minecraft:iron_sword', ['minecraft:chest', 'minecraft:barrel'], 0)
        .silverfishChance(0).id('cwi:contracts/lock_failure')
    event.recipes.cwi.block_spouting(['minecraft:mud'], 'minecraft:dirt', Fluid.of('minecraft:water', 1000))
        .id('cwi:contracts/mud_spouting')
    event.recipes.cwi.budding_catalysis('minecraft:budding_amethyst', Fluid.of('minecraft:lava', 50), [
        'minecraft:small_amethyst_bud', 'minecraft:medium_amethyst_bud',
        'minecraft:large_amethyst_bud', 'minecraft:amethyst_cluster'
    ]).chance(1).attempts(3).id('cwi:contracts/budding')
    event.recipes.cwi.drying('cwi:drying_contract_dry', 'cwi:drying_contract_wet', 1)
        .id('cwi:contracts/drying')
    event.recipes.cwi.drying('cwi:drying_contract_final', 'cwi:drying_contract_dry', 0)
        .id('cwi:contracts/not_drying')
})
