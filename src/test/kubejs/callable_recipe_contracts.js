ServerEvents.tags('item', event => event.add('cwi:callable/shared', global.cwiCallableReloadTag ? 'minecraft:end_rod' : 'minecraft:lightning_rod'))
ServerEvents.tags('fluid', event => event.add('cwi:callable/shared', global.cwiCallableReloadTag ? 'minecraft:lava' : 'minecraft:water'))
ServerEvents.recipes(event => {
  if (!global.cwiCallableStartup) throw new Error('Callable startup contract did not run')
  function reject(name, callback, fragment) {
    let error = null
    try { callback() } catch (e) { error = String(e) }
    if (error === null || fragment && !error.includes(fragment)) throw new Error('Expected descriptor error: ' + name + ': ' + error)
  }
  const advanced = event.recipes.cwi.mixing_vessel_mixing(
    [Item('2x minecraft:clay_ball'), Fluid('minecraft:lava', 5).perTick(2)],
    [Item('minecraft:heart_of_the_sea').catalyst(), Item('minecraft:iron_sword').damage(10),
      Fluid('minecraft:water', 5).perTick(2, 'recipe')]
  ).processingTime(100).priority(10)
    .outputPool([Item('2x minecraft:gravel').withWeight(3), Item('minecraft:cobblestone')]).showOutputs()
    .outputPool([Item('minecraft:redstone'), Item('minecraft:glowstone_dust')]).showOutputs(false)
    .id('cwi:callable/vessel_advanced')
  event.replaceInput({id:'cwi:callable/vessel_replace'}, 'minecraft:iron_sword', 'minecraft:stone_sword')
  event.replaceOutput({id:'cwi:callable/vessel_replace'}, 'minecraft:gravel', Item('2x minecraft:flint').withWeight(7))
  event.recipes.cwi.mixing_vessel_mixing(Item('minecraft:emerald'), [
    Item('minecraft:heart_of_the_sea').catalyst(), Fluid('minecraft:water', 5).perTick(2, 'recipe')
  ]).processingTime(100).priority(-10).id('cwi:callable/vessel_priority_fallback')
  const unpooled = event.recipes.cwi.mixing_vessel_mixing('minecraft:diamond', 'minecraft:nether_star').id('cwi:callable/unpooled')
  reject('show before pool', () => unpooled.showOutputs(), 'follow')
  reject('pool tag', () => unpooled.outputPool([Item('#c:sands')]), 'concrete')
  reject('pool chance', () => unpooled.outputPool([Item('minecraft:flint').withChance(0.5)]), 'weights only')
  reject('pool raw chance', () => unpooled.outputPool([{id: 'minecraft:flint', chance: 0.5}]), 'weights only')
  reject('pool timing', () => unpooled.outputPool([Item('minecraft:flint').perTick()]), 'weights only')
  reject('pool fluid', () => unpooled.outputPool([Fluid('minecraft:water', 5)]), 'items')
  reject('interval0', () => Item('minecraft:flint').perTick(0), 'positive')
  reject('fractional interval', () => Fluid('minecraft:water', 5).perTick(1.5), 'integer')
  reject('invalid clock', () => Item('minecraft:flint').perTick(2, 'world'), 'working or recipe')
  reject('catalyst chance', () => advanced.ingredients(Item('minecraft:flint').catalyst().withChance(0.5)), 'Catalysts')
  reject('priority fraction', () => advanced.priority(1.5), 'integer')
  reject('nondamageable tool', () => advanced.ingredients(Item('minecraft:sand').damage(5)), 'damageable')
  reject('tool output', () => advanced.results(Item('minecraft:iron_sword').damage(5)), 'input-only')
  reject('cost count2', () => advanced.ingredients(Item('2x minecraft:iron_sword').damage(5)), 'one item')
  reject('tool catalyst', () => Item('minecraft:iron_sword').damage(5).catalyst(), 'mutually exclusive')
  reject('other machine advanced', () => event.recipes.cwi.incubating('minecraft:map', 'minecraft:paper').ingredients(Item('minecraft:paper').catalyst()), 'unsupported by incubators')
  // Restore a complete recipe after rejected setters; immutable descriptors keep their metadata.
  const energy = Item('minecraft:compass').consumeEnergy(20).perTick(2)
  event.recipes.cwi.mixing_vessel_mixing(Item('minecraft:clay_ball'), Item(energy)).id('cwi:callable/vessel_energy')
  event.recipes.cwi.mixing_vessel_mixing({ingredients: [Item('minecraft:recovery_compass')], results: [Item('minecraft:emerald').withChance(0.5)]}).id('cwi:callable/object_constructor')
  const mixed = event.recipes.cwi.mixing_vessel_mixing(
    [Item('2x minecraft:clay_ball').withChance(0.25), Fluid('75x minecraft:lava')],
    [Item('2x #c:sands').withChance(0), Fluid('250x minecraft:water').perTick()]
  ).id('cwi:callable/mixed')
  const fluent = event.recipes.cwi.mixing_vessel_mixing().id('cwi:callable/fluent')
    .ingredients([Item('#2x c:sands'), Fluid('#250x minecraft:water')])
    .results(Item('minecraft:clay_ball').withChance(0.5).perTick())
  fluent.ingredients([global.cwiCallableReusable.perTick(false)])
  fluent.set('results', [Item('minecraft:clay_ball').withChance(0.5).perTick(), Fluid('50x minecraft:water').withChance(0)])
  // Descriptor replacement resets metadata even when the ID is unchanged.
  event.replaceOutput({id: 'cwi:callable/mixed'}, 'minecraft:clay_ball', Item('minecraft:clay_ball'))
  event.replaceOutput({id: 'cwi:callable/mixed'}, Fluid.of('minecraft:lava'), Fluid('125x minecraft:water').perTick(false))
  // Restore the intentionally distinct source recipe after testing replacement paths.
  mixed.results([Item('2x minecraft:clay_ball').withChance(0.25), Fluid('75x minecraft:lava')])
  const furnaceAdvanced = event.recipes.cwi.blast_furnace_processing(
    [Item('minecraft:brick'), Fluid('minecraft:lava', 25)],
    [Item('minecraft:sand'), Item('minecraft:flint').catalyst(), Fluid('minecraft:water', 5).perTick(2, 'recipe')]
  ).processingTime(100).minimumHeat(1200).maximumHeat(2200).priority(10)
    .outputPool([Item('2x minecraft:gravel').withWeight(3), Item('minecraft:cobblestone')]).showOutputs()
    .outputPool([Item('minecraft:redstone')]).showOutputs(false)
    .id('cwi:callable/furnace_advanced')
  reject('furnace tool damage', () => furnaceAdvanced.ingredients(Item('minecraft:iron_sword').damage(1)), 'tool costs')
  reject('furnace tool energy', () => furnaceAdvanced.ingredients(Item('minecraft:compass').consumeEnergy(1)), 'tool costs')
  reject('furnace fractional priority', () => furnaceAdvanced.priority(0.5), 'integer')
  // KubeJS replacements visit pre-existing datapack recipes, not recipes added above.
  event.replaceOutput({id: 'cwi:callable/furnace_replace'}, 'minecraft:gravel', Item('minecraft:gold_nugget').withWeight(7))

  event.recipes.cwi.blast_furnace_processing(Fluid('25x minecraft:lava').withChance(0.75), [Item('minecraft:cobblestone'), Fluid('1x minecraft:water')])
    .id('cwi:callable/furnace')
  event.recipes.cwi.incubating(Item('minecraft:clay_ball').perTick(), Item('minecraft:glass_bottle').withChance(0))
    .id('cwi:callable/incubating')
  reject('core weight', () => mixed.results(Item('minecraft:iron_ingot').withWeight(1)), 'weight')
  reject('core tag output', () => mixed.results(Item('#c:sands')), 'concrete')
  const incubating = event.recipes.cwi.incubating('minecraft:clay_ball', 'minecraft:glass_bottle').id('cwi:callable/incubating_validation')
  reject('core wrong channel', () => incubating.ingredients(Fluid('1x minecraft:water')), 'kind')
  reject('incubator timing', () => incubating.ingredients(Item('minecraft:glass_bottle').perTick(false)), 'timing')
  reject('incubator count', () => incubating.ingredients(Item('2x minecraft:glass_bottle')), 'count-1')
  reject('incubator multiple entries', () => incubating.ingredients([Item('minecraft:glass_bottle'), Item('minecraft:glass_bottle')]), 'exactly one')
  reject('empty item tag', () => mixed.ingredients(Item('#cwi:callable/missing')), 'registered item')
  reject('empty fluid tag', () => mixed.ingredients(Fluid('1x #cwi:callable/missing')), 'registered fluid')
  reject('missing item', () => mixed.results(Item('cwi:missing_registration')), 'Unknown')
  reject('missing fluid', () => mixed.results(Fluid('1x cwi:missing_registration')), 'Unknown')
  reject('core output codec count', () => mixed.results(Item('2147483647x minecraft:clay_ball')), 'range')
  event.recipes.cwi.mixing_vessel_mixing([Item('2x minecraft:clay_ball').withChance(0.25), Fluid('75x minecraft:lava').withChance(0.5)], [Item('2x minecraft:amethyst_shard').withChance(0), Fluid('250x minecraft:water').perTick()]).id('cwi:callable/execution')
  const nativeStack = Item.of('minecraft:iron_ingot', 3)
  const copied = Item(nativeStack)
  nativeStack.count = 17
  event.recipes.cwi.mixing_vessel_mixing(copied, Item('minecraft:cobblestone')).id('cwi:callable/snapshot')
  reject('unsupported Core hammer', () => event.recipes.cwi.depot_conversion('minecraft:clay_ball', 'minecraft:dirt').set('input', Item('minecraft:dirt')), 'not supported')
  event.recipes.cwi.mixing_vessel_mixing(Item('minecraft:emerald'), [global.cwiCallableTagItem, global.cwiCallableTagFluid]).id('cwi:callable/tags')
  event.replaceInput({id: 'cwi:callable/replace_machine'}, 'minecraft:sand', Item('2x minecraft:red_sand'))
  event.replaceOutput({id: 'cwi:callable/replace_machine'}, 'minecraft:clay_ball', Item('minecraft:clay_ball'))
  event.replaceOutput({id: 'cwi:callable/replace_machine'}, Fluid.of('minecraft:lava'), Fluid('50x minecraft:water'))
  if (!Platform.isLoaded('kubejs_create')) return
  const mixing = event.recipes.create.mixing([Item('2x minecraft:iron_ingot').withChance(0.25), Fluid('50x minecraft:water')], [Item('2x #c:sands'), Fluid('#100x minecraft:water')])
    .id('cwi:callable/create_mixing')
  const filling = event.recipes.create.filling(Item('minecraft:clay_ball').withChance(0.5), [Item('minecraft:glass_bottle'), Fluid('25x minecraft:water')])
    .id('cwi:callable/create_filling')
  const deploying = event.recipes.create.deploying([Item('minecraft:iron_ingot'), Item('minecraft:clay_ball')], [Item('minecraft:iron_ingot'), Item('minecraft:iron_ingot')])
    .keepHeldItem().id('cwi:callable/create_deploying')
  const pressing = event.recipes.create.pressing(Item('99x minecraft:iron_ingot').withChance(1), Item('minecraft:iron_ingot'))
    .id('cwi:callable/create_pressing')
  const assembly = event.recipes.create.sequenced_assembly([Item('minecraft:gold_ingot').withWeight(2), Item('minecraft:iron_ingot').withWeight(0.25)], Item('minecraft:iron_ingot'), [
    event.recipes.create.pressing(Item('minecraft:iron_ingot'), Item('minecraft:iron_ingot')),
    event.recipes.create.deploying(Item('minecraft:iron_ingot'), [Item('minecraft:iron_ingot'), Item('minecraft:clay_ball')]),
    event.recipes.create.filling(Item('minecraft:iron_ingot'), [Item('minecraft:iron_ingot'), Fluid('1x minecraft:water')])
  ]).transitionalItem(Item('minecraft:iron_ingot')).loops(2).showOutputs().id('cwi:callable/create_assembly')
  event.recipes.create.sequenced_assembly([Item('minecraft:gold_ingot').withWeight(2), Item('minecraft:flint')], Item('minecraft:iron_ingot'), [
    event.recipes.create.pressing(Item('minecraft:iron_ingot'), Item('minecraft:iron_ingot'))
  ]).transitionalItem(Item('minecraft:iron_ingot')).showOutputs(true).showOutputs(false).id('cwi:callable/assembly_hidden')
  event.recipes.create.sequenced_assembly([Item('minecraft:gold_ingot').withWeight(2), Item('minecraft:flint')], Item('minecraft:iron_ingot'), [
    event.recipes.create.pressing(Item('minecraft:iron_ingot'), Item('minecraft:iron_ingot'))
  ]).transitionalItem(Item('minecraft:iron_ingot')).id('cwi:callable/assembly_default')
  reject('assembly invalid visibility', () => assembly.showOutputs('yes'), 'boolean')
  event.recipes.create.mixing(Item('minecraft:clay_ball'), Item('#c:sands').withChance(0.5)).id('cwi:callable/native_chance_input')
  event.recipes.create.mixing(Item('minecraft:iron_ingot').perTick(2, 'recipe'), Item('minecraft:flint')).id('cwi:callable/native_periodic')
  event.recipes.create.mixing(Fluid('minecraft:water', 1).withChance(0.5), Item('minecraft:flint')).id('cwi:callable/native_fluid_chance')
  reject('mixing overflow', () => mixing.ingredients(Item('2147483647x minecraft:iron_ingot')), 'count')
  reject('filling quantity', () => filling.ingredients([Item('2x minecraft:glass_bottle'), Fluid('25x minecraft:water')]), 'count 1')
  reject('filling missing fluid', () => filling.ingredients(Item('minecraft:glass_bottle')), 'slots')
  reject('deploying positions', () => deploying.ingredients(Item('2x minecraft:iron_ingot')), 'count 1')
  reject('pressing fluid', () => pressing.ingredients(Fluid('1x minecraft:water')), 'channel')
  for (const chance of [0, 1e-100]) reject('create native float ' + chance, () => pressing.results(Item('minecraft:iron_ingot').withChance(chance)), 'native float')
  reject('create count100', () => pressing.results(Item('100x minecraft:iron_ingot')), '1..99')
  reject('create item weight', () => pressing.results(Item('minecraft:iron_ingot').withWeight(1)), 'weight')
  reject('assembly chance', () => assembly.results(Item('minecraft:iron_ingot').withChance(1)), 'withWeight')
  reject('assembly float overflow', () => assembly.results(Item('minecraft:iron_ingot').withWeight(1e100)), 'native float')
  reject('assembly total overflow', () => assembly.results([Item('minecraft:iron_ingot').withWeight(3e38), Item('minecraft:gold_ingot').withWeight(3e38)]), 'total')
  reject('transitional metadata', () => assembly.transitionalItem(Item('minecraft:iron_ingot').withWeight(1)), 'Transitional')
  reject('transitional count', () => assembly.transitionalItem(Item('2x minecraft:iron_ingot')), 'count 1')
  event.recipes.create.crushing(Item('minecraft:clay_ball').perTick(2), Item('minecraft:sand').perTick(2)).id('cwi:callable/native_crushing')
  pressing.set('results', Item('minecraft:gold_ingot').withChance(0.125))
  event.replaceOutput({id: 'cwi:callable/replace_pressing'}, 'minecraft:gold_ingot', Item('2x minecraft:gold_ingot'))
  event.replaceOutput({id: 'cwi:callable/replace_assembly'}, 'minecraft:gold_ingot', Item('2x minecraft:gold_ingot'))
})
