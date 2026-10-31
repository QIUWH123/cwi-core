package net.quiwh.cwi.client.vintage;

import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.minecraft.world.item.ItemStack;

/** Consume the removed installation hint without creating a visible Ponder element. */
public enum ObsoleteModuleControls implements InputElementBuilder {
    INSTANCE;
    @Override public InputElementBuilder withItem(ItemStack stack) { return this; }
    @Override public InputElementBuilder leftClick() { return this; }
    @Override public InputElementBuilder rightClick() { return this; }
    @Override public InputElementBuilder scroll() { return this; }
    @Override public InputElementBuilder showing(ScreenElement element) { return this; }
    @Override public InputElementBuilder whileSneaking() { return this; }
    @Override public InputElementBuilder whileCTRL() { return this; }
}
