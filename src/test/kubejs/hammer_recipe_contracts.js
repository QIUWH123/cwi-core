// Test-only coverage for aliases, omitted block targets and native stack drops.
ServerEvents.recipes(event => {
  event.recipes.cwi.block_conversion()
    .ingredient('minecraft:glass')
    .outputs([
      { stack: Item.of('minecraft:diamond', 2), chance: 1 },
      { item: 'minecraft:stick', chance: 0 },
      Item.of('minecraft:flint')
    ])
    .requiredStage(2)
    .id('cwi:contracts/hammer_dsl_break')
})
