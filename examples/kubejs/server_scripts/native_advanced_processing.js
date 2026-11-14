// Optional examples: native recipe types and machine animations are retained.
ServerEvents.recipes(event => {
  const pool = recipe => recipe
    .priority(10)
    .outputPool([Item('minecraft:flint').withWeight(3), Item('minecraft:gravel')]).showOutputs()
    .outputPool([Item('minecraft:diamond')]) // This second pool stays hidden.

  // Recipe-clock totals stay constant at different RPM; working-clock totals depend on runtime.
  for (const type of ['milling', 'crushing', 'cutting']) {
    pool(event.recipes.create[type](
      [Item('minecraft:clay_ball').perTick(20, 'recipe')],
      [Item('minecraft:sand').perTick(20, 'recipe')]
    )).processingTime(100).id('cwi:example/native_advanced/' + type)
  }

  pool(event.recipes.create.mixing(
    [Item('minecraft:clay_ball').perTick(20, 'recipe')],
    [Item('minecraft:sand').perTick(20, 'recipe'), Item('minecraft:flint').catalyst(),
      Item('minecraft:iron_sword').damage(2)]
  )).processingTime(100).id('cwi:example/native_advanced/mixing')

  pool(event.recipes.create.compacting(
    [Item('minecraft:clay_ball').perTick(20, 'recipe')],
    [Item('minecraft:sand').perTick(20, 'recipe'), Item('minecraft:flint').catalyst()]
  )).processingTime(100).id('cwi:example/native_advanced/compacting')

  // These are actions: no periodic IO. The deployer's second input is its held tool/catalyst.
  pool(event.recipes.create.deploying(Item('minecraft:clay_ball'),
    [Item('minecraft:sand'), Item('minecraft:iron_sword').damage(2)]
  )).id('cwi:example/native_advanced/deploying')
  pool(event.recipes.create.pressing(Item('minecraft:clay_ball'), Item('minecraft:sand')))
    .id('cwi:example/native_advanced/pressing')
  pool(event.recipes.create.filling(Item('minecraft:clay_ball'),
    [Item('minecraft:sand'), Fluid('minecraft:water', 100)]
  )).id('cwi:example/native_advanced/filling')
  pool(event.recipes.create.emptying(
    [Item('minecraft:clay_ball'), Fluid('minecraft:water', 100)], Item('minecraft:sand')
  )).id('cwi:example/native_advanced/emptying')

  if (Platform.isLoaded('createaddition')) {
    pool(event.recipes.createaddition.rolling(Item('minecraft:clay_ball').perTick(20, 'recipe'),
      Item('minecraft:sand').perTick(20, 'recipe')))
      .processingTime(100).id('cwi:example/native_advanced/rolling')
  }
  if (Platform.isLoaded('vintageimprovements')) {
    pool(event.recipes.vintageimprovements.vibrating(Item('minecraft:clay_ball').perTick(2),
      Item('minecraft:sand').perTick(2)))
      .processingTime(100).id('cwi:example/native_advanced/vibrating')
    pool(event.recipes.vintageimprovements.centrifugation(
      [Item('minecraft:clay_ball').perTick(20, 'recipe'), Fluid('minecraft:lava', 5).perTick(20, 'recipe')],
      [Item('minecraft:sand').perTick(20, 'recipe'), Item('minecraft:flint').catalyst(),
        Fluid('minecraft:water', 5).perTick(20, 'recipe')]
    )).processingTime(100).id('cwi:example/native_advanced/centrifugation')
  }
  if (Platform.isLoaded('createmetallurgy')) {
    for (const type of ['melting', 'alloying']) {
      pool(event.recipes.createmetallurgy[type](Fluid('minecraft:water', 5).perTick(20, 'recipe'),
        [Item('minecraft:sand').perTick(20, 'recipe'), Item('minecraft:flint').catalyst()]))
        .processingTime(100).heated().id('cwi:example/native_advanced/' + type)
    }
  }
  if (Platform.isLoaded('tfmg')) {
    pool(event.recipes.tfmg.vat(Fluid('minecraft:lava', 5).perTick(20, 'recipe'),
      [Fluid('create:honey', 5).perTick(20, 'recipe'), Item('minecraft:flint').catalyst()]))
      .processingTime(100).id('cwi:example/native_advanced/vat')
    event.recipes.tfmg.distillation(
      [Fluid('minecraft:water', 5).perTick(20, 'recipe'), Fluid('minecraft:water', 10).perTick(20, 'recipe')],
      Fluid('create:honey', 20).perTick(20, 'recipe')
    ).processingTime(100).priority(10).id('cwi:example/native_advanced/tfmg_distillation')
  }
  if (Platform.isLoaded('createdieselgenerators')) {
    event.recipes.createdieselgenerators.distillation(
      [Fluid('minecraft:water', 5).perTick(20, 'recipe'), Fluid('minecraft:water', 10).perTick(20, 'recipe')],
      Fluid('create:honey', 20).perTick(20, 'recipe')
    ).processingTime(100).priority(10).id('cwi:example/native_advanced/diesel_distillation')
  }
  if (Platform.isLoaded('rubberworks')) {
    pool(event.recipes.rubberworks.compressing(
      [Item('minecraft:clay_ball').perTick(20, 'recipe'), Fluid('minecraft:lava', 5).perTick(20, 'recipe')],
      Fluid('create:honey', 10).perTick(20, 'recipe')
    )).processingTime(100).id('cwi:example/native_advanced/compressing')
    // Compressor products leave through its mouth; external item/fluid extraction is disabled.
  }
})
