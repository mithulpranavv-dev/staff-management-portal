package com.staffportal.staff_management_portal.repository;

import com.staffportal.staff_management_portal.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmployee_EmpId(Long empId);
}