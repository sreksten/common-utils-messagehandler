# Using MessageHandler with CDI 4.1

`MessageHandler` supports a `ContextInfo` parameter on every logging method, making it a natural fit for CDI environments. An interceptor can populate the context with request- or session-scoped data before any log call, and the formatters (`PlainTextLogFormatter`, `JsonLogFormatter`) will automatically include that data in the output.

---

## Core idea

Make `ContextInfo` a `@RequestScoped` CDI bean, inject it both into an interceptor (to populate) and into your call sites (to pass to `MessageHandler`):

```java
// Extend ContextInfoImpl with a CDI scope
@RequestScoped
public class RequestContextInfo extends ContextInfoImpl {
    // nothing extra needed
}
```

```java
// Custom interceptor binding annotation
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface Logged {}
```

```java
@Logged
@Interceptor
public class LoggingInterceptor {

    @Inject RequestContextInfo contextInfo;
    @Inject HttpServletRequest  request;

    @AroundInvoke
    public Object enrich(InvocationContext ic) throws Exception {
        contextInfo.add(ContextInfo.CLASS_NAME, ic.getTarget().getClass().getName());
        contextInfo.add("requestId",  request.getHeader("X-Request-Id"));
        String sessionId = Optional.ofNullable(request.getSession(false))
                                   .map(HttpSession::getId).orElse(null);
        contextInfo.add("sessionId", sessionId);  // null removes the key — safe
        return ic.proceed();
    }
}
```

```java
@ApplicationScoped
@Logged
public class MyService {

    @Inject MessageHandler     messageHandler;
    @Inject RequestContextInfo contextInfo;

    public void doSomething() {
        messageHandler.info("Processing request", contextInfo);
        // Output (PlainTextLogFormatter, abbreviation enabled):
        // [2026-04-18T...] [c.e.MyService] [INFO ] Processing request {requestId=abc, sessionId=xyz}
    }
}
```

---

## What works well

- **`ContextInfo.CLASS_NAME`** is populated naturally via `ic.getTarget().getClass().getName()` —
  the formatter's class name promotion kicks in automatically (bracketed segment between
  timestamp and level label).
- **`@RequestScoped`** gives each request its own isolated `ContextInfo` instance with no
  thread-safety concerns.
- **Null-as-remove** in `ContextInfoImpl.add()` means you can safely call
  `contextInfo.add("sessionId", null)` when there is no session — the key is simply omitted
  from the output.
- **`PlainTextLogFormatter`** and **`JsonLogFormatter`** natively consume `ContextInfo`, so
  `ConsoleMessageHandler` and `FileMessageHandler` benefit immediately with no extra wiring.

---

## Important limitations

### SLF4J / Log4J / JUL bridges discard `ContextInfo`

Those bridge implementations call `logger.info(message)` and ignore the `ContextInfo` parameter.
Context enrichment will not flow to the underlying framework.

**Option 1** — Use `FileMessageHandler` or `ConsoleMessageHandler` with `JsonLogFormatter` for
structured output. The full stack already works.

**Option 2** — If you must stay on SLF4J/Logback, extend the bridge to also populate MDC:

```java
@Override
protected void handleInfoMessageImpl(String message, ContextInfo contextInfo) {
    contextInfo.getValues().forEach((k, v) -> MDC.put(k, v.toString()));
    try {
        logger.info(message);
    } finally {
        contextInfo.getValues().keySet().forEach(MDC::remove);
    }
}
```

### Async methods

`@Asynchronous` methods and reactive pipelines do not inherit the CDI request context by default.
Use `@ActivateRequestContext` on the async boundary, or copy the `ContextInfo` values explicitly
before dispatching to another thread.

---

## Feature matrix

| Feature | Works out of the box? |
|---|---|
| Class name in output | ✅ via `CLASS_NAME` + interceptor |
| Request / session data in output | ✅ via `@RequestScoped` + interceptor |
| `FileMessageHandler` / `ConsoleMessageHandler` | ✅ full support |
| `SLF4JMessageHandler` / `Log4JMessageHandler` | ⚠️ needs MDC bridging |
| Thread-safety | ✅ `ConcurrentHashMap` + CDI scoping |
| Async context propagation | ⚠️ needs explicit handling |
