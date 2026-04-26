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
 * <p>
 * Specification references used for these mappings:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/data-model-appendix/">OpenTelemetry Logs Data Model Appendix</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/semconv/registry/attributes/browser/">Browser Attributes</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/semconv/registry/attributes/device/">Device Attributes</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/semconv/registry/attributes/http/">HTTP Attributes</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/semconv/registry/attributes/url/">URL Attributes</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/semconv/registry/attributes/network/">Network Attributes</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/semconv/registry/attributes/client/">Client Attributes</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/semconv/registry/attributes/gcp/">GCP Attributes</a></li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public enum OTelTags {

    // ---------------------------------------------------------------------
    // Semantic Conventions (Reserved)
    // ---------------------------------------------------------------------
    /**
     * Error type that classifies a failed operation.
     * Set this only when an operation fails and prefer stable, low-cardinality values
     * (for example an exception class or domain error code).
     */
    ERROR_TYPE("error.type"),
    /**
     * Human-readable exception message.
     * Use in exception events/logs together with {@link #EXCEPTION_TYPE} when available.
     */
    EXCEPTION_MESSAGE("exception.message"),
    /**
     * Exception stacktrace in the natural language format used by the runtime.
     * Use in exception events/logs when stack information is available.
     */
    EXCEPTION_STACKTRACE("exception.stacktrace"),
    /**
     * Exception type (typically fully-qualified class name).
     * In exception events/logs, set this or {@link #EXCEPTION_MESSAGE} (preferably both).
     */
    EXCEPTION_TYPE("exception.type"),
    /**
     * Logical server address the request is sent to (for example host name or IP).
     * For proxied requests, capture the backend server address when known.
     */
    SERVER_ADDRESS("server.address"),
    /**
     * Logical server port paired with {@link #SERVER_ADDRESS}.
     * For proxied requests, capture the backend server port when known.
     */
    SERVER_PORT("server.port"),
    /**
     * Logical service name.
     * Resource attribute: MUST be the same for all instances of the same service.
     * If not explicitly configured, SDKs fall back to {@code unknown_service:*}.
     */
    SERVICE_NAME("service.name"),
    /**
     * Unique identifier of a specific service instance.
     * Resource attribute: value must be unique per service namespace/name pair.
     */
    SERVICE_INSTANCE_ID("service.instance.id"),
    /**
     * Programming language of the telemetry SDK.
     * Resource attribute typically set by the SDK (for example: java).
     */
    TELEMETRY_SDK_LANGUAGE("telemetry.sdk.language"),
    /**
     * Name of the telemetry SDK.
     * Resource attribute typically set to the SDK identifier (for OTel SDKs: opentelemetry).
     */
    TELEMETRY_SDK_NAME("telemetry.sdk.name"),
    /**
     * Version of the telemetry SDK that produced the telemetry.
     * Resource attribute used for SDK provenance and troubleshooting.
     */
    TELEMETRY_SDK_VERSION("telemetry.sdk.version"),
    /**
     * URL scheme component (for example: http, https).
     * Use with related URL/server attributes to describe the protocol in use.
     */
    URL_SCHEME("url.scheme"),

    /**
     * Event name for exception events.
     * Per exception semantic conventions, span exception event name is {@code exception}.
     * This library also uses the same literal as the log record event name.
     */
    EVENT_EXCEPTION("exception"),

    // ---------------------------------------------------------------------
    // Deployment
    // ---------------------------------------------------------------------
    /**
     * Name of the deployment environment (deployment tier), for example {@code staging} or {@code production}.
     * Resource attribute: set once per deployment and keep values stable/low-cardinality.
     */
    DEPLOYMENT_ENVIRONMENT_NAME("deployment.environment.name"),

    // ---------------------------------------------------------------------
    // Host
    // ---------------------------------------------------------------------
    /**
     * The CPU architecture the host system is running on.
     * Resource attribute: use OTel well-known values when applicable
     * (for example: amd64, arm32, arm64, ia64, ppc32, ppc64, s390x, x86).
     */
    HOST_ARCH("host.arch"),
    /**
     * Unique host ID. For Cloud, this must be the instance_id assigned by the cloud provider.
     * For non-containerized systems, this should be the machine-id.
     * Resource attribute: use a stable identifier for host identity correlation.
     */
    HOST_ID("host.id"),
    /**
     * VM image ID or host OS image ID. For Cloud, this value is from the provider.
     * Resource attribute: set when telemetry needs image-level provenance.
     */
    HOST_IMAGE_ID("host.image.id"),
    /**
     * Name of the VM image or OS install the host was instantiated from.
     * Resource attribute: set together with {@link #HOST_IMAGE_ID} when available.
     */
    HOST_IMAGE_NAME("host.image.name"),
    /**
     * The version string of the VM image or host OS as defined in Version Attributes.
     * Resource attribute: set together with {@link #HOST_IMAGE_NAME} when available.
     */
    HOST_IMAGE_VERSION("host.image.version"),
    /**
     * Name of the host. On Unix systems, it may contain what the hostname command returns, or the fully
     * qualified hostname, or another name specified by the user.
     * Resource attribute: set a stable host name representation for correlation.
     */
    HOST_NAME("host.name"),
    /**
     * Type of host. For Cloud, this must be the machine type.
     * Resource attribute: useful to correlate telemetry with host sizing/capacity.
     */
    HOST_TYPE("host.type"),
    /**
     * Available IP addresses of the host, excluding loopback interfaces.
     * Resource attribute: IPv4 in dotted-quad, IPv6 in RFC 5952 format.
     */
    HOST_IP("host.ip"),
    /**
     * Available MAC addresses of the host, excluding loopback interfaces.
     * Resource attribute: use IEEE RA uppercase hexadecimal form with hyphen-separated octets.
     */
    HOST_MAC("host.mac"),
    /**
     * The amount of level 2 memory cache available to the processor (in Bytes).
     * Resource attribute: capture when hardware-level host profiling is needed.
     */
    HOST_CPU_CACHE_L2_SIZE("host.cpu.cache.l2.size"),
    /**
     * Family or generation of the CPU.
     * Resource attribute: set when CPU family-level differentiation matters.
     */
    HOST_CPU_FAMILY("host.cpu.family"),
    /**
     * Model identifier. It provides more granular information about the CPU, distinguishing it
     * from other CPUs within the same family.
     * Resource attribute: set alongside {@link #HOST_CPU_FAMILY} when available.
     */
    HOST_CPU_MODEL_ID("host.cpu.model.id"),
    /**
     * Model designation of the processor.
     * Resource attribute: use vendor/model display string from the host OS/runtime.
     */
    HOST_CPU_MODEL_NAME("host.cpu.model.name"),
    /**
     * Stepping or core revisions.
     * Resource attribute: set for low-level CPU revision diagnostics.
     */
    HOST_CPU_STEPPING("host.cpu.stepping"),
    /**
     * Processor manufacturer identifier. A maximum 12-character string.
     * Resource attribute: use the CPUID vendor string when available.
     */
    HOST_CPU_VENDOR_ID("host.cpu.vendor.id"),

    // ---------------------------------------------------------------------
    // Container
    // ---------------------------------------------------------------------
    /**
     * Command name used to run the container.
     * If command values may include embedded credentials or other secrets, redact before recording.
     */
    CONTAINER_COMMAND("container.command"),
    /**
     * Full argument list used to run the container, including the executable/command itself.
     * If argument values may include embedded credentials or other secrets, redact before recording.
     */
    CONTAINER_COMMAND_ARGS("container.command_args"),
    /**
     * Full container command represented as a single command-line string.
     * Prefer this when preserving exact invocation order/format is required.
     */
    CONTAINER_COMMAND_LINE("container.command_line"),
    /**
     * CSI plugin name used by the volume.
     * In CSI terms, this corresponds to the {@code name} from {@code GetPluginInfo}.
     */
    CONTAINER_CSI_PLUGIN_NAME("container.csi.plugin.name"),
    /**
     * Unique volume identifier returned by the CSI plugin.
     * In CSI implementations this can be referred to as the volume handle ({@code Volume.volume_id}).
     */
    CONTAINER_CSI_VOLUME_ID("container.csi.volume.id"),
    /**
     * Container ID. Usually a UUID, as for example used to identify Docker containers.
     * The UUID might be abbreviated.
     */
    CONTAINER_ID("container.id"),
    /**
     * Runtime-specific container image identifier (typically digest/hash based).
     * This value is assigned by the container runtime and can vary across environments.
     */
    CONTAINER_IMAGE_ID("container.image.id"),
    /**
     * Name of the image the container is built from.
     * Use the repository/image reference without tag-only extraction.
     */
    CONTAINER_IMAGE_NAME("container.image.name"),
    /**
     * Repository digests reported by the container runtime for the image.
     * Record as the full list when multiple digests are present.
     */
    CONTAINER_IMAGE_REPO_DIGESTS("container.image.repo_digests"),
    /**
     * Image tags for the container image.
     * Record only the {@code <tag>} segment (for example from {@code repo/image:<tag>}).
     */
    CONTAINER_IMAGE_TAGS("container.image.tags"),
    /**
     * Prefix for container label attributes.
     * Build a concrete key by appending the label name, for example:
     * {@code container.label.app} with value {@code nginx}.
     */
    CONTAINER_LABELS("container.label."),
    /**
     * Container name assigned by the container runtime.
     * Use the runtime-visible name for operational correlation.
     */
    CONTAINER_NAME("container.name"),
    /**
     * Runtime description, which may include CRI/API version details or custom runtime details.
     * Use when runtime flavor/build details are needed beyond name/version.
     */
    CONTAINER_RUNTIME_DESCRIPTION("container.runtime.description"),
    /**
     * Container runtime managing the container (for example: docker, containerd, rkt).
     */
    CONTAINER_RUNTIME_NAME("container.runtime.name"),
    /**
     * Container runtime version as returned by the runtime without modification.
     */
    CONTAINER_RUNTIME_VERSION("container.runtime.version"),

    // ---------------------------------------------------------------------
    // Process
    // ---------------------------------------------------------------------
    /**
     * Number of entries in {@link #PROCESS_COMMAND_ARGS}.
     * Useful for process-start analytics and suspicious-activity detection.
     */
    PROCESS_ARGS_COUNT("process.args_count"),
    /**
     * Command name used to launch the process.
     * On Linux this can map to the first token of {@code /proc/[pid]/cmdline}.
     */
    PROCESS_COMMAND("process.command"),
    /**
     * Full argument list as received by the process, including the executable/command itself.
     * SHOULD NOT be collected by default unless sensitive data is sanitized.
     */
    PROCESS_COMMAND_ARGS("process.command_args"),
    /**
     * Full launch command represented as one string.
     * Do not assemble this solely for observability; use {@link #PROCESS_COMMAND_ARGS} if needed.
     * SHOULD NOT be collected by default unless sensitive data is sanitized.
     */
    PROCESS_COMMAND_LINE("process.command_line"),
    /**
     * Context-switch type for the process data point.
     * Use OTel well-known values when applicable: {@code voluntary}, {@code involuntary}.
     */
    PROCESS_CONTEXT_SWITCH_TYPE("process.context_switch.type"),
    /**
     * Process creation timestamp in ISO 8601 format.
     */
    PROCESS_CREATION_TIME("process.creation.time"),
    /**
     * Prefix for process environment variable attributes.
     * Build a concrete key by appending the environment variable name, for example:
     * {@code process.environment_variable.PATH}.
     * Environment-variable values may be highly sensitive; record only after allowlisting/sanitization.
     */
    PROCESS_ENVIRONMENT_VARIABLE("process.environment_variable."),
    /**
     * GNU build ID from ELF {@code .note.gnu.build-id} section (hex string).
     */
    PROCESS_EXECUTABLE_BUILD_ID_GNU("process.executable.build_id.gnu"),
    /**
     * Go build ID as reported by {@code go tool buildid}.
     */
    PROCESS_EXECUTABLE_BUILD_ID_GO("process.executable.build_id.go"),
    /**
     * Profiling-oriented executable build ID used by OTel Profiles semantic conventions.
     */
    PROCESS_EXECUTABLE_BUILD_ID_HTLHASH("process.executable.build_id.htlhash"),
    /**
     * Executable base name of the process binary.
     */
    PROCESS_EXECUTABLE_NAME("process.executable.name"),
    /**
     * Full path to the process executable.
     */
    PROCESS_EXECUTABLE_PATH("process.executable.path"),
    /**
     * Process exit code.
     */
    PROCESS_EXIT_CODE("process.exit.code"),
    /**
     * Process exit timestamp in ISO 8601 format.
     */
    PROCESS_EXIT_TIME("process.exit.time"),
    /**
     * PID of the process group leader.
     * This is also the process group ID (PGID).
     */
    PROCESS_GROUP_LEADER_PID("process.group_leader.pid"),
    /**
     * Whether the process is connected to an interactive shell.
     */
    PROCESS_INTERACTIVE("process.interactive"),
    /**
     * Username of the user owning the process.
     */
    PROCESS_OWNER("process.owner"),
    /**
     * Parent process identifier (PPID).
     */
    PROCESS_PARENT_PID("process.parent_pid"),
    /**
     * Process identifier (PID).
     */
    PROCESS_PID("process.pid"),
    /**
     * Real user ID (RUID) of the process.
     */
    PROCESS_REAL_USER_ID("process.real_user.id"),
    /**
     * Username of the real user associated with the process.
     */
    PROCESS_REAL_USER_NAME("process.real_user.name"),
    /**
     * Additional runtime description (for example vendor/runtime distribution details).
     */
    PROCESS_RUNTIME_DESCRIPTION("process.runtime.description"),
    /**
     * Runtime name of the process (for example JVM/CLR/runtime implementation name).
     */
    PROCESS_RUNTIME_NAME("process.runtime.name"),
    /**
     * Runtime version as returned by the runtime without modification.
     */
    PROCESS_RUNTIME_VERSION("process.runtime.version"),
    /**
     * Saved user ID (SUID) of the process.
     */
    PROCESS_SAVED_USER_ID("process.saved_user.id"),
    /**
     * Username of the saved user.
     */
    PROCESS_SAVED_USER_NAME("process.saved_user.name"),
    /**
     * PID of the process session leader.
     * This is also the session ID (SID).
     */
    PROCESS_SESSION_LEADER_PID("process.session_leader.pid"),
    /**
     * Process state.
     * Use OTel well-known values when applicable: {@code running}, {@code sleeping},
     * {@code stopped}, {@code defunct}.
     */
    PROCESS_STATE("process.state"),
    /**
     * Process title (proctitle) as shown by system monitoring tools.
     */
    PROCESS_TITLE("process.title"),
    /**
     * Effective user ID (EUID) of the process.
     */
    PROCESS_USER_ID("process.user.id"),
    /**
     * Username of the effective user of the process.
     */
    PROCESS_USER_NAME("process.user.name"),
    /**
     * Virtual process ID (VPID) in a PID namespace.
     * This is unique within the namespace, not necessarily host-global.
     */
    PROCESS_VPID("process.vpid"),
    /**
     * Current working directory of the process.
     */
    PROCESS_WORKING_DIRECTORY("process.working_directory"),

    // ---------------------------------------------------------------------
    // Kubernetes
    // ---------------------------------------------------------------------
    /**
     * Name of the Kubernetes cluster.
     * Use the cluster display/name identifier configured for operators.
     */
    K8S_CLUSTER_NAME("k8s.cluster.name"),
    /**
     * Pseudo-ID for the cluster, typically the UID of the {@code kube-system} namespace.
     * Use as the stable cluster identity attribute.
     */
    K8S_CLUSTER_UID("k8s.cluster.uid"),
    /**
     * Container name from Pod spec (unique within a Pod).
     * This differs from the runtime-global {@link #CONTAINER_NAME}.
     */
    K8S_CONTAINER_NAME("k8s.container.name"),
    /**
     * Number of container restarts.
     * Use to distinguish container instances across restarts within the same Pod spec.
     */
    K8S_CONTAINER_RESTART_COUNT("k8s.container.restart_count"),
    /**
     * Last terminated reason from container status.
     * Captures the previous termination cause when present.
     */
    K8S_CONTAINER_STATUS_LAST_TERMINATED_REASON("k8s.container.status.last_terminated_reason"),
    /**
     * Current reason for waiting/terminated container state.
     * Aligns with Kubernetes container status reason fields.
     */
    K8S_CONTAINER_STATUS_REASON("k8s.container.status.reason"),
    /**
     * Current container state.
     * Use well-known values when applicable: {@code running}, {@code waiting}, {@code terminated}.
     */
    K8S_CONTAINER_STATUS_STATE("k8s.container.status.state"),
    /**
     * Prefix for Kubernetes CronJob annotations.
     * Build a concrete key as {@code k8s.cronjob.annotation.<key>}.
     */
    K8S_CRONJOB_ANNOTATION("k8s.cronjob.annotation."),
    /**
     * Prefix for Kubernetes CronJob labels.
     * Build a concrete key as {@code k8s.cronjob.label.<key>}.
     */
    K8S_CRONJOB_LABEL("k8s.cronjob.label."),
    /**
     * Name of the CronJob.
     */
    K8S_CRONJOB_NAME("k8s.cronjob.name"),
    /**
     * UID of the CronJob.
     */
    K8S_CRONJOB_UID("k8s.cronjob.uid"),
    /**
     * Prefix for Kubernetes DaemonSet annotations.
     * Build a concrete key as {@code k8s.daemonset.annotation.<key>}.
     */
    K8S_DAEMONSET_ANNOTATION("k8s.daemonset.annotation."),
    /**
     * Prefix for Kubernetes DaemonSet labels.
     * Build a concrete key as {@code k8s.daemonset.label.<key>}.
     */
    K8S_DAEMONSET_LABEL("k8s.daemonset.label."),
    /**
     * Name of the DaemonSet.
     */
    K8S_DAEMONSET_NAME("k8s.daemonset.name"),
    /**
     * UID of the DaemonSet.
     */
    K8S_DAEMONSET_UID("k8s.daemonset.uid"),
    /**
     * Prefix for Kubernetes Deployment annotations.
     * Build a concrete key as {@code k8s.deployment.annotation.<key>}.
     */
    K8S_DEPLOYMENT_ANNOTATION("k8s.deployment.annotation."),
    /**
     * Prefix for Kubernetes Deployment labels.
     * Build a concrete key as {@code k8s.deployment.label.<key>}.
     */
    K8S_DEPLOYMENT_LABEL("k8s.deployment.label."),
    /**
     * Name of the Deployment.
     */
    K8S_DEPLOYMENT_NAME("k8s.deployment.name"),
    /**
     * UID of the Deployment.
     */
    K8S_DEPLOYMENT_UID("k8s.deployment.uid"),
    /**
     * Metric type configured in HPA behavior/evaluation.
     * Use the value as reported by the Kubernetes API.
     */
    K8S_HPA_METRIC_TYPE("k8s.hpa.metric.type"),
    /**
     * Name of the HorizontalPodAutoscaler.
     */
    K8S_HPA_NAME("k8s.hpa.name"),
    /**
     * API version of the HPA scale target reference.
     */
    K8S_HPA_SCALETARGETREF_API_VERSION("k8s.hpa.scaletargetref.api_version"),
    /**
     * Kind of the HPA scale target reference.
     */
    K8S_HPA_SCALETARGETREF_KIND("k8s.hpa.scaletargetref.kind"),
    /**
     * Name of the HPA scale target reference.
     */
    K8S_HPA_SCALETARGETREF_NAME("k8s.hpa.scaletargetref.name"),
    /**
     * UID of the HorizontalPodAutoscaler.
     */
    K8S_HPA_UID("k8s.hpa.uid"),
    /**
     * Huge page size for Kubernetes resources (for example {@code 2Mi}, {@code 1Gi}).
     */
    K8S_HUGEPAGE_SIZE("k8s.hugepage.size"),
    /**
     * Prefix for Kubernetes Job annotations.
     * Build a concrete key as {@code k8s.job.annotation.<key>}.
     */
    K8S_JOB_ANNOTATION("k8s.job.annotation."),
    /**
     * Prefix for Kubernetes Job labels.
     * Build a concrete key as {@code k8s.job.label.<key>}.
     */
    K8S_JOB_LABEL("k8s.job.label."),
    /**
     * Name of the Job.
     */
    K8S_JOB_NAME("k8s.job.name"),
    /**
     * UID of the Job.
     */
    K8S_JOB_UID("k8s.job.uid"),
    /**
     * Prefix for Kubernetes Namespace annotations.
     * Build a concrete key as {@code k8s.namespace.annotation.<key>}.
     */
    K8S_NAMESPACE_ANNOTATION("k8s.namespace.annotation."),
    /**
     * Prefix for Kubernetes Namespace labels.
     * Build a concrete key as {@code k8s.namespace.label.<key>}.
     */
    K8S_NAMESPACE_LABEL("k8s.namespace.label."),
    /**
     * Name of the Namespace.
     */
    K8S_NAMESPACE_NAME("k8s.namespace.name"),
    /**
     * Current namespace phase from Kubernetes API.
     * Use well-known values when applicable: {@code active}, {@code terminating}.
     */
    K8S_NAMESPACE_PHASE("k8s.namespace.phase"),
    /**
     * Prefix for Kubernetes Node annotations.
     * Build a concrete key as {@code k8s.node.annotation.<key>}.
     */
    K8S_NODE_ANNOTATION("k8s.node.annotation."),
    /**
     * Condition status for a Kubernetes Node condition.
     * Use well-known values when applicable: {@code true}, {@code false}, {@code unknown}.
     */
    K8S_NODE_CONDITION_STATUS("k8s.node.condition.status"),
    /**
     * Node condition type.
     * Use well-known values when applicable: {@code DiskPressure}, {@code MemoryPressure},
     * {@code NetworkUnavailable}, {@code PIDPressure}, {@code Ready}.
     */
    K8S_NODE_CONDITION_TYPE("k8s.node.condition.type"),
    /**
     * Prefix for Kubernetes Node labels.
     * Build a concrete key as {@code k8s.node.label.<key>}.
     */
    K8S_NODE_LABEL("k8s.node.label."),
    /**
     * Node name.
     */
    K8S_NODE_NAME("k8s.node.name"),
    /**
     * UID of the Node.
     */
    K8S_NODE_UID("k8s.node.uid"),
    /**
     * Prefix for Kubernetes Pod annotations.
     * Build a concrete key as {@code k8s.pod.annotation.<key>}.
     */
    K8S_POD_ANNOTATION("k8s.pod.annotation."),
    /**
     * Pod hostname as configured in Pod spec/status.
     */
    K8S_POD_HOSTNAME("k8s.pod.hostname"),
    /**
     * Pod IP address.
     */
    K8S_POD_IP("k8s.pod.ip"),
    /**
     * Prefix for Kubernetes Pod labels.
     * Build a concrete key as {@code k8s.pod.label.<key>}.
     */
    K8S_POD_LABEL("k8s.pod.label."),
    /**
     * Pod name.
     */
    K8S_POD_NAME("k8s.pod.name"),
    /**
     * Pod start timestamp in ISO 8601 format.
     * This aligns with PodStatus {@code startTime} (RFC 3339 compatible).
     */
    K8S_POD_START_TIME("k8s.pod.start_time"),
    /**
     * Pod lifecycle phase.
     * Use well-known values when applicable: {@code Pending}, {@code Running},
     * {@code Succeeded}, {@code Failed}, {@code Unknown}.
     */
    K8S_POD_STATUS_PHASE("k8s.pod.status.phase"),
    /**
     * Pod status reason.
     * Use the reason string as reported by Kubernetes.
     */
    K8S_POD_STATUS_REASON("k8s.pod.status.reason"),
    /**
     * UID of the Pod.
     */
    K8S_POD_UID("k8s.pod.uid"),
    /**
     * Prefix for Kubernetes ReplicaSet annotations.
     * Build a concrete key as {@code k8s.replicaset.annotation.<key>}.
     */
    K8S_REPLICASET_ANNOTATION("k8s.replicaset.annotation."),
    /**
     * Prefix for Kubernetes ReplicaSet labels.
     * Build a concrete key as {@code k8s.replicaset.label.<key>}.
     */
    K8S_REPLICASET_LABEL("k8s.replicaset.label."),
    /**
     * Name of the ReplicaSet.
     */
    K8S_REPLICASET_NAME("k8s.replicaset.name"),
    /**
     * UID of the ReplicaSet.
     */
    K8S_REPLICASET_UID("k8s.replicaset.uid"),
    /**
     * Name of the ReplicationController.
     */
    K8S_REPLICATIONCONTROLLER_NAME("k8s.replicationcontroller.name"),
    /**
     * UID of the ReplicationController.
     */
    K8S_REPLICATIONCONTROLLER_UID("k8s.replicationcontroller.uid"),
    /**
     * Name of the ResourceQuota.
     */
    K8S_RESOURCEQUOTA_NAME("k8s.resourcequota.name"),
    /**
     * Name of the limited resource in a ResourceQuota (for example {@code limits.cpu}).
     */
    K8S_RESOURCEQUOTA_RESOURCE_NAME("k8s.resourcequota.resource_name"),
    /**
     * UID of the ResourceQuota.
     */
    K8S_RESOURCEQUOTA_UID("k8s.resourcequota.uid"),
    /**
     * Prefix for Kubernetes Service annotations.
     * Build a concrete key as {@code k8s.service.annotation.<key>}.
     */
    K8S_SERVICE_ANNOTATION("k8s.service.annotation."),
    /**
     * Address type of the endpoint.
     * Use well-known values when applicable: {@code IPv4}, {@code IPv6}, {@code FQDN}.
     */
    K8S_SERVICE_ENDPOINT_ADDRESS_TYPE("k8s.service.endpoint.address_type"),
    /**
     * Endpoint condition.
     * Use well-known values when applicable: {@code ready}, {@code serving}, {@code terminating}.
     */
    K8S_SERVICE_ENDPOINT_CONDITION("k8s.service.endpoint.condition"),
    /**
     * Endpoint zone from endpoint hints/topology.
     */
    K8S_SERVICE_ENDPOINT_ZONE("k8s.service.endpoint.zone"),
    /**
     * Prefix for Kubernetes Service labels.
     * Build a concrete key as {@code k8s.service.label.<key>}.
     */
    K8S_SERVICE_LABEL("k8s.service.label."),
    /**
     * Name of the Service.
     */
    K8S_SERVICE_NAME("k8s.service.name"),
    /**
     * Whether Service routes traffic to not-ready endpoints.
     */
    K8S_SERVICE_PUBLISH_NOT_READY_ADDRESSES("k8s.service.publish_not_ready_addresses"),
    /**
     * Prefix for Kubernetes Service selectors.
     * Build a concrete key as {@code k8s.service.selector.<key>}.
     */
    K8S_SERVICE_SELECTOR("k8s.service.selector."),
    /**
     * Service traffic distribution policy.
     * Known values include {@code PreferSameZone} and {@code PreferSameNode}.
     * If not set on the Service, this attribute SHOULD NOT be emitted.
     */
    K8S_SERVICE_TRAFFIC_DISTRIBUTION("k8s.service.traffic_distribution"),
    /**
     * Service type.
     * Use well-known values when applicable: {@code ClusterIP}, {@code NodePort},
     * {@code LoadBalancer}, {@code ExternalName}.
     */
    K8S_SERVICE_TYPE("k8s.service.type"),
    /**
     * UID of the Service.
     */
    K8S_SERVICE_UID("k8s.service.uid"),
    /**
     * Prefix for Kubernetes StatefulSet annotations.
     * Build a concrete key as {@code k8s.statefulset.annotation.<key>}.
     */
    K8S_STATEFULSET_ANNOTATION("k8s.statefulset.annotation."),
    /**
     * Prefix for Kubernetes StatefulSet labels.
     * Build a concrete key as {@code k8s.statefulset.label.<key>}.
     */
    K8S_STATEFULSET_LABEL("k8s.statefulset.label."),
    /**
     * Name of the StatefulSet.
     */
    K8S_STATEFULSET_NAME("k8s.statefulset.name"),
    /**
     * UID of the StatefulSet.
     */
    K8S_STATEFULSET_UID("k8s.statefulset.uid"),
    /**
     * Name of the StorageClass.
     */
    K8S_STORAGECLASS_NAME("k8s.storageclass.name"),
    /**
     * Volume name from Pod spec.
     */
    K8S_VOLUME_NAME("k8s.volume.name"),
    /**
     * Kubernetes volume source type.
     * Use well-known values when applicable: {@code configMap}, {@code downwardAPI},
     * {@code emptyDir}, {@code local}, {@code persistentVolumeClaim}, {@code secret}.
     */
    K8S_VOLUME_TYPE("k8s.volume.type"),

    // ---------------------------------------------------------------------
    // Kubernetes (Deprecated)
    // ---------------------------------------------------------------------
    /**
     * Deprecated prefix for Kubernetes Pod labels.
     * Build a concrete key as {@code k8s.pod.labels.<key>}.
     * Replaced by {@code k8s.pod.label.<key>}.
     */
    K8S_POD_LABELS("k8s.pod.labels."),

    // ---------------------------------------------------------------------
    // Cloud (Resource)
    // ---------------------------------------------------------------------
    /**
     * Cloud account ID the monitored resource belongs to (for example AWS account ID).
     * Resource attribute: set for cloud-hosted resources whenever the account is known.
     */
    CLOUD_ACCOUNT_ID("cloud.account.id"),
    /**
     * Availability zone where the resource runs.
     * Use provider-native zone identifiers (for example {@code us-east-1c}).
     */
    CLOUD_AVAILABILITY_ZONE("cloud.availability_zone"),
    /**
     * Cloud platform/service in use (for example {@code aws_ec2}, {@code aws_lambda}, {@code gcp_cloud_run}).
     * If a well-known OTel value applies, it MUST be used; otherwise a custom value MAY be used.
     * The service prefix SHOULD match {@link #CLOUD_PROVIDER}.
     */
    CLOUD_PLATFORM("cloud.platform"),
    /**
     * Name of the cloud provider (for example {@code aws}, {@code azure}, {@code gcp}).
     * If a well-known OTel value applies, it MUST be used; otherwise a custom value MAY be used.
     */
    CLOUD_PROVIDER("cloud.provider"),
    /**
     * Geographic region within the cloud provider (for example {@code us-east-1}, {@code us-central1}).
     * Use provider-defined region identifiers.
     */
    CLOUD_REGION("cloud.region"),
    /**
     * Provider-native resource identifier (for example AWS ARN, Azure fully qualified resource ID, GCP URI).
     * Resource attribute by default; if full ID is unavailable at startup, this MAY be recorded as a span attribute.
     */
    CLOUD_RESOURCE_ID("cloud.resource_id"),

    // ---------------------------------------------------------------------
    // Service
    // ---------------------------------------------------------------------
    /**
     * Operational criticality of the service.
     * If one well-known value applies, it MUST be used: {@code critical}, {@code high},
     * {@code medium}, {@code low}; otherwise a custom value MAY be used.
     */
    SERVICE_CRITICALITY("service.criticality"),
    /**
     * Version of the service component.
     * Resource attribute: format is intentionally not constrained by OTel.
     */
    SERVICE_VERSION("service.version"),
    /**
     * Namespace that scopes {@code service.name}.
     * Resource attribute: use to group related services; {@code service.name} should be unique inside the namespace.
     */
    SERVICE_NAMESPACE("service.namespace"),
    /**
     * Logical name of the remote service on the other side of a connection.
     * SHOULD be equal to the remote service's actual {@code service.name} resource value when known.
     */
    SERVICE_PEER_NAME("service.peer.name"),
    /**
     * Logical namespace of the remote service on the other side of a connection.
     * SHOULD be equal to the remote service's actual {@code service.namespace} resource value when known.
     */
    SERVICE_PEER_NAMESPACE("service.peer.namespace"),

    // ---------------------------------------------------------------------
    // Code
    // ---------------------------------------------------------------------
    /**
     * Source column number best representing the operation inside {@link #CODE_FILE_PATH}.
     * It SHOULD point within the code unit named in {@link #CODE_FUNCTION_NAME}.
     */
    CODE_COLUMN_NUMBER("code.column.number"),
    /**
     * Source code file path identifying the code unit as uniquely as possible.
     * Prefer absolute file paths when available.
     */
    CODE_FILE_PATH("code.file.path"),
    /**
     * Fully-qualified method/function name without arguments.
     * Use the runtime-native representation (typically consistent with frames in {@link #CODE_STACKTRACE}).
     */
    CODE_FUNCTION_NAME("code.function.name"),
    /**
     * Source line number best representing the operation inside {@link #CODE_FILE_PATH}.
     * It SHOULD point within the code unit named in {@link #CODE_FUNCTION_NAME}.
     */
    CODE_LINE_NUMBER("code.line.number"),
    /**
     * Stacktrace string in the runtime-native format.
     * Representation is the same format used by {@link #EXCEPTION_STACKTRACE}.
     */
    CODE_STACKTRACE("code.stacktrace"),
    /**
     * Deprecated code namespace attribute.
     * Namespace is now expected to be included in {@link #CODE_FUNCTION_NAME}.
     */
    CODE_NAMESPACE("code.namespace"),

    // ---------------------------------------------------------------------
    // RFC5424 Syslog
    // ---------------------------------------------------------------------
    /**
     * Syslog FACILITY value describing where the event originated.
     * From RFC5424 mapping in the OTel Logs Data Model Appendix.
     */
    SYSLOG_FACILITY("syslog.facility"),
    /**
     * Syslog protocol VERSION value.
     * This is protocol metadata and does not represent event severity/content.
     */
    SYSLOG_VERSION("syslog.version"),
    /**
     * Syslog PROCID field from RFC5424 header.
     * Typically identifies the process instance that emitted the event.
     */
    SYSLOG_PROCID("syslog.procid"),
    /**
     * Syslog MSGID field from RFC5424 header.
     * Identifies the event type/category in producer-defined terms.
     */
    SYSLOG_MSGID("syslog.msgid"),
    /**
     * Client address: domain when available (without reverse lookup), otherwise IP or Unix socket name.
     * When observed through intermediaries, prefer the original client behind proxy hops when known.
     */
    CLIENT_ADDRESS("client.address"),

    // ---------------------------------------------------------------------
    // Windows Event Log
    // ---------------------------------------------------------------------
    /**
     * Windows Event Log event identifier (EventID) from the provider.
     * Used to identify the event definition/template.
     */
    WINLOG_EVENT_ID("winlog.event_id"),

    // ---------------------------------------------------------------------
    // SignalFx Events
    // ---------------------------------------------------------------------
    /**
     * SignalFx-specific event type.
     * Short machine-readable identifier describing the event kind.
     */
    COM_SPLUNK_SIGNALFX_EVENT_TYPE("com.splunk.signalfx.event_type"),
    /**
     * SignalFx-specific event category.
     * Describes the source/category for the SignalFx event.
     */
    COM_SPLUNK_SIGNALFX_EVENT_CATEGORY("com.splunk.signalfx.event_category"),

    // ---------------------------------------------------------------------
    // Splunk HEC
    // ---------------------------------------------------------------------
    /**
     * Splunk HEC source field.
     * Use for source identity expected by Splunk indexing/search pipelines.
     */
    COM_SPLUNK_SOURCE("com.splunk.source"),
    /**
     * Splunk HEC sourcetype field.
     * Use the sourcetype configured for downstream parsing/routing.
     */
    COM_SPLUNK_SOURCETYPE("com.splunk.sourcetype"),
    /**
     * Splunk HEC index field.
     * Name of the target index where the event should be routed.
     */
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
    /**
     * Peer address of the network connection (IP or Unix domain socket name).
     */
    NETWORK_PEER_ADDRESS("network.peer.address"),
    /**
     * Local address of the network connection (IP or Unix domain socket name).
     */
    NETWORK_LOCAL_ADDRESS("network.local.address"),
    /**
     * HTTP request method.
     * Use known methods from OTel HTTP conventions when applicable.
     */
    HTTP_REQUEST_METHOD("http.request.method"),
    /**
     * Absolute URL of the request target according to RFC3986.
     * Scrub sensitive URL components before recording.
     */
    URL_FULL("url.full"),
    /**
     * HTTP response status code.
     */
    HTTP_RESPONSE_STATUS_CODE("http.response.status_code"),

    // ---------------------------------------------------------------------
    // CloudTrail Log Event
    // ---------------------------------------------------------------------
    /**
     * AWS CloudTrail error code for failed service requests.
     * This is typically the value of CloudTrail {@code errorCode}.
     */
    CLOUDTRAIL_ERROR_CODE("cloudtrail.error_code"),

    // ---------------------------------------------------------------------
    // Google Cloud Logging
    // ---------------------------------------------------------------------
    /**
     * Google Cloud Logging log stream identifier from log entry {@code log_name}.
     * Typically the URL-encoded LOG_ID suffix identifying the stream.
     */
    GCP_LOG_NAME("gcp.log_name"),
    /**
     * Google Cloud Logging structured HTTP request object.
     * Preserves provider-specific HTTP request metadata from log entries.
     */
    GCP_HTTP_REQUEST("gcp.http_request"),

    // ---------------------------------------------------------------------
    // Elastic Common Schema
    // ---------------------------------------------------------------------
    /**
     * ECS agent ephemeral identifier.
     * Changes across agent restarts/lifecycles.
     */
    AGENT_EPHEMERAL_ID("agent.ephemeral_id"),
    /**
     * ECS stable agent identifier.
     */
    AGENT_ID("agent.id"),
    /**
     * ECS cloud availability zone (mapped to cloud zone naming).
     */
    CLOUD_ZONE("cloud.zone"),
    /**
     * ECS cloud instance identifier.
     */
    CLOUD_INSTANCE_ID("cloud.instance.id"),
    /**
     * ECS cloud instance name.
     */
    CLOUD_INSTANCE_NAME("cloud.instance.name"),
    /**
     * ECS cloud machine type/shape.
     */
    CLOUD_MACHINE_TYPE("cloud.machine.type"),
    /**
     * ECS container image tag value.
     */
    CONTAINER_IMAGE_TAG("container.image.tag"),
    /**
     * ECS container runtime name.
     */
    CONTAINER_RUNTIME("container.runtime"),
    /**
     * Destination address in network exchanges.
     * Use domain when available, otherwise IP or Unix socket name.
     */
    DESTINATION_ADDRESS("destination.address"),
    /**
     * Error code describing the error class/result.
     */
    ERROR_CODE("error.code"),
    /**
     * Unique identifier of a specific error occurrence.
     */
    ERROR_ID("error.id"),
    /**
     * Human-readable error message.
     * Avoid duplicating other error fields when possible.
     */
    ERROR_MESSAGE("error.message"),
    /**
     * Plain-text stack trace associated with the error.
     */
    ERROR_STACK_TRACE("error.stack_trace"),
    /**
     * ECS host architecture string.
     */
    HOST_ARCHITECTURE("host.architecture"),
    /**
     * Domain to which the host belongs (for example AD/LDAP domain).
     */
    HOST_DOMAIN("host.domain"),
    /**
     * Ephemeral service identifier (changes across service lifecycle/restarts).
     */
    SERVICE_EPHEMERAL_ID("service.ephemeral_id"),
    /**
     * Stable service identifier shared across service nodes/instances.
     */
    SERVICE_ID("service.id"),
    /**
     * Current service state (provider/application defined).
     */
    SERVICE_STATE("service.state"),
    /**
     * Service type/classification (for example api, worker, database-proxy).
     */
    SERVICE_TYPE("service.type"),

    // ---------------------------------------------------------------------
    // Browser
    // ---------------------------------------------------------------------
    /**
     * Browser brands from User-Agent Client Hints.
     * Array entries are brand + version pairs.
     */
    BROWSER_BRANDS("browser.brands"),
    /**
     * Preferred browser language (for example {@code en-US}).
     */
    BROWSER_LANGUAGE("browser.language"),
    /**
     * True when browser is running on a mobile device.
     * If unavailable from client hints, this attribute should be left unset.
     */
    BROWSER_MOBILE("browser.mobile"),
    /**
     * Browser platform from User-Agent Client Hints.
     * If unavailable, legacy {@code navigator.platform} SHOULD NOT be used and this SHOULD be left unset.
     */
    BROWSER_PLATFORM("browser.platform"),
    /**
     * Full original User-Agent string.
     * In browser contexts, emit this only when granular browser attributes cannot be obtained via client hints.
     */
    USER_AGENT_ORIGINAL("user_agent.original"),

    // ---------------------------------------------------------------------
    // Device
    // ---------------------------------------------------------------------
    /**
     * Unique device identifier.
     * May contain sensitive data; collection should be opt-in and privacy-reviewed.
     */
    DEVICE_ID("device.id"),
    /**
     * Device manufacturer name.
     */
    DEVICE_MANUFACTURER("device.manufacturer"),
    /**
     * Machine-readable device model identifier.
     */
    DEVICE_MODEL_IDENTIFIER("device.model.identifier"),
    /**
     * Human-readable marketing model name.
     */
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
