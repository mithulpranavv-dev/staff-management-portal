package com.staffportal.staff_management_portal.model;

public enum Department {
    CSE("Computer Science and Engineering"),
    ECE("Electronics and Communication Engineering"),
    EEE("Electrical and Electronics Engineering"),
    MECH("Mechanical Engineering"),
    CIVIL("Civil Engineering"),
    IT("Information Technology"),
    AI_DS("Artificial Intelligence and Data Science"),
    CHEMICAL("Chemical Engineering"),
    BIOMEDICAL("Biomedical Engineering"),
    MATHEMATICS("Mathematics"),
    PHYSICS("Physics"),
    CHEMISTRY("Chemistry");

    private final String displayName;

    Department(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}