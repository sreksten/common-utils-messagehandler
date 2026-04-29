package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.TraceState;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An immutable implementation of the TraceState interface.
 *
 * @author Stefano Reksten
 */
public class TraceStateImpl implements TraceState {

    private final Map<String, AnyValue> traceStateMap;

    public TraceStateImpl() {
        traceStateMap = Collections.emptyMap();
    }

    private TraceStateImpl(Map<String, AnyValue> initialMap) {
        traceStateMap = Collections.unmodifiableMap(new HashMap<>(initialMap));
    }

    @Override
    public AnyValue get(String key) {
        if (key == null) {
            OpenTelemetryAttributeValidator.handleBundled("keyMustNotBeNull");
            return null;
        }
        return traceStateMap.get(key);
    }

    @Override
    public TraceState set(String key, AnyValue value) {
        if (key == null) {
            OpenTelemetryAttributeValidator.handleBundled("keyMustNotBeNull");
            return this;
        }
        if (traceStateMap.containsKey(key) && Objects.equals(traceStateMap.get(key), value)) {
            return this;
        }
        Map<String, AnyValue> newMap = new HashMap<>(traceStateMap);
        newMap.put(key, value);
        return new TraceStateImpl(newMap);
    }

    @Override
    public TraceState delete(String key) {
        if (key == null) {
            OpenTelemetryAttributeValidator.handleBundled("keyMustNotBeNull");
            return this;
        }
        if (traceStateMap.containsKey(key)) {
            Map<String, AnyValue> newMap = new HashMap<>(traceStateMap);
            newMap.remove(key);
            return new TraceStateImpl(newMap);
        }
        return this;
    }

    @Override
    public Collection<KeyValue> getValues() {
        return Collections.unmodifiableCollection(traceStateMap.entrySet().stream()
                .map(entry -> new KeyValueImpl(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
    }
}
