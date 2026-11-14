// CWI Core owns execution; these are reloadable balance settings.
ServerEvents.recipes(event => {
    const recipeId = id => id.replace(':', '/')
    const hasFluid = id => Fluid.exists(id)
    const impacts = [
    [
        "kubejs:ash",
        2,
        [
            {
                "type": "sound",
                "id": "minecraft:block.sand.place",
                "volume": 2,
                "pitch": 2
            },
            {
                "type": "particle",
                "dx": 0.5,
                "dy": 0.3,
                "dz": 0.5,
                "speed": 0.1,
                "count": 16,
                "id": "minecraft:falling_dust",
                "block": "minecraft:gravel"
            },
            {
                "type": "discard"
            }
        ]
    ],
    [
        "kubejs:dark_ash",
        2,
        [
            {
                "type": "sound",
                "id": "minecraft:block.sand.place",
                "volume": 2,
                "pitch": 2
            },
            {
                "type": "particle",
                "dx": 0.5,
                "dy": 0.3,
                "dz": 0.5,
                "speed": 0.1,
                "count": 16,
                "id": "minecraft:falling_dust",
                "block": "minecraft:deepslate"
            },
            {
                "type": "discard"
            }
        ]
    ],
    [
        "create:polished_rose_quartz",
        2,
        [
            {
                "type": "sound",
                "id": "minecraft:block.amethyst_block.break",
                "volume": 0.8,
                "pitch": 1.2
            },
            {
                "type": "particle",
                "dx": 0.3,
                "dy": 0.3,
                "dz": 0.3,
                "speed": 0.1,
                "count": 4,
                "id": "minecraft:electric_spark"
            },
            {
                "type": "replace_item",
                "output": {
                    "id": "kubejs:damaged_polished_rose_quartz"
                }
            }
        ]
    ],
    [
        "create:precision_mechanism",
        3,
        [
            {
                "type": "sound",
                "id": "minecraft:block.chain.break",
                "volume": 0.6,
                "pitch": 1.2
            },
            {
                "type": "particle",
                "dx": 0.3,
                "dy": 0.3,
                "dz": 0.3,
                "speed": 0.1,
                "count": 12,
                "id": "minecraft:ash"
            },
            {
                "type": "replace_item",
                "output": {
                    "id": "kubejs:damaged_precision_mechanism"
                }
            }
        ]
    ],
    [
        "naturescompass:naturescompass",
        3,
        [
            {
                "type": "sound",
                "id": "minecraft:block.glass.break",
                "volume": 0.4,
                "pitch": 1.8
            },
            {
                "type": "particle",
                "dx": 0.3,
                "dy": 0.3,
                "dz": 0.3,
                "speed": 0.1,
                "count": 4,
                "id": "minecraft:electric_spark"
            },
            {
                "type": "replace_item",
                "output": {
                    "id": "kubejs:damaged_natures_compass"
                }
            }
        ]
    ],
    [
        "create_connected:control_chip",
        2,
        [
            {
                "type": "sound",
                "id": "minecraft:block.comparator.click",
                "volume": 0.5,
                "pitch": 1.5
            },
            {
                "type": "particle",
                "dx": 0.3,
                "dy": 0.3,
                "dz": 0.3,
                "speed": 0.1,
                "count": 4,
                "id": "minecraft:electric_spark"
            },
            {
                "type": "replace_item",
                "output": {
                    "id": "kubejs:damaged_control_chip"
                }
            }
        ]
    ],
    [
        "create:electron_tube",
        2,
        [
            {
                "type": "sound",
                "id": "minecraft:block.glass.break",
                "volume": 0.4,
                "pitch": 1.8
            },
            {
                "type": "particle",
                "dx": 0.3,
                "dy": 0.3,
                "dz": 0.3,
                "speed": 0.1,
                "count": 4,
                "id": "minecraft:electric_spark"
            },
            {
                "type": "replace_item",
                "output": {
                    "id": "kubejs:damaged_electron_tube"
                }
            }
        ]
    ],
    [
        "minecraft:glass",
        2,
        [
            {
                "type": "sound",
                "id": "minecraft:block.glass.break",
                "volume": 1,
                "pitch": 1
            },
            {
                "type": "particle",
                "dx": 0.3,
                "dy": 0.3,
                "dz": 0.3,
                "speed": 0.1,
                "count": 20,
                "id": "minecraft:block",
                "block": "minecraft:glass"
            },
            {
                "type": "discard"
            }
        ]
    ],
    [
        "kubejs:devitrified_glass",
        4,
        [
            {
                "type": "sound",
                "id": "minecraft:block.glass.break",
                "volume": 1,
                "pitch": 1
            },
            {
                "type": "particle",
                "dx": 0.3,
                "dy": 0.3,
                "dz": 0.3,
                "speed": 0.1,
                "count": 20,
                "id": "minecraft:block",
                "block": "minecraft:glass"
            },
            {
                "type": "discard"
            }
        ]
    ],
    [
        "kubejs:devitrified_glass",
        2,
        [
            {
                "type": "sound",
                "id": "minecraft:block.glass.break",
                "volume": 1,
                "pitch": 1
            },
            {
                "type": "particle",
                "dx": 0.3,
                "dy": 0.3,
                "dz": 0.3,
                "speed": 0.1,
                "count": 20,
                "id": "minecraft:block",
                "block": "minecraft:glass"
            },
            {
                "type": "replace_item",
                "output": {
                    "id": "kubejs:cracked_glass"
                }
            }
        ]
    ],
    [
        "kubejs:cracked_glass",
        2,
        [
            {
                "type": "sound",
                "id": "minecraft:block.glass.break",
                "volume": 1,
                "pitch": 1
            },
            {
                "type": "particle",
                "dx": 0.3,
                "dy": 0.3,
                "dz": 0.3,
                "speed": 0.1,
                "count": 20,
                "id": "minecraft:block",
                "block": "minecraft:glass"
            },
            {
                "type": "discard"
            }
        ]
    ],
    [
        "minecraft:tnt",
        6,
        [
            {
                "type": "sound",
                "id": "minecraft:entity.generic.explode",
                "volume": 1,
                "pitch": 1
            },
            {
                "type": "particle",
                "dx": 0.5,
                "dy": 0.5,
                "dz": 0.5,
                "speed": 0.1,
                "count": 10,
                "id": "minecraft:explosion"
            },
            {
                "type": "explosion",
                "strength": 2,
                "fire": false,
                "interaction": "TNT"
            },
            {
                "type": "discard"
            }
        ]
    ],
    [
        "ae2:tiny_tnt",
        6,
        [
            {
                "type": "sound",
                "id": "minecraft:entity.generic.explode",
                "volume": 0.5,
                "pitch": 1
            },
            {
                "type": "particle",
                "dx": 0,
                "dy": 0,
                "dz": 0,
                "speed": 0,
                "count": 1,
                "id": "minecraft:explosion"
            },
            {
                "type": "explosion",
                "strength": 1,
                "fire": false,
                "interaction": "BLOCK"
            },
            {
                "type": "discard"
            }
        ]
    ],
    [
        "minecraft:gunpowder",
        4,
        [
            {
                "type": "sound",
                "id": "minecraft:block.sand.place",
                "volume": 2,
                "pitch": 2
            },
            {
                "type": "particle",
                "dx": 0.5,
                "dy": 0.3,
                "dz": 0.5,
                "speed": 0.1,
                "count": 16,
                "id": "minecraft:falling_dust",
                "block": "minecraft:gravel"
            },
            {
                "type": "place_block",
                "block": "minecraft:fire"
            },
            {
                "type": "discard"
            }
        ]
    ]
]
    impacts.forEach(([input, height, actions]) => {
        if (!Item.exists(input) || actions.some(a => a.type === 'replace_item' && !Item.exists(a.output.id))) return
        event.recipes.cwi.item_impact(input, height, actions)
            .id(`cwi:world/impact/${recipeId(input)}/${height}`)
    })

    event.recipes.cwi.inventory_hazard('#cwi:hot_items', 1).interval(10)
        .damageType('minecraft:in_fire').id('cwi:world/hot_items')
    event.recipes.cwi.toxin_exposure('#cwi:neurotoxin', 'clanginghowl:neurotoxin')
        .additionalInput('#cwi:neurotoxin_lv2').additionalWeight(9).interval(10)
        .retention(0.85).scale(0.02).threshold(1.5).stateKey('neurotoxinAmount').duration(11)
        .meleeChance(0.2).meleeDuration(100).strongMeleeChance(0.4).strongMeleeDuration(200)
        .id('cwi:world/neurotoxin')

    const molten = [
    'kubejs:molten_aluminum',
    'kubejs:molten_vanadium',
    'kubejs:molten_tin',
    'kubejs:molten_silver',
    'kubejs:molten_andesite_alloy',
    'kubejs:molten_zinc',
    'kubejs:molten_gold',
    'kubejs:molten_iron',
    'kubejs:molten_pig_iron',
    'kubejs:molten_undeoxidized_steel',
    'kubejs:molten_cast_iron',
    'kubejs:molten_copper',
    'kubejs:molten_brass',
    'kubejs:molten_lead',
    'kubejs:molten_cobalt',
    'kubejs:molten_nickel',
    'kubejs:molten_constantan',
    'kubejs:molten_bronze',
    'kubejs:molten_electrum',
    'kubejs:molten_slag',
    'kubejs:liquid_silicon',
    'kubejs:molten_glass'
]
    molten.filter(hasFluid).forEach(fluid => {
        event.recipes.cwi.fluid_hazard(fluid, 7).interval(10).damageType('minecraft:lava').fireTicks(60)
            .effects([{ id: 'minecraft:slowness', duration: 10, amplifier: 6 }])
            .id(`cwi:world/molten/${recipeId(fluid)}`)
    })
    const acids = [
    ['kubejs:redstone_acid', 3],
    ['kubejs:concentrated_sulfuric_acid', 5],
    ['kubejs:nitric_acid', 4],
    ['kubejs:muriatic_acid', 4.5],
    ['kubejs:acetic_acid', 2],
    ['kubejs:propionic_acid', 1],
    ['kubejs:lactic_acid', 2],
    ['kubejs:wood_vinegar', 0.5],
    ['kubejs:humic_acid_slurry', 0.5],
    ['kubejs:sulfuric_acid', 4]
]
    acids.filter(([fluid]) => hasFluid(fluid)).forEach(([fluid, damage]) => {
        event.recipes.cwi.fluid_hazard(fluid, damage).interval(10).damageType('minecraft:magic')
            .effects([
                { id: 'biomancy:armor_shred', duration: 100, amplifier: 1 },
                { id: 'biomancy:corrosive', duration: 20, amplifier: 1 }
            ]).id(`cwi:world/acid/${recipeId(fluid)}`)
    })

    const lockTools = {
    "minecraft:wooden_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "minecraft:wooden_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "minecraft:wooden_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "minecraft:wooden_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:wooden_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:stone_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "minecraft:stone_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "minecraft:stone_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "minecraft:stone_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:stone_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:iron_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "minecraft:iron_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "minecraft:iron_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "minecraft:iron_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:iron_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:golden_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "minecraft:golden_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "minecraft:golden_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "minecraft:golden_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:golden_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:diamond_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "minecraft:diamond_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "minecraft:diamond_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "minecraft:diamond_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:diamond_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:netherite_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "minecraft:netherite_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "minecraft:netherite_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "minecraft:netherite_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "minecraft:netherite_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:rusted_iron_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "kubejs:rusted_iron_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "kubejs:rusted_iron_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "kubejs:rusted_iron_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:rusted_iron_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:lead_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "kubejs:lead_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "kubejs:lead_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "kubejs:lead_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:lead_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:copper_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "kubejs:copper_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "kubejs:copper_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "kubejs:copper_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:copper_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:zinc_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "kubejs:zinc_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "kubejs:zinc_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "kubejs:zinc_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:zinc_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:brass_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "kubejs:brass_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "kubejs:brass_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "kubejs:brass_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:brass_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:bronze_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "kubejs:bronze_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "kubejs:bronze_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "kubejs:bronze_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:bronze_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:steel_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "kubejs:steel_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "kubejs:steel_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "kubejs:steel_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:steel_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:stainless_steel_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "kubejs:stainless_steel_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "kubejs:stainless_steel_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "kubejs:stainless_steel_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "kubejs:stainless_steel_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "ae2:nether_quartz_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "ae2:nether_quartz_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "ae2:nether_quartz_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "ae2:nether_quartz_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "ae2:nether_quartz_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "ae2:certus_quartz_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "ae2:certus_quartz_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "ae2:certus_quartz_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "ae2:certus_quartz_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "ae2:certus_quartz_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "ae2:fluix_sword": {
        "successChance": 0.2,
        "durabilityCost": 3
    },
    "ae2:fluix_axe": {
        "successChance": 0.4,
        "durabilityCost": 3
    },
    "ae2:fluix_pickaxe": {
        "successChance": 0.5,
        "durabilityCost": 3
    },
    "ae2:fluix_shovel": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "ae2:fluix_hoe": {
        "successChance": 0.3,
        "durabilityCost": 3
    },
    "clanginghowl:advanced_chainsword": {
        "successChance": 1,
        "durabilityCost": 3
    }
}
    Object.entries(lockTools).forEach(([tool, config]) => {
        if (!Item.exists(tool)) return
        event.recipes.cwi.lockpicking(tool, ['minecraft:chest', 'minecraft:barrel'], config.successChance)
            .durability(config.durabilityCost).lootOnly(true).silverfishChance(0.1)
            .id(`cwi:world/lock/${recipeId(tool)}`)
    })

    event.recipes.cwi.block_spouting(['minecraft:mud'], 'minecraft:dirt', Fluid.of('minecraft:water', 1000))
        .id('cwi:world/spouting/mud')
    event.recipes.cwi.block_spouting(['minecraft:obsidian'], 'minecraft:magma_block', Fluid.of('minecraft:water', 1000))
        .id('cwi:world/spouting/obsidian')
    if (hasFluid('kubejs:nitrogen_fertilizer')) {
        for (const color of ['brown', 'red']) {
            event.recipes.cwi.block_spouting([`minecraft:${color}_mushroom_block`, 'minecraft:mushroom_stem'],
                `minecraft:${color}_mushroom`, Fluid.of('kubejs:nitrogen_fertilizer', 10))
                .id(`cwi:world/spouting/${color}_mushroom`)
        }
    }

    const budding = [
        ['minecraft:budding_amethyst', 1, ['minecraft:small_amethyst_bud', 'minecraft:medium_amethyst_bud', 'minecraft:large_amethyst_bud', 'minecraft:amethyst_cluster']],
        ['cwi:infected_budding_amethyst', 2, ['minecraft:small_amethyst_bud', 'minecraft:medium_amethyst_bud', 'minecraft:large_amethyst_bud', 'minecraft:amethyst_cluster']],
        ['geode_plus:budding_nether_quartz', 1, ['geode_plus:small_nether_quartz_bud', 'geode_plus:medium_nether_quartz_bud', 'geode_plus:large_nether_quartz_bud', 'geode_plus:nether_quartz_crystal']],
        ['cwi:infected_budding_quartz', 2, ['geode_plus:small_nether_quartz_bud', 'geode_plus:medium_nether_quartz_bud', 'geode_plus:large_nether_quartz_bud', 'geode_plus:nether_quartz_crystal']]
    ]
    budding.forEach(([block, multiplier, stages]) => {
        if (!Item.exists(block) || stages.some(stage => !Item.exists(stage))) return
        for (const [fluid, amount, chance, attempts] of [
            ['minecraft:water', 200, 0.5, 1], ['minecraft:lava', 50, 0.75, 2], ['kubejs:caustic_soda', 50, 1, 3]
        ]) {
            if (!hasFluid(fluid)) continue
            event.recipes.cwi.budding_catalysis(block, Fluid.of(fluid, amount), stages)
                .chance(chance).attempts(attempts * multiplier)
                .id(`cwi:world/budding/${recipeId(block)}/${recipeId(fluid)}`)
        }
    })

    event.recipes.cwi.drying('kubejs:pressed_wet_web', 'kubejs:wet_web', 0.02).id('cwi:world/drying/wet_web')
    event.recipes.cwi.drying('kubejs:dried_web', 'kubejs:pressed_wet_web', 0.1).id('cwi:world/drying/pressed_wet_web')
})
