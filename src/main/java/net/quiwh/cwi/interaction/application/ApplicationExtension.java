package net.quiwh.cwi.interaction.application;

/** Only manual item-application recipes use this optional metadata. */
public interface ApplicationExtension {
    ApplicationOptions cwi$interaction();
    void cwi$interaction(ApplicationOptions options);
}
