package com.aibridge.dto.openai;

import java.util.List;

/** Envelope for {@code GET /v1/models}: {@code {"object":"list","data":[...]}}. */
public class ModelListResponse {

    private String object = "list";
    private List<ModelObject> data;

    public ModelListResponse() {
    }

    public ModelListResponse(List<ModelObject> data) {
        this.data = data;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<ModelObject> getData() {
        return data;
    }

    public void setData(List<ModelObject> data) {
        this.data = data;
    }
}
