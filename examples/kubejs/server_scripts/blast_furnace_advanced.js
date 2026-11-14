// Illustrative recipe; copy into your pack's server_scripts when wanted.
ServerEvents.recipes(event => {
  event.recipes.cwi.blast_furnace_processing(
    [Item('minecraft:brick'), Fluid('minecraft:lava', 25),
      Item('minecraft:gold_nugget').perTick(20)],
    [Item('2x minecraft:sand'), Item('minecraft:flint').catalyst(),
      Item('minecraft:redstone').perTick(20),
      Fluid('minecraft:water', 5).perTick(2, 'recipe')]
  )
    .processingTime(100)
    .minimumHeat(1200)
    .maximumHeat(2200)
    .priority(10)
    .outputPool([
      Item('2x minecraft:gravel').withWeight(3),
      Item('minecraft:cobblestone')
    ]).showOutputs()
    .outputPool([Item('minecraft:glowstone_dust'), Item('minecraft:quartz')])
    .id('cwi:example/furnace_advanced')
})

// All amounts and catalyst reservations are per recipe copy in a parallel batch.
// Each copy rolls each output pool independently; the second pool is hidden.
// perTick(20): every 20 successful working ticks. Paused ticks do not count.
// perTick(2, 'recipe'): every 2 base processing ticks, independent of furnace heat.
// Periodic recipes pay ordinary inputs upfront; other recipes pay at completion.
// Heat changes pause/resume work but do not change the duration sampled at its start.
// Item results use six output slots. Extract through the underside of a hatch.
// Furnace inputs do not support damage() or consumeEnergy().
