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
  if (Platform.isLoaded('lychee')) {
    const emptyHandTest = Java.loadClass('net.quiwh.cwi.LycheeEmptyHandGameTests')
    const emptyHand = event.custom({type: 'lychee:block_interacting', item_in: {}, block_in: 'minecraft:cyan_concrete', contextual: {type: 'is_sneaking'}, post: [{type: 'place', block: 'minecraft:blue_concrete'}]}).id('cwi:schema_contracts/lychee_empty_hand')
    const emptyPrimary = event.custom({type: 'lychee:block_interacting', item_in: [{}, {item: 'minecraft:flint', count: 2}], block_in: 'minecraft:magenta_concrete', contextual: {type: 'is_sneaking'}, post: [{type: 'place', block: 'minecraft:purple_concrete'}]}).id('cwi:schema_contracts/lychee_empty_primary')
    emptyHandTest.verifySchemaJson(emptyHand)
    emptyHandTest.verifySchemaJson(emptyPrimary)
    event.replaceInput({id: 'cwi:schema_contracts/lychee_replace'}, 'minecraft:quartz', 'minecraft:flint')
    event.replaceOutput({id: 'cwi:schema_contracts/lychee_replace'}, 'minecraft:gold_nugget', 'minecraft:iron_nugget')
  }
})
