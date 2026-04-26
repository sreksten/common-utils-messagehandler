package com.threeamigos.common.util.implementations.messagehandler.otel;

/**
 *
 * @author Stefano Reksten
 */
public class KeyValueListBuilderFactory {

    private KeyValueListBuilderFactory() {}

    public static KeyValueListBuilderInterface getBuilder() {
        return new KeyValueListBuilderImpl();
    }
}
