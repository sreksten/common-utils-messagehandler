package com.threeamigos.common.util.implementations.messagehandler.otel;

/**
 *
 * @author Stefano Reksten
 */
public class InstrumentationScopeBuilderFactory {

    private InstrumentationScopeBuilderFactory() {}

    public static InstrumentationScopeBuilderInterface.InstrumentationScopeBuilderStepName getBuilder() {
        return new InstrumentationScopeBuilderImpl();
    }

}
