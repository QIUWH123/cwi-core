// Server scripts: edits apply on /reload. No startup Java registry is needed.
CwiEvents.equipmentUpgrades(event => {
    event.material('example_iron')
        .tier(2).durabilityMultiplier(1)
        .equipment('sword', 'minecraft:iron_sword')
        .equipment('pickaxe', 'minecraft:iron_pickaxe')
        .component('ingot', 'minecraft:iron_ingot')

    event.upgrade('example_sharpness')
        .types('sword').enchantment('minecraft:sharpness')
        .maxLevel(3).durabilityCost(0.20)
        .ingredient('minecraft:flint', 1)
        .tool('minecraft:shears', 1)

    event.upgrade('example_unbreaking')
        .types('sword', 'pickaxe').enchantment('minecraft:unbreaking')
        .maxLevel(3).durabilityCost(0.02)
        .component('ingot', 1, true, 0)
        .tool('minecraft:shears', 1)
})

// A single direct recipe also supports ordinary KubeJS ingredients/tags.
ServerEvents.recipes(event => {
    event.recipes.cwi.equipment_upgrade(
        'minecraft:leather_boots',
        ['minecraft:feather', {item: 'minecraft:shears', consume: false, damage: 1}],
        'minecraft:feather_falling', 2, 0.03
    ).id('cwi:example/feather_falling_manual_only')
})
