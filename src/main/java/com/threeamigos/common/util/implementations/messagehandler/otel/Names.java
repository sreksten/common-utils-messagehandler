package com.threeamigos.common.util.implementations.messagehandler.otel;

/**
 * Known resource and attribute names referenced in OpenTelemetry Logs Data Model
 * Appendix A example mappings.
 * <p>
 * Source:
 * https://opentelemetry.io/docs/specs/otel/logs/data-model-appendix/
 *
 * @author Stefano Reksten
 */
public enum Names {

    // ---------------------------------------------------------------------
    // RFC5424 Syslog
    // ---------------------------------------------------------------------
    ATTR_SYSLOG_FACILITY("syslog.facility"),
    ATTR_SYSLOG_VERSION("syslog.version"),
    RES_HOST_NAME("host.name"),
    RES_SERVICE_NAME("service.name"),
    ATTR_SYSLOG_PROCID("syslog.procid"),
    ATTR_SYSLOG_MSGID("syslog.msgid"),
    RES_SERVICE_VERSION("service.version"),
    ATTR_CLIENT_ADDRESS("client.address"),

    // ---------------------------------------------------------------------
    // Windows Event Log
    // ---------------------------------------------------------------------
    ATTR_WINLOG_EVENT_ID("winlog.event_id"),

    // ---------------------------------------------------------------------
    // SignalFx Events
    // ---------------------------------------------------------------------
    ATTR_COM_SPLUNK_SIGNALFX_EVENT_TYPE("com.splunk.signalfx.event_type"),
    ATTR_COM_SPLUNK_SIGNALFX_EVENT_CATEGORY("com.splunk.signalfx.event_category"),

    // ---------------------------------------------------------------------
    // Splunk HEC
    // ---------------------------------------------------------------------
    RES_COM_SPLUNK_SOURCE("com.splunk.source"),
    RES_COM_SPLUNK_SOURCETYPE("com.splunk.sourcetype"),
    ATTR_COM_SPLUNK_INDEX("com.splunk.index"),

    // ---------------------------------------------------------------------
    // Log4j
    // ---------------------------------------------------------------------
    // No additional explicit Appendix A key names.

    // ---------------------------------------------------------------------
    // Zap
    // ---------------------------------------------------------------------
    // No additional explicit Appendix A key names.

    // ---------------------------------------------------------------------
    // Apache HTTP Server access log
    // ---------------------------------------------------------------------
    ATTR_NETWORK_PEER_ADDRESS("network.peer.address"),
    ATTR_NETWORK_LOCAL_ADDRESS("network.local.address"),
    ATTR_HTTP_REQUEST_METHOD("http.request.method"),
    ATTR_URL_FULL("url.full"),
    ATTR_HTTP_RESPONSE_STATUS_CODE("http.response.status_code"),

    // ---------------------------------------------------------------------
    // CloudTrail Log Event
    // ---------------------------------------------------------------------
    RES_CLOUD_REGION("cloud.region"),
    ATTR_CLOUDTRAIL_ERROR_CODE("cloudtrail.error_code"),

    // ---------------------------------------------------------------------
    // Google Cloud Logging
    // ---------------------------------------------------------------------
    ATTR_GCP_LOG_NAME("gcp.log_name"),
    ATTR_GCP_HTTP_REQUEST("gcp.http_request"),

    // ---------------------------------------------------------------------
    // Elastic Common Schema
    // ---------------------------------------------------------------------
    RES_AGENT_EPHEMERAL_ID("agent.ephemeral_id"),
    RES_AGENT_ID("agent.id"),
    RES_TELEMETRY_SDK_NAME("telemetry.sdk.name"),
    RES_TELEMETRY_SDK_LANGUAGE("telemetry.sdk.language"),
    RES_TELEMETRY_SDK_VERSION("telemetry.sdk.version"),
    RES_CLOUD_ACCOUNT_ID("cloud.account.id"),
    RES_CLOUD_ZONE("cloud.zone"),
    RES_CLOUD_INSTANCE_ID("cloud.instance.id"),
    RES_CLOUD_INSTANCE_NAME("cloud.instance.name"),
    RES_CLOUD_MACHINE_TYPE("cloud.machine.type"),
    RES_CLOUD_PROVIDER("cloud.provider"),
    RES_HOST_IMAGE_NAME("host.image.name"),
    RES_CONTAINER_ID("container.id"),
    RES_CONTAINER_IMAGE_NAME("container.image.name"),
    RES_CONTAINER_IMAGE_TAG("container.image.tag"),
    RES_CONTAINER_NAME("container.name"),
    RES_CONTAINER_RUNTIME("container.runtime"),
    ATTR_DESTINATION_ADDRESS("destination.address"),
    ATTR_ERROR_CODE("error.code"),
    ATTR_ERROR_ID("error.id"),
    ATTR_ERROR_MESSAGE("error.message"),
    ATTR_ERROR_STACK_TRACE("error.stack_trace"),
    RES_HOST_ARCHITECTURE("host.architecture"),
    RES_HOST_DOMAIN("host.domain"),
    RES_HOST_ID("host.id"),
    RES_HOST_IP("host.ip"),
    RES_HOST_MAC("host.mac"),
    RES_HOST_TYPE("host.type"),
    RES_SERVICE_EPHEMERAL_ID("service.ephemeral_id"),
    RES_SERVICE_ID("service.id"),
    RES_SERVICE_INSTANCE_ID("service.instance.id"),
    ATTR_SERVICE_STATE("service.state"),
    RES_SERVICE_TYPE("service.type");

    private final String value;

    Names(final String value) {
        this.value = value;
    }

    /**
     * @return the raw resource/attribute name from the OpenTelemetry mapping.
     */
    public String getValue() {
        return value;
    }
}
