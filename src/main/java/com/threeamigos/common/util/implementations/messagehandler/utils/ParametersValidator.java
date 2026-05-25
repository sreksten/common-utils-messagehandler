package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;

/**
 *
 * @author Stefano Reksten
 */
public class ParametersValidator {

    public static <T> T validateNotNull(final T parameter, final String parameterName) {
        if (parameter == null) {
            throw new NullPointerException(String.format(MessageHandlerResourceBundle.get("parameterMustNotBeNull"), parameterName));
        }
        return parameter;
    }

    public static String validateNotBlank(final String parameter, final String parameterName) {
        String nonNullParameter = validateNotNull(parameter, parameterName).trim();
        if (nonNullParameter.isEmpty()) {
            throw new IllegalArgumentException(String.format(MessageHandlerResourceBundle.get("parameterMustNotBeBlank"), parameterName));
        }
        return nonNullParameter;
    }

    public static int validateGreaterThanZero(final int parameter, final String parameterName) {
        if (parameter <= 0) {
            throw new IllegalArgumentException(String.format(MessageHandlerResourceBundle.get("parameterMustBeGreaterThanZero"), parameterName));
        }
        return parameter;
    }

    public static long validateGreaterThanZero(final long parameter, final String parameterName) {
        if (parameter <= 0) {
            throw new IllegalArgumentException(String.format(MessageHandlerResourceBundle.get("parameterMustBeGreaterThanZero"), parameterName));
        }
        return parameter;
    }

    public static <T> void validateNonEmpty(final T[] parameter, final String parameterName) {
        if (parameter == null) {
            throw new NullPointerException(String.format(MessageHandlerResourceBundle.get("parameterMustNotBeNull"), parameterName));
        }
        if (parameter.length == 0) {
            throw new IllegalArgumentException(String.format(MessageHandlerResourceBundle.get("parameterMustNotBeEmpty"), parameterName));
        }
    }

    public static void validateNonEmpty(final byte[] parameter, final String parameterName) {
        if (parameter == null) {
            throw new NullPointerException(String.format(MessageHandlerResourceBundle.get("parameterMustNotBeNull"), parameterName));
        }
        if (parameter.length == 0) {
            throw new IllegalArgumentException(String.format(MessageHandlerResourceBundle.get("parameterMustNotBeEmpty"), parameterName));
        }
    }

}
