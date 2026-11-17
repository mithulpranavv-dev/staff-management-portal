package com.staffportal.staff_management_portal.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "payroll_deduction_detail")
public class PayrollDeductionDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long detailId;

    @ManyToOne
    @JoinColumn(name = "payroll_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonBackReference
    private Payroll payroll;

    @Column(nullable = false)
    private LocalDate deductionDate;

    @Column(nullable = false)
    private Double amount;

    private String reason; // e.g. "Sick Leave - unapproved" or "Casual Leave beyond quota"

    public Long getDetailId() {
        return detailId;
    }

    public void setDetailId(Long detailId) {
        this.detailId = detailId;
    }

    public Payroll getPayroll() {
        return payroll;
    }

    public void setPayroll(Payroll payroll) {
        this.payroll = payroll;
    }

    public LocalDate getDeductionDate() {
        return deductionDate;
    }

    public void setDeductionDate(LocalDate deductionDate) {
        this.deductionDate = deductionDate;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}