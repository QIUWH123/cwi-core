// Datapack fixtures exercise matching/replacement, rather than only added recipe serialization.
ServerEvents.recipes(event => {
  if (Platform.isLoaded('createaddition')) {
    event.replaceInput({id: 'cwi:schema_contracts/rolling_replace'}, 'minecraft:quartz', 'minecraft:flint')
    event.replaceOutput({id: 'cwi:schema_contracts/rolling_replace'}, 'minecraft:gold_nugget', 'minecraft:iron_nugget')
  }
  if (Platform.isLoaded('tfmg')) {
    event.replaceInput({id: 'cwi:schema_contracts/distillation_replace'}, Fluid.of('minecraft:water'), Fluid.of('minecraft:lava'))
    event.replaceOutput({id: 'cwi:schema_contracts/distillation_replace'}, Fluid.of('minecraft:lava'), Fluid.of('minecraft:water'))
  }
})
