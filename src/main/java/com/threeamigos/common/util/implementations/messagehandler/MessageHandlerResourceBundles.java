package com.threeamigos.common.util.implementations.messagehandler;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Internal utility for loading {@link ResourceBundle} instances used by
 * {@link com.threeamigos.common.util.interfaces.messagehandler.MessageHandler} implementations.
 * <p>
 * Centralises classloader-aware bundle loading so that each handler class does not need
 * to replicate the lookup logic. Using the owner class's own {@link ClassLoader} ensures
 * correct resolution in modular and OSGi environments where the context classloader may
 * not have visibility of the handler's resources.
 * <p>
 * This class is package-private and not part of the public API.
 */
final class MessageHandlerResourceBundles {

    private MessageHandlerResourceBundles() {
    }

    /**
     * Loads a {@link ResourceBundle} using the classloader of the given owner class
     * and the default {@link Locale}.
     *
     * @param bundleBaseName the fully qualified base name of the resource bundle
     *                       (e.g. {@code "com.example.MyClass.MyClass"})
     * @param ownerClass     the class whose {@link ClassLoader} should be used for bundle resolution
     * @return the loaded {@link ResourceBundle}
     * @throws MissingResourceException if no bundle for the given base name can be found
     */
    static ResourceBundle load(final String bundleBaseName, final Class<?> ownerClass) {
        return ResourceBundle.getBundle(bundleBaseName, Locale.getDefault(), ownerClass.getClassLoader());
    }
}
