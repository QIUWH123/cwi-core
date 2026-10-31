// Demonstrates the native CWI KubeJS item type.
StartupEvents.registry('item', event => {
  event.create('golden_hammer', 'hammer')
    .tag('cwi:hammers') // Explicitly include this item in hammer recipe catalysts.
    .tier('gold')
    .attackDamageBaseline(4)
    .speedBaseline(-2.8)
    .chargeTime(10)
    .maxStage(3)
    // Adds behavior after a successful charged hit without replacing native actions.
    .afterImpact(context => {
      // Configure item-specific effects here. For example:
      // context.target.addMotion(0, Math.sqrt(context.stage), 0)
      // context.target.hurtMarked = true
      // context.target.playSound('cwi:steel_pipe', 0.8 * Math.sqrt(context.stage), 1)
    })
    // The base and all three charged models are generated from this texture.
    .texture('kubejs:item/tools/golden_hammer')
    // For stone's dedicated charged art, use:
    // .chargedTexture(1, 'kubejs:item/tools/charged_stone_hammer')
    // Existing special 3D models can opt out with .chargeModels(false).
})
