// CWI Core callable descriptor examples. Keep .of() for real inventory stacks.
ServerEvents.recipes(event => {
  event.recipes.cwi.mixing_vessel_mixing(
    Item('2x minecraft:clay_ball').withChance(0.75),
    [Item('2x minecraft:sand'), Fluid('250x minecraft:water')]
  ).id('cwi:example/callable_mixing')

  event.recipes.cwi.blast_furnace_processing(
    Fluid('100x minecraft:lava'),
    [Item('minecraft:magma_block'), Fluid('1x minecraft:water').perTick()]
  ).id('cwi:example/callable_furnace')

  // The native incubator still requires an appropriate plate/item tag to run.
  event.recipes.cwi.incubating(
    Item('minecraft:slime_ball'),
    Item('minecraft:glass_bottle').withChance(0)
  ).id('cwi:example/callable_incubating')

  if (!Platform.isLoaded('kubejs_create')) return
  event.recipes.create.mixing(
    [Item('minecraft:clay_ball').withChance(0.5), Fluid('50x minecraft:water')],
    [Item('2x minecraft:sand'), Fluid('250x minecraft:water')]
  ).id('cwi:example/callable_create_mixing')

  const transitional = Item('create:incomplete_precision_mechanism')
  event.recipes.create.sequenced_assembly(
    [Item('create:precision_mechanism').withWeight(4), Item('minecraft:gold_nugget').withWeight(1)],
    Item('minecraft:gold_ingot'),
    [event.recipes.create.pressing(transitional, transitional)]
  ).transitionalItem(transitional).loops(2).id('cwi:example/callable_assembly')
})
