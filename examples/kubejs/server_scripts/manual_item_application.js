// Optional examples. These are create:item_application recipes; Lychee is not required.
ServerEvents.recipes(event => {
  event.recipes.create.item_application('minecraft:stripped_oak_log', ['minecraft:oak_log', '#minecraft:axes'])
    .damageHeldItem(1).cooldown('0.2s')
    .drops([Item('2x minecraft:stick'), Item('3x minecraft:flint').withChance(0.25)])
    .sound('minecraft:item.axe.strip')
    .particle({id:'minecraft:block', block:'minecraft:oak_log', count:8, dx:0.3, dy:0.3, dz:0.3})
    .id('cwi:example/manual_stripping');

  // Empty hand, a sneak requirement, removal and drops; no placeholder item needed.
  event.recipes.create.item_application().blockInput('minecraft:moss_block').emptyHand()
    .sneaking().removeBlock().cooldown('0.75s').drops(['minecraft:wheat_seeds'])
    .id('cwi:example/manual_scavenging');

  // Keep the block while brushing. Both actions share ONE 23% roll.
  event.recipes.create.item_application().blockInput('minecraft:calcite').heldItem('minecraft:brush')
    .sneaking().keepBlock().damageHeldItem(1).cooldown('0.75s')
    .drops([Item('minecraft:redstone').withChance(0.13)])
    .actions([{type:'if', chance:0.23, then:[
      {type:'set_block', block:'minecraft:moss_block'},
      {type:'drop', stack:{id:'minecraft:quartz', count:2}}
    ]}])
    .id('cwi:example/manual_brushing');
});
// Methods: blockInput(idOrTag, optionalStateMap), heldItem(ingredient), emptyHand(),
// sneaking(optionalBoolean), priority(integer), keepBlock(), removeBlock(),
// damageHeldItem(integer), consumeHeldItem(integer), cooldown(ticksOrTimeString),
// damagePlayer(integer), drops(array), sound(id, optionalVolume, optionalPitch),
// particle(idOrObject), actions(array).
// drops and actions append; scalar setters replace. Extension recipes are manual-only.
// An omitted held cost follows Create: keepHeldItem() retains it; otherwise wear a tool
// by one or consume one item. Explicit damageHeldItem / consumeHeldItem override that.
// A chance drop rolls the ENTIRE stack. A failed block output roll leaves the old block.
// if actions support chance and/or sneaking plus then/else arrays. Actions are data only:
// set_block, drop, sound, particle, damage_player, if. No commands are necessary.
