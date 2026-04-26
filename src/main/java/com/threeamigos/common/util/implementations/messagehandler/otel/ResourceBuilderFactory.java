package com.threeamigos.common.util.implementations.messagehandler.otel;

/**
 *
 * @author Stefano Reksten
 */
public class ResourceBuilderFactory {

    private ResourceBuilderFactory() {}

    public static ResourceBuilderInterface getBuilder() {
        return new ResourceBuilderImpl();
    }
}
