package com.hms.dto;

/** Toggle a vital on/off, or define a new custom vital (name + unit). */
public class VitalSettingRequest {
    private Boolean enabled;
    private String name;
    private String unit;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
