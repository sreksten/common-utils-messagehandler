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
 * The shared {@link #BUNDLE} constant provides a single point of access to all localized
 * strings used across the handler package, loaded once at class-initialization time.
 * <p>
 * This class is public, so sibling implementation packages can share the same bundle.
 */
public final class MessageHandlerResourceBundle {

    /**
     * The single shared {@link ResourceBundle} for all message-handler implementations.
     * <p>
     * Loaded once from
     * {@code com/threeamigos/common/util/implementations/messagehandler/MessageHandler/MessageHandler.properties}
     * (and its locale variants) using this class's own {@link ClassLoader}.
     */
    private static final ResourceBundle BUNDLE = load(
            "com.threeamigos.common.util.implementations.messagehandler.MessageHandler.MessageHandler",
            MessageHandlerResourceBundle.class);

    private MessageHandlerResourceBundle() {
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

    /**
     * Returns a localized message by key from the shared bundle.
     *
     * @param key message key
     * @return localized message
     */
    public static String get(final String key) {
        return BUNDLE.getString(key);
    }

    /**
     * Returns a formatted localized message using {@link String#format(String, Object...)}.
     *
     * @param key  message key
     * @param args format arguments
     * @return formatted localized message
     */
    public static String format(final String key, final Object... args) {
        return String.format(BUNDLE.getString(key), args);
    }
}
