// Copy this file into kubejs/server_scripts/, then run /reload.
// Test recipes only: remove this file and /reload when finished.
// Use an empty cwi:mixing_vessel at 128 or 256 RPM. Insert/extract with automation;
// all sides accept items/fluids, but only the first four item slots accept insertion.
// Clear the vessel between tests. Keep output space available unless testing pauses.
// Item(...) / Fluid(...) below require the updated CWI Core callable recipe API.
// processingTime is BASE recipe time: 100 base ticks takes 31 ticks at 128 RPM
// or 16 ticks at 256 RPM, so a base time of 100 finishes in less than two seconds.

ServerEvents.recipes(event => {
  // 1. Ordinary mixed IO: 1 dirt + 250 mB water -> 2 clay balls.
  // Inputs are paid at completion. Stop rotation halfway: work pauses and resumes.
  event.recipes.cwi.mixing_vessel_mixing(
    Item('2x minecraft:clay_ball'),
    [Item('minecraft:dirt'), Fluid('minecraft:water', 250)]
  ).processingTime(1200).minimumRpm(64)
    .id('cwi:example/vessel_test/basic')

  // 2. Separate catalyst units: insert TWO flint, preferably in one stack.
  // One is consumed; one remains. A single flint must not start this recipe.
  // JEI gives the retained input the catalyst frame.
  event.recipes.cwi.mixing_vessel_mixing(
    Item('minecraft:quartz'),
    [Item('minecraft:flint'), Item('minecraft:flint').catalyst()]
  ).processingTime(100).minimumRpm(64)
    .id('cwi:example/vessel_test/item_catalyst')

  // 3. Fluid catalyst: insert 1 andesite and at least 300 mB water.
  // Consume 50 mB; retain the separate 250 mB catalyst. With only 250 mB, wait.
  event.recipes.cwi.mixing_vessel_mixing(
    Item('minecraft:clay_ball'),
    [Item('minecraft:andesite'), Fluid('minecraft:water', 250).catalyst(),
      Fluid('minecraft:water', 50)]
  ).processingTime(100).minimumRpm(64)
    .id('cwi:example/vessel_test/fluid_catalyst')

  // 4. Chance: potato always consumed; 25% chance to consume BOTH sand together.
  // Native Create item result: 0-4 nuggets, rolling 25% per nugget.
  // Fluid result: 50% chance of all 250 mB lava, otherwise no lava.
  // Repeat many times to observe the distribution; one result proves little.
  event.recipes.cwi.mixing_vessel_mixing(
    [Item('4x minecraft:gold_nugget').withChance(0.25),
      Fluid('minecraft:lava', 250).withChance(0.5)],
    [Item('minecraft:poisonous_potato'), Item('2x minecraft:sand').withChance(0.25)]
  ).processingTime(100).minimumRpm(64)
    .id('cwi:example/vessel_test/chance')

  // 5. Tool wear: 1 gravel -> 1 flint, and damage the retained sword by 10.
  // A sword with only 2 durability left is allowed and may break normally.
  // Obtain one with: /give @s minecraft:iron_sword[minecraft:damage=248]
  // Unbreaking can reduce wear; the sword's other components remain intact.
  event.recipes.cwi.mixing_vessel_mixing(
    Item('minecraft:flint'),
    [Item('minecraft:gravel'), Item('minecraft:iron_sword').damage(10)]
  ).processingTime(100).minimumRpm(64)
    .id('cwi:example/vessel_test/tool_damage')

  // 6. Periodic wear: 50 damage actions per operation, regardless of RPM.
  // Use an unenchanted sword for predictable wear. A broken sword needs replacing
  // at the next due action; the operation waits without resetting progress.
  event.recipes.cwi.mixing_vessel_mixing(
    Item('minecraft:iron_nugget'),
    [Item('minecraft:cobblestone'),
      Item('minecraft:iron_sword').damage(1).perTick(2, 'recipe')]
  ).processingTime(100).minimumRpm(64)
    .id('cwi:example/vessel_test/periodic_damage')

  // 7. Working clock: 5 mB water in + 1 iron nugget out every 2 successful ticks.
  // At constant 128 RPM: 31 working ticks -> 75 mB and 15 nuggets.
  // At constant 256 RPM: 16 working ticks -> 40 mB and 8 nuggets.
  // Seed is consumed on the first successful tick; clay arrives at completion.
  event.recipes.cwi.mixing_vessel_mixing(
    [Item('minecraft:clay_ball'), Item('minecraft:iron_nugget').perTick(2)],
    [Item('minecraft:pumpkin_seeds'), Fluid('minecraft:water', 5).perTick(2)]
  ).processingTime(100).minimumRpm(64)
    .id('cwi:example/vessel_test/working_clock')

  // 8. Recipe clock: exactly 250 mB consumed and 10 redstone produced per cycle.
  // Try 128 RPM, 256 RPM, then change RPM halfway: completed totals stay the same.
  // Insufficient water/output space pauses the next due step atomically.
  event.recipes.cwi.mixing_vessel_mixing(
    [Item('minecraft:clay_ball'), Item('minecraft:redstone').perTick(10, 'recipe')],
    [Item('minecraft:melon_seeds'), Fluid('minecraft:water', 5).perTick(2, 'recipe')]
  ).processingTime(100).minimumRpm(64)
    .id('cwi:example/vessel_test/recipe_clock')

  // 9. Two independent pools, IN ADDITION to the normal clay output.
  // Pool A: 75% a stack of 2 gravel, 25% 1 cobblestone; visible cycling JEI slot.
  // Pool B: 50% redstone, 50% glowstone dust; hidden '?' slot.
  // Add .showOutputs() after the SECOND pool to reveal that pool independently.
  event.recipes.cwi.mixing_vessel_mixing(
    Item('minecraft:clay_ball'), Item('minecraft:egg')
  ).processingTime(100).minimumRpm(64)
    .outputPool([
      Item('2x minecraft:gravel').withWeight(3),
      Item('minecraft:cobblestone').withWeight(1)
    ]).showOutputs()
    .outputPool([Item('minecraft:redstone'), Item('minecraft:glowstone_dust')])
    .id('cwi:example/vessel_test/pools')

  // 10. Priority pair: hide alone -> leather; hide + retained flint -> 3 string.
  // With both available BEFORE starting, priority 10 wins over priority -10.
  // Start the fallback first, THEN insert flint: it finishes leather; the next
  // operation uses the high-priority recipe. Priority is not displayed in JEI.
  event.recipes.cwi.mixing_vessel_mixing(
    Item('minecraft:leather'), Item('minecraft:rabbit_hide')
  ).processingTime(1200).minimumRpm(64).priority(-10)
    .id('cwi:example/vessel_test/priority_fallback')

  event.recipes.cwi.mixing_vessel_mixing(
    Item('3x minecraft:string'),
    [Item('minecraft:rabbit_hide'), Item('minecraft:flint').catalyst()]
  ).processingTime(1200).minimumRpm(64).priority(10)
    .id('cwi:example/vessel_test/priority_preferred')

  // Optional FE test. Set this to a real rechargeable item ID from your pack.
  // The ITEM must expose an EXTRACTABLE NeoForge FE capability; a powered block
  // or an item named 'battery' does not necessarily support it while in item form.
  const energyItem = ''
  if (energyItem !== '') {
    // 1 dead bush -> 1 redstone; retain the charged item, spending exactly 200 FE.
    // An empty/insufficient battery waits; a blocked completion spends no FE.
    event.recipes.cwi.mixing_vessel_mixing(
      Item('minecraft:redstone'),
      [Item('minecraft:dead_bush'), Item(energyItem).consumeEnergy(200)]
    ).processingTime(100).minimumRpm(64)
      .id('cwi:example/vessel_test/energy')
  }
})

// Additional checks with any suitable recipe above:
// - Stop rotation or remove a catalyst: work pauses, with no extra transfers.
// - Block outputs AFTER starting: free space again to resume without double costs.
// - Save/quit/reopen or /reload without changing the recipe: resume the saved work.
// - Changing only priority/showOutputs preserves work; changing IO/time cancels it.
// - .perTick() means .perTick(1); pauses and unloaded time never count as work.
// Optional 18x18 JEI frames belong in kubejs/assets/cwi/textures/gui/jei/:
// catalyst_slot.png and per_tick_slot.png. Missing files use distinct fallback borders.
