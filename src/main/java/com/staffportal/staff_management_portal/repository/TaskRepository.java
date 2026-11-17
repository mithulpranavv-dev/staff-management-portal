package com.staffportal.staff_management_portal.repository;

import com.staffportal.staff_management_portal.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}