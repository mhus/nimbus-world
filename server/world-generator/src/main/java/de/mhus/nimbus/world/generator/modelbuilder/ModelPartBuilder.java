package de.mhus.nimbus.world.generator.modelbuilder;

/**
 * Interface for building a part of a model (e.g. trunk, leaves, roots).
 * Implementations are registered as Spring components and looked up by name.
 */
public interface ModelPartBuilder {

    /**
     * Technical name used for lookup from step definitions.
     */
    String name();

    /**
     * Build a part of the model using the given context and resolved step parameters.
     */
    void buildPart(ModelBuilderContext context, ResolvedStep step) throws ModelBuilderException;
}
