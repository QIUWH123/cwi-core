// Mixing vessel only. Copy into your pack's server_scripts when wanted.
ServerEvents.recipes(event => {
  event.recipes.cwi.mixing_vessel_mixing(
    [Item('2x minecraft:clay_ball'), Item('minecraft:iron_nugget').perTick(2)],
    [Item('2x minecraft:sand'), Item('minecraft:flint').catalyst(),
      Fluid('minecraft:water', 5).perTick(2, 'recipe')]
  )
    .processingTime(100)
    .minimumRpm(64)
    .priority(10)
    .outputPool([
      Item('2x minecraft:gravel').withWeight(3),
      Item('minecraft:cobblestone').withWeight(1)
    ]).showOutputs() // Only this pool is visible.
    .outputPool([
      Item('minecraft:redstone'),
      Item('minecraft:glowstone_dust')
    ]) // Independent pool, hidden by default.
    .id('cwi:example/vessel_catalyst_and_pools')

  event.recipes.cwi.mixing_vessel_mixing(
    Item('minecraft:flint'),
    [Item('minecraft:gravel'), Item('minecraft:iron_sword').damage(10)]
  ).processingTime(100).id('cwi:example/vessel_tool_cost')
})

// .perTick() / .perTick(2): successful working ticks; pauses never count.
// .perTick(2, 'recipe'): base processing-time ticks; total uses stay the same at any RPM.
// Item('minecraft:iron_sword').damage(1).perTick(2) pays wear every second working tick.
// Item('your_mod:battery').consumeEnergy(20) retains an extractable FE item and spends20FE.
// Ordinary costs are paid at completion, or upfront when any entry is periodic.
// Pool entries are concrete item stacks with optional positive weights; no chance, tags or timing.
// Optional18x18 custom frames (kept in KubeJS, never bundled by Core):
// assets/cwi/textures/gui/jei/catalyst_slot.png
// assets/cwi/textures/gui/jei/per_tick_slot.png
