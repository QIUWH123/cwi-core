// Native lifecycle coverage of the exact drop-in registerUpgradeRecipe adapter.
ServerEvents.recipes(event => {
    let idCount = 1

    function registerUpgradeRecipe(inputItem, ingredients, enchantId, maxLevel, durabilityMultiplier, outputItem) {
        const recipe = event.recipes.cwi.equipment_upgrade(
            inputItem, ingredients, enchantId, maxLevel, durabilityMultiplier
        ).id(`cwi:equipment_upgrade/${idCount++}_manual_only`)
        if (outputItem) recipe.output(outputItem)
        return recipe
    }


    registerUpgradeRecipe('minecraft:stone_sword', [
        {item: 'minecraft:flint', count: 2},
        {id: 'minecraft:shears', consume: false}
    ], 'minecraft:sweeping_edge', 2, 0.12)
    registerUpgradeRecipe('minecraft:stone_hoe', [
        {item: 'minecraft:iron_ingot'}, {item: 'minecraft:shears', consume: false}
    ], 'minecraft:unbreaking', 3, 0.04, 'minecraft:iron_hoe')
})
