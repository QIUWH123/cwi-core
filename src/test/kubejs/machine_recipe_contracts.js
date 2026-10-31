ServerEvents.tags('item', event => event.add('cwi:petri_dish', 'minecraft:phantom_membrane'))
// Exercise the public machine DSL through real KubeJS serialization and reload.
ServerEvents.recipes(event => {
  event.recipes.cwi.blast_furnace_processing(
    Fluid.of('minecraft:lava', 375),
    ['2x #c:sands', Item.of('minecraft:cobblestone', 3), Fluid.of('minecraft:water', 125)]
  ).processingTime(80).id('cwi:contracts/machine_furnace')

  event.recipes.cwi.incubating('2x minecraft:honey_bottle', 'minecraft:glass_bottle')
    .id('cwi:contracts/machine_incubator')

  event.recipes.cwi.mixing_vessel_mixing(
    [Item.of('minecraft:clay_ball', 3), Fluid.of('minecraft:lava', 75)],
    ['2x #c:sands', Fluid.of('minecraft:water', 250)]
  ).id('cwi:contracts/machine_mixed')

  event.recipes.cwi.mixing_vessel_mixing(
    [Item.of('minecraft:clay_ball'), Fluid.of('minecraft:lava', 75)],
    ['minecraft:sand', Fluid.of('minecraft:water', 250)]
  ).inputs(Item.of('minecraft:dirt', 4)).outputs('2x minecraft:gravel')
    .id('cwi:contracts/machine_reset')

  event.recipes.cwi.blast_furnace_processing()
    .input('minecraft:cobblestone').output(Fluid.of('minecraft:lava', 50))
    .id('cwi:contracts/machine_aliases')

  event.recipes.cwi.mixing_vessel_mixing()
    .itemInputs(['3x #c:sands'])
    .fluidInputs([{ ingredient: 'minecraft:water', amount: 10, chance: 0.5, per_tick: true }])
    .itemOutputs([{ item: 'minecraft:clay_ball', count: 2, chance: 0.25 }])
    .fluidOutputs([{ stack: Fluid.of('minecraft:lava', 20), chance: 0.75 }])
    .id('cwi:contracts/machine_advanced')

  // KubeJS event replacements operate on recipes loaded from datapacks.
  event.replaceInput({ id: 'cwi:contracts/machine_replace' }, 'minecraft:sand', 'minecraft:red_sand')
  event.replaceOutput({ id: 'cwi:contracts/machine_replace' }, 'minecraft:clay_ball', 'minecraft:brick')

  const ProcessingOutput = Java.loadClass('com.simibubi.create.content.processing.recipe.ProcessingOutput')
  event.recipes.cwi.mixing_vessel_mixing(
    new ProcessingOutput(Item.of('minecraft:brick', 2), 0.5),
    { ingredient: 'minecraft:sand', count: 2, chance: 0.5, per_tick: true }
  ).fluidInputs([Fluid.of('minecraft:water', 100)])
    .id('cwi:contracts/machine_chance')

  // Overlapping native recipes verify specificity, speed feedback, and reload behavior.
  event.recipes.cwi.mixing_vessel_mixing('minecraft:emerald', 'minecraft:nether_star')
    .processingTime(4).id('cwi:contracts/a_simple_vessel')
  event.recipes.cwi.mixing_vessel_mixing('minecraft:diamond', ['minecraft:nether_star', 'minecraft:nether_star'])
    .processingTime(4).id('cwi:contracts/z_specific_vessel')
  event.recipes.cwi.mixing_vessel_mixing('minecraft:amethyst_shard', '2x minecraft:echo_shard')
    .minimumRpm(128).processingTime(20).id('cwi:contracts/vessel_speed')
  event.recipes.cwi.incubating('minecraft:amethyst_shard', 'minecraft:phantom_membrane')
    .minimumRpm(128).processingTime(20).id('cwi:contracts/a_incubator_speed')

})
