# common-utils-messagehander

Generic interface for message handling capabilities

Previously kept in common.utils

### Logging and message handling

In the com.threeamigos.common.util.interfaces.messagehandler package there are some functional interfaces
that can be used to handle info, warn, error, debug or trace messages, and exceptions.
Implementations for these classes include a console logger, an in-memory store (useful to run tests),
a popup dialog, and a forwarder used to route messages to one or more other handlers.
For each of these handlers, you can enable or disable a given level of messages. Thus, you can disable debug
or trace messages if you want to run your application in production mode. Or, using a CompositeMessageHandler,
you can forward a certain level of messages to a log file while sending other messages to the user via a popup window.

The common class for those handlers is the AbstractMessageHandler, from which you can enable or disable certain
message levels and derive your own handlers.

You can replace the handler with a custom one while keeping the rest of your code unchanged. Should not be too
difficult to e.g., implement a handler that sends messages to a Slack channel or to a Log4J appender.

Handlers accept messages or Suppliers of messages, which can be useful for lazy evaluation of messages.

## How to test

Run your tests with the AWT_TESTS environment variable set to true if you want to check the popup dialogs.
