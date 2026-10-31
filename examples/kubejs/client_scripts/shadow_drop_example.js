// Put this file in kubejs/client_scripts/.
// Requires CWI Core's Shadow Drop integration and the Shadow Drop mod.
// These settings affect GUI item shadows, including block items.
// Reload client scripts to apply changes; startup shadow overrides take precedence.

CwiEvents.shadowDrop(event => {
  event.amount(0.4)       // Any number from 0 to 1: 0 = invisible, 1 = fully opaque.
  event.color('#000000') // Six-digit RGB. Numeric colors such as 0x203040 also work.

  // Alternatives: replace the lines above with the settings you want.
  // event.amount(0)                  // Disable shadows globally.
  // event.amount(1).color('#302040')  // Fully opaque purple shadows.
  // event.color(0x203040)            // Blue-gray; same format as '#203040'.
})

// Omit amount() or color() to inherit that property from Shadow Drop's config.
// Remove this handler and reload client scripts to restore both native defaults.
// Shadow Drop's enabled/context settings are still respected.
