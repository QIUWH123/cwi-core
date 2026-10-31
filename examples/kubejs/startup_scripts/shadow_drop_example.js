// Put this file in kubejs/startup_scripts/. Fully restart after changes.
// Requires CWI Core's Shadow Drop integration; rendering requires Shadow Drop.
// Opacity and color independently override client-script defaults and native config.

// Modify existing items using the original KubeJS modification event.
ItemEvents.modification(event => {
  event.modify('minecraft:glass', item => {
    item.shadowAmount(0.15)
    item.shadowColor('#203040')
  })

  // Override only opacity; keep the global/configured color.
  event.modify('minecraft:diamond', item => item.shadowAmount(0))

  // Override only color; keep the global/configured opacity.
  event.modify('minecraft:gold_ingot', item => item.shadowColor(0xCC9900))
})

// For blocks, these settings affect the inventory item, not the placed block.
BlockEvents.modification(event => {
  event.modify('minecraft:stone', block => {
    block.shadowAmount(0.6)
    block.shadowColor('#102030')
  })
})

// Registration examples: change false to true to register these two demo objects.
// You can also copy the shadow methods into your existing registrations.
if (false) {
  StartupEvents.registry('item', event => {
    event.create('cwi:example_shadow_item')
      .displayName('Shadow Example Item')
      .texture('minecraft:item/diamond')
      .shadowAmount(0.75)
      .shadowColor('#302040')
  })

  StartupEvents.registry('block', event => {
    event.create('cwi:example_shadow_block')
      .displayName('Shadow Example Block')
      .textureAll('minecraft:block/stone')
      .shadowAmount(0.5)
      .shadowColor(0x203040)
      // Explicit item settings take priority over the block-builder defaults.
      .item(item => item.shadowColor('#704020'))
  })
}

// shadowAmount(0) hides the shadow; shadowAmount(1) requests full opacity.
// Colors accept '#RRGGBB' or numeric 0xRRGGBB.
// A block with noItem() stays itemless; don't modify its nonexistent item shadow.
// Include startup overrides on clients too; they are not synchronized by the server.
