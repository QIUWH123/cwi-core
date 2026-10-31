// Test-only: actual KubeJS startup builders and modification events.
StartupEvents.registry('item', event => {
  event.create('cwi:shadow_item_builder')
    .shadowAmount(0.25).shadowColor('#123456').displayName('Shadow item contract')
  event.create('cwi:shadow_hammer_builder', 'hammer')
    .shadowAmount(0.75).shadowColor(0xABCDEF).chargeTime(12)
})
StartupEvents.registry('block', event => {
  event.create('cwi:shadow_block_builder')
    .shadowAmount(0.5).shadowColor('#102030')
    .item(item => item.shadowColor('#FEDCBA'))
  event.create('cwi:shadow_no_item').shadowAmount(1).noItem()
})
ItemEvents.modification(event => {
  event.modify('cwi:shadow_item_builder', item => item.shadowAmount(0))
  event.modify('minecraft:gold_ingot', item => item.shadowAmount(1).shadowColor(0x445566))
})
BlockEvents.modification(event => {
  event.modify('cwi:shadow_block_builder', block => block.shadowAmount(0.2))
})
