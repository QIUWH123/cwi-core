// Upgrade recipe inputs decide which equipment can receive each enchantment. No eligibility tags are needed.
ServerEvents.recipes(event => {
    // Replace the demonstration lava ingredient with your acid fluid for chemical hazards.
    event.recipes.cwi.fluid_hazard('minecraft:lava', 4)
        .protection('cwi:acid', 4).durabilityCost(1).protectionSlots(['feet'])
        .interval(10).id('cwi:example/chemical_protection')
    event.recipes.cwi.inventory_hazard('minecraft:blaze_powder', 2)
        .fireTicks(40).protection('cwi:heat', 4).durabilityCost(2).protectionSlots(['chest'])
        .id('cwi:example/heat_protection')
    event.recipes.cwi.equipment_upgrade('minecraft:iron_boots', ['minecraft:slime_ball'],
        'cwi:chemical_sealing', 4, 0.02).id('cwi:example/chemical_sealing_manual_only')
    // Reserved enchantments are usable in upgrades, but provide no protection/effects yet.
})
