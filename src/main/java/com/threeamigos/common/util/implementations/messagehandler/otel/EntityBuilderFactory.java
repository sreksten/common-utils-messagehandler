package com.threeamigos.common.util.implementations.messagehandler.otel;

/**
 *
 * @author Stefano Reksten
 */
public class EntityBuilderFactory {

    private EntityBuilderFactory() {}

    public static EntityBuilderInterface getBuilder() {
        return new EntityBuilderImpl();
    }

    private static void main(String[] args) {

    }
}
