# OTel Compliance Report — `messagehandler.otel` package

**Date:** 2026-04-19  
**Status:** Up-to-date  
**Scope:** `src/main/java/com/threeamigos/common/util/interfaces/messagehandler/otel/` and `src/main/java/com/threeamigos/common/util/implementations/messagehandler/otel/`

## References
- [OTel Log Data Model](https://opentelemetry.io/docs/specs/otel/logs/data-model/)
- [OTel Common (AnyValue, KeyValue, InstrumentationScope)](https://opentelemetry.io/docs/specs/otel/common/)
- [OTel Resource SDK](https://opentelemetry.io/docs/specs/otel/resource/sdk/)
- [OTLP HTTP/JSON Encoding](https://opentelemetry.io/docs/specs/otlp/#otlphttp-json-encoding)
- [OTel proto: logs/v1/logs.proto](https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/logs/v1/logs.proto)
- [OTel proto: common/v1/common.proto](https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/common/v1/common.proto)
- [OTel proto: resource/v1/resource.proto](https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/resource/v1/resource.proto)

---

## Summary

The OTel package now conforms to the targeted OpenTelemetry Logs/Common/OTLP JSON requirements implemented by this library.

---

## Implemented Compliance Matrix

### 1) Data Model
| Requirement | Status |
|---|---|
| `LogRecord` fields modeled: timestamp, observed timestamp, trace/span ids, flags, severity text/number, body, attributes, dropped count, event name | ✅ |
| `SeverityNumber` supports canonical range `0..24` and reverse lookup | ✅ |
| `AnyValue` supports `EMPTY`, `STRING`, `BOOL`, `INT`, `DOUBLE`, `ARRAY`, `KVLIST`, `BYTES` | ✅ |
| `AnyValue` typed getters enforce discriminator via `IllegalStateException` | ✅ |
| `AnyValueImpl` immutability guarantees via defensive copies (`array`, `kvlist`, `bytes`) | ✅ |
| `KeyValue` enforces non-null, non-empty keys and non-null values | ✅ |

### 2) Validation
| Requirement | Status |
|---|---|
| `traceId`: 32 lowercase hex, not all zeros | ✅ |
| `spanId`: 16 lowercase hex, not all zeros | ✅ |
| `traceFlags`: bounded to `0x00..0xFF` | ✅ |
| `droppedAttributesCount`: non-negative in log/resource/scope implementations | ✅ |
| Attribute collections reject `null` entries, `null`/empty keys, `null` values, duplicate keys | ✅ |
| `severityText` is independent from `severityNumber` (both optional and can coexist independently) | ✅ |

### 3) OTLP JSON Encoding
| Requirement | Status |
|---|---|
| `format()` emits `ExportLogsServiceRequest` with `resourceLogs/scopeLogs/logRecords` | ✅ |
| `formatRecord()` emits only naked `LogRecord` object | ✅ |
| `resource` and `scope` blocks omitted entirely when absent | ✅ |
| `schemaUrl` emitted at `ResourceLogs` / `ScopeLogs` level (not nested in `resource`/`scope`) | ✅ |
| `timeUnixNano` and `observedTimeUnixNano` emitted as decimal strings | ✅ |
| timestamp conversion validated as unsigned `uint64` nanoseconds (reject negative/overflow) | ✅ |
| `traceId` / `spanId` emitted as hex strings | ✅ |
| `intValue` emitted as decimal string | ✅ |
| `doubleValue` handles finite + `NaN`/`Infinity`/`-Infinity` per proto3 JSON mapping | ✅ |
| `bytesValue` emitted as base64 string | ✅ |
| `arrayValue` / `kvlistValue` encoded using nested `values` objects | ✅ |
| `EMPTY` AnyValue encoded as `{}` | ✅ |
| `eventName` encoded as `eventName` (proto field `12`) | ✅ |
| null/empty/default fields omitted when appropriate | ✅ |

---

## Verification Evidence

### Build and tests
- `mvn test -q` passed
- `mvn verify -q` passed

### Coverage (Jacoco)
Using `target/site/jacoco/jacoco.csv`:
- Instruction missed: `0`
- Branch missed: `0`
- Line missed: `0`

### Test suite breadth
Surefire summary:
- Tests run: `405`
- Failures: `0`
- Errors: `0`
- Skipped: `0`

---

## Notes

- This report covers the OTel model/formatter functionality implemented in this repository (logs/common/OTLP JSON path).  
- Broader OpenTelemetry concerns not modeled here (SDK runtime behavior, exporters, collectors, semantic convention completeness per domain, etc.) are outside this package scope.
