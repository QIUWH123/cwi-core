// Test-only real Rhino coverage for the ordinary-model generator and manual opt-out.
StartupEvents.registry('item', event => {
  event.create('cwi:model_contract_hammer', 'hammer')
    .tag('cwi:hammers')
    .tier('iron')
    .texture('kubejs:item/tools/iron_hammer')
    .maxStage(1)
    .chargedTexture(1, 'kubejs:item/tools/iron_hammer')
  event.create('cwi:manual_model_contract_hammer', 'hammer')
    .chargeModels(false)
    .tier('iron')
    .texture('kubejs:item/tools/iron_hammer')
})
