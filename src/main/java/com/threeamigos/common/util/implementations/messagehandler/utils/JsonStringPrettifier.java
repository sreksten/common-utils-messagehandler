package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import jakarta.annotation.Nonnull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Pretty-prints JSON text using spaces.
 *
 * @author Stefano Reksten
 */
public class JsonStringPrettifier {

    private static final int DEFAULT_INDENT_SIZE = 2;
    private static final String INVALID_JSON_PREFIX = "Invalid JSON: ";

    private final int indentSize;

    /**
     * Builds a prettifier that indents using 2 spaces.
     */
    public JsonStringPrettifier() {
        this(DEFAULT_INDENT_SIZE);
    }

    /**
     * Builds a prettifier with a custom number of spaces per level.
     *
     * @param indentSize the number of spaces used for each indentation level. Must be greater than zero.
     */
    public JsonStringPrettifier(final int indentSize) {
        if (indentSize <= 0) {
            throw new IllegalArgumentException("indentSize must be greater than zero.");
        }
        this.indentSize = indentSize;
    }

    /**
     * Formats compact or noisy JSON into a readable form.
     *
     * @param json JSON text to format.
     * @return prettified JSON text.
     */
    @Nonnull public String prettify(@Nonnull final String json) {
        Objects.requireNonNull(json, MessageHandlerResourceBundle.get("valueMustNotBeNull"));

        final StringBuilder output = new StringBuilder(json.length() + 32);
        final Deque<ContainerState> openStack = new ArrayDeque<>();

        boolean inString = false;
        boolean escaping = false;

        for (int i = 0; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (inString) {
                output.append(c);
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (Character.isWhitespace(c)) {
                continue;
            }

            switch (c) {
                case '"':
                    beginElementIfNeeded(output, openStack);
                    inString = true;
                    output.append(c);
                    break;
                case '{':
                case '[':
                    beginElementIfNeeded(output, openStack);
                    output.append(c);
                    openStack.push(new ContainerState(c));
                    break;
                case '}':
                case ']':
                    appendCloseToken(output, openStack, c);
                    break;
                case ',':
                    if (openStack.isEmpty()) {
                        throw new IllegalArgumentException(INVALID_JSON_PREFIX + "unexpected comma.");
                    }
                    output.append(c).append('\n');
                    appendIndent(output, openStack.size());
                    break;
                case ':':
                    if (openStack.isEmpty() || openStack.peek().openToken != '{') {
                        throw new IllegalArgumentException(INVALID_JSON_PREFIX + "unexpected colon.");
                    }
                    output.append(c).append(' ');
                    break;
                default:
                    beginElementIfNeeded(output, openStack);
                    output.append(c);
            }
        }

        if (output.length() == 0) {
            throw new IllegalArgumentException(INVALID_JSON_PREFIX + "input is blank.");
        }
        if (inString) {
            throw new IllegalArgumentException(INVALID_JSON_PREFIX + "unterminated string.");
        }
        if (!openStack.isEmpty()) {
            throw new IllegalArgumentException(INVALID_JSON_PREFIX + "unclosed object or array.");
        }

        return output.toString();
    }

    private void appendCloseToken(final StringBuilder output, final Deque<ContainerState> openStack, final char closeToken) {
        if (openStack.isEmpty()) {
            throw new IllegalArgumentException(INVALID_JSON_PREFIX
                    + "unexpected closing token '" + closeToken + "'.");
        }

        final ContainerState state = openStack.pop();
        if (state.openToken != matchingOpen(closeToken)) {
            throw new IllegalArgumentException(INVALID_JSON_PREFIX
                    + "mismatched closing token '" + closeToken + "'.");
        }

        if (state.hasElements) {
            output.append('\n');
            appendIndent(output, openStack.size());
        }
        output.append(closeToken);
    }

    private void beginElementIfNeeded(final StringBuilder output, final Deque<ContainerState> openStack) {
        if (openStack.isEmpty()) {
            return;
        }
        final ContainerState current = openStack.peek();
        if (!current.hasElements) {
            current.hasElements = true;
            output.append('\n');
            appendIndent(output, openStack.size());
        }
    }

    private void appendIndent(final StringBuilder output, final int depth) {
        final int spaces = depth * indentSize;
        for (int i = 0; i < spaces; i++) {
            output.append(' ');
        }
    }

    private static char matchingOpen(final char closeToken) {
        return closeToken == '}' ? '{' : '[';
    }

    private static final class ContainerState {
        private final char openToken;
        private boolean hasElements;

        private ContainerState(final char openToken) {
            this.openToken = openToken;
            this.hasElements = false;
        }
    }
}
