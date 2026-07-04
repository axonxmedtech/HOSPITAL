package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTemplateDTO {
    private String name;
    private String type;
    private Boolean hasOwnStock;
    private List<String> suggestedRelativeItemNames;
}
