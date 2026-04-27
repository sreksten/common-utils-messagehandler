package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;

/**
 *
 * @author Stefano Reksten
 */
public interface InstrumentationScopeBuilderInterface {

    interface InstrumentationScopeBuilderStepName {
        /**
         * Sets scope name. Must be non-null and non-blank.
         */
        InstrumentationScopeBuilderStepVersion withName(String name);
    }

    interface InstrumentationScopeBuilderStepVersion extends KeyValueBuilderInterface {
        /**
         * Sets scope version. Must be non-null and non-blank.
         */
        InstrumentationScopeBuilderStepScopeUrl withVersion(String version);
        /**
         * Sets scope URL. Must be non-null and non-blank.
         */
        InstrumentationScopeBuilderStepBuild withScopeUrl(String scopeUrl);
        InstrumentationScope build();
    }

    interface InstrumentationScopeBuilderStepScopeUrl extends KeyValueBuilderInterface {
        /**
         * Sets scope URL. Must be non-null and non-blank.
         */
        InstrumentationScopeBuilderStepBuild withScopeUrl(String scopeUrl);
        InstrumentationScope build();
    }

    interface InstrumentationScopeBuilderStepBuild extends KeyValueBuilderInterface {
        InstrumentationScope build();
    }
}
