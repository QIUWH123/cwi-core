package net.quiwh.cwi.compat.processing;

/** Metadata travels with native processing parameters, including addon parameter subclasses. */
public interface AdvancedParams {
    AdvancedProcessing cwi$advanced();
    void cwi$advanced(AdvancedProcessing value);
    AdvancedProcessing cwi$current();
    int cwi$duration();
    com.simibubi.create.content.processing.recipe.HeatCondition cwi$heat();
    void cwi$load(AdvancedProcessing value,int duration,com.simibubi.create.content.processing.recipe.HeatCondition heat);
}
