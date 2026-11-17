// Uses the global `token` declared in dashboard.js. Do not redeclare it here.
let employeesMap = new Map();
let allTasks = [];

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

async function loadDepartmentStaff() {
    try {
        const response = await fetch('/api/employees/my-department', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error('Failed to load department staff');
        const employees = await response.json();

        const select = document.getElementById('taskEmpSelect');
        select.innerHTML = '';
        employeesMap.clear();

        employees.forEach(emp => {
            employeesMap.set(Number(emp.empId), emp);

            const option = document.createElement('option');
            option.value = emp.empId;
            const codeDisplay = emp.employeeCode ? ` (${emp.employeeCode})` : '';
            option.textContent = `${emp.name}${codeDisplay}`;
            select.appendChild(option);
        });
    } catch (err) {
        console.error('Error loading department staff:', err);
    }
}

function getPriorityBadge(priority) {
    switch (priority) {
        case 'Urgent': return '<span class="badge bg-danger">Urgent</span>';
        case 'High': return '<span class="badge bg-warning text-dark">High</span>';
        case 'Medium': return '<span class="badge bg-info text-dark">Medium</span>';
        case 'Low': return '<span class="badge bg-secondary">Low</span>';
        default: return `<span class="badge bg-light text-dark border">${escapeHtml(priority) || 'Normal'}</span>`;
    }
}

function getStatusBadgeClass(status) {
    if (status === 'Completed') return 'bg-success';
    if (status === 'In Progress') return 'bg-info text-dark';
    return 'bg-warning text-dark';
}

async function loadTasks() {
    try {
        const response = await fetch('/api/tasks/my-department', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.status === 403) {
            document.getElementById('tasksTableBody').innerHTML =
                `<tr><td colspan="7" class="text-center text-muted py-4">You do not have permission to view tasks.</td></tr>`;
            allTasks = [];
            return;
        }

        if (!response.ok) throw new Error('Failed to load tasks');
        allTasks = await response.json();
        allTasks.sort((a, b) => b.taskId - a.taskId);

        renderTasksTable();
    } catch (err) {
        console.error('Error loading tasks:', err);
    }
}

function renderTasksTable() {
    const filterValue = document.getElementById('filterStatus').value;
    const tableBody = document.getElementById('tasksTableBody');
    tableBody.innerHTML = '';

    const filtered = allTasks.filter(t => filterValue === 'ALL' || t.status === filterValue);

    if (filtered.length === 0) {
        tableBody.innerHTML = `<tr><td colspan="7" class="text-center text-muted py-4">No department tasks found.</td></tr>`;
        return;
    }

    filtered.forEach(task => {
        const assignees = task.assignedEmployees || [];
        let staffDisplay;

        if (assignees.length === 0) {
            staffDisplay = '<span class="text-muted">Unassigned</span>';
        } else if (assignees.length === 1) {
            staffDisplay = `<strong>${escapeHtml(assignees[0].name)}</strong><br><small class="text-muted">${escapeHtml(assignees[0].employeeCode)}</small>`;
        } else {
            const names = assignees.map(a => a.name).join(', ');
            const shortNames = names.length > 40 ? names.substring(0, 40) + '...' : names;
            staffDisplay = `<strong>${assignees.length} staff</strong><br><small class="text-muted" title="${escapeHtml(names)}">${escapeHtml(shortNames)}</small>`;
        }

        const row = document.createElement('tr');
        row.innerHTML = `
            <td><strong>#${Number(task.taskId)}</strong></td>
            <td>${staffDisplay}</td>
            <td>
                <strong>${escapeHtml(task.title)}</strong>
                <br><small class="text-muted">${escapeHtml(task.description) || '-'}</small>
            </td>
            <td>${getPriorityBadge(task.priority)}</td>
            <td><small>${escapeHtml(task.dueDate) || '-'}</small></td>
            <td><span class="badge ${getStatusBadgeClass(task.status)}">${escapeHtml(task.status)}</span></td>
            <td class="text-end">
                <button class="btn btn-sm btn-outline-danger" onclick="deleteTask(${Number(task.taskId)})">Delete</button>
            </td>
        `;
        tableBody.appendChild(row);
    });
}

document.getElementById('createTaskForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const select = document.getElementById('taskEmpSelect');
    const empIds = Array.from(select.selectedOptions).map(opt => Number(opt.value));

    const title = document.getElementById('taskTitle').value.trim();
    const priority = document.getElementById('taskPriority').value;
    const dueDate = document.getElementById('taskDueDate').value;
    const description = document.getElementById('taskDescription').value.trim();

    if (empIds.length === 0) {
        alert('Please select at least one staff member to assign!');
        return;
    }

    try {
        const response = await fetch('/api/tasks', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ title, priority, dueDate, description, empIds })
        });

        if (!response.ok) {
            let message = 'Could not assign the task.';
            try {
                const data = await response.json();
                if (data && data.error) message = data.error;
            } catch (ignored) { /* no JSON body */ }
            alert(message);
            return;
        }

        await loadTasks();

        document.getElementById('taskTitle').value = '';
        document.getElementById('taskDescription').value = '';
        select.selectedIndex = -1;
    } catch (err) {
        console.error('Error creating task:', err);
        alert('Something went wrong while assigning the task.');
    }
});

async function deleteTask(taskId) {
    if (!confirm('Are you sure you want to delete this task assignment?')) return;

    try {
        const response = await fetch(`/api/tasks/${taskId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.status === 403) {
            alert('You can only delete tasks from your own department.');
            return;
        }

        if (!response.ok) throw new Error('Failed to delete task');
        await loadTasks();
    } catch (err) {
        console.error('Error deleting task:', err);
    }
}

(async function init() {
    const futureDate = new Date();
    futureDate.setDate(futureDate.getDate() + 7);
    document.getElementById('taskDueDate').value = futureDate.toISOString().split('T')[0];

    await loadDepartmentStaff();
    await loadTasks();
})();