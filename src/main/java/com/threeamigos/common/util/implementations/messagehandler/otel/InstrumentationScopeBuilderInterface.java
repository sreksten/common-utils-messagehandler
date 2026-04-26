package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;

/**
 *
 * @author Stefano Reksten
 */
public interface InstrumentationScopeBuilderInterface {

    interface InstrumentationScopeBuilderStepName {
        InstrumentationScopeBuilderStepVersion withName(String name);
    }

    interface InstrumentationScopeBuilderStepVersion extends KeyValueBuilderInterface {
        InstrumentationScopeBuilderStepScopeUrl withVersion(String version);
        InstrumentationScopeBuilderStepBuild withScopeUrl(String scopeUrl);
        InstrumentationScope build();
    }

    interface InstrumentationScopeBuilderStepScopeUrl extends KeyValueBuilderInterface {
        InstrumentationScopeBuilderStepBuild withScopeUrl(String scopeUrl);
        InstrumentationScope build();
    }

    interface InstrumentationScopeBuilderStepBuild extends KeyValueBuilderInterface {
        InstrumentationScope build();
    }
}
