package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#CLOUD_PLATFORM}.
 */
public enum CloudPlatformKnownValuesEnum implements OTelTagKnownValue {
    AKAMAI_CLOUD_COMPUTE("akamai_cloud.compute"),
    ALIBABA_CLOUD_ECS("alibaba_cloud_ecs"),
    ALIBABA_CLOUD_FC("alibaba_cloud_fc"),
    ALIBABA_CLOUD_OPENSHIFT("alibaba_cloud_openshift"),
    AWS_APP_RUNNER("aws_app_runner"),
    AWS_EC2("aws_ec2"),
    AWS_ECS("aws_ecs"),
    AWS_EKS("aws_eks"),
    AWS_ELASTIC_BEANSTALK("aws_elastic_beanstalk"),
    AWS_LAMBDA("aws_lambda"),
    AWS_OPENSHIFT("aws_openshift"),
    AZURE_AKS("azure.aks"),
    AZURE_APP_SERVICE("azure.app_service"),
    AZURE_CONTAINER_APPS("azure.container_apps"),
    AZURE_CONTAINER_INSTANCES("azure.container_instances"),
    AZURE_FUNCTIONS("azure.functions"),
    AZURE_OPENSHIFT("azure.openshift"),
    AZURE_VM("azure.vm"),
    GCP_AGENT_ENGINE("gcp.agent_engine"),
    GCP_APP_ENGINE("gcp_app_engine"),
    GCP_BARE_METAL_SOLUTION("gcp_bare_metal_solution"),
    GCP_CLOUD_FUNCTIONS("gcp_cloud_functions"),
    GCP_CLOUD_RUN("gcp_cloud_run"),
    GCP_COMPUTE_ENGINE("gcp_compute_engine"),
    GCP_KUBERNETES_ENGINE("gcp_kubernetes_engine"),
    GCP_OPENSHIFT("gcp_openshift"),
    HETZNER_CLOUD_SERVER("hetzner.cloud_server"),
    IBM_CLOUD_OPENSHIFT("ibm_cloud_openshift"),
    ORACLE_CLOUD_COMPUTE("oracle_cloud_compute"),
    ORACLE_CLOUD_OKE("oracle_cloud_oke"),
    TENCENT_CLOUD_CVM("tencent_cloud_cvm"),
    TENCENT_CLOUD_EKS("tencent_cloud_eks"),
    TENCENT_CLOUD_SCF("tencent_cloud_scf"),
    VULTR_CLOUD_COMPUTE("vultr.cloud_compute");

    private final String value;

    CloudPlatformKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.CLOUD_PLATFORM;
    }

    @Override
    public String getValue() {
        return value;
    }
}

