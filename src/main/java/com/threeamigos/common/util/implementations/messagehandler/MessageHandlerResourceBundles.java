package com.threeamigos.common.util.implementations.messagehandler;

import java.util.Locale;
import java.util.ResourceBundle;

final class MessageHandlerResourceBundles {

    private MessageHandlerResourceBundles() {
    }

    static ResourceBundle load(final String bundleBaseName, final Class<?> ownerClass) {
        return ResourceBundle.getBundle(bundleBaseName, Locale.getDefault(), ownerClass.getClassLoader());
    }
}
