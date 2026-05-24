# common-utils-messagehandler

Part of the common-utils classes, that can help when writing standalone Java applications.

This package is primarily intended for small standalone Java applications.
Server/high-throughput deployments are supported, but should explicitly configure HTTP durability
policy and dead-letter (DLQ) handling for Jaeger/Grafana handlers.

This subpackage addresses the following needs:

### Logging and message handling

The `com.threeamigos.common.util.interfaces.messagehandler` package contains some functional interfaces (`Handler`s)
that can be used to deal with info, warn, error, fatal, debug, or trace messages, and `Throwable`s.
For convenience, all those interfaces are grouped in a `MessageHandler` interface.
Implementations include a console logger, an optionally rotable file, an in-memory store (useful to run tests),
a popup dialog, and a composite used to route messages to one or more other handlers.
For each of those, you can enable or disable any given level of messages. Thus, you can disable debug
or trace messages if you want to run your application in production mode. Or, using the composite,
you can forward a certain level of messages to a log file while sending other messages to the user via a popup window.

The common class for those handlers is the `AbstractMessageHandler`, from which you can derive your own handlers.

Being an interface, you can replace the MessageHandler passed to your application while keeping the rest of your code
unchanged. Should not be too difficult to e.g., implement a handler that sends messages to a Slack channel.

These handlers can accept a simple `String` or a `Supplier<String>`, which can be useful for lazy evaluation of messages.

Bridges for Log4J, SLF4J, and JUL are also provided, together with OTLP/HTTP handlers
for Jaeger- and Grafana-compatible backends.
The same package also exposes an OTel-like tracing model (`TracerProvider`, `Tracer`, `Span`) and
W3C Trace Context helpers (`TraceContextValidator`) for `traceparent`/`tracestate` ingestion and propagation.

# Primer

## Introduction

Logging is an important aspect of any computer program, from simple standalone applications to complex distributed
systems. It can help to understand what is happening under the hood, run forensics analysis, and find errors, bugs,
and bottlenecks in your code.

This section is a practical logging primer for this package, from basic console logging to OTel-style correlation
and backend dispatch.

## Baseline: `System.out` and `System.err`

Many projects start with direct prints:

```java
public class BaselineLoggingExample {
    public static void main(String[] args) {
        System.out.println("Application started");
        System.out.println("Warning! Cache is near capacity");
        System.err.println("Error... Payment failed");

        try {
            throw new IllegalStateException("Boom");
        } catch (Exception ex) {
            System.err.println("An exception happened! It says, " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }
}
```

If this was a larger project with multiple developers, and message handling was left to their
own preferences, as you can see, the resulting log would be inconsistent and challenging to manage.
A cleaner approach could be:

```java
public class BaselineLoggingExample {
    public static void main(String[] args) {
        System.out.println("[INFO] Application started");
        System.out.println("[WARN] Cache is near capacity");
        System.err.println("[ERROR] Payment failed");

        try {
            throw new IllegalStateException("Boom");
        } catch (Exception ex) {
            System.err.println("[ERROR] " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }
}
```

This works, but formatting, routing, filtering, and backend integration are still all manual.

Logging systems have a standardized approach to message formatting and routing, and expose methods like
`info`, `warning`, `error`, etc. that somehow decorate the message itself. The produced output
can be filtered by looking, for example, for log entries where an ERROR was present.

Following this approach, this package provides a series of interfaces designed to handle messages and their severity.
Moreover, instead of passing a `String` directly, the message itself may be lazily produced by a `Supplier<String>`, 
thus effectively producing it only when needed. In this way, useless calculation time for a complex `String` that formats 
various parameters can be avoided.

The severities this package supports are as follows, along with Java's `Throwable`s. For each severity, a couple of
interfaces are provided that accept a `String` or a `Supplier<String>`, depending on the flavor you're using:

| Severity  |Handler           | Handler using a Supplier | Method         |
|-----------|------------------|--------------------------|----------------|
| INFO      |`InfoHandler`     | `InfoSupplierHandler`    | info(...)      |
| WARN      |`WarnHandler`     | `WarnSupplierHandler`    | warning(...)   |
| ERROR     |`ErrorHandler`    | `ErrorSupplierHandler`   | error(...)     |
| FATAL     |`FatalHandler`    | `FatalSupplierHandler`   | fatal(...)     |
| DEBUG     |`DebugHandler`    | `DebugSupplierHandler`   | debug(...)     |
| TRACE     |`TraceHandler`    | `TraceSupplierHandler`   | trace(...)     |
| THROWABLE |`ThrowableHandler`|                          | exception(...) |

The Throwable part does not have the Supplier equivalent (if you are handling a Throwable, it's already there!),
but it has a `ThrowableWithMessageHandler` with an `exception(Throwable, String)` method, to pass a custom message.

As these interfaces are quite a lot, a composite interface called `MessageHandler` is defined,
which collects all of them.

The MessageHandler exposes additional methods:
- `handleMessage(SeverityNumber, String)` to specify directly the severity level of a message;
- `startSpan(String)` to start a Span directly (more on that later) from the handler when the handler is tracer-bound.

Internally the package supports the whole OpenTelemetry `SeverityNumber`s:
`INFO`, `INFO2`, `INFO3`, `INFO4`, `WARN`, `WARN2`, ...
For more information, see [OpenTelemetry Logs Data Model - 
field SeverityNumber](https://opentelemetry.io/docs/specs/otel/logs/data-model/#field-severitynumber).

This package provides a series of classes that implement the `MessageHandler` interface out of the hood:

```
AbstractMessageHandler (implements MessageHandler)
├── AbstractOutputMessageHandler (may handle the message asynchronously)
│   ├── AbstractHTTPOutputMessageHandler
│   │   ├── JaegerMessageHandler (OTLP/HTTP log transport for Jaeger-compatible pipelines)
│   │   └── GrafanaMessageHandler (OTLP/HTTP log transport for Grafana-compatible pipelines)
│   ├── ConsoleMessageHandler
│   └── FileMessageHandler
├── SwingMessageHandler (JOptionPane dialogs)
├── CompositeMessageHandler (fan-out pattern)
├── InMemoryMessageHandler (capture for testing)
├── JULMessageHandler (java.util.logging bridge)
├── Log4JMessageHandler (Apache Log4j 2 bridge)
├── SLF4JMessageHandler (SLF4J bridge)
├── JaegerMessageHandler (OTLP/HTTP log transport for Jaeger-compatible pipelines)
├── GrafanaMessageHandler (OTLP/HTTP log transport for Grafana-compatible pipelines)
└── VoidMessageHandler (does nothing)
```

## `ConsoleMessageHandler`: formatted print to the console

Instead of using `System.out` and `System.err` directly, `ConsoleMessageHandler` gives level-aware logging and 
exception handling through one interface. It outputs messages to the console, along with the current timestamp and log 
level. The output can be customized with a custom formatter (more on that later).

```java
import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;

public class ConsoleHandlerExample {
    public static void main(String[] args) {
        try (MessageHandler handler = new ConsoleMessageHandler()) {
            //  Logging a String.
            handler.info("Application started");
            // Using a Supplier<String>. If warn is not enabled, the formatting would not be done.
            handler.warn(() -> String.format("Cache is near capacity: %d%%", 85));
            handler.error("Payment failed");

            try {
                throw new IllegalStateException("Boom");
            } catch (Exception ex) {
                handler.exception("Failure while processing checkout", ex);
            }
        }
    }
}
```

## Severities: filter upon importance

To identify the severity level of a message, a `SeverityNumber` (`INFO`, `WARN`, `ERROR`, `DEBUG`, `TRACE`, with 
numbered variants) is used. This is used to categorize messages by their importance and urgency, allowing for 
fine-grained control over what information is logged and when. The `SeverityNumber` enum is equivalent to that used by
[OpenTelemetry](https://opentelemetry.io/docs/specs/otel/logs/data-model/#field-severitynumber). For a practical 
approach, have a look at the [OpenTelemetry SeverityNumber example 
mappings](https://opentelemetry.io/docs/specs/otel/logs/data-model-appendix/#appendix-b-severitynumber-example-mappings).

Default enabled levels are `INFO*`, `WARN*`, `ERROR*`, `FATAL*`, while `DEBUG*` and `TRACE*` are disabled.

All `MessageHandler` implementations extend `AbstractMessageHandler` which offers methods to enable or disable severity 
levels: `enable` and `disable` both accept a collection or a varargs list of `SeverityNumber`s; as shortcuts,
`setXXXEnabled` or `setXXXDisabled` will enable or disable a whole range of SeverityNumbers (e.g., `setInfoEnabled(true)`
will enable from `INFO` to `INFO4`).

Granularity notes:
- `setInfoEnabled(...)`, `setWarnEnabled(...)`, `setErrorEnabled(...)`, `setFatalEnabled(...)`,
  `setDebugEnabled(...)`, and `setTraceEnabled(...)` are group toggles for the related numbered variants.
- For per-variant control, use `setEnabled(SeverityNumber, boolean)`, `enable(...)`, and `disable(...)`.
- In `CompositeMessageHandler`, level mutation behavior is defined by `LevelControlMode`
  (`COMPOSITE_ONLY`, `PROPAGATE_TO_DELEGATES`, `DELEGATE_ONLY`).

```java
import com.threeamigos.common.util.implementations.messagehandler.AbstractMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;

public class SeverityFilteringExample {
    public static void main(String[] args) {
        AbstractMessageHandler handler = new ConsoleMessageHandler();

        handler.debug("Not shown by default");
        handler.setDebugEnabled(true);
        handler.debug("Now visible");

        handler.setEnabled(SeverityNumber.INFO3, false);
        handler.log(SeverityNumber.INFO3, "Filtered out");
        handler.log(SeverityNumber.INFO, "Still visible");
    }
}
```

## `FileMessageHandler`: store logs in files

Soon you'll discover that logging on the console can help when debugging a standalone application, but for production
environments, file-based logging is often preferred for its persistence and ability to handle large volumes of logs.

### Basic file logging

```java
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;

public class FileLoggingExample {
    public static void main(String[] args) {
        try (MessageHandler handler = new FileMessageHandler("/var/logs/app.log")) {
            handler.info("Started");
            handler.error("Example error");
        }
    }
}
```
Logs can grow quite large, so a form of rotation is often needed. When you rotate a log, you stop writing to
the old log file and start writing to a new file.

By default, `FileMessageHandler` constructors that do not accept a `RotationPolicy` use size-based
rotation with a threshold of `10 MB` (`10 * 1024 * 1024` bytes, exposed as
`FileMessageHandler.DEFAULT_SIZE_ROTATION_MAX_BYTES`).

This package supports three rotation-policy options: size-based, daily, and an explicit no-rotation
policy.
Constructors that accept a `RotationPolicy` expect a non-null value; use `new NoRotationPolicy()`
when you want to disable rotation.
By default, inter-process locking is disabled. You can opt in when multiple JVMs may write to the
same file.

### Size-based rotation

```java
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.file.SizeRotationPolicy;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ConsoleLogRecordFormatter;

public class SizeRotationExample {
    public static void main(String[] args) {
        FileMessageHandler handler = new FileMessageHandler(
                "logs/app.log",
                new SizeRotationPolicy(5L * 1024L * 1024L) // 5 MB
        );
        handler.info("Will rotate when threshold is reached");
        handler.close();
    }
}
```
When the file gets bigger than the limit, the file gets rotated; the old file gets a `.<yyyyMMdd-HHmmss-SSS>` suffix and
a new one is created.

### Daily rotation

```java
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.file.DailyRotationPolicy;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ConsoleLogRecordFormatter;

public class DailyRotationExample {
    public static void main(String[] args) {
        FileMessageHandler handler = new FileMessageHandler(
                "logs/app.log",
                new DailyRotationPolicy()
        );
        handler.info("Rotates on first write of a new day");
        handler.close();
    }
}
```
When the day changes, the log file gets rotated: the old file gets a `.yyyy-MM-dd` suffix and a new one is created.

### Disable rotation explicitly (opt-in)

```java
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.file.NoRotationPolicy;

public class NoRotationExample {
    public static void main(String[] args) {
        FileMessageHandler handler = new FileMessageHandler(
                "logs/app.log",
                new NoRotationPolicy()
        );
        handler.info("Always append to the same file");
        handler.close();
    }
}
```

### Inter-process file locking (opt-in)

```java
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;

public class LockedFileLoggingExample {
    public static void main(String[] args) {
        FileMessageHandler handler = new FileMessageHandler("logs/app.log", true);
        handler.info("Serialized across JVMs using app.log.lck");
        handler.close();
    }
}
```

When enabled, `FileMessageHandler` acquires an exclusive lock on a sidecar file
(`app.log.lck`) for each write operation. This is a cooperative lock: all writers must use the
same locking strategy to guarantee serialization.

## Output handler health metrics

All handlers based on `AbstractOutputMessageHandler` expose runtime health counters:
- `getHandlerHealthMetrics()`
- `isHealthy()`

This applies to:
- `ConsoleMessageHandler`
- `FileMessageHandler`
- `JaegerMessageHandler`
- `GrafanaMessageHandler`

`getHandlerHealthMetrics()` returns an immutable snapshot with:
- successful/failed/total output operations
- consecutive failure count
- last success/failure timestamps (epoch millis)
- async mode flag and pending queue size
- closed flag and derived health flag
- dropped operation count (for example, dispatch attempts after close)
- retry telemetry counters (attempts/successes/failures; available for retry-capable handlers)
- saturation telemetry (dispatch attempts, enqueue count, sync fallback count, saturation events,
  max observed queue size, configured queue capacity, bounded/unbounded flag, saturation ratio)
- overload policy telemetry (current overflow policy, overflow timeout, drop-newest/drop-oldest counters,
  block-timeout counter)
- overload shedding telemetry (rate-limited count, sampled-out count, active rate-limit settings)

Async backpressure behavior for these handlers:
- `async=true` uses one background worker and a queue.
- With a bounded queue (`queueCapacity > 0`), overflow handling is configurable via
  `setQueueOverflowPolicy(...)`:
  - `CALLER_RUNS` (default, backward-compatible): queue full -> sync fallback on caller thread
  - `DROP_NEWEST`: shed the incoming message
  - `DROP_OLDEST`: shed one queued message and keep the newest
  - `BLOCK_WITH_TIMEOUT`: wait up to `queueOverflowBlockTimeoutMillis`, then shed on timeout
- `queueCapacity <= 0` creates an unbounded queue.

```java
import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.AbstractOutputMessageHandler;

public class HandlerHealthExample {
    public static void main(String[] args) {
        ConsoleMessageHandler handler = new ConsoleMessageHandler();
        try {
            handler.info("startup complete");

            AbstractOutputMessageHandler.HandlerHealthMetrics metrics =
                    handler.getHandlerHealthMetrics();

            System.out.println("successful=" + metrics.getSuccessfulOperations());
            System.out.println("failed=" + metrics.getFailedOperations());
            System.out.println("healthy=" + handler.isHealthy());
        } finally {
            handler.close();
        }
    }
}
```

### Overload-control configuration example

```java
import com.threeamigos.common.util.implementations.messagehandler.AbstractOutputMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;

public class OverloadControlsExample {
    public static void main(String[] args) {
        GrafanaMessageHandler handler = new GrafanaMessageHandler(
                "http://localhost:4318/v1/logs",
                true,
                1000
        );

        // Queue overflow policy (default is CALLER_RUNS)
        handler.setQueueOverflowPolicy(AbstractOutputMessageHandler.QueueOverflowPolicy.DROP_OLDEST);
        handler.setQueueOverflowBlockTimeoutMillis(25L); // used only by BLOCK_WITH_TIMEOUT

        // Token bucket rate limit (disabled when permitsPerSecond <= 0)
        handler.setRateLimitPolicy(5000L, 10000L);
        handler.setRateLimitBypassSeverity(SeverityNumber.ERROR);

        // Severity-bucket sampling rates (TRACE, DEBUG, INFO, WARN, ERROR, FATAL)
        handler.setSeveritySamplingPolicy(0.05d, 0.10d, 0.25d, 1.0d, 1.0d, 1.0d);
    }
}
```

## HTTP durability policies (Jaeger/Grafana)

`JaegerMessageHandler` and `GrafanaMessageHandler` now accept a pluggable durability policy via
`HttpDispatchDurabilityStore`:
- `InMemoryHttpDispatchDurabilityStore` (default)
- `FileHttpDispatchDurabilityStore`
- `RedisHttpDispatchDurabilityStore` (Jedis)

Default constructors use:
- durability store: `InMemoryHttpDispatchDurabilityStore`
- dead-letter consumer: `System.err::println`

For standalone applications, these defaults are usually acceptable.
For server/high-throughput workloads, explicitly set both:
- a non-volatile durability policy (`FileHttpDispatchDurabilityStore` or `RedisHttpDispatchDurabilityStore`);
- a production DLQ consumer (`Consumer<String>`) aligned with your operational tooling.

When dispatch fails with a permanent/non-retryable failure (for example HTTP 400), entries are
routed to dead-letter reporting (`Consumer<String>`), so callers can redirect DLQ output to
console, file, or any custom sink. Retryable/transient I/O failures are instead reported through
the handler error-consumer path and records remain in the durability store for later retries.

```java
import com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.durability.FileHttpDispatchDurabilityStore;
import com.threeamigos.common.util.implementations.messagehandler.durability.RedisHttpDispatchDurabilityStore;

import java.nio.file.Paths;

public class HttpDurabilityExample {
    public static void main(String[] args) throws Exception {
        JaegerMessageHandler fileDurable = new JaegerMessageHandler(
                "http://localhost:4318/v1/logs",
                new FileHttpDispatchDurabilityStore(Paths.get("logs", "jaeger-pending.bin")),
                msg -> System.err.println("[DLQ] " + msg)
        );

        JaegerMessageHandler redisDurable = new JaegerMessageHandler(
                "http://localhost:4318/v1/logs",
                new RedisHttpDispatchDurabilityStore("redis://localhost:6379", "mh:jaeger:pending"),
                msg -> System.err.println("[DLQ] " + msg)
        );

        fileDurable.info("persisted locally before dispatch");
        redisDurable.info("persisted in Redis before dispatch");

        fileDurable.close();
        redisDurable.close();
    }
}
```

## Global internal error sink (`InnerErrorMessageHandler`)

Internal failures raised by the logging infrastructure itself (for example backend dispatch
runtime failures or failing per-handler error consumers) are routed through
`InnerErrorMessageHandler`.

Default global consumer:
- `System.err::println`

You can replace it once at bootstrap time:

```java
import com.threeamigos.common.util.implementations.messagehandler.InnerErrorMessageHandler;

public class InnerErrorBootstrap {
    public static void main(String[] args) {
        InnerErrorMessageHandler.setGlobalConsumer(
                msg -> System.err.println("[INNER-ERROR] " + msg)
        );

        // ... create and use handlers

        // Optional: restore default behavior.
        InnerErrorMessageHandler.resetGlobalConsumer();
    }
}
```

`FileMessageHandler`, `CompositeMessageHandler`, `JaegerMessageHandler`, `GrafanaMessageHandler`,
and `SwingMessageHandler` default their internal error reporting to this global sink.
Runtime exceptions thrown by user-provided extension points (for example custom error consumers,
custom delegates used by `CompositeMessageHandler`, or custom `RotationPolicy` logic) are also
reported through this mechanism to preserve caller flow.

## `SwingMessageHandler`: standalone desktop apps

For Swing/AWT apps, this handler shows popup dialogs instead of writing to console/file.

```java
import com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler;

import javax.swing.JFrame;

public class SwingHandlerExample {
    public static void main(String[] args) {
        JFrame parent = new JFrame("Demo");
        SwingMessageHandler handler = new SwingMessageHandler(parent);

        handler.info("Configuration loaded");
        handler.warn("Disk space is low");
        handler.error("Unable to save settings");
    }
}
```
`SwingMessageHandler` requires a graphical runtime. In headless environments, dialogs are
silently suppressed by design (calls become no-ops). Runtime UI invocation failures are treated as
internal handler failures and reported through `InnerErrorMessageHandler` instead of being
propagated to logging callers.

Very probably, when dealing with a standalone application, you might want to show info messages to the user, while
writing debug and error information to a file. You can do this using the next `MessageHandler`.

## `CompositeMessageHandler`: fan-out to multiple outputs

Use it when one log call must go to multiple destinations. For example, to the console and to a file.
Level control can be configured through `CompositeMessageHandler.LevelControlMode`:
- `COMPOSITE_ONLY` (default): only the composite gates levels.
- `PROPAGATE_TO_DELEGATES`: composite level changes are also applied to level-aware delegates.
- `DELEGATE_ONLY`: delegates own their level state; composite level mutators throw.

Calling `composite.close()` closes all registered delegates.

```java
import com.threeamigos.common.util.implementations.messagehandler.CompositeMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;

public class CompositeExample {
    public static void main(String[] args) {
        MessageHandler console = new ConsoleMessageHandler();
        MessageHandler file = new FileMessageHandler("logs/composite.log");

        CompositeMessageHandler composite = new CompositeMessageHandler(console, file);
        composite.info("This goes to console and file");
        composite.error("Same fan-out for errors");

        composite.close();
    }
}
```

With some fine-tuning, you can also have different output for the underlying handlers. For example, you could enable
debug and trace to the console while managing errors in a file.

```java
import com.threeamigos.common.util.implementations.messagehandler.CompositeMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;

public class CompositeExample2 {
    public static void main(String[] args) {
        MessageHandler console = new ConsoleMessageHandler();
        console.enableDebug();
        console.enableTrace();
        console.disableError();
        console.disableException();
        
        MessageHandler file = new FileMessageHandler("logs/composite.log");

        CompositeMessageHandler composite = new CompositeMessageHandler(console, file);
        composite.info("This goes to console and file");
        composite.error("This will go to a file but not to the console");

        composite.close();
    }
}
```

## `InMemoryMessageHandler`: testing

`InMemoryMessageHandler` is particularly useful in unit tests: it captures all messages in
memory and exposes them for inspection, without writing to any output.

```java
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;

public class InMemoryHandlerExample {
    public static void main(String[] args) {
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        handler.info("Configuration loaded");
        handler.warn("Cache near capacity");
        handler.error("Payment failed");

        System.out.println("Last message: " + handler.getLastMessage());
        System.out.println("All messages: " + handler.getAllMessages());
        System.out.println("Error count: " + handler.getErrorMessageCount());

        // Retrieve by level
        handler.getInfoMessages().forEach(System.out::println);
        handler.getWarnMessages().forEach(System.out::println);

        // Atomic snapshot (thread-safe)
        InMemoryMessageHandler.Snapshot snapshot = handler.snapshot();
    }
}
```

Key properties:
- Thread-safe via `ReentrantLock`.
- Optionally bounded: construct with `maxEntries` to limit stored messages (FIFO eviction).
- `snapshot()` returns an immutable, atomic view of all stored messages at that instant.
- Captures exceptions too (`getAllExceptions()`).

## `VoidMessageHandler`

When you need a no-op handler (for example, in production code where a handler is optional):

```java
import com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;

public class VoidHandlerExample {
    public static void main(String[] args) {
        MessageHandler handler = new VoidMessageHandler();
        handler.info("This is silently discarded");
    }
}
```

## Adapters: JUL, SLF4J, Log4J

If you already have in place some other forms of logging based on JUL, SLF4J, or Log4J, you can use the
provided adapters to integrate this package with them.

```java
import com.threeamigos.common.util.implementations.messagehandler.JULMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.Log4JMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.SLF4JMessageHandler;

public class AdaptersExample {
    public static void main(String[] args) {
       java.util.logging.Logger julLogger = getJulLogger();
       JULMessageHandler jul = new JULMessageHandler(julLogger);
       jul.info("JUL message");

       org.slf4j.Logger slf4jLogger = getSlf4jLogger();
       SLF4JMessageHandler slf4j = new SLF4JMessageHandler(slf4jLogger);
       slf4j.warn("SLF4J message");

       org.apache.logging.log4j.Logger log4jLogger = getLog4jLogger();
       Log4JMessageHandler log4j = new Log4JMessageHandler(log4jLogger);
       log4j.error("Log4J message");
    }
}
```

## Side note: MDC / ThreadContext (only for mixed logging stacks)

Most users can ignore this section.

If all logs go through `MessageHandler` implementations from this package, correlation is already
handled by `TracerProvider` and the tracer-bound `LogRecordFactory`.

Use backend thread-context (`MDC` for SLF4J, `ThreadContext` for Log4J) only when you intentionally
mix:
- handler-based logging from this package
- direct logger calls (`org.slf4j.Logger`, `org.apache.logging.log4j.Logger`, etc.)

At a request/task boundary, attach package correlation and mirror IDs into MDC/ThreadContext:

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class MixedLoggingExample {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixedLoggingExample.class);

    public void handle(TracerProvider provider, Tracer tracer, SpanContext incomingContext) {
        MessageHandler handler = tracer.getConsoleMessageHandler();
        try (TracerProvider.CorrelationScope ignored =
                     provider.attachCorrelation(incomingContext, tracer.getInstrumentationScope())) {
            if (incomingContext != null && incomingContext.isValid()) {
                MDC.put("traceId", incomingContext.getTraceId());
                MDC.put("spanId", incomingContext.getSpanId());
            }
            try {
                handler.info("message via MessageHandler");
                LOGGER.info("direct SLF4J message");
            } finally {
                MDC.remove("traceId");
                MDC.remove("spanId");
            }
        } finally {
            handler.close();
        }
    }
}
```

For async execution, continue using `provider.wrap(...)` or
`provider.contextAwareExecutorService(...)` for this package correlation propagation.
If worker threads also write direct SLF4J/Log4J logs, propagate MDC/ThreadContext there as well.

## Custom output format with `LogRecordFormatter`

You can customize the output by passing to a class that extends the `AbstractOutputMessageHandler` (Console or File)
an implementation of the `LogRecordFormatter` interface. By default, both of those classes use the
`ConsoleLogRecordFormatter`, although you could use the `RawJsonRecordFormatter`. This because even if you pass a
simple `String` to a `MessageHandler`, internally this package uses a representation of the
[OpenTelemetry LogRecord format](https://opentelemetry.io/docs/concepts/signals/logs/#log-record).
Thus, having a grasp of how it works, you can choose what to show. Of course, this
has greater effectiveness if you have instrumented your application properly with a `Tracer` (more on that later).

`JaegerMessageHandler` and `GrafanaMessageHandler` differ here: their default constructors already
emit JSON payloads (OTLP `ExportLogsServiceRequest` JSON; Grafana can also emit Loki push JSON
when targeting a Loki push endpoint).

For server deployments that ingest local console/file logs, formatter choice should be an
application bootstrap policy, not an ad hoc per-call-site decision. A practical baseline is:
centralized handler factory/bootstrap + fixed JSON formatter + schema/golden-output tests
to keep fields stable across modules and environments.

```java
import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;

public final class HandlerFactory {
    private static final LogRecordFormatter JSON_FORMATTER = new RawJsonRecordFormatter();

    private HandlerFactory() {
    }

    public static MessageHandler newConsoleHandler() {
        return new ConsoleMessageHandler(JSON_FORMATTER);
    }

    public static MessageHandler newFileHandler(String path) {
        return new FileMessageHandler(path, JSON_FORMATTER);
    }
}
```

A utility class is bundled that offers a static method to reduce a long class name by replacing the package name with
an abbreviation (initial letters only): the `ClassNameReducer.reduce`. E.g., it could replace
`com.threeamigos.common.utils.TestClass` with `c.t.c.u.TestClass`, a-la SpringBoot.

```java
import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;

public class CustomFormatterExample {
    public static void main(String[] args) {
        LogRecordFormatter formatter = logRecord -> {
            String severity = logRecord.getSeverityText();
            String body = logRecord.getBody() == null ? "" : String.valueOf(logRecord.getBody().asString());
            return logRecord.getTimestamp() + " | " + severity + " | " + body;
        };

        try (MessageHandler handler = new ConsoleMessageHandler(formatter)) {
            handler.info("Hello with custom format");
        }
    }
}
```

## Tracer

This part of the package tries to produce an output compliant with [OpenTelemetry](https://opentelemetry.io/) and helps
deal with systems composed by one or more parts (for example, a web application interacting with many microservices). 
For distributed ingress/egress, it also supports W3C Trace Context (`traceparent`, `tracestate`)
through `TraceContextValidator` (see the HTTP ingress section below).

In such an environment, one server could start a user request, but it could have to delegate something to
another server or microservice. In this case, logs could be produced in more than one server. Usually, all those logs
are ingested by a log aggregator, like ELK, Splunk, or Grafana Loki; and all those logs are bound via a `traceId`, 
created by the server that first handles the user request, and sent to all other components interacting with it.

When dealing with that request, each component is responsible for creating a `Span` for each operation it performs.
The `Span` is the basic unit of work in the tracing system. It represents a single operation or task that is being
performed by that component. Spans can be nested, forming a tree-like structure that represents the flow of operations
within a system.

### Example

```text
traceId=9f4a6a52c2d54f6a8d7b67e2a9c3f1b4
└── spanId=1c9b0f4d7a2e5b8c      service=checkout-web      (local system) operation=POST /checkout
    ├── spanId=6a2e9d1f4b7c3a50  service=payment-service   (remote)       operation=authorizePayment
    └── spanId=b7d3e14a9c2f6850  service=inventory-service (remote)       operation=reserveItems
```

You can then query a system like Grafana or Jaeger to visualize the trace and understand the flow of operations.

---

### Workflow

THe `TracerProvider` is the application-level factory/configuration point. You have to build one `TracerProvider` for
your service (`serviceName`, `serviceVersion`, environment, shared attributes), where the service might be a standalone 
application (in this case the `serviceName` is your application name), or a microservice. 

 A `Tracer` is scoped to an instrumentation name/version. Basically, it could be a library, or module, or a component of
your service. So for each module you want to instrument, you have to obtain a `Tracer` from that provider for the module
or component (`provider.getTracer(instrumentationName, version, ...)`). 

A `Span` represents one timed operation. Start a `Span` either with `tracer.createSpan(...)`, or directly from a
tracer-created handler with `handler.startSpan(...)`.
Once you have a parent span, you can create child spans directly from it with `parentSpan.create("child-name")`
(equivalent to `tracer.createSpan("child-name", parentSpan.getSpanContext())`).

Use a `MessageHandler` created from that `Tracer` (`tracer.getConsoleMessageHandler()`, `getFileMessageHandler(...)`, 
etc.) to log your messages.

End the span with `span.end()`.

This produces a series of logs that are correlated by a `traceId` (root operation) and one or more `spanId`s.

### Using multiple Tracers to track different parts of a system

If your goal is one output file and multiple tracers (for different modules/components), use:
1. one shared `TracerProvider`
2. multiple `Tracer` instances from that provider (`provider.getTracer(...)`)
3. one tracer-created file handler per tracer, targeting the same file path

For this setup, an OpenTelemetry Collector is not required.

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

public class MultipleTracersSingleFileExample {
    public static void main(String[] args) {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("checkout-service")
                .defaultFilePath("logs/system.log")
                .build();

        Tracer apiTracer = provider.getTracer("api-module", "1.0.0");
        Tracer dbTracer = provider.getTracer("db-module", "1.0.0");
        MessageHandler apiHandler = apiTracer.getFileMessageHandler(provider.getDefaultFilePath());
        MessageHandler dbHandler = dbTracer.getFileMessageHandler(provider.getDefaultFilePath());

        Span apiSpan = apiHandler.startSpan("api.request");
        try {
            apiHandler.info("API request started");
        } finally {
            apiSpan.end();
        }

        Span dbSpan = dbHandler.startSpan("db.query");
        try {
            dbHandler.info("DB query started");
        } finally {
            dbSpan.end();
        }

        apiHandler.close();
        dbHandler.close();
    }
}
```

Note: creating many file handlers for the same file can cause interleaved writes.

### How `MessageHandler`, `Tracer`, and `Span` are related

- A `MessageHandler` created by a `Tracer` is enriched with the tracer scope and provider metadata.
- The same tracer-created handler can start spans directly with `startSpan(name)`.
- A `Span` can produce child spans directly with `span.create(childName)`, without injecting `Tracer` at call sites.
- When a span is active on the current thread, emitted logs are automatically correlated with that span context
  (trace id + span id).
- The same log can also be appended as a span event (`"log"`) on the active span.
- If there is no active span, logging still works; it just has no trace/span correlation fields.

### Example 1: basic span and correlated logs

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

public class TracerWorkflowExample {
    public static void main(String[] args) {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("checkout-api")
                .serviceVersion("1.0.0")
                .deploymentEnvironment("prod")
                .build();

        Tracer tracer = provider.getTracer("checkout-api", "1.0.0");
        MessageHandler handler = tracer.getConsoleMessageHandler();

        Span span = handler.startSpan("checkout.request");
        try {
            handler.info("Checkout request started");
            handler.warn("Inventory service latency is increasing");
            span.setStatus(StatusCode.OK);
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, "checkout failed");
            throw ex;
        } finally {
            span.end();
            handler.close();
        }
    }
}
```

### Example 2: parent/child spans in the same flow

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

public class ParentChildSpanExample {
    public static void main(String[] args) {
        Tracer tracer = TracerProvider.builder()
                .serviceName("checkout-api")
                .build()
                .getTracer("checkout-api", "1.0.0");

        Span parent = tracer.createSpan("checkout");
        // child span from parent span
        Span child = parent.create("checkout.payment-authorize");

        child.addEvent("payment-provider-call");
        child.end();
        parent.end();
    }
}
```

## Class filtering with `FilterByClassName`

`FilterByClassName` is an OTel-log filter. Typical usage is through `Tracer` handlers:

```java
import com.threeamigos.common.util.implementations.messagehandler.filters.FilterByClassName;
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

public class FilterByClassNameExample {
    public static void main(String[] args) {
        FilterByClassName filter = new FilterByClassName();
        filter.add("com\\.example\\.critical\\..*", SeverityNumber.INFO);
        filter.add("com\\.example\\.noisy\\..*", SeverityNumber.ERROR);
        filter.prune("com\\.example\\.chatty\\..*");

        Tracer tracer = TracerProvider.builder()
                .serviceName("checkout-api")
                .build()
                .getTracer("checkout-api", filter);

        MessageHandler handler = tracer.getConsoleMessageHandler();
        handler.info("Filtered according to source class attributes");
    }
}
```

You can also load filter rules from properties via:
- `loadPropertiesFromFile(...)`
- `loadPropertiesFromResource(...)`

From the example, it is clear that the key part of the property is actually a Regex.

The value of the property indicates the minimum `SeverityNumber` the log record should have to be included in the log
output. To completely remove a class or package, use OFF instead of INFO n a property file, or use the `prune(...)`
method to disable it programmatically.

## Advanced OTel-like model in this package

The package includes an OTel-like model (`LogRecord`, `Span`, `SpanContext`, `Resource`, `InstrumentationScope`, etc.)
and a `TracerProvider`/`Tracer` API to produce enriched logs and spans.

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

public class TracerQuickStart {
    public static void main(String[] args) {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("checkout-api")
                .serviceVersion("1.0.0")
                .deploymentEnvironment("prod")
                .resourceAttribute(OTelTags.SERVICE_NAMESPACE, "payments")
                .commonAttribute("app.region", "eu-west-1")
                .build();

        Tracer tracer = provider.getTracer("checkout-api", "1.0.0");
        MessageHandler handler = tracer.getConsoleMessageHandler();
        handler.info("Structured/enriched message");
    }
}
```

### Validation mode: `OTEL_ERROR_HANDLER_LENIENT`

OTel attribute validation uses `OpenTelemetryAttributeValidator` with two modes:
- strict mode (default): invalid input throws `IllegalArgumentException`
- lenient mode: invalid input is reported and processing continues with safe fallbacks where possible

Set the environment variable before starting the JVM:

```bash
export OTEL_ERROR_HANDLER_LENIENT=true
```

The environment value is resolved once during class initialization. A runtime override is also
available through `OpenTelemetryAttributeValidator.setLenientModeOverride(...)` and
`OpenTelemetryAttributeValidator.clearLenientModeOverride()`.

## `OTelTags` and the Known Values subpackage

`OTelTags` is the central catalog of semantic-convention attribute keys. Use it when setting
resource or common attributes on `TracerProvider.builder()` to avoid typos and keep keys
aligned with OTel semantic conventions.

### Dynamic prefix tags

Some `OTelTags` constants are key prefixes that must be completed with a concrete segment:

```java
// container.label.<key>
String containerLabelKey = OTelTags.CONTAINER_LABELS.getValue() + "app"; // → "container.label.app"

// process.environment_variable.<key>
String envKey = OTelTags.PROCESS_ENV_VARIABLE.getValue() + "JAVA_HOME";
```

Kubernetes label, annotation, and selector prefixes follow the same pattern.

### Known values enums

Package `com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues` provides
strongly typed enums for tags that have an OTel-defined set of known values. All enums implement
the `OTelTagKnownValue` interface (`getTag()`, `getValue()`).

Available enums and their associated OTel tags:

| Enum | OTel tag |
|---|---|
| `TelemetrySdkLanguageKnownValuesEnum` | `telemetry.sdk.language` |
| `HostArchKnownValuesEnum` | `host.arch` |
| `CloudProviderKnownValuesEnum` | `cloud.provider` |
| `CloudPlatformKnownValuesEnum` | `cloud.platform` |
| `ProcessContextSwitchTypeKnownValuesEnum` | `process.context_switch.type` |
| `ProcessStateKnownValuesEnum` | `process.state` |
| `ServiceCriticalityKnownValuesEnum` | `service.criticality` |
| `K8sContainerStatusStateKnownValuesEnum` | `k8s.container.status.state` |
| `K8sNamespacePhaseKnownValuesEnum` | `k8s.namespace.phase` |
| `K8sNodeConditionStatusKnownValuesEnum` | `k8s.node.condition.status` |
| `K8sNodeConditionTypeKnownValuesEnum` | `k8s.node.condition.type` |
| `K8sPodStatusPhaseKnownValuesEnum` | `k8s.pod.status.phase` |
| `K8sServiceEndpointAddressTypeKnownValuesEnum` | `k8s.service.endpoint.address_type` |
| `K8sServiceEndpointConditionKnownValuesEnum` | `k8s.service.endpoint.condition` |
| `K8sServiceTrafficDistributionKnownValuesEnum` | `k8s.service.traffic_distribution` |
| `K8sServiceTypeKnownValuesEnum` | `k8s.service.type` |
| `K8sVolumeTypeKnownValuesEnum` | `k8s.volume.type` |

Usage examples:

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues.CloudPlatformKnownValuesEnum;
import com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues.CloudProviderKnownValuesEnum;
import com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues.ServiceCriticalityKnownValuesEnum;

public class OTelTagsExample {
    public static void main(String[] args) {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("checkout-api")
                .serviceVersion("1.4.2")
                .deploymentEnvironment("prod")
                // service criticality using a strongly typed enum
                .resourceAttribute(OTelTags.SERVICE_CRITICALITY,
                        ServiceCriticalityKnownValuesEnum.HIGH.getValue())
                // cloud provider + platform pairing
                .resourceAttribute(OTelTags.CLOUD_PROVIDER,
                        CloudProviderKnownValuesEnum.AWS.getValue())
                .resourceAttribute(OTelTags.CLOUD_PLATFORM,
                        CloudPlatformKnownValuesEnum.AWS_EKS.getValue())
                .build();
    }
}
```

## Log enrichment precedence

When a `LogRecord` is emitted through a tracer-aware handler, enrichment fields are applied
with the following precedence (higher number = lower priority):

1. Caller-provided values on the `LogRecord` are never overwritten.
2. Tracer-provided instrumentation scope and explicit span context are applied next.
3. Provider defaults (resource, common attributes, resolver) fill only missing values.

Specifically:
- `resource` is set only when the record does not already carry one.
- `instrumentationScope` is set only when absent.
- `traceId`, `spanId`, `traceFlags` are set only when the record's correlation fields are missing.
- Common attributes are appended only for keys not already present on the record.

## Jaeger/Grafana backend usage (logs vs traces)

A **trace** represents one end-to-end request flow.  
A **span** is one timed operation inside that trace (DB call, HTTP call, business step, etc.).

### Log export handlers

```java
import com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler;

public class BackendLogExportExample {
    public static void main(String[] args) {
        JaegerMessageHandler jaegerLogs = new JaegerMessageHandler("http://localhost:4318/v1/traces");
        jaegerLogs.info("log transformed into span event for Jaeger OTLP traces endpoint");

        GrafanaMessageHandler grafanaLogs = new GrafanaMessageHandler("http://localhost:3100/loki/api/v1/push");
        grafanaLogs.error("log to Grafana Loki push endpoint");

        jaegerLogs.close();
        grafanaLogs.close();
    }
}
```

If you are exporting to a shared OpenTelemetry Collector instead of directly to backends,
both handlers can target the same collector OTLP endpoint (commonly `http://localhost:4318/v1/logs`).

Transport behavior for both handlers (`AbstractHTTPOutputMessageHandler`):
- bounded retry/backoff is enabled by default (`maxRetries=1`, exponential backoff from `50 ms`, capped at `500 ms`);
- retryable failures include I/O exceptions and HTTP `408`, `429`, and `5xx`;
- failures are buffered and retried on subsequent emissions as a batch;
- for OTLP log endpoints and `ExportLogsServiceRequestLogRecordFormatter`, buffered retries and the
  current record are sent in one `ExportLogsServiceRequest` envelope;
- for Jaeger `/v1/traces` transform mode and Grafana Loki push mode, records are retried but sent
  with per-record backend semantics.

Important:
- `GrafanaMessageHandler` exports log records only (Loki push payload or OTLP logs payload depending on endpoint).
- Calling `startSpan(...)` / `span.end()` does not export traces by itself.
- Trace export happens only when a span dispatcher is configured on `TracerProvider`.

### Span export (traces) via dispatcher

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.implementations.messagehandler.utils.JaegerSpanDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

public class SpanExportExample {
    public static void main(String[] args) {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("checkout-api")
                .build();
        provider.setDefaultSpanDispatcher(new JaegerSpanDispatcher("http://localhost:4318/v1/traces"));

        Tracer tracer = provider.getTracer("checkout-api", "1.0.0");
        Span span = tracer.createSpan("checkout.validate");
        span.addEvent("validation-started");
        span.end(); // dispatches SpanData through configured SpanDispatcher
    }
}
```

For Grafana Tempo, use `GrafanaSpanDispatcher` similarly.

### Grafana Loki logs + Tempo traces (full example)

```java
import com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.implementations.messagehandler.utils.GrafanaSpanDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

public class GrafanaLogsAndTracesExample {
    public static void main(String[] args) {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("checkout-api")
                .serviceVersion("1.0.0")
                .build();

        // Required for trace export (Tempo OTLP traces endpoint)
        provider.setDefaultSpanDispatcher(new GrafanaSpanDispatcher("http://localhost:14318/v1/traces"));

        Tracer tracer = provider.getTracer("checkout-api", "1.0.0");
        GrafanaMessageHandler logs = tracer.getGrafanaMessageHandler("http://localhost:3100/loki/api/v1/push");

        Span span = logs.startSpan("checkout.request");
        try {
            logs.info("checkout request started");
            logs.info("checkout request completed");
        } finally {
            span.end(); // exported to Tempo because default span dispatcher is configured
            logs.close(); // log records exported to Loki
        }
    }
}
```

## Correlation mechanism (with examples)

Correlation is thread-local by design. For multi-thread execution, propagate correlation explicitly.

### Who needs this

Most users do not need to touch correlation APIs directly. If your usage is:
1. `TracerProvider.builder(...)` → `getTracer(...)` → `get*MessageHandler(...)` → `startSpan(...)`/`info/warn/error/...`

then correlation is handled automatically for same-thread execution. Use the APIs below only for:
- HTTP ingress context attachment (for example `traceparent`/`tracestate` from servlet filters)
- Async/thread-pool propagation
- Explicit request/task boundary correlation control

### Why manual propagation is needed

`TracerProvider` stores correlation (`SpanContext` and `InstrumentationScope`) in thread-local
state. This means correlation is available only on the current thread unless you propagate it
explicitly when work moves to another thread.

Typical failure pattern:
1. Main thread creates a span.
2. Main thread submits async work to a pool.
3. Worker thread logs, but no trace/span is attached — thread-local state was never propagated.

### Available correlation APIs

`TracerProvider` exposes:

| Method | Purpose |
|---|---|
| `attachCorrelation(SpanContext, InstrumentationScope)` | Attach context at a request/task boundary |
| `attachCorrelation(SpanContext)` | Shorthand when scope is unchanged/unneeded |
| `clearCorrelation()` | Remove current thread's correlation |
| `wrap(Runnable)` | Wrap a task so it inherits submitter correlation |
| `wrap(Callable<T>)` | Same for Callable |
| `contextAwareExecutor(Executor)` | Wrap an Executor so all tasks inherit submitter correlation |
| `contextAwareExecutorService(ExecutorService)` | Same for ExecutorService |

### Attach correlation at a request boundary

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;

public class CorrelationBoundaryExample {
    public void handle(TracerProvider provider, SpanContext incomingSpanContext) {
        InstrumentationScope scope = provider.getTracer("checkout-api", "1.0.0").getInstrumentationScope();
        try (TracerProvider.CorrelationScope ignored = provider.attachCorrelation(incomingSpanContext, scope)) {
            // Logs/spans created on this thread are correlated with incomingSpanContext.
        }
    }
}
```

### Propagate into thread pools

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CorrelationExecutorExample {
    public static void main(String[] args) throws Exception {
        TracerProvider provider = TracerProvider.builder().serviceName("checkout-api").build();
        ExecutorService rawPool = Executors.newFixedThreadPool(4);
        ExecutorService contextAwarePool = provider.contextAwareExecutorService(rawPool);

        contextAwarePool.submit(new Runnable() {
            @Override
            public void run() {
                // Correlation captured from submitter thread is active here.
            }
        }).get();

        contextAwarePool.shutdownNow();
    }
}
```

### Additional wrapping pattern (wrap individual tasks)

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WrapTaskExample {
    public static void main(String[] args) throws Exception {
        TracerProvider provider = TracerProvider.builder().serviceName("checkout-api").build();
        ExecutorService pool = Executors.newFixedThreadPool(4);

        pool.submit(provider.wrap(new Runnable() {
            @Override
            public void run() {
                // Correlation from submitter thread is active here
            }
        }));

        Future<String> result = pool.submit(provider.wrap(new Callable<String>() {
            @Override
            public String call() {
                // Correlation from submitter thread is active here
                return "ok";
            }
        }));

        pool.shutdownNow();
    }
}
```

Use `wrap(...)` for isolated tasks; use `contextAwareExecutorService(...)` when you want
all submissions on a shared pool to inherit submitter correlation automatically.

### Operational rules

1. Do not rely on raw `ThreadLocal` state in worker threads.
2. Do not use `InheritableThreadLocal` for thread pools: reused workers make it unsafe for request correlation.
3. Always restore previous context after execution (`CorrelationScope.close()` or wrappers handle this).
4. Prefer `contextAwareExecutorService(...)` for shared infrastructure and `wrap(...)` for one-off tasks.
5. Prefer provider-level APIs; avoid exposing correlation internals in normal application code.

### Troubleshooting

If logs in parallel tasks still miss trace/span IDs:
1. Verify tasks are submitted through provider wrappers or context-aware executors.
2. Verify the span is still active when tasks are submitted.
3. Verify worker code is not clearing correlation state prematurely.

## HTTP ingress: attaching incoming trace context (Servlet filter)

When a service receives an HTTP request, the upstream caller may send W3C trace context
headers (`traceparent`, `tracestate`). `TraceContextValidator` parses and normalizes these
headers; if they are missing or invalid it generates a fresh valid context automatically.

```java
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.implementations.messagehandler.tracecontext.TraceContextValidator;
import com.threeamigos.common.util.implementations.messagehandler.otel.SpanContextImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.TraceStateImpl;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

public final class TraceContextServletFilter implements javax.servlet.Filter {

    private final TracerProvider tracerProvider;

    public TraceContextServletFilter(TracerProvider tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    public void doFilter(javax.servlet.ServletRequest req,
                         javax.servlet.ServletResponse res,
                         javax.servlet.FilterChain chain)
            throws java.io.IOException, javax.servlet.ServletException {

        javax.servlet.http.HttpServletRequest http =
                (javax.servlet.http.HttpServletRequest) req;

        String traceparent = http.getHeader(TraceContextValidator.TRACEPARENT_HEADER);
        String tracestate  = http.getHeader(TraceContextValidator.TRACESTATE_HEADER);

        // Parses valid headers or generates a fresh context when headers are missing/invalid
        TraceContextValidator incoming =
                TraceContextValidator.fromIncomingHeaders(traceparent, tracestate);

        SpanContext remoteSpanContext = new SpanContextImpl(
                incoming.getTraceId(),
                incoming.getParentId(),
                incoming.getTraceFlagsByte(),
                true,           // remote = true
                new TraceStateImpl()
        );

        Tracer tracer = tracerProvider.getTracer("checkout-api", "1.0.0");
        req.setAttribute("mh.tracer", tracer);

        try (TracerProvider.CorrelationScope ignored =
                     tracerProvider.attachCorrelation(
                             remoteSpanContext, tracer.getInstrumentationScope())) {
            chain.doFilter(req, res);
        }
    }
}
```

Rules:
- Header extraction happens once per request (not per log call).
- Invalid or missing headers fall back to a generated valid context — no exception is thrown.
- If a log record already carries explicit trace fields, the enricher does not overwrite them.
- Correlation is thread-local: async thread hops still require explicit propagation (see previous section).

The downstream servlet can then use the tracer placed in the request attribute and log normally:

```java
public final class CheckoutServlet extends javax.servlet.http.HttpServlet {

    @Override
    protected void doPost(javax.servlet.http.HttpServletRequest req,
                          javax.servlet.http.HttpServletResponse resp)
            throws java.io.IOException {

        Tracer tracer = (Tracer) req.getAttribute("mh.tracer");
        com.threeamigos.common.util.interfaces.messagehandler.MessageHandler handler =
                tracer.getFileMessageHandler("message-handler.log");
        com.threeamigos.common.util.interfaces.messagehandler.otel.Span span =
                handler.startSpan("checkout.request");

        try {
            handler.info("checkout request received");
            handler.info("checkout completed");
            resp.setStatus(javax.servlet.http.HttpServletResponse.SC_OK);
        } catch (RuntimeException ex) {
            handler.exception("checkout failed", ex);
            resp.setStatus(javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            span.end();
            handler.close();
        }
    }
}
```

## Jakarta EE / WildFly CDI wiring

In a Jakarta EE container (WildFly, Payara, Open Liberty, etc.) with CDI enabled, wire
`TracerProvider`, `Tracer`, and `MessageHandler` as CDI producer beans:

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

@ApplicationScoped
public class TelemetryProducers {

    private final TracerProvider tracerProvider = TracerProvider.builder()
            .serviceName("checkout-api")
            .serviceVersion("1.4.2")
            .deploymentEnvironment("prod")
            .build();

    @Produces @ApplicationScoped
    public TracerProvider tracerProvider() { return tracerProvider; }

    @Produces @ApplicationScoped
    public Tracer tracer() { return tracerProvider.getTracer("checkout-api", "1.0.0"); }

    @Produces @ApplicationScoped
    public MessageHandler messageHandler(Tracer tracer) {
        return tracer.getFileMessageHandler("message-handler.log");
    }
}
```

Then inject and use normally in filters and servlets:

```java
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;

@WebServlet("/checkout")
public class CheckoutServlet extends HttpServlet {

    @Inject MessageHandler handler;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        Span span = handler.startSpan("checkout.request");
        try {
            handler.info("checkout request received");
            resp.setStatus(HttpServletResponse.SC_OK);
        } finally {
            span.end();
        }
    }
}
```

Note: CDI must be enabled for the deployment (add `beans.xml` to `WEB-INF` or `META-INF`).

## Important behavior notes

1. Null message strings are ignored (no-op).
2. Null message suppliers are ignored (no-op).
3. If a supplier returns null, the message is ignored (no-op).
4. A null severity is treated as `SeverityNumber.INFO`.
5. `MessageHandler.startSpan(name)` works only on tracer-created handlers.
6. Calling `startSpan(name)` on non-tracer handlers throws `IllegalStateException` (localized message).
7. Output handlers throw `IllegalStateException` if log methods are called after `close()`.
8. There is no `endSpan(...)` helper on handlers; callers close spans explicitly with `span.end()`.
9. Async dispatch is optional and available only in output handlers based on `AbstractOutputMessageHandler`:
   - `ConsoleMessageHandler`
   - `FileMessageHandler`
   - `JaegerMessageHandler`
   - `GrafanaMessageHandler`
   In bounded async mode, queue saturation follows `setQueueOverflowPolicy(...)`.
   Default is `CALLER_RUNS` (`queue full -> sync fallback`), with optional shedding policies
   (`DROP_NEWEST`, `DROP_OLDEST`, `BLOCK_WITH_TIMEOUT`). With `queueCapacity <= 0`,
   the queue is unbounded.
   For `ConsoleMessageHandler`, `FileMessageHandler`, `JaegerMessageHandler`, and
   `GrafanaMessageHandler`, async convenience constructors (without an explicit
   `registerShutdownHook` argument) register a JVM shutdown hook by default.
   If you choose a constructor with `registerShutdownHook=false`, call `close()` explicitly
   during application shutdown.
10. Other handlers are synchronous unless they implement their own threading model.
11. `GrafanaMessageHandler` exports logs only; configure `TracerProvider#setDefaultSpanDispatcher(...)`
    (for example with `GrafanaSpanDispatcher`) to export spans/traces.
12. `CompositeMessageHandler.close()` closes all currently registered delegates.
13. `SwingMessageHandler` dialogs are no-ops in headless environments; runtime UI invocation
    failures are reported through `InnerErrorMessageHandler` and are not propagated to logging callers.
14. `exception(...)` overloads treat null message/throwable inputs as no-op.
15. `FileMessageHandler` constructors without an explicit `RotationPolicy` use default size-based
    rotation (`FileMessageHandler.DEFAULT_SIZE_ROTATION_MAX_BYTES` = 10 MB). Use
    `new NoRotationPolicy()` to disable rotation explicitly.
16. `FileMessageHandler` inter-process locking is opt-in (`new FileMessageHandler(path, true)` or
    full constructor with `interProcessLocking=true`) and uses a sidecar `.lck` file.
17. `JaegerMessageHandler` and `GrafanaMessageHandler` use JSON payloads by default
    (`ExportLogsServiceRequestLogRecordFormatter`); `ConsoleMessageHandler` and
    `FileMessageHandler` default to `ConsoleLogRecordFormatter` (human-readable text).
18. For server deployments that ingest console/file logs, enforce formatter selection centrally
    (bootstrap/factory), keep a stable JSON schema, and verify it with parser or golden-output tests.
19. Output handlers (`ConsoleMessageHandler`, `FileMessageHandler`, `JaegerMessageHandler`,
    `GrafanaMessageHandler`) expose health metrics via `getHandlerHealthMetrics()` and a
    quick health status via `isHealthy()`.
20. `JaegerMessageHandler` and `GrafanaMessageHandler` do not propagate dispatch/transport exceptions
    back to logging callers; failures are reported through the configured error consumer and reflected
    in handler health metrics.
21. Internal logging-system failures are reported through `InnerErrorMessageHandler` (default:
    `System.err::println`). You can override this globally via
    `InnerErrorMessageHandler.setGlobalConsumer(...)`.
22. `JaegerMessageHandler` and `GrafanaMessageHandler` use a pluggable pending-record durability
    policy (`HttpDispatchDurabilityStore`): default is in-memory, with optional file/Redis
    implementations. Constructors that do not expose DLQ configuration use
    `System.err::println` for dead-letter reporting. This package targets small standalone
    applications; for server/high-throughput use, set durability policy and DLQ handling
    explicitly.
23. Output handlers also expose optional overload controls:
    queue overflow policy (`setQueueOverflowPolicy(...)`), token-bucket rate limiting
    (`setRateLimitPolicy(...)` + `setRateLimitBypassSeverity(...)`), and
    severity-bucket sampling (`setSeveritySamplingPolicy(...)`).

Default output format by handler:

| Handler | Default formatter / payload | Default output style |
|---|---|---|
| `ConsoleMessageHandler` | `ConsoleLogRecordFormatter` | Human-readable text |
| `FileMessageHandler` | `ConsoleLogRecordFormatter` | Human-readable text |
| `JaegerMessageHandler` | `ExportLogsServiceRequestLogRecordFormatter` | OTLP `ExportLogsServiceRequest` JSON |
| `GrafanaMessageHandler` | `ExportLogsServiceRequestLogRecordFormatter` (OTLP endpoints) / Loki push payload (Loki endpoints) | JSON |

## Java compatibility

All examples above are Java 1.8 compatible.

## Building and verification

#### Build commands

```bash
# Compile main sources only (skip tests)
mvn -q -DskipTests compile

# Run unit tests
mvn -q clean test

# Run full quality gate (tests + JaCoCo 100% coverage check)
mvn -q verify

# Install to local Maven repository, skipping tests and JaCoCo
mvn -DskipTests -Djacoco.skip=true install
```

#### Load-profile evidence report

The integration test `LoadProfileEvidenceReportIntegrationTest` runs a repeatable load profile
(`sustained`, `burst`, `outage-recovery`) and writes a Markdown report with measured results.

Default report path:
- `target/load-profile-report.md`

Report contents include:
- scenario throughput (`attempted msg/s`);
- caller-side latency (avg/max);
- queue saturation and overflow counters;
- rate-limit/sampling counters;
- retry counters;
- dispatcher success/failure totals.

```bash
# Generate default report at target/load-profile-report.md
mvn -q -Dtest=LoadProfileEvidenceReportIntegrationTest test

# Optional: custom report output path
mvn -q -Dtest=LoadProfileEvidenceReportIntegrationTest \
  -Dmessagehandler.load.report.path=target/custom-load-report.md test
```

#### Server operational envelope (baseline)

This section publishes an initial server envelope/SLO baseline from the latest generated report:
- report: `target/load-profile-report.md`
- generated at: `2026-05-24T01:28:44.631973Z`
- environment: Java `21.0.2`, `Mac OS X 26.5`
- test backend: synthetic in-test dispatcher (no external network dependency)
- test config: queue policy `DROP_OLDEST`, queue capacity `256`, retry `2` (`1..8 ms`),
  rate limit `200000/s` (burst `200000`), sampling `1.0` for all severities

Published limits for this exact profile:
- sustained throughput: **up to 800,000 attempted msg/s**
- burst throughput (1.5s profile): **up to 700,000 attempted msg/s**
- outage/recovery throughput (2.5s profile, first 1.0s outage): **up to 600,000 attempted msg/s**
- queue-drain SLO after producer stop: **<= 50 ms**
- retry-overhead SLO during outage profile (`retryAttempts / dispatchAttempts`): **<= 0.01%**
- caller-side max latency SLO in this profile: **<= 60 ms**

Observed latest-run values (for reference):
- sustained: `842879.62 msg/s`, drain `22 ms`, max caller latency `20.35 ms`
- burst: `753274.50 msg/s`, drain `25 ms`, max caller latency `3.63 ms`
- outage-recovery: `640851.96 msg/s`, drain `25 ms`, max caller latency `47.94 ms`,
  retry overhead `80 / 1602360 = 0.00499%`

Expected behavior outside envelope:
- overflow shedding increases (`queueOverflowDropOldestOperations` rises);
- rate limiting can shed messages (`rateLimitedOperations` rises);
- retry counters rise during transport-outage windows.

The project enforces 100% JaCoCo code coverage (`INSTRUCTION`, `BRANCH`, `LINE`) as part of
`mvn verify`. All tests use JUnit 5, Mockito, and Hamcrest.
