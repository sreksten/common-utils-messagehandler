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
public enum Names {

    // ---------------------------------------------------------------------
    // Semantic Conventions (Reserved)
    // ---------------------------------------------------------------------
    ATTR_ERROR_TYPE("error.type"),
    ATTR_EXCEPTION_MESSAGE("exception.message"),
    ATTR_EXCEPTION_STACKTRACE("exception.stacktrace"),
    ATTR_EXCEPTION_TYPE("exception.type"),
    ATTR_SERVER_ADDRESS("server.address"),
    ATTR_SERVER_PORT("server.port"),
    ATTR_SERVICE_NAME("service.name"),
    ATTR_SERVICE_INSTANCE_ID("service.instance.id"),
    ATTR_TELEMETRY_SDK_LANGUAGE("telemetry.sdk.language"),
    ATTR_TELEMETRY_SDK_NAME("telemetry.sdk.name"),
    ATTR_TELEMETRY_SDK_VERSION("telemetry.sdk.version"),
    ATTR_URL_SCHEME("url.scheme"),

    EVENT_EXCEPTION("exception"),

    // ---------------------------------------------------------------------
    // Deployment
    // ---------------------------------------------------------------------
    ATTR_DEPLOYMENT_ENVIRONMENT_NAME("deployment.environment.name"),

    // ---------------------------------------------------------------------
    // Host
    // ---------------------------------------------------------------------
    /**
     * The CPU architecture the host system is running on.
     * E.g., amd64; arm32; arm64; ia64; ppc32; ppc64; s390x; x86
     */
    ATTR_HOST_ARCH("host.arch"),
    /**
     * Unique host ID. For Cloud, this must be the instance_id assigned by the cloud provider.
     * For non-containerized systems, this should be the machine-id. See
     * <a href="https://opentelemetry.io/docs/specs/semconv/resource/host/">Host</a>
     */
    ATTR_HOST_ID("host.id"),
    /**
     * VM image ID or host OS image ID. For Cloud, this value is from the provider.
     */
    ATTR_HOST_IMAGE_ID("host.image.id"),
    /**
     * Name of the VM image or OS install the host was instantiated from.
     */
    ATTR_HOST_IMAGE_NAME("host.image.name"),
    /**
     * The version string of the VM image or host OS as defined in Version Attributes.
     */
    ATTR_HOST_IMAGE_VERSION("host.image.version"),
    /**
     * Name of the host. On Unix systems, it may contain what the hostname command returns, or the fully
     * qualified hostname, or another name specified by the user.
     */
    ATTR_HOST_NAME("host.name"),
    /**
     * Type of host. For Cloud, this must be the machine type.
     */
    ATTR_HOST_TYPE("host.type"),
    /**
     * Available IP addresses of the host, excluding loopback interfaces.
     */
    ATTR_HOST_IP("host.ip"),
    /**
     * Available MAC addresses of the host, excluding loopback interfaces.
     */
    ATTR_HOST_MAC("host.mac"),
    /**
     * The amount of level 2 memory cache available to the processor (in Bytes).
     */
    ATTR_HOST_CPU_CACHE_L2_SIZE("host.cpu.cache.l2.size"),
    /**
     * Family or generation of the CPU.
     */
    ATTR_HOST_CPU_FAMILY("host.cpu.family"),
    /**
     * Model identifier. It provides more granular information about the CPU, distinguishing it
     * from other CPUs within the same family.
     */
    ATTR_HOST_CPU_MODEL_ID("host.cpu.model.id"),
    /**
     * Model designation of the processor.
     */
    ATTR_HOST_CPU_MODEL_NAME("host.cpu.model.name"),
    /**
     * Stepping or core revisions.
     */
    ATTR_HOST_CPU_STEPPING("host.cpu.stepping"),
    /**
     * Processor manufacturer identifier. A maximum 12-character string.
     */
    ATTR_HOST_CPU_VENDOR_ID("host.cpu.vendor.id"),

    // ---------------------------------------------------------------------
    // Container
    // ---------------------------------------------------------------------
    ATTR_CONTAINER_COMMAND("container.command"),
    ATTR_CONTAINER_COMMAND_ARGS("container.command_args"),
    ATTR_CONTAINER_COMMAND_LINE("container.command_line"),
    ATTR_CONTAINER_CSI_PLUGIN_NAME("container.csi.plugin.name"),
    CONTAINER_CSI_VOLUME_ID("container.csi.volume.id"),
    /**
     * Container ID. Usually a UUID, as for example used to identify Docker containers.
     * The UUID might be abbreviated.
     */
    ATTR_CONTAINER_ID("container.id"),
    ATTR_CONTAINER_IMAGE_ID("container.image.id"),
    ATTR_CONTAINER_IMAGE_NAME("container.image.name"),
    ATTR_CONTAINER_IMAGE_REPO_DIGESTS("container.image.repo_digests"),
    ATTR_CONTAINER_IMAGE_TAGS("container.image.tags"),
    // ???
    ATTR_CONTAINER_LABELS("container.label."),
    ATTR_CONTAINER_NAME("container.name"),
    ATTR_CONTAINER_RUNTIME_DESCRIPTION("container.runtime.description"),
    ATTR_CONTAINER_RUNTIME_NAME("container.runtime.name"),
    ATTR_CONTAINER_RUNTIME_VERSION("container.runtime.version"),

    // ---------------------------------------------------------------------
    // Process
    // ---------------------------------------------------------------------
    ATTR_PROCESS_ARGS_COUNT("process.args_count"),
    ATTR_PROCESS_COMMAND("process.command"),
    ATTR_PROCESS_COMMAND_ARGS("process.command_args"),
    ATTR_PROCESS_COMMAND_LINE("process.command_line"),
    ATTR_PROCESS_CONTEXT_SWITCH_TYPE("process.context_switch.type"),
    ATTR_PROCESS_CREATION_TIME("process.creation_time"),
    // ???
    ATTR_PROCESS_ENVIRONMENT_VARIABLE("process.environment_variable."),
    ATTR_PROCESS_EXECUTABLE_BUILD_ID_GNU("process.executable.build_id.gnu"),
    ATTR_PROCESS_EXECUTABLE_BUILD_ID_GO("process.executable.build_id.go"),
    ATTR_PROCESS_EXECUTABLE_BUILD_ID_HTLHASH("process.executable.build_id.htlhash"),
    ATTR_PROCESS_EXECUTABLE_NAME("process.executable.name"),
    ATTR_PROCESS_EXECUTABLE_PATH("process.executable.path"),
    ATTR_PROCESS_EXIT_CODE("process.exit.code"),
    ATTR_PROCESS_EXIT_TIME("process.exit.time"),
    ATTR_PROCESS_GROUP_LEADER_PID("process.group_leader.pid"),
    ATTR_PROCESS_INTERACTIVE("process.interactive"),
    ATTR_PROCESS_OWNER("process.owner"),
    ATTR_PROCESS_PARENT_PID("process.parent_pid"),
    ATTR_PROCESS_PID("process.pid"),
    ATTR_PROCESS_REAL_USER_ID("process.real_user.id"),
    ATTR_PROCESS_REAL_USER_NAME("process.real_user.name"),
    ATTR_PROCESS_RUNTIME_DESCRIPTION("process.runtime.description"),
    ATTR_PROCESS_RUNTIME_NAME("process.runtime.name"),
    ATTR_PROCESS_RUNTIME_VERSION("process.runtime.version"),
    ATTR_PROCESS_SAVED_USER_ID("process.saved_user.id"),
    ATTR_PROCESS_SAVED_USER_NAME("process.saved_user.name"),
    ATTR_PROCESS_SESSION_LEADER_PID("process.session_leader.pid"),
    ATTR_PROCESS_STATE("process.state"),
    ATTR_PROCESS_TITLE("process.title"),
    ATTR_PROCESS_USER_ID("process.user.id"),
    ATTR_PROCESS_USER_NAME("process.user.name"),
    ATTR_PROCESS_VPID("process.vpid"),
    ATTR_PROCESS_WORKING_DIRECTORY("process.working_directory"),

    // ---------------------------------------------------------------------
    // Kubernetes
    // ---------------------------------------------------------------------
    ATTR_K8S_CLUSTER_NAME("k8s.cluster.name"),
    ATTR_K8S_CLUSTER_UID("k8s.cluster.uid"),
    ATTR_K8S_CONTAINER_NAME("k8s.container.name"),
    ATTR_K8S_CONTAINER_RESTART_COUNT("k8s.container.restart_count"),
    ATTR_K8S_CONTAINER_STATUS_LAST_TERMINATED_REASON("k8s.container.status.last_terminated_reason"),
    ATTR_K8S_CONTAINER_STATUS_REASON("k8s.container.status.reason"),
    ATTR_K8S_CONTAINER_STATUS_STATE("k8s.container.status.state"),
    ATTR_K8S_CRONJOB_ANNOTATION("k8s.cronjob.annotation."),
    ATTR_K8S_CRONJOB_LABEL("k8s.cronjob.label."),
    ATTR_K8S_CRONJOB_NAME("k8s.cronjob.name"),
    ATTR_K8S_CRONJOB_UID("k8s.cronjob.uid"),
    ATTR_K8S_DAEMONSET_ANNOTATION("k8s.daemonset.annotation."),
    ATTR_K8S_DAEMONSET_LABEL("k8s.daemonset.label."),
    ATTR_K8S_DAEMONSET_NAME("k8s.daemonset.name"),
    ATTR_K8S_DAEMONSET_UID("k8s.daemonset.uid"),
    ATTR_K8S_DEPLOYMENT_ANNOTATION("k8s.deployment.annotation."),
    ATTR_K8S_DEPLOYMENT_LABEL("k8s.deployment.label."),
    ATTR_K8S_DEPLOYMENT_NAME("k8s.deployment.name"),
    ATTR_K8S_DEPLOYMENT_UID("k8s.deployment.uid"),
    ATTR_K8S_HPA_METRIC_TYPE("k8s.hpa.metric.type"),
    ATTR_K8S_HPA_NAME("k8s.hpa.name"),
    ATTR_K8S_HPA_SCALETARGETREF_API_VERSION("k8s.hpa.scaletargetref.api_version"),
    ATTR_K8S_HPA_SCALETARGETREF_KIND("k8s.hpa.scaletargetref.kind"),
    ATTR_K8S_HPA_SCALETARGETREF_NAME("k8s.hpa.scaletargetref.name"),
    ATTR_K8S_HPA_UID("k8s.hpa.uid"),
    ATTR_K8S_HUGEPAGE_SIZE("k8s.hugepage.size"),
    ATTR_K8S_JOB_ANNOTATION("k8s.job.annotation."),
    ATTR_K8S_JOB_LABEL("k8s.job.label."),
    ATTR_K8S_JOB_NAME("k8s.job.name"),
    ATTR_K8S_JOB_UID("k8s.job.uid"),
    ATTR_K8S_NAMESPACE_ANNOTATION("k8s.namespace.annotation."),
    ATTR_K8S_NAMESPACE_LABEL("k8s.namespace.label."),
    ATTR_K8S_NAMESPACE_NAME("k8s.namespace.name"),
    ATTR_K8S_NAMESPACE_PHASE("k8s.namespace.phase"),
    ATTR_K8S_NODE_ANNOTATION("k8s.node.annotation."),
    ATTR_K8S_NODE_CONDITION_STATUS("k8s.node.condition.status"),
    ATTR_K8S_NODE_CONDITION_TYPE("k8s.node.condition.type"),
    ATTR_K8S_NODE_LABEL("k8s.node.label."),
    ATTR_K8S_NODE_NAME("k8s.node.name"),
    ATTR_K8S_NODE_UID("k8s.node.uid"),
    ATTR_K8S_POD_ANNOTATION("k8s.pod.annotation."),
    ATTR_K8S_POD_HOSTNAME("k8s.pod.hostname"),
    ATTR_K8S_POD_IP("k8s.pod.ip"),
    ATTR_K8S_POD_LABEL("k8s.pod.label."),
    ATTR_K8S_POD_NAME("k8s.pod.name"),
    ATTR_K8S_POD_START_TIME("k8s.pod.start_time"),
    ATTR_K8S_POD_STATUS_PHASE("k8s.pod.status.phase"),
    ATTR_K8S_POD_STATUS_REASON("k8s.pod.status.reason"),
    ATTR_K8S_POD_UID("k8s.pod.uid"),
    ATTR_K8S_REPLICASET_ANNOTATION("k8s.replicaset.annotation."),
    ATTR_K8S_REPLICASET_LABEL("k8s.replicaset.label."),
    ATTR_K8S_REPLICASET_NAME("k8s.replicaset.name"),
    ATTR_K8S_REPLICASET_UID("k8s.replicaset.uid"),
    ATTR_K8S_REPLICATIONCONTROLLER_NAME("k8s.replicationcontroller.name"),
    ATTR_K8S_REPLICATIONCONTROLLER_UID("k8s.replicationcontroller.uid"),
    ATTR_K8S_RESOURCEQUOTA_NAME("k8s.resourcequota.name"),
    ATTR_K8S_RESOURCEQUOTA_RESOURCE_NAME("k8s.resourcequota.resource_name"),
    ATTR_K8S_RESOURCEQUOTA_UID("k8s.resourcequota.uid"),
    ATTR_K8S_SERVICE_ANNOTATION("k8s.service.annotation."),
    ATTR_K8S_SERVICE_ENDPOINT_ADDRESS_TYPE("k8s.service.endpoint.address_type"),
    ATTR_K8S_SERVICE_ENDPOINT_CONDITION("k8s.service.endpoint.condition"),
    ATTR_K8S_SERVICE_ENDPOINT_ZONE("k8s.service.endpoint.zone"),
    ATTR_K8S_SERVICE_LABEL("k8s.service.label."),
    ATTR_K8S_SERVICE_NAME("k8s.service.name"),
    ATTR_K8S_SERVICE_PUBLISH_NOT_READY_ADDRESSES("k8s.service.publish_not_ready_addresses"),
    ATTR_K8S_SERVICE_SELECTOR("k8s.service.selector."),
    ATTR_K8S_SERVICE_TRAFFIC_DISTRIBUTION("k8s.service.traffic_distribution"),
    ATTR_K8S_SERVICE_TYPE("k8s.service.type"),
    ATTR_K8S_SERVICE_UID("k8s.service.uid"),
    ATTR_K8S_STATEFULSET_ANNOTATION("k8s.statefulset.annotation."),
    ATTR_K8S_STATEFULSET_LABEL("k8s.statefulset.label."),
    ATTR_K8S_STATEFULSET_NAME("k8s.statefulset.name"),
    ATTR_K8S_STATEFULSET_UID("k8s.statefulset.uid"),
    ATTR_K8S_STORAGECLASS_NAME("k8s.storageclass.name"),
    ATTR_K8S_VOLUME_NAME("k8s.volume.name"),
    ATTR_K8S_VOLUME_TYPE("k8s.volume.type"),

    // ---------------------------------------------------------------------
    // Kubernetes (Deprecated)
    // ---------------------------------------------------------------------
    ATTR_K8S_POD_LABELS("k8s.pod.labels."),

    // ---------------------------------------------------------------------
    // Cloud (Resource)
    // ---------------------------------------------------------------------
    ATTR_CLOUD_ACCOUNT_ID("cloud.account.id"),
    ATTR_CLOUD_AVAILABILITY_ZONE("cloud.availability_zone"),
    ATTR_CLOUD_PLATFORM("cloud.platform"),
    ATTR_CLOUD_PROVIDER("cloud.provider"),
    ATTR_CLOUD_REGION("cloud.region"),
    ATTR_CLOUD_RESOURCE_ID("cloud.resource_id"),

    // ---------------------------------------------------------------------
    // Service
    // ---------------------------------------------------------------------
    ATTR_SERVICE_CRITICALITY("service.criticality"), // critical, high, low, medium
    ATTR_SERVICE_VERSION("service.version"),
    ATTR_SERVICE_NAMESPACE("service.namespace"),
    ATTR_SERVICE_PEER_NAME("service.peer.name"),
    ATTR_SERVICE_PEER_NAMESPACE("service.peer.namespace"),

    // ---------------------------------------------------------------------
    // Code
    // ---------------------------------------------------------------------
    ATTR_CODE_COLUMN_NUMBER("code.column.number"),
    ATTR_CODE_FILE_PATH("code.file.path"),
    ATTR_CODE_FUNCTION_NAME("code.function.name"),
    ATTR_CODE_LINE_NUMBER("code.line.number"),
    ATTR_CODE_STACKTRACE("code.stacktrace"),
    ATTR_CODE_NAMESPACE("code.namespace"),

    // ---------------------------------------------------------------------
    // RFC5424 Syslog
    // ---------------------------------------------------------------------
    ATTR_SYSLOG_FACILITY("syslog.facility"),
    ATTR_SYSLOG_VERSION("syslog.version"),
    ATTR_SYSLOG_PROCID("syslog.procid"),
    ATTR_SYSLOG_MSGID("syslog.msgid"),
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
    RES_CLOUD_ZONE("cloud.zone"),
    RES_CLOUD_INSTANCE_ID("cloud.instance.id"),
    RES_CLOUD_INSTANCE_NAME("cloud.instance.name"),
    RES_CLOUD_MACHINE_TYPE("cloud.machine.type"),
    RES_CONTAINER_IMAGE_TAG("container.image.tag"),
    RES_CONTAINER_RUNTIME("container.runtime"),
    ATTR_DESTINATION_ADDRESS("destination.address"),
    ATTR_ERROR_CODE("error.code"),
    ATTR_ERROR_ID("error.id"),
    ATTR_ERROR_MESSAGE("error.message"),
    ATTR_ERROR_STACK_TRACE("error.stack_trace"),
    RES_HOST_ARCHITECTURE("host.architecture"),
    RES_HOST_DOMAIN("host.domain"),
    RES_SERVICE_EPHEMERAL_ID("service.ephemeral_id"),
    RES_SERVICE_ID("service.id"),
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
