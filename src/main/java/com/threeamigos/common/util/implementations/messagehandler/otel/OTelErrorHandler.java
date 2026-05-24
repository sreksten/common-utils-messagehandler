package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;

/**
 *
 * @author Stefano Reksten
 */
public class OTelErrorLogger {

    public static void log(Object object, String message) {
        java.util.logging.Logger.getLogger(getClassName(object)).severe(message);
    }

    public static void logBundled(Object object, String errorMessage) {
        String message;
        try {
            message = MessageHandlerResourceBundle.get(errorMessage);
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(getClassName(object)).severe(e.getMessage());
            message = errorMessage;
        }
        java.util.logging.Logger.getLogger(getClassName(object)).severe(message);
    }

    private static String getClassName(Object object) {
        if (object == null) {
            return "no class";
        }
        if (object instanceof Class) {
            return ((Class<?>) object).getName();
        }
        return object.getClass().getName();
    }
}
