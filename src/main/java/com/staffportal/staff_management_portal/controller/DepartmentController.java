package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.model.Department;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class DepartmentController {

    @GetMapping("/api/departments")
    public List<Map<String, String>> getAllDepartments() {
        return Arrays.stream(Department.values())
                .map(dept -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    map.put("value", dept.name());
                    map.put("label", dept.getDisplayName());
                    return map;
                })
                .collect(Collectors.toList());
    }
}