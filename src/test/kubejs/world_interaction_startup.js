StartupEvents.registry('block', event => {
    for (const stage of ['wet', 'dry', 'final']) {
        event.create(`cwi:drying_contract_${stage}`, 'cwi:drying')
            .box(0, 0, 0, 16, 2, 16)
            .notSolid()
            .suffocating(false)
            .defaultCutout()
    }
})
