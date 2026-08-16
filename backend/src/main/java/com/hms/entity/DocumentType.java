package com.hms.entity;

/** What kind of outside record this is. OTHER exists so nothing is ever un-filable. */
public enum DocumentType {
    LAB_REPORT, XRAY, SCAN, PRESCRIPTION, DISCHARGE_SUMMARY, OTHER
}
