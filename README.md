# common-util-messagehandler

Part of the common-util classes, designed to help when writing standalone Java applications.

This subpackage addresses the following needs:

### Logging and message handling

The `com.threeamigos.common.util.interfaces.messagehandler` package contains some `Handler`s
that can be used to deal with info, warn, error, fatal, debug, or trace messages, and `Throwable`s.

Implementations include a console logger, a rotable file, an in-memory store (useful to run tests),
a popup dialog, and a composite used to route messages to one or more other handlers.
Bridges for Log4J, SLF4J, and JUL are also provided.

For each of those, you can enable or disable any given level of messages. For example, you can disable debug
or trace messages if you want to run your application in production mode. Or, using the composite,
you can forward a certain level of messages to a log file while sending other messages to the user via a popup window.

The same package also exposes an [OpenTelemetry](https://opentelemetry.io)-like tracing model (`TracerProvider`,
`Tracer`, `Span`) and W3C Trace Context helpers (`TraceContextValidator`) for `traceparent`/`tracestate` ingestion and 
propagation, together with OTLP/HTTP handlers for Jaeger- and Grafana-compatible backends.

HTTP handlers (Jaeger/Grafana/OTel Collector) ship with built-in retry/backoff, circuit breaker,
opportunistic request batching, and a configurable worker pool (`setHttpWorkerPoolSize`).
For server/high-throughput deployments, also a non-volatile durability store should be configured
(`FileHttpDispatchDurabilityStore` or `RedisHttpDispatchDurabilityStore`) along with a dead-letter
consumer — the defaults (in-memory store, `System.err`) are not suitable for production. Note that
HTTP handlers are anyway provided for convenience and testing purposes only.

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
thus effectively producing it only when needed. In this way, useless calculation time for a complex `String` that
formats multiple parameters can be avoided.

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

## The `MessageHandler` interface

As these interfaces are quite a lot, a composite interface called `MessageHandler` is defined,
which collects all of them.

The `MessageHandler` exposes additional methods:
- `handleMessage(SeverityNumber, String)` to directly specify the severity level of a message;
- `startSpan(String)` to start a Span directly from the handler, when the handler is tracer-bound (more on that later).

### Important behavior notes

In order not to break the flow of the logging caller,

1. Null message strings are ignored (no-op).
2. Null message suppliers are ignored (no-op).
3. If a supplier returns null, the message is ignored (no-op).
4. If `supplier.get()` throws a runtime exception, it is trapped and reported; it is not propagated to the caller.
5. A null SeverityNumber is treated as `SeverityNumber.INFO`.
6. `exception(...)` overloads treat null message/throwable inputs as no-op.
7. Output handlers do not throw when log methods are called after `close()`: those dispatch attempts are dropped and 
   reported.

The following classes implement the `MessageHandler` interface out of the hood. The common class for those handlers is
the `AbstractMessageHandler`, from which you can derive your own handlers.

```
AbstractMessageHandler (basic implementation of MessageHandler)
├── AbstractOutputMessageHandler (may handle the message asynchronously)
│   ├── AbstractHTTPOutputMessageHandler
│   │   ├── JaegerMessageHandler (OTLP/HTTP log transport for Jaeger-compatible pipelines)
│   │   └── GrafanaMessageHandler (OTLP/HTTP log transport for Grafana-compatible pipelines)
│   ├── ConsoleMessageHandler (prints to the console)
│   └── FileMessageHandler (writes to a file)
├── SwingMessageHandler (JOptionPane dialogs)
├── CompositeMessageHandler (fan-out pattern)
├── InMemoryMessageHandler (capture for testing)
├── JULMessageHandler (java.util.logging bridge)
├── Log4JMessageHandler (Apache Log4j 2 bridge)
├── SLF4JMessageHandler (SLF4J bridge)
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
[OpenTelemetry](https://opentelemetry.io/docs/specs/otel/logs/data-model/#field-severitynumber).
For a practical approach, have a look at the [OpenTelemetry SeverityNumber example 
mappings](https://opentelemetry.io/docs/specs/otel/logs/data-model-appendix/#appendix-b-severitynumber-example-mappings).

Default enabled levels are `INFO*`, `WARN*`, `ERROR*`, `FATAL*`, while `DEBUG*` and `TRACE*` are disabled.

`AbstractMessageHandler` offers methods to enable or disable severity levels:
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
rotation with a threshold of 10 MB (`FileMessageHandler.DEFAULT_SIZE_ROTATION_MAX_BYTES`).

This package supports three rotation-policy options: size-based, daily, and an explicit no-rotation policy.
Constructors that accept a `RotationPolicy` expect a non-null value; use `new NoRotationPolicy()` when you want to
disable rotation.

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

### Disable rotation

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

For tracer-created file handlers (`Tracer#getFileMessageHandler(...)` overloads), sidecar locking
is enabled automatically.

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
internal handler failures and reported instead of being propagated to logging callers.

Very probably, when dealing with a standalone application, you might want to show info messages to the user while
writing debug and error information to a file. You can do this using the next `MessageHandler`.

## `CompositeMessageHandler`: fan-out to multiple outputs

Use it when one log call must go to multiple destinations. For example, to the console and to a file.

Level control can be configured through `CompositeMessageHandler.LevelControlMode`:
- `COMPOSITE_ONLY` (default): only the composite gates levels. **All delegates must have all
  severity levels enabled** — a delegate with a level disabled will silently drop messages at
  that level even when the composite has forwarded them.
- `PROPAGATE_TO_DELEGATES`: composite level changes are also propagated to level-aware delegates,
  keeping them in sync. Use this when delegates are configured independently but should follow the
  composite's decisions.
- `DELEGATE_ONLY`: each delegate owns its own level state; composite level mutators throw
  `UnsupportedOperationException`. Use this when each destination has a different filtering policy.

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

With some fine-tuning, you can have different output for the underlying handlers. For example, you could enable
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

## OpenTelemetry

This package tries to produce an output compliant with [OpenTelemetry](https://opentelemetry.io/) and to help dealing
with systems composed by one or more parts (for example, a web application interacting with many microservices).
For distributed ingress/egress, it also supports W3C Trace Context (`traceparent`, `tracestate`)
through `TraceContextValidator` (see the HTTP ingress section below).

In such an environment, one server could start a user request, but it could have to delegate something to
another server or microservice. In this case, logs will be produced in more than one server. Usually, all those logs
are ingested by a log aggregator, like ELK, Splunk, or Grafana Loki; and all those logs are bound via a `traceId`,
created by the server that first handles the user request, and sent to all other components interacting with it,
to correlate the various logs.

When dealing with such a request, each component is responsible for creating a `Span` for each operation it performs.
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

To achieve this, the package includes an OTel-like model (`LogRecord`, `Span`, `SpanContext`, `Resource`,
`InstrumentationScope`, etc.) and a `TracerProvider`/`Tracer` API to produce enriched logs and spans.
So, even if you pass a simple `String` to a `MessageHandler`, internally a representation of the
[OpenTelemetry LogRecord format](https://opentelemetry.io/docs/concepts/signals/logs/#log-record) is used.

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

### Important note

`MessageHandler.startSpan(name)` works only on tracer-created handlers.
Calling `startSpan(name)` on non-tracer handlers throws an `IllegalStateException`.

Use a `MessageHandler` created from that `Tracer` (`tracer.getConsoleMessageHandler()`, `getFileMessageHandler(...)`,
etc.) to log your messages.

There is no `endSpan(...)` on handlers; callers close spans explicitly with `span.end()`.

This produces a series of logs that are correlated by a `traceId` (root operation) and one or more `spanId`s.

### Using multiple Tracers to track different parts of a system

If your goal is one output file and multiple tracers (for different modules/components), use:
1. one shared `TracerProvider`
2. multiple `Tracer` instances from that provider (`provider.getTracer(...)`)
3. one tracer-created file handler per tracer, targeting the same file path

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

Note: creating many file handlers for the same file could cause interleaved writes, so the sidecar mechanism is
silently enabled.

### How `MessageHandler`, `Tracer`, and `Span` are related

- A `MessageHandler` created by a `Tracer` is enriched with the tracer scope and provider metadata.
- The same tracer-created handler can start spans directly with `startSpan(name)`.
- A `Span` can produce child spans directly with `span.create(childName)`, without injecting `Tracer` at call sites.
- When a span is active on the current thread, emitted logs are automatically correlated with that span context
  (trace id and span id).
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

## `OTelTags` and the Known Values subpackage

Other than the service name and version, OpenTelemetry supports a rich set of attributes which can be set in the log
record. For example, host name, cloud provider, and so on.

`OTelTags` is the central catalog of semantic-convention attribute keys. Use it when setting resource or common
attributes on `TracerProvider.builder()` to avoid typos and keep keys aligned with OTel semantic conventions.

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

### Validation mode: `OTEL_ERROR_HANDLER_LENIENT`

OTel attribute validation uses `OpenTelemetryAttributeValidator` with two modes:
- strict mode (default): invalid input throws `IllegalArgumentException`.
- lenient mode: invalid input is reported and processing continues with safe fallbacks where possible.

This is handled by the `OTEL_ERROR_HANDLER_LENIENT` environment variable. If `true` (case-insensitive), 
the validation will report invalid input and continue processing with safe fallbacks.

Recommended environment policy:
- production: set `OTEL_ERROR_HANDLER_LENIENT=true`
- development/test: keep strict mode (unset the variable or set `OTEL_ERROR_HANDLER_LENIENT=false`)

The environment value is resolved once during class initialization. A runtime override is also
available through `OpenTelemetryAttributeValidator.setLenientModeOverride(...)` and
`OpenTelemetryAttributeValidator.clearLenientModeOverride()`.

## Custom output format with `LogRecordFormatter`

You can customize the output by passing to a class that extends the `AbstractOutputMessageHandler` (Console or File)
an implementation of the `LogRecordFormatter` interface. By default, both of those classes use the
`ConsoleLogRecordFormatter`, although you could use the `RawJsonRecordFormatter`. Knowing what the
[OpenTelemetry LogRecord format](https://opentelemetry.io/docs/concepts/signals/logs/#log-record) holds, 
you can choose what to show. Of course, this has greater effectiveness if you have instrumented your application 
properly with a `Tracer`.

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
From the example, it is clear that the key part of the property is actually a Regex.

Its task is to filter logs produced from certain classes. It filters `LogRecord`s on SeverityNumber and function (or
class) name. Rules:
- if SeverityNumber was not included, the record is not filtered.
- if the function name (or the class name) was not included, or it is not present in the filter list, the record is not
  filtered.
- if the function (or class) name os mapped to `OFF` then every log from that function (or class) is filtered.
- if the SeverityNumber is greater or equal to the minimum SeverityNumber in the filter collection, the record is not
  filtered.
- Otherwise, the record is filtered.

You can also load filter rules from properties via:
- `loadPropertiesFromFile(...)`
- `loadPropertiesFromResource(...)`

Regex policy:
- invalid regex patterns are ignored (not fail-fast) and each invalid pattern is reported,
- valid rules in the same configuration are still applied.

## Jaeger/Grafana export handlers

Two `MessageHandler`s are provided which can send LogRecord to Jaeger and Grafana Loki: `JaegerMessageHandler` and
`GrafanaMessageHandler`. Those are experimental and should not be used in a production environment!

They use JSON payloads by default (`ExportLogsServiceRequestLogRecordFormatter`) that will be sent directly to an 
endpoint.

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
- failures are buffered and retried on later emissions as a batch (single-worker mode);
- for OTLP log endpoints and `ExportLogsServiceRequestLogRecordFormatter`, buffered retries and the
  current record are sent in one `ExportLogsServiceRequest` envelope;
- for Jaeger `/v1/traces` transform mode and Grafana Loki push mode, records are retried but sent
  with per-record backend semantics;
- throughput can be scaled with `setHttpWorkerPoolSize(N)` — see the
  [HTTP worker pool](#http-worker-pool-jaebergrafana-throughput-scaling) section.

Important:
- `GrafanaMessageHandler` exports log records only (Loki push payload or OTLP logs payload depending on endpoint).
- Calling `startSpan(...)` / `span.end()` does not export traces by itself.
- Trace export happens only when a span dispatcher is configured on `TracerProvider`. See next section.

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

### Collector fan-out dispatcher failure policy

When you chain multiple backend dispatchers with:
- `OTelCollectorDispatcher` (for logs)
- `OTelCollectorSpanDispatcher` (for spans)

the failure behavior is:
- all delegates are attempted (no short-circuit on first failure);
- delegate `IOException` and `RuntimeException` are both captured;
- after fan-out completes, one aggregated `IOException` is thrown;
- each delegate failure is attached as a suppressed cause (`ex.getSuppressed()`).

This keeps delivery attempts best-effort across all configured targets while preserving a checked
failure signal to the caller.

## HTTP transport layer (`HttpTransport`)

All HTTP dispatchers (`JaegerLogRecordDispatcher`, `GrafanaLogRecordDispatcher`, and span
dispatchers) delegate the low-level HTTP POST to a pluggable `HttpTransport` instance. Two
implementations are provided:

| Transport | Dependencies | Connection handling | When to use |
|---|---|---|---|
| `HttpUrlConnectionTransport` | None (JDK only) | One connection per call; JVM keep-alive cache may reuse sockets | Default; suitable for low-to-medium throughput |
| `ApacheHttpClientTransport` | `org.apache.httpcomponents:httpclient` (optional) | Shared `PoolingHttpClientConnectionManager`; persistent connections | Sustained high-throughput workloads; explicit pool sizing |

### Default: `HttpUrlConnectionTransport`

Used automatically when no transport is specified. Requires no additional dependencies:

```java
// Default — uses HttpUrlConnectionTransport implicitly
JaegerMessageHandler handler = new JaegerMessageHandler("http://localhost:4318/v1/logs");
```

### Connection pooling: `ApacheHttpClientTransport`

For workloads where per-call TCP handshake overhead is measurable, use `ApacheHttpClientTransport`.
It creates a single `PoolingHttpClientConnectionManager` (50 total / 10 per-route by default)
shared across all dispatch calls.

**Step 1** — add the optional dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
    <version>4.5.14</version>
</dependency>
```

**Step 2** — supply the transport to the dispatcher constructor:

```java
import com.threeamigos.common.util.implementations.messagehandler.transport.ApacheHttpClientTransport;
import com.threeamigos.common.util.implementations.messagehandler.utils.GrafanaLogRecordDispatcher;

...
ApacheHttpClientTransport transport = new ApacheHttpClientTransport();

GrafanaLogRecordDispatcher dispatcher = new GrafanaLogRecordDispatcher(
        "https://logs-prod.example.com/loki/api/v1/push",
        null, null, "bearer-token",
        5_000, 10_000, null,
        transport  // connection-pooled transport
);

// ... use dispatcher ...

transport.close();  // release pooled connections on shutdown
...
```

Custom pool sizing is available via the second constructor:

```java
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

...
PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
cm.setMaxTotal(100);
cm.setDefaultMaxPerRoute(20);

CloseableHttpClient customClient = HttpClients.custom()
        .setConnectionManager(cm)
        .build();

ApacheHttpClientTransport transport = new ApacheHttpClientTransport(customClient);
...
```

### Lifecycle note

`ApacheHttpClientTransport` implements `Closeable`. Call `transport.close()` at application
shutdown to release pooled sockets. `HttpUrlConnectionTransport` is stateless and does not need
explicit closing.

A single transport instance may be shared across multiple dispatcher instances.

## HTTP durability policies

`JaegerMessageHandler` and `GrafanaMessageHandler` accept a pluggable durability policy via
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

When dispatch fails with a permanent/non-retryable failure (for example, HTTP 400), entries are
routed to dead-letter reporting (`Consumer<String>`), so callers can redirect DLQ output to
console, file, or any custom sink. Retryable/transient I/O failures are instead reported through
the handler error-consumer path, and records remain in the durability store for later retries.

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

## HTTP worker pool (Jaeger/Grafana throughput scaling)

By default each HTTP handler dispatches one HTTP POST at a time (single worker). For RTT-bound
transports this limits throughput to roughly `1000 ms / backend-latency-ms` records per second.
Use `setHttpWorkerPoolSize(int)` to enable concurrent in-flight HTTP calls:

```java
JaegerMessageHandler handler = new JaegerMessageHandler("http://localhost:4318/v1/logs");
handler.setHttpWorkerPoolSize(4);   // up to 4 concurrent HTTP POSTs
```

The pool size can also be read back:

```java
int size = handler.getHttpWorkerPoolSize();   // 1 (default) or the configured value
```

**Behavior when `poolSize > 1`:**
- Each log record is dispatched individually by a dedicated pool thread; no batching across workers.
- The durability-store catch-up batch (retrieve-all-pending on startup) is disabled to prevent
  duplicate dispatch between concurrent workers.
- `close()` waits up to 5 seconds for in-flight HTTP calls to complete before closing the
  durability store, so no records are left orphaned on normal shutdown.

**When to keep `poolSize = 1` (the default):**
- You rely on crash-recovery batching — records pending before a JVM restart are re-dispatched as
  a batch on the next startup only when a single worker is active.
- You prefer the simpler, lower-resource profile for low-throughput workloads.

The underlying queue/overload controls (rate limiting, sampling, queue overflow policy) remain
active in both modes; the worker pool only parallelizes the outbound HTTP leg.

---

## Global internal error sink (`InnerErrorMessageHandler`)

`InnerErrorMessageHandler` is the package-level safety sink for failures that happen
while the logging infrastructure is processing a log call.

Default global consumer:
- `System.err::println`

Typical events reported to this sink include:
- backend dispatch/runtime failures in handlers;
- runtime failures thrown by `Supplier<String>` message producers;
- failures in user-provided extension points (for example, custom error consumers,
  custom delegates in `CompositeMessageHandler`, or custom `RotationPolicy` code);
- runtime failures in handler internals that are trapped to preserve caller flow.

Behavior contract:
- null messages are ignored;
- runtime exceptions thrown by a custom global consumer trigger a fallback to default `System.err`;
- `consume(localConsumer, message)` tries the local consumer first, then falls back to the global sink
  if the local consumer is null or throws a runtime exception.

Configure the global consumer once at bootstrap:

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

Operational guidance:
- keep the global consumer lightweight and non-blocking;
- avoid routing this consumer back into the same logging pipeline to prevent recursive error loops;
- use `resetGlobalConsumer()` mainly in tests or controlled bootstrap reconfiguration.

## Internal error handling model (exception boundary)

This library uses a clear boundary between setup-time failures and runtime internal failures.

Setup/bootstrap failures are fail-fast and can throw:
- invalid constructor/configuration arguments;
- endpoint/auth/timeout/durability initialization errors;
- strict trace-context validation failures;
- strict resource-bundle validation failures (`validateRequiredKeysStrict(...)`).

Runtime logging/tracing paths are best-effort. Internal failures are generally trapped and
reported through `InnerErrorMessageHandler` (and, for HTTP handlers, also through configured
error/DLQ consumers), so caller code can continue.

Runtime behavior highlights:
- `Supplier<String>` failures are trapped and reported (not propagated to log callers);
- dispatch attempts after `close()` are dropped and reported;
- `FileMessageHandler`, `SwingMessageHandler`, and HTTP handler runtime dispatch failures are reported instead of breaking caller flow;
- `OTelCollectorDispatcher` and `OTelCollectorSpanDispatcher` invoke all delegates, aggregate delegate
  `IOException` + `RuntimeException` failures, and rethrow one `IOException` with suppressed causes.

Fatal JVM conditions are not masked:
- `CompositeMessageHandler` catches `RuntimeException` from delegates but does not swallow `Error` subclasses.

---

### Resource bundle reliability (startup vs runtime)

To avoid hard failures caused by missing localization resources:
- use runtime-safe accessors in normal execution paths: `MessageHandlerResourceBundle.get(...)`,
 `MessageHandlerResourceBundle.format(...)`, `MessageHandlerResourceBundle.getOrDefault(...)`,
 `MessageHandlerResourceBundle.formatOrDefault(...)`
- these methods fallback instead of throwing when the bundle or key is missing.

For deployment correctness, add one strict startup validation step and fail fast before serving traffic:

```java
import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;

public class BootstrapValidationExample {
    public static void main(String[] args) {
        MessageHandlerResourceBundle.validateRequiredKeysStrict(
                "handlerIsClosed",
                "nullFormatterProvided",
                "logRecordMustNotBeNull"
        );
        // start application
    }
}
```

If `validateRequiredKeysStrict(...)` throws `MissingResourceException`, treat it as a setup/packaging issue (not a recoverable runtime event).

---

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

## Important behavior notes

1. Async dispatch is optional and available only in output handlers based on `AbstractOutputMessageHandler`:
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
   **Message-loss guarantee (async mode only):** `close()` drains the queue in two passes
   (worker `finally` drain + caller-thread drain) so tasks already in the queue when `close()` is
   called are executed before the handler shuts down. Tasks submitted *concurrently with* or *after*
   `close()` are dropped and reported through `InnerErrorMessageHandler`. Synchronous handlers have no queue;
   `close()` simply seals the handler and releases the output resource immediately.
2. Other handlers are synchronous unless they implement their own threading model.
3. `JaegerMessageHandler` and `GrafanaMessageHandler` do not propagate dispatch/transport exceptions
    back to logging callers; failures are reported through the configured error consumer and reflected
    in handler health metrics.
4. Output handlers also expose optional overload controls:
    queue overflow policy (`setQueueOverflowPolicy(...)`), token-bucket rate limiting
    (`setRateLimitPolicy(...)` + `setRateLimitBypassSeverity(...)`), and
    severity-bucket sampling (`setSeveritySamplingPolicy(...)`).
5. HTTP handlers (`JaegerMessageHandler`, `GrafanaMessageHandler`) expose
    `setHttpWorkerPoolSize(int)` to run multiple concurrent HTTP dispatch threads. Default is
    `1` (single worker, backward-compatible). When `poolSize > 1`, each record is dispatched
    individually; crash-recovery batching (retrieve-all-pending on restart) is disabled in pool
    mode to prevent duplicate dispatch.
6. Collector fan-out dispatchers (`OTelCollectorDispatcher`, `OTelCollectorSpanDispatcher`) always
    attempt every delegate; delegate `IOException` and `RuntimeException` are aggregated and
    rethrown as one `IOException` with suppressed causes.

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
