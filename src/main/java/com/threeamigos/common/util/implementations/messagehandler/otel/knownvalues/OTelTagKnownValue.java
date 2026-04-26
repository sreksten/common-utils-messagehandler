package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Contract for enums that model well-known values of a specific {@link OTelTags} attribute.
 */
public interface OTelTagKnownValue {

    /**
     * @return associated OTel tag.
     */
    OTelTags getTag();

    /**
     * @return raw semantic-convention value.
     */
    String getValue();
}

