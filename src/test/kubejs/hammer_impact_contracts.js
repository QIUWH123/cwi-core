// Test-only native hammer callbacks, exercised by real charged entity hits.
StartupEvents.registry('item', event => {
  event.create('cwi:impact_contract_hammer', 'hammer')
    .tier('iron')
    .chargeTime(10)
    .maxStage(3)
    .afterImpact(context => {
      const data = context.target.persistentData
      data.putInt('cwiImpactCalls', data.getInt('cwiImpactCalls') + 1)
      data.putInt('cwiImpactStage', context.stage)
      data.putFloat('cwiImpactDamage', context.damage)
      data.putString('cwiImpactHand', String(context.hand))
      data.putBoolean('cwiImpactPlayer', context.player.getUsedItemHand() === context.hand)
      data.putBoolean('cwiImpactLevel', context.level === context.target.level)
      data.putBoolean('cwiImpactItem', context.item === context.player.getItemInHand(context.hand))
      data.putBoolean('cwiImpactAfterDamage', context.target.health < context.target.maxHealth)
      context.target.addMotion(0, Math.sqrt(context.stage), 0)
      context.target.hurtMarked = true
      context.target.playSound('cwi:steel_pipe', 0.8 * Math.sqrt(context.stage), 1)
    })

  event.create('cwi:throwing_contract_hammer', 'hammer')
    .tier('iron')
    .chargeTime(10)
    .afterImpact(context => {
      context.target.persistentData.putBoolean('cwiThrowingImpactCalled', true)
      throw new Error('intentional hammer callback failure')
    })
})
