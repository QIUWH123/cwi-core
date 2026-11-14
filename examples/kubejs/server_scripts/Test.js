// CWI Schema And Machine Test Recipes. Remove This File To Remove Its Test Recipes.
// Outputs Come First And Inputs Second. Optional Mods Are Guarded.
// Hammer Items Must Be Registered With The Hammer Builder During Startup.
ServerEvents.tags('item', event => event.add('cwi:petri_dish', ['minecraft:dragon_breath', 'minecraft:experience_bottle']))

ServerEvents.recipes(event => {
    const r = event.recipes
    const id = name => 'cwi:test/' + name
    const chance = (item, count, probability) => ({ id: item, count: count, chance: probability })

    r.cwi.block_conversion('minecraft:quartz_block', 'minecraft:chiseled_quartz_block')
        .requireStage(1).id(id('hammer_block_1'))
    r.cwi.block_conversion().input('minecraft:quartz_block').target('minecraft:quartz_bricks')
        .drops([{ item: 'minecraft:quartz', count: 2, chance: 0.5 }]).requireStage(2).id(id('hammer_block_2'))
    r.cwi.depot_conversion('2x minecraft:quartz', 'minecraft:quartz_pillar')
        .requireStage(1).id(id('hammer_depot_1'))
    r.cwi.depot_conversion().input('minecraft:quartz_bricks').output('minecraft:quartz_block')
        .requiredStage(3).id(id('hammer_depot_2'))

    r.cwi.blast_furnace_processing(Fluid.of('minecraft:lava', 125), 'minecraft:quartz_block')
        .processingTime(20).id(id('furnace_1'))
    r.cwi.blast_furnace_processing().ingredients(['2x minecraft:quartz', Fluid.of('minecraft:water', 100)])
        .results(Fluid.of('minecraft:lava', 250)).processingTime(40).heated().id(id('furnace_2'))
    r.cwi.incubating('minecraft:slime_ball', 'minecraft:dragon_breath')
        .minimumRpm(16).processingTime(20).id(id('incubator_1'))
    r.cwi.incubating({ item: 'minecraft:honey_bottle', count: 2, chance: 0.5 }, 'minecraft:experience_bottle')
        .minimumRpm(64).processingTime(40).id(id('incubator_2'))
    r.cwi.mixing_vessel_mixing('2x minecraft:prismarine_crystals', ['minecraft:prismarine_shard', Fluid.of('minecraft:water', 100)])
        .minimumRpm(16).processingTime(20).id(id('mixer_1'))
    r.cwi.mixing_vessel_mixing([chance('minecraft:prismarine_shard', 3, 0.5), Fluid.of('minecraft:water', 50)],
        ['2x minecraft:prismarine_crystals', Fluid.of('minecraft:lava', 100)])
        .minimumRpm(64).processingTime(40).id(id('mixer_2'))

// Empty Templates Are Valid Native Smithing Data. The Normal Shorthand Remains Available.
    r.minecraft.smithing_transform('minecraft:quartz_bricks', [], 'minecraft:quartz_block', 'minecraft:quartz')
        .id(id('smithing_empty'))
    event.smithing('minecraft:netherite_axe', 'minecraft:diamond_axe', 'minecraft:netherite_ingot')
        .id(id('smithing_normal'))

    if (Platform.isLoaded('createaddition')) {
        r.createaddition.rolling('2x minecraft:prismarine_crystals', 'minecraft:nautilus_shell').id(id('rolling_1'))
        r.createaddition.rolling(chance('minecraft:quartz', 3, 0.5), 'minecraft:amethyst_shard').id(id('rolling_2'))
        r.createaddition.charging('minecraft:heart_of_the_sea', 'minecraft:nautilus_shell', 1000, 100).id(id('charging_1'))
        r.createaddition.charging('2x minecraft:prismarine_crystals', 'minecraft:heart_of_the_sea', 2000, 200).id(id('charging_2'))
        r.createaddition.liquid_burning(Fluid.of('minecraft:lava', 100), 400).id(id('liquid_burning_1'))
        r.createaddition.liquid_burning(Fluid.of('minecraft:water', 100), 200).superheated(true).id(id('liquid_burning_2'))
        if (Platform.isLoaded('kubejs_create')) {
            let testIncomplete = 'minecraft:nautilus_shell'
            r.create.sequenced_assembly('2x minecraft:heart_of_the_sea', 'minecraft:prismarine', [
                r.createaddition.rolling(testIncomplete, testIncomplete), r.create.pressing(testIncomplete, testIncomplete)
            ]).transitionalItem(testIncomplete).loops(2).id(id('rolling_assembly'))
        }
    }
    if (Platform.isLoaded('createdieselgenerators')) {
        for (let n = 1; n <= 2; n++) {
            r.createdieselgenerators.hammering(chance('minecraft:quartz', n, n === 1 ? 1 : 0.5), n === 1 ? 'minecraft:quartz_pillar' : 'minecraft:quartz_slab')
                .id(id('hammering_' + n))
            r.createdieselgenerators.wire_cutting(n + 'x minecraft:string', n === 1 ? 'minecraft:nautilus_shell' : 'minecraft:prismarine_shard').id(id('wire_cutting_' + n))
            r.createdieselgenerators.distillation([Fluid.of('minecraft:lava', 50), Fluid.of('minecraft:water', 50)], Fluid.of(n === 1 ? 'minecraft:water' : 'minecraft:lava', 100))
                .processingTime(40 * n).heated().id(id('diesel_distillation_' + n))
            r.createdieselgenerators.basin_fermenting(Fluid.of('minecraft:water', 100 * n), [n === 1 ? 'minecraft:sugar' : 'minecraft:honey_bottle', Fluid.of('minecraft:water', 50)])
                .processingTime(40 * n).id(id('basin_fermenting_' + n))
            r.createdieselgenerators.bulk_fermenting([n + 'x minecraft:slime_ball', Fluid.of('minecraft:water', 100)],
                [n === 1 ? 'minecraft:prismarine' : 'minecraft:sea_lantern', Fluid.of('minecraft:water', 50)]).processingTime(40 * n).id(id('bulk_fermenting_' + n))
        }
    }
    if (Platform.isLoaded('tfmg')) {
        for (let n = 1; n <= 2; n++) {
        // TFMG Requires Both Fluid Results And Emits Their Amounts Every Processing Tick.
            r.tfmg.coking([n + 'x minecraft:charcoal', Fluid.of('minecraft:water', 1), Fluid.of('minecraft:lava', 1)], n === 1 ? 'minecraft:quartz_bricks' : 'minecraft:quartz_slab')
                .processingTime(40 * n).id(id('coking_' + n))
            r.tfmg.distillation([Fluid.of('minecraft:water', 50), Fluid.of('minecraft:lava', 50)], Fluid.of(n === 1 ? 'minecraft:lava' : 'minecraft:water', 100))
                .processingTime(40 * n).id(id('tfmg_distillation_' + n))
            r.tfmg.vat_machine_recipe([chance('minecraft:quartz', n, 0.5), Fluid.of('minecraft:water', 50)],
                [n === 1 ? '2x minecraft:prismarine' : '2x minecraft:prismarine_bricks', Fluid.of('minecraft:lava', 100)]).processingTime(40 * n)
                .minSize(n).heatLevel(n - 1).pressure(0).machines(['tfmg:mixing']).allowedVatTypes(['tfmg:steel'])
                .id(id('vat_' + n))
            // TFMG 1.3.3's canonical type; the older name above remains a native alias.
            r.tfmg.vat([chance('minecraft:quartz', n, 0.5), Fluid.of('minecraft:water', 50)],
                [n === 1 ? '2x minecraft:prismarine' : '2x minecraft:prismarine_bricks', Fluid.of('minecraft:lava', 100)]).processingTime(40 * n)
                .minSize(n).heatLevel(n - 1).pressure(0).machines(['tfmg:mixing']).allowedVatTypes(['tfmg:steel'])
                .id(id('vat_canonical_' + n))
        }
    }
    if (Platform.isLoaded('ratatouille')) {
        r.ratatouille.threshing(['2x minecraft:wheat', chance('minecraft:wheat_seeds', 1, 0.5)], 'minecraft:hay_block')
            .processingTime(40).id(id('threshing_1'))
        r.ratatouille.threshing('minecraft:string', 'minecraft:prismarine').processingTime(80).id(id('threshing_2'))
        r.ratatouille.demolding('minecraft:clay_ball', 'minecraft:brick').id(id('demolding_1'))
        r.ratatouille.demolding(['minecraft:brick', 'minecraft:clay_ball'], 'minecraft:flower_pot').id(id('demolding_2'))
    }
    if (Platform.isLoaded('vintageimprovements')) {
        for (let n = 1; n <= 2; n++) {
            r.vintageimprovements.pressurizing([], n === 1 ? 'minecraft:prismarine' : 'minecraft:prismarine_bricks').secondaryFluidOutput(Fluid.of('minecraft:water', 50 * n))
                .secondaryFluidInput(Fluid.of('minecraft:lava', 25 * n))
                .processingTime(40).id(id('pressurizing_' + n))
            r.vintageimprovements.leaves_vibrating([], n === 1 ? Ingredient.of('#minecraft:leaves') : Ingredient.of('minecraft:birch_leaves')).processingTime(40 * n).id(id('leaves_vibrating_' + n))
            r.vintageimprovements.auto_upgrade([], ['minecraft:netherite_ingot', n === 1 ? 'minecraft:diamond_sword' : 'minecraft:diamond_axe']).id(id('auto_upgrade_' + n))
            r.vintageimprovements.auto_smithing([], ['minecraft:sentry_armor_trim_smithing_template', n === 1 ? 'minecraft:iron_chestplate' : 'minecraft:diamond_chestplate', 'minecraft:quartz'])
                .id(id('auto_smithing_' + n))
        }
    }
    if (Platform.isLoaded('createmetallurgy')) {
        for (let n = 1; n <= 2; n++) {
            r.createmetallurgy.entity_melting(Fluid.of('minecraft:lava', 100 * n),
                EntityIngredient.of(n === 1 ? 'minecraft:iron_golem' : 'minecraft:piglin', 6))
                .id(id('entity_melting_' + n))
            r.createmetallurgy.melting([Fluid.of('minecraft:lava', 100)], ['minecraft:prismarine', 'minecraft:quartz', 'minecraft:amethyst_shard', n === 1 ? 'minecraft:flint' : 'minecraft:gunpowder'])
                .processingTime(40 * n).id(id('melting_' + n))
            r.createmetallurgy.alloying([Fluid.of('minecraft:water', 100)], ['minecraft:prismarine', 'minecraft:quartz', 'minecraft:amethyst_shard', n === 1 ? 'minecraft:flint' : 'minecraft:gunpowder'])
                .processingTime(40 * n).id(id('alloying_' + n))
        }
    }
})
