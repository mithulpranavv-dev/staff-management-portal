package com.staffportal.staff_management_portal.repository;

import com.staffportal.staff_management_portal.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByEmpId(Long empId);

    List<Attendance> findByEmpIdIn(List<Long> empIds);

    List<Attendance> findByEmpIdAndAttDateBetween(Long empId, LocalDate start, LocalDate end);

    void deleteByEmpId(Long empId);

    Optional<Attendance> findByEmpIdAndAttDate(Long empId, LocalDate attDate);

    long countByEmpIdAndStatusAndAttDateBetween(Long empId, String status, LocalDate start, LocalDate end);
}