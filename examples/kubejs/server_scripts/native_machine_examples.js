// Illustrative recipes only. Copy this file into a test instance to enable them.
// These are not the CWI modpack's production/progression recipes.
ServerEvents.tags('item', event => {
  event.add('cwi:petri_dish', 'minecraft:glass_bottle')
})

ServerEvents.recipes(event => {
  // Standard KubeJS order: outputs, inputs. A single value or an array works.
  event.recipes.cwi.blast_furnace_processing(
    Fluid.of('minecraft:lava', 250),
    'minecraft:cobblestone'
  )
    .processingTime(200)
    .heated()
    // Optional numeric range: .minimumHeat(1200).maximumHeat(1600)
    .id('cwi:example/blast_furnace')

  event.recipes.cwi.incubating(
    { item: 'minecraft:honey_bottle', chance: 0.5 },
    'minecraft:glass_bottle'
  )
    .processingTime(400)
    .minimumRpm(64)
    .incubationDisplay({
      microbe: 'minecraft:slime_ball',
      yield_count: 3,
      steps: [
        { type: 'deploying', item: 'minecraft:sugar', count: 2 },
        { type: 'filling', fluid: 'minecraft:water', amount: 250, count: 2 }
      ]
    }) // JEI preparation guidance; the incubator consumes only the prepared bottle above.
    .id('cwi:example/incubating')

  event.recipes.cwi.mixing_vessel_mixing(
    'minecraft:clay_ball',
    ['2x #c:sands', Fluid.of('minecraft:water', 250)]
  )
    .processingTime(100) // Create mixer base time: 16 active ticks at 256 RPM, 31 at 128 RPM.
    .minimumRpm(16)
    .id('cwi:example/mixing_vessel')

  // Shorthand follows Create's output, input order; the fluent form also works.
  event.recipes.cwi.block_conversion('minecraft:polished_basalt', 'minecraft:smooth_basalt')
    .requireStage(1)
    .id('cwi:example/hammer_block_stage_1')

  event.recipes.cwi.block_conversion()
    .input('minecraft:smooth_basalt')
    .target('minecraft:stone')
    .drops([{ item: 'minecraft:gravel', count: 4, chance: 0.75 }])
    .requireStage(2)
    .id('cwi:example/hammer_block_stage_2')

  event.recipes.cwi.depot_conversion('create:crushed_raw_iron', 'minecraft:raw_iron')
    .requireStage(3)
    .id('cwi:example/hammer_depot_conversion')
})
