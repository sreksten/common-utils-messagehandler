package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;

/**
 *
 * @author Stefano Reksten
 */
class InstrumentationScopeBuilderImpl extends KeyValueBuilderImpl implements InstrumentationScopeBuilderInterface,
        InstrumentationScopeBuilderInterface.InstrumentationScopeBuilderStepName,
        InstrumentationScopeBuilderInterface.InstrumentationScopeBuilderStepVersion,
        InstrumentationScopeBuilderInterface.InstrumentationScopeBuilderStepScopeUrl,
        InstrumentationScopeBuilderInterface.InstrumentationScopeBuilderStepBuild {

    private String name;
    private String version;
    private String scopeUrl;

    InstrumentationScopeBuilderImpl() {}

    @Override
    public InstrumentationScopeBuilderStepVersion withName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public InstrumentationScopeBuilderStepScopeUrl withVersion(String version) {
        this.version = version;
        return this;
    }

    @Override
    public InstrumentationScopeBuilderStepBuild withScopeUrl(String scopeUrl) {
        this.scopeUrl = scopeUrl;
        return this;
    }

    public InstrumentationScope build() {
        return InstrumentationScopeFactory.create(name, version, scopeUrl, attributes);
    }
}
