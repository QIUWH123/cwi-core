StartupEvents.registry('block', event => {
    // The production pack owns ordinary dirt; Core only owns its suspicious variants.
    event.create('kubejs:depleted_dirt')
    event.create('kubejs:ash_dirt', 'falling')
    for (const stage of ['wet', 'dry', 'final']) {
        event.create(`cwi:drying_contract_${stage}`, 'cwi:drying')
            .box(0, 0, 0, 16, 2, 16)
            .notSolid()
            .suffocating(false)
            .defaultCutout()
    }
})
