package com.hms.dto;

import java.util.List;

public class AdministerItemsRequest {
    private List<ConsultationRequest.AdministeredItem> administeredItems;

    public List<ConsultationRequest.AdministeredItem> getAdministeredItems() {
        return administeredItems;
    }

    public void setAdministeredItems(List<ConsultationRequest.AdministeredItem> administeredItems) {
        this.administeredItems = administeredItems;
    }
}
