package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.Task;
import com.staffportal.staff_management_portal.model.TaskAssignment;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.EmployeeRepository;
import com.staffportal.staff_management_portal.repository.TaskAssignmentRepository;
import com.staffportal.staff_management_portal.repository.TaskRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskAssignmentRepository taskAssignmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    public Task assignTask(String title, String description, String priority,
                           String dueDate, List<Long> empIds) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority);
        task.setDueDate(dueDate);
        task.setStatus("Pending");

        Task savedTask = taskRepository.save(task);

        for (Long empId : empIds) {
            Employee employee = employeeRepository.findById(empId)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + empId));

            TaskAssignment assignment = new TaskAssignment();
            assignment.setTask(savedTask);
            assignment.setEmployee(employee);
            taskAssignmentRepository.save(assignment);
        }

        return savedTask;
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public List<Task> getTasksByEmployee(Long empId) {
        List<TaskAssignment> assignments = taskAssignmentRepository.findByEmployee_EmpId(empId);
        return assignments.stream()
                .map(TaskAssignment::getTask)
                .collect(Collectors.toList());
    }

    // Saves the completion date when a task becomes Completed, and clears it for any other status.
    // The on-time rate in Performance Insights depends on this date.
    public Task updateTaskStatus(Long taskId, String status) {
        return taskRepository.findById(taskId).map(task -> {
            task.setStatus(status);
            if ("Completed".equalsIgnoreCase(status)) {
                task.setCompletedDate(LocalDate.now().toString());
            } else {
                task.setCompletedDate(null);
            }
            return taskRepository.save(task);
        }).orElse(null);
    }

    public boolean deleteTask(Long taskId) {
        if (taskRepository.existsById(taskId)) {
            taskRepository.deleteById(taskId);
            return true;
        }
        return false;
    }

    // ---- Self-scoped access (for Staff portal) ----

    public List<Task> getMyTasks(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }

        Long myEmpId = user.getEmployee().getEmpId();
        return getTasksByEmployee(myEmpId);
    }

    // ---- Department scoping (for HOD portal) ----

    public List<Task> getTasksForMyDepartment(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }

        Department department = user.getEmployee().getDepartment();

        Set<Long> deptEmpIds = employeeRepository.findByDepartment(department).stream()
                .map(Employee::getEmpId)
                .collect(Collectors.toSet());

        // A task belongs to "my department" if at least one assignee is in my department
        return taskRepository.findAll().stream()
                .filter(task -> task.getAssignments().stream()
                        .anyMatch(a -> deptEmpIds.contains(a.getEmployee().getEmpId())))
                .distinct()
                .collect(Collectors.toList());
    }
}