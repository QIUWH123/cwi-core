ServerEvents.recipes(event => {
  event.recipes.create.item_application('minecraft:gold_block', ['minecraft:iron_block', '#minecraft:axes'])
    .damageHeldItem(2).cooldown('0.2s').sneaking(false).priority(7)
    .drops([Item('3x minecraft:flint').withChance(0.5), 'minecraft:stick'])
    .sound('minecraft:item.axe.strip', 0.8, 0.5)
    .particle({id:'minecraft:block', block:'minecraft:iron_block', count:3})
    .id('cwi:manual_contract/script_tool');
  event.recipes.create.item_application().blockInput('minecraft:iron_block').emptyHand().sneaking()
    .removeBlock().drops(['minecraft:diamond']).cooldown(10)
    .id('cwi:manual_contract/script_empty');
  event.recipes.create.item_application().blockInput('minecraft:oak_log', {axis:'x'}).heldItem(Item('minecraft:brush'))
    .keepBlock().damageHeldItem(1)
    .actions([{type:'if', chance:0.23, sneaking:true,
      then:[{type:'set_block', block:'minecraft:gold_block'},
            {type:'drop', stack:{id:'minecraft:diamond', count:2}}],
      else:[{type:'sound', id:'minecraft:block.sand.place'}]}])
    .id('cwi:manual_contract/script_conditional');
  event.recipes.create.item_application('minecraft:emerald_block', ['minecraft:iron_block', 'minecraft:flint'])
    .consumeHeldItem(2).damagePlayer(1).id('cwi:manual_contract/script_costs');
  // The ordinary two-argument constructor keeps Create's existing automation.
  event.recipes.create.item_application(Item('minecraft:diamond_block'), ['minecraft:iron_block','minecraft:diamond'])
    .keepHeldItem().id('cwi:manual_contract/script_legacy');
});
