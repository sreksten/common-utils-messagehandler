package com.threeamigos.common.util.implementations.messagehandler.otel;

/**
 * Known resource and attribute names referenced in OpenTelemetry Logs Data Model,
 * taken from
 * <a href="https://opentelemetry.io/docs/specs/otel/semantic-conventions/">Semantic Conventions</a>
 * and
 * <a href="https://opentelemetry.io/docs/specs/otel/logs/data-model-appendix/">Data Model Appendix</a>
 * <p>
 * The complete list can be found at
 * <a href="https://opentelemetry.io/docs/specs/semconv/registry/attributes/">OpenTelemetry Attribute Registry</a>
 *
 * @author Stefano Reksten
 */
public enum OTelTags {

    // ---------------------------------------------------------------------
    // Semantic Conventions (Reserved)
    // ---------------------------------------------------------------------
    ERROR_TYPE("error.type"),
    EXCEPTION_MESSAGE("exception.message"),
    EXCEPTION_STACKTRACE("exception.stacktrace"),
    EXCEPTION_TYPE("exception.type"),
    SERVER_ADDRESS("server.address"),
    SERVER_PORT("server.port"),
    SERVICE_NAME("service.name"),
    SERVICE_INSTANCE_ID("service.instance.id"),
    TELEMETRY_SDK_LANGUAGE("telemetry.sdk.language"),
    TELEMETRY_SDK_NAME("telemetry.sdk.name"),
    TELEMETRY_SDK_VERSION("telemetry.sdk.version"),
    URL_SCHEME("url.scheme"),

    EVENT_EXCEPTION("exception"),

    // ---------------------------------------------------------------------
    // Deployment
    // ---------------------------------------------------------------------
    DEPLOYMENT_ENVIRONMENT_NAME("deployment.environment.name"),

    // ---------------------------------------------------------------------
    // Host
    // ---------------------------------------------------------------------
    /**
     * The CPU architecture the host system is running on.
     * E.g., amd64; arm32; arm64; ia64; ppc32; ppc64; s390x; x86
     */
    HOST_ARCH("host.arch"),
    /**
     * Unique host ID. For Cloud, this must be the instance_id assigned by the cloud provider.
     * For non-containerized systems, this should be the machine-id. See
     * <a href="https://opentelemetry.io/docs/specs/semconv/resource/host/">Host</a>
     */
    HOST_ID("host.id"),
    /**
     * VM image ID or host OS image ID. For Cloud, this value is from the provider.
     */
    HOST_IMAGE_ID("host.image.id"),
    /**
     * Name of the VM image or OS install the host was instantiated from.
     */
    HOST_IMAGE_NAME("host.image.name"),
    /**
     * The version string of the VM image or host OS as defined in Version Attributes.
     */
    HOST_IMAGE_VERSION("host.image.version"),
    /**
     * Name of the host. On Unix systems, it may contain what the hostname command returns, or the fully
     * qualified hostname, or another name specified by the user.
     */
    HOST_NAME("host.name"),
    /**
     * Type of host. For Cloud, this must be the machine type.
     */
    HOST_TYPE("host.type"),
    /**
     * Available IP addresses of the host, excluding loopback interfaces.
     */
    HOST_IP("host.ip"),
    /**
     * Available MAC addresses of the host, excluding loopback interfaces.
     */
    HOST_MAC("host.mac"),
    /**
     * The amount of level 2 memory cache available to the processor (in Bytes).
     */
    HOST_CPU_CACHE_L2_SIZE("host.cpu.cache.l2.size"),
    /**
     * Family or generation of the CPU.
     */
    HOST_CPU_FAMILY("host.cpu.family"),
    /**
     * Model identifier. It provides more granular information about the CPU, distinguishing it
     * from other CPUs within the same family.
     */
    HOST_CPU_MODEL_ID("host.cpu.model.id"),
    /**
     * Model designation of the processor.
     */
    HOST_CPU_MODEL_NAME("host.cpu.model.name"),
    /**
     * Stepping or core revisions.
     */
    HOST_CPU_STEPPING("host.cpu.stepping"),
    /**
     * Processor manufacturer identifier. A maximum 12-character string.
     */
    HOST_CPU_VENDOR_ID("host.cpu.vendor.id"),

    // ---------------------------------------------------------------------
    // Container
    // ---------------------------------------------------------------------
    CONTAINER_COMMAND("container.command"),
    CONTAINER_COMMAND_ARGS("container.command_args"),
    CONTAINER_COMMAND_LINE("container.command_line"),
    CONTAINER_CSI_PLUGIN_NAME("container.csi.plugin.name"),
    CONTAINER_CSI_VOLUME_ID("container.csi.volume.id"),
    /**
     * Container ID. Usually a UUID, as for example used to identify Docker containers.
     * The UUID might be abbreviated.
     */
    CONTAINER_ID("container.id"),
    CONTAINER_IMAGE_ID("container.image.id"),
    CONTAINER_IMAGE_NAME("container.image.name"),
    CONTAINER_IMAGE_REPO_DIGESTS("container.image.repo_digests"),
    CONTAINER_IMAGE_TAGS("container.image.tags"),
    // ???
    CONTAINER_LABELS("container.label."),
    CONTAINER_NAME("container.name"),
    CONTAINER_RUNTIME_DESCRIPTION("container.runtime.description"),
    CONTAINER_RUNTIME_NAME("container.runtime.name"),
    CONTAINER_RUNTIME_VERSION("container.runtime.version"),

    // ---------------------------------------------------------------------
    // Process
    // ---------------------------------------------------------------------
    PROCESS_ARGS_COUNT("process.args_count"),
    PROCESS_COMMAND("process.command"),
    PROCESS_COMMAND_ARGS("process.command_args"),
    PROCESS_COMMAND_LINE("process.command_line"),
    PROCESS_CONTEXT_SWITCH_TYPE("process.context_switch.type"),
    PROCESS_CREATION_TIME("process.creation.time"),
    // ???
    PROCESS_ENVIRONMENT_VARIABLE("process.environment_variable."),
    PROCESS_EXECUTABLE_BUILD_ID_GNU("process.executable.build_id.gnu"),
    PROCESS_EXECUTABLE_BUILD_ID_GO("process.executable.build_id.go"),
    PROCESS_EXECUTABLE_BUILD_ID_HTLHASH("process.executable.build_id.htlhash"),
    PROCESS_EXECUTABLE_NAME("process.executable.name"),
    PROCESS_EXECUTABLE_PATH("process.executable.path"),
    PROCESS_EXIT_CODE("process.exit.code"),
    PROCESS_EXIT_TIME("process.exit.time"),
    PROCESS_GROUP_LEADER_PID("process.group_leader.pid"),
    PROCESS_INTERACTIVE("process.interactive"),
    PROCESS_OWNER("process.owner"),
    PROCESS_PARENT_PID("process.parent_pid"),
    PROCESS_PID("process.pid"),
    PROCESS_REAL_USER_ID("process.real_user.id"),
    PROCESS_REAL_USER_NAME("process.real_user.name"),
    PROCESS_RUNTIME_DESCRIPTION("process.runtime.description"),
    PROCESS_RUNTIME_NAME("process.runtime.name"),
    PROCESS_RUNTIME_VERSION("process.runtime.version"),
    PROCESS_SAVED_USER_ID("process.saved_user.id"),
    PROCESS_SAVED_USER_NAME("process.saved_user.name"),
    PROCESS_SESSION_LEADER_PID("process.session_leader.pid"),
    PROCESS_STATE("process.state"),
    PROCESS_TITLE("process.title"),
    PROCESS_USER_ID("process.user.id"),
    PROCESS_USER_NAME("process.user.name"),
    PROCESS_VPID("process.vpid"),
    PROCESS_WORKING_DIRECTORY("process.working_directory"),

    // ---------------------------------------------------------------------
    // Kubernetes
    // ---------------------------------------------------------------------
    K8S_CLUSTER_NAME("k8s.cluster.name"),
    K8S_CLUSTER_UID("k8s.cluster.uid"),
    K8S_CONTAINER_NAME("k8s.container.name"),
    K8S_CONTAINER_RESTART_COUNT("k8s.container.restart_count"),
    K8S_CONTAINER_STATUS_LAST_TERMINATED_REASON("k8s.container.status.last_terminated_reason"),
    K8S_CONTAINER_STATUS_REASON("k8s.container.status.reason"),
    K8S_CONTAINER_STATUS_STATE("k8s.container.status.state"),
    K8S_CRONJOB_ANNOTATION("k8s.cronjob.annotation."),
    K8S_CRONJOB_LABEL("k8s.cronjob.label."),
    K8S_CRONJOB_NAME("k8s.cronjob.name"),
    K8S_CRONJOB_UID("k8s.cronjob.uid"),
    K8S_DAEMONSET_ANNOTATION("k8s.daemonset.annotation."),
    K8S_DAEMONSET_LABEL("k8s.daemonset.label."),
    K8S_DAEMONSET_NAME("k8s.daemonset.name"),
    K8S_DAEMONSET_UID("k8s.daemonset.uid"),
    K8S_DEPLOYMENT_ANNOTATION("k8s.deployment.annotation."),
    K8S_DEPLOYMENT_LABEL("k8s.deployment.label."),
    K8S_DEPLOYMENT_NAME("k8s.deployment.name"),
    K8S_DEPLOYMENT_UID("k8s.deployment.uid"),
    K8S_HPA_METRIC_TYPE("k8s.hpa.metric.type"),
    K8S_HPA_NAME("k8s.hpa.name"),
    K8S_HPA_SCALETARGETREF_API_VERSION("k8s.hpa.scaletargetref.api_version"),
    K8S_HPA_SCALETARGETREF_KIND("k8s.hpa.scaletargetref.kind"),
    K8S_HPA_SCALETARGETREF_NAME("k8s.hpa.scaletargetref.name"),
    K8S_HPA_UID("k8s.hpa.uid"),
    K8S_HUGEPAGE_SIZE("k8s.hugepage.size"),
    K8S_JOB_ANNOTATION("k8s.job.annotation."),
    K8S_JOB_LABEL("k8s.job.label."),
    K8S_JOB_NAME("k8s.job.name"),
    K8S_JOB_UID("k8s.job.uid"),
    K8S_NAMESPACE_ANNOTATION("k8s.namespace.annotation."),
    K8S_NAMESPACE_LABEL("k8s.namespace.label."),
    K8S_NAMESPACE_NAME("k8s.namespace.name"),
    K8S_NAMESPACE_PHASE("k8s.namespace.phase"),
    K8S_NODE_ANNOTATION("k8s.node.annotation."),
    K8S_NODE_CONDITION_STATUS("k8s.node.condition.status"),
    K8S_NODE_CONDITION_TYPE("k8s.node.condition.type"),
    K8S_NODE_LABEL("k8s.node.label."),
    K8S_NODE_NAME("k8s.node.name"),
    K8S_NODE_UID("k8s.node.uid"),
    K8S_POD_ANNOTATION("k8s.pod.annotation."),
    K8S_POD_HOSTNAME("k8s.pod.hostname"),
    K8S_POD_IP("k8s.pod.ip"),
    K8S_POD_LABEL("k8s.pod.label."),
    K8S_POD_NAME("k8s.pod.name"),
    K8S_POD_START_TIME("k8s.pod.start_time"),
    K8S_POD_STATUS_PHASE("k8s.pod.status.phase"),
    K8S_POD_STATUS_REASON("k8s.pod.status.reason"),
    K8S_POD_UID("k8s.pod.uid"),
    K8S_REPLICASET_ANNOTATION("k8s.replicaset.annotation."),
    K8S_REPLICASET_LABEL("k8s.replicaset.label."),
    K8S_REPLICASET_NAME("k8s.replicaset.name"),
    K8S_REPLICASET_UID("k8s.replicaset.uid"),
    K8S_REPLICATIONCONTROLLER_NAME("k8s.replicationcontroller.name"),
    K8S_REPLICATIONCONTROLLER_UID("k8s.replicationcontroller.uid"),
    K8S_RESOURCEQUOTA_NAME("k8s.resourcequota.name"),
    K8S_RESOURCEQUOTA_RESOURCE_NAME("k8s.resourcequota.resource_name"),
    K8S_RESOURCEQUOTA_UID("k8s.resourcequota.uid"),
    K8S_SERVICE_ANNOTATION("k8s.service.annotation."),
    K8S_SERVICE_ENDPOINT_ADDRESS_TYPE("k8s.service.endpoint.address_type"),
    K8S_SERVICE_ENDPOINT_CONDITION("k8s.service.endpoint.condition"),
    K8S_SERVICE_ENDPOINT_ZONE("k8s.service.endpoint.zone"),
    K8S_SERVICE_LABEL("k8s.service.label."),
    K8S_SERVICE_NAME("k8s.service.name"),
    K8S_SERVICE_PUBLISH_NOT_READY_ADDRESSES("k8s.service.publish_not_ready_addresses"),
    K8S_SERVICE_SELECTOR("k8s.service.selector."),
    K8S_SERVICE_TRAFFIC_DISTRIBUTION("k8s.service.traffic_distribution"),
    K8S_SERVICE_TYPE("k8s.service.type"),
    K8S_SERVICE_UID("k8s.service.uid"),
    K8S_STATEFULSET_ANNOTATION("k8s.statefulset.annotation."),
    K8S_STATEFULSET_LABEL("k8s.statefulset.label."),
    K8S_STATEFULSET_NAME("k8s.statefulset.name"),
    K8S_STATEFULSET_UID("k8s.statefulset.uid"),
    K8S_STORAGECLASS_NAME("k8s.storageclass.name"),
    K8S_VOLUME_NAME("k8s.volume.name"),
    K8S_VOLUME_TYPE("k8s.volume.type"),

    // ---------------------------------------------------------------------
    // Kubernetes (Deprecated)
    // ---------------------------------------------------------------------
    K8S_POD_LABELS("k8s.pod.labels."),

    // ---------------------------------------------------------------------
    // Cloud (Resource)
    // ---------------------------------------------------------------------
    CLOUD_ACCOUNT_ID("cloud.account.id"),
    CLOUD_AVAILABILITY_ZONE("cloud.availability_zone"),
    CLOUD_PLATFORM("cloud.platform"),
    CLOUD_PROVIDER("cloud.provider"),
    CLOUD_REGION("cloud.region"),
    CLOUD_RESOURCE_ID("cloud.resource_id"),

    // ---------------------------------------------------------------------
    // Service
    // ---------------------------------------------------------------------
    SERVICE_CRITICALITY("service.criticality"), // critical, high, low, medium
    SERVICE_VERSION("service.version"),
    SERVICE_NAMESPACE("service.namespace"),
    SERVICE_PEER_NAME("service.peer.name"),
    SERVICE_PEER_NAMESPACE("service.peer.namespace"),

    // ---------------------------------------------------------------------
    // Code
    // ---------------------------------------------------------------------
    CODE_COLUMN_NUMBER("code.column.number"),
    CODE_FILE_PATH("code.file.path"),
    CODE_FUNCTION_NAME("code.function.name"),
    CODE_LINE_NUMBER("code.line.number"),
    CODE_STACKTRACE("code.stacktrace"),
    CODE_NAMESPACE("code.namespace"),

    // ---------------------------------------------------------------------
    // RFC5424 Syslog
    // ---------------------------------------------------------------------
    SYSLOG_FACILITY("syslog.facility"),
    SYSLOG_VERSION("syslog.version"),
    SYSLOG_PROCID("syslog.procid"),
    SYSLOG_MSGID("syslog.msgid"),
    CLIENT_ADDRESS("client.address"),

    // ---------------------------------------------------------------------
    // Windows Event Log
    // ---------------------------------------------------------------------
    WINLOG_EVENT_ID("winlog.event_id"),

    // ---------------------------------------------------------------------
    // SignalFx Events
    // ---------------------------------------------------------------------
    COM_SPLUNK_SIGNALFX_EVENT_TYPE("com.splunk.signalfx.event_type"),
    COM_SPLUNK_SIGNALFX_EVENT_CATEGORY("com.splunk.signalfx.event_category"),

    // ---------------------------------------------------------------------
    // Splunk HEC
    // ---------------------------------------------------------------------
    COM_SPLUNK_SOURCE("com.splunk.source"),
    COM_SPLUNK_SOURCETYPE("com.splunk.sourcetype"),
    COM_SPLUNK_INDEX("com.splunk.index"),

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
    NETWORK_PEER_ADDRESS("network.peer.address"),
    NETWORK_LOCAL_ADDRESS("network.local.address"),
    HTTP_REQUEST_METHOD("http.request.method"),
    URL_FULL("url.full"),
    HTTP_RESPONSE_STATUS_CODE("http.response.status_code"),

    // ---------------------------------------------------------------------
    // CloudTrail Log Event
    // ---------------------------------------------------------------------
    CLOUDTRAIL_ERROR_CODE("cloudtrail.error_code"),

    // ---------------------------------------------------------------------
    // Google Cloud Logging
    // ---------------------------------------------------------------------
    GCP_LOG_NAME("gcp.log_name"),
    GCP_HTTP_REQUEST("gcp.http_request"),

    // ---------------------------------------------------------------------
    // Elastic Common Schema
    // ---------------------------------------------------------------------
    AGENT_EPHEMERAL_ID("agent.ephemeral_id"),
    AGENT_ID("agent.id"),
    CLOUD_ZONE("cloud.zone"),
    CLOUD_INSTANCE_ID("cloud.instance.id"),
    CLOUD_INSTANCE_NAME("cloud.instance.name"),
    CLOUD_MACHINE_TYPE("cloud.machine.type"),
    CONTAINER_IMAGE_TAG("container.image.tag"),
    CONTAINER_RUNTIME("container.runtime"),
    DESTINATION_ADDRESS("destination.address"),
    ERROR_CODE("error.code"),
    ERROR_ID("error.id"),
    ERROR_MESSAGE("error.message"),
    ERROR_STACK_TRACE("error.stack_trace"),
    HOST_ARCHITECTURE("host.architecture"),
    HOST_DOMAIN("host.domain"),
    SERVICE_EPHEMERAL_ID("service.ephemeral_id"),
    SERVICE_ID("service.id"),
    SERVICE_STATE("service.state"),
    SERVICE_TYPE("service.type"),

    // ---------------------------------------------------------------------
    // Browser
    // ---------------------------------------------------------------------
    BROWSER_BRANDS("browser.brands"),
    BROWSER_LANGUAGE("browser.language"),
    BROWSER_MOBILE("browser.mobile"),
    BROWSER_PLATFORM("browser.platform"),
    USER_AGENT_ORIGINAL("user_agent.original"),

    // ---------------------------------------------------------------------
    // Device
    // ---------------------------------------------------------------------
    DEVICE_ID("device.id"),
    DEVICE_MANUFACTURER("device.manufacturer"),
    DEVICE_MODEL_IDENTIFIER("device.model.identifier"),
    DEVICE_MODEL_NAME("device.model.name");

    private final String value;

    OTelTags(final String value) {
        this.value = value;
    }

    /**
     * @return the raw resource/attribute name from the OpenTelemetry mapping.
     */
    public String getValue() {
        return value;
    }
}
