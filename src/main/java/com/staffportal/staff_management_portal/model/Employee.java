package com.staffportal.staff_management_portal.model;

import jakarta.persistence.*;

@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long empId;

    @Column(unique = true)
    private String employeeCode;
    private Double salaryPerAnnum;
    private String name;
    private String email;
    private String designation;

    @Enumerated(EnumType.STRING)
    private Department department;

    @Transient
    private String tempPasswordForResponse;

    @Transient
    private String generatedUsernameForResponse;

    public Double getSalaryPerAnnum() {
        return salaryPerAnnum;
    }

    public void setSalaryPerAnnum(Double salaryPerAnnum) {
        this.salaryPerAnnum = salaryPerAnnum;
    }
    @Transient
    public Double getMonthlySalary() {
        return salaryPerAnnum != null ? salaryPerAnnum / 12.0 : null;
    }

    public Double getPerDaySalary(int daysInMonth) {
        Double monthly = getMonthlySalary();
        return monthly != null ? monthly / daysInMonth : null;
    }

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getTempPasswordForResponse() {
        return tempPasswordForResponse;
    }

    public void setTempPasswordForResponse(String tempPasswordForResponse) {
        this.tempPasswordForResponse = tempPasswordForResponse;
    }

    public String getGeneratedUsernameForResponse() {
        return generatedUsernameForResponse;
    }

    public void setGeneratedUsernameForResponse(String generatedUsernameForResponse) {
        this.generatedUsernameForResponse = generatedUsernameForResponse;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }
}