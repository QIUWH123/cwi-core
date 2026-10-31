// Requires Create Crafts & Additions and KubeJS Create. Illustrative only.
ServerEvents.recipes(event => {
  if (!Platform.isLoaded('createaddition') || !Platform.isLoaded('kubejs_create')) return

  const incomplete = 'create:incomplete_precision_mechanism'
  event.recipes.create.sequenced_assembly('minecraft:diamond', 'minecraft:quartz', [
    event.recipes.createaddition.rolling(incomplete, incomplete),
    event.recipes.create.pressing(incomplete, incomplete)
  ]).transitionalItem(incomplete).loops(2).id('cwi:example/rolling_assembly')
})
