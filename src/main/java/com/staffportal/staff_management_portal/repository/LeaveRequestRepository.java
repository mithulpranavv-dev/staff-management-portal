package com.staffportal.staff_management_portal.repository;

import com.staffportal.staff_management_portal.model.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmpId(Long empId);

    List<LeaveRequest> findByEmpIdIn(List<Long> empIds);
    void deleteByEmpId(Long empId);
}