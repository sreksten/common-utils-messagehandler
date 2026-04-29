package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.TraceState;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * An immutable implementation of the TraceState interface.
 *
 *
 * @author Stefano Reksten
 */
public class TraceStateImpl implements TraceState {

    private static final int TRACE_STATE_MAX_MEMBERS = 32;

    // W3C trace-context v1 key ABNF:
    // key = simple-key / multi-tenant-key
    // simple-key starts with lcalpha, max 256 chars.
    // multi-tenant-key = tenant-id "@" system-id.
    private static final Pattern TRACE_STATE_KEY_PATTERN = Pattern.compile(
            "[a-z][a-z0-9_\\-*/]{0,255}|[a-z0-9][a-z0-9_\\-*/]{0,240}@[a-z][a-z0-9_\\-*/]{0,13}");

    // W3C trace-context v1 value ABNF:
    // value = 0*255(chr) nblk-chr
    // chr excludes "," and "=", and nblk-chr also excludes trailing space.
    private static final Pattern TRACE_STATE_VALUE_PATTERN = Pattern.compile(
            "[\\x20-\\x2B\\x2D-\\x3C\\x3E-\\x7E]{0,255}[\\x21-\\x2B\\x2D-\\x3C\\x3E-\\x7E]");

    private final Map<String, AnyValue> traceStateMap;

    public TraceStateImpl() {
        traceStateMap = Collections.emptyMap();
    }

    private TraceStateImpl(final Map<String, AnyValue> initialMap) {
        traceStateMap = Collections.unmodifiableMap(new LinkedHashMap<>(initialMap));
    }

    @Override
    public AnyValue get(final String key) {
        if (!isValidTraceStateKey(key)) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "invalidTraceStateKey",
                    key));
            return null;
        }
        return traceStateMap.get(key);
    }

    @Override
    public TraceState set(final String key, final AnyValue value) {
        if (!isValidTraceStateKey(key)) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "invalidTraceStateKey",
                    key));
            return this;
        }
        if (!isValidTraceStateValue(value)) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "invalidTraceStateValueForKey",
                    key));
            return this;
        }

        LinkedHashMap<String, AnyValue> newMap = new LinkedHashMap<>(TRACE_STATE_MAX_MEMBERS);
        newMap.put(key, value);

        for (Map.Entry<String, AnyValue> entry : traceStateMap.entrySet()) {
            if (key.equals(entry.getKey())) {
                continue;
            }
            if (newMap.size() >= TRACE_STATE_MAX_MEMBERS) {
                break;
            }
            newMap.put(entry.getKey(), entry.getValue());
        }
        return new TraceStateImpl(newMap);
    }

    @Override
    public TraceState delete(final String key) {
        if (!isValidTraceStateKey(key)) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "invalidTraceStateKey",
                    key));
            return this;
        }
        if (!traceStateMap.containsKey(key)) {
            return this;
        }
        LinkedHashMap<String, AnyValue> newMap = new LinkedHashMap<>(traceStateMap);
        newMap.remove(key);
        return new TraceStateImpl(newMap);
    }

    @Override
    public Collection<KeyValue> getValues() {
        return Collections.unmodifiableCollection(traceStateMap.entrySet().stream()
                .map(entry -> new KeyValueImpl(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
    }

    private static boolean isValidTraceStateKey(final String key) {
        return key != null && TRACE_STATE_KEY_PATTERN.matcher(key).matches();
    }

    private static boolean isValidTraceStateValue(final AnyValue value) {
        if (value == null || value.getType() != AnyValue.Type.STRING) {
            return false;
        }
        String stringValue = value.asString();
        return stringValue != null && TRACE_STATE_VALUE_PATTERN.matcher(stringValue).matches();
    }
}
