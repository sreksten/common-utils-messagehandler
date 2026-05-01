package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Stefano Reksten
 */
public final class TracerProviderBuilder {
    private String serviceName;
    private String serviceVersion;
    private String serviceInstanceId;
    private String deploymentEnvironment;
    private String schemaUrl;
    private final List<KeyValue> resourceAttributes = new ArrayList<>();
    private final List<KeyValue> commonAttributes = new ArrayList<>();
    private String defaultFilePath;
    private Resource resource;

    public TracerProviderBuilder serviceName(final String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public TracerProviderBuilder serviceVersion(final String serviceVersion) {
        this.serviceVersion = serviceVersion;
        return this;
    }

    public TracerProviderBuilder serviceInstanceId(final String serviceInstanceId) {
        this.serviceInstanceId = serviceInstanceId;
        return this;
    }

    public TracerProviderBuilder deploymentEnvironment(final String deploymentEnvironment) {
        this.deploymentEnvironment = deploymentEnvironment;
        return this;
    }

    public TracerProviderBuilder schemaUrl(final String schemaUrl) {
        this.schemaUrl = schemaUrl;
        return this;
    }

    public TracerProviderBuilder resourceAttribute(final OTelTags tag, final String value) {
        if (tag == null) {
            OpenTelemetryAttributeValidator.reportBundled("valueMustNotBeNull");
            return this;
        }
        return resourceAttribute(tag.getValue(), value);
    }

    public TracerProviderBuilder resourceAttribute(final String key, final String value) {
        if (key == null || key.trim().isEmpty()) {
            OpenTelemetryAttributeValidator.reportBundled("emptyAttributeKeyNotAllowed");
            return this;
        }
        resourceAttributes.add(KeyValueFactory.of(key, AnyValueFactory.ofString(value == null ? "" : value)));
        return this;
    }

    public TracerProviderBuilder commonAttribute(final String key, final String value) {
        if (key == null || key.trim().isEmpty()) {
            OpenTelemetryAttributeValidator.reportBundled("emptyAttributeKeyNotAllowed");
            return this;
        }
        commonAttributes.add(KeyValueFactory.of(key, AnyValueFactory.ofString(value == null ? "" : value)));
        return this;
    }

    public TracerProviderBuilder defaultFilePath(final String defaultFilePath) {
        this.defaultFilePath = defaultFilePath;
        return this;
    }

    public TracerProviderBuilder resource(final Resource resource) {
        this.resource = resource;
        return this;
    }

    public TracerProvider build() {
        TracerProvider provider = TracerProvider.createProvider();
        provider.setDefaultSchemaUrl(schemaUrl);
        provider.setDefaultCommonAttributes(commonAttributes);
        provider.setDefaultFilePath(defaultFilePath);

        List<KeyValue> resolvedResourceAttributes = new ArrayList<>(resourceAttributes);
        maybeAddResourceAttribute(resolvedResourceAttributes, OTelTags.SERVICE_NAME.getValue(), serviceName);
        maybeAddResourceAttribute(resolvedResourceAttributes, OTelTags.SERVICE_VERSION.getValue(), serviceVersion);
        maybeAddResourceAttribute(resolvedResourceAttributes, OTelTags.SERVICE_INSTANCE_ID.getValue(), serviceInstanceId);
        maybeAddResourceAttribute(resolvedResourceAttributes, OTelTags.DEPLOYMENT_ENVIRONMENT_NAME.getValue(), deploymentEnvironment);
        Resource builderResource = ResourceFactory.create(schemaUrl, null, resolvedResourceAttributes);
        if (resource == null) {
            provider.setDefaultResource(builderResource);
        } else {
            provider.setDefaultResource(resource.merge(builderResource));
        }
        return provider;
    }

    private static void maybeAddResourceAttribute(final List<KeyValue> attributes,
                                                  final String key,
                                                  final String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        attributes.add(KeyValueFactory.of(key, AnyValueFactory.ofString(value.trim())));
    }
}
