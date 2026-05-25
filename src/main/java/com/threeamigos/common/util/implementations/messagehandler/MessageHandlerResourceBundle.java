package com.threeamigos.common.util.implementations.messagehandler;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

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

    private static final String DEFAULT_BUNDLE_BASE_NAME =
            "com.threeamigos.common.util.implementations.messagehandler.MessageHandler.MessageHandler";
    private static final Object BUNDLE_LOCK = new Object();

    /**
     * Lazily loaded shared bundle.
     * <p>
     * This field is intentionally not initialized eagerly to avoid class-initialization failures when
     * localization resources are absent in constrained runtime packaging scenarios.
     */
    private static volatile ResourceBundle bundle;
    private static volatile boolean bundleLoadAttempted;
    private static volatile MissingResourceException bundleLoadFailure;

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

    private static ResourceBundle resolveSharedBundleOrNull() {
        ResourceBundle shared = bundle;
        if (shared != null) {
            return shared;
        }
        if (bundleLoadAttempted) {
            return null;
        }
        synchronized (BUNDLE_LOCK) {
            shared = bundle;
            if (shared != null) {
                return shared;
            }
            if (bundleLoadAttempted) {
                return null;
            }
            try {
                shared = load(DEFAULT_BUNDLE_BASE_NAME, MessageHandlerResourceBundle.class);
                bundle = shared;
                return shared;
            } catch (MissingResourceException missingResourceException) {
                bundleLoadFailure = missingResourceException;
                return null;
            } catch (RuntimeException ignored) {
                return null;
            } finally {
                bundleLoadAttempted = true;
            }
        }
    }

    private static ResourceBundle resolveSharedBundleStrict() {
        ResourceBundle shared = bundle;
        if (shared != null) {
            return shared;
        }
        synchronized (BUNDLE_LOCK) {
            shared = bundle;
            if (shared != null) {
                return shared;
            }
            if (bundleLoadAttempted && bundleLoadFailure != null) {
                throw bundleLoadFailure;
            }
            try {
                shared = load(DEFAULT_BUNDLE_BASE_NAME, MessageHandlerResourceBundle.class);
                bundle = shared;
                bundleLoadFailure = null;
                return shared;
            } catch (MissingResourceException missingResourceException) {
                bundleLoadFailure = missingResourceException;
                throw missingResourceException;
            } finally {
                bundleLoadAttempted = true;
            }
        }
    }

    /**
     * Returns a localized message by key from the shared bundle.
     * <p>
     * This accessor is runtime-safe: if the bundle or key is missing, it returns {@code key}
     * instead of throwing.
     *
     * @param key message key
     * @return localized message
     */
    public static String get(final String key) {
        String fallback = key == null ? "" : key;
        return getOrDefault(key, fallback);
    }

    /**
     * Returns a localized message by key from the shared bundle, with a caller-provided fallback.
     * <p>
     * This accessor never throws.
     *
     * @param key message key
     * @param fallback fallback value used when bundle/key is unavailable
     * @return localized message or fallback
     */
    public static String getOrDefault(final String key, final String fallback) {
        String effectiveFallback = fallback == null ? "" : fallback;
        if (key == null || key.trim().isEmpty()) {
            return effectiveFallback;
        }
        ResourceBundle shared = resolveSharedBundleOrNull();
        if (shared == null) {
            return effectiveFallback;
        }
        try {
            return shared.getString(key);
        } catch (MissingResourceException ignored) {
            return effectiveFallback;
        } catch (RuntimeException ignored) {
            return effectiveFallback;
        }
    }

    /**
     * Strict bundle lookup.
     * <p>
     * Unlike {@link #get(String)}, this method is intended for startup validation and fails fast
     * when bundle/key is missing.
     *
     * @param key message key
     * @return localized message
     * @throws MissingResourceException if bundle or key is missing
     */
    public static String getStrict(final String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key must not be null or blank");
        }
        return resolveSharedBundleStrict().getString(key);
    }

    /**
     * Returns a formatted localized message using {@link String#format(String, Object...)}.
     * <p>
     * This accessor is runtime-safe: if bundle/key/formatting fails, it returns a non-throwing
     * fallback string.
     *
     * @param key  message key
     * @param args format arguments
     * @return formatted localized message
     */
    public static String format(final String key, final Object... args) {
        String fallback = key == null ? "" : key;
        return formatOrDefault(key, fallback, args);
    }

    /**
     * Returns a formatted localized message using a caller-provided fallback pattern.
     * <p>
     * This accessor never throws.
     *
     * @param key message key
     * @param fallbackPattern fallback pattern/message when bundle/key is unavailable
     * @param args format arguments
     * @return formatted localized message or fallback
     */
    public static String formatOrDefault(final String key,
                                         final String fallbackPattern,
                                         final Object... args) {
        String pattern = getOrDefault(key, fallbackPattern);
        try {
            return String.format(pattern, args);
        } catch (RuntimeException ignored) {
            if (args == null || args.length == 0) {
                return pattern;
            }
            return pattern + " " + Arrays.toString(args);
        }
    }

    /**
     * Strict formatted bundle lookup.
     * <p>
     * Unlike {@link #format(String, Object...)}, this method is intended for startup validation
     * and fails fast when bundle/key is missing.
     *
     * @param key message key
     * @param args format arguments
     * @return formatted localized message
     * @throws MissingResourceException if bundle or key is missing
     */
    public static String formatStrict(final String key, final Object... args) {
        return String.format(getStrict(key), args);
    }

    /**
     * Strict startup validation helper for required bundle keys.
     *
     * @param requiredKeys required message keys
     * @throws MissingResourceException if one or more keys are missing
     */
    public static void validateRequiredKeysStrict(final String... requiredKeys) {
        if (requiredKeys == null || requiredKeys.length == 0) {
            return;
        }
        ResourceBundle shared = resolveSharedBundleStrict();
        List<String> missingKeys = new ArrayList<String>();
        for (String key : requiredKeys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            if (!shared.containsKey(key)) {
                missingKeys.add(key);
            }
        }
        if (!missingKeys.isEmpty()) {
            throw new MissingResourceException(
                    "Missing required message-handler bundle keys: " + missingKeys,
                    DEFAULT_BUNDLE_BASE_NAME,
                    missingKeys.get(0));
        }
    }
}
