StartupEvents.registry('block', event => {
    const stages = ['small', 'medium', 'large', 'cluster']
    for (const stage of stages) {
        event.create(`cwi:crystal_contract_${stage}`, 'crystal_cluster')
            .stage(stage)
            .texture('minecraft:block/amethyst_cluster')
            .tagBlock('minecraft:mineable/pickaxe')
    }
    event.create('cwi:crystal_contract_source', 'budding')
        .texture('minecraft:block/budding_amethyst')
    event.create('cwi:crystal_contract_family_source', 'budding')
        .stages(stages.map(stage => `cwi:crystal_contract_${stage}`))
    event.create('cwi:crystal_contract_up_source', 'budding')
        .stages(stages.map(stage => `cwi:crystal_contract_${stage}`))
        .growthFaces(['up']).growthChance(1)
    event.create('cwi:crystal_contract_dry_source', 'budding')
        .growthFaces(['up']).growthChance(1).growthInWater(false)
    event.create('cwi:crystal_contract_zero_source', 'budding').growthChance(0)
    event.create('cwi:crystal_contract_effect_source', 'budding')
        .growthFaces(['up']).growthChance(1)
        .growthSound('minecraft:block.amethyst_block.chime', 0.7, 1)
        .growthParticles('minecraft:happy_villager', 4)
        .growthEffect('minecraft:darkness', 100, 10)
    event.create('cwi:crystal_contract_box', 'crystal_cluster')
        .box(4, 0, 4, 12, 3, 12)
        .lightLevel(10 / 15)
    event.create('cwi:crystal_contract_unlit', 'crystal_cluster').lightLevel(0)
    event.create('cwi:crystal_contract_no_drops', 'crystal_cluster').noDrops()
    event.create('cwi:crystal_contract_model', 'crystal_cluster').model('minecraft:block/amethyst_cluster')
    event.create('cwi:crystal_contract_source_model', 'budding').model('minecraft:block/amethyst_block')
    event.create('cwi:crystal_contract_source_drops', 'budding').drops(() =>
        Java.loadClass('dev.latvian.mods.kubejs.block.drop.BlockDrops').createDefault(Item.of('minecraft:diamond')))
    // Current original quartz names also exercise legacy source retargeting.
    for (const stage of ['small', 'medium', 'large']) {
        event.create(`cwi:${stage}_quartz_bud`, 'crystal_cluster').stage(stage)
    }
    event.create('cwi:quartz_crystal', 'crystal_cluster')
})
