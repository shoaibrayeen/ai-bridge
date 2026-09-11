package com.aibridge.dto.openai;

import com.aibridge.model.LlmConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** One entry of {@code GET /v1/models}, in the OpenAI model-object shape. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelObject {

    private String id;
    private String object = "model";
    private Long created;

    @JsonProperty("owned_by")
    private String ownedBy;

    /** The provider's own model name that this gateway model resolves to. */
    private String root;

    /** Feature labels this config answers to — an AIBridge extension to the OpenAI shape. */
    private List<String> features;

    public static ModelObject from(LlmConfig config) {
        ModelObject m = new ModelObject();
        m.id = config.getGatewayModelName();
        m.created = config.getCreatedAt() == null ? null : config.getCreatedAt().getEpochSecond();
        m.ownedBy = config.getProvider() == null ? null : config.getProvider().getName().name().toLowerCase();
        m.root = config.getModelName();
        m.features = config.getFeatures() == null
                ? List.of()
                : config.getFeatures().stream().map(f -> f.getFeature()).sorted().toList();
        return m;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public String getOwnedBy() {
        return ownedBy;
    }

    public void setOwnedBy(String ownedBy) {
        this.ownedBy = ownedBy;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }
}
