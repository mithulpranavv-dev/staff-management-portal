package com.staffportal.staff_management_portal.repository;

import com.staffportal.staff_management_portal.model.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    List<TaskAssignment> findByEmployee_EmpId(Long empId);
    List<TaskAssignment> findByTask_TaskId(Long taskId);
    void deleteByEmployee_EmpId(Long empId);
}