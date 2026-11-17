// Uses the global `token` declared in dashboard.js. Do not redeclare it here.
let myEmpId = null;

// Staff and HOD share this page, so the dashboard links must depend on the role.
function fixDashboardLinks() {
    const role = String(localStorage.getItem('userRole') || '').replace(/^ROLE_/, '').toUpperCase();
    const dashboards = {
        DEAN: 'dean-dashboard.html',
        ADMINISTRATOR: 'admin-dashboard.html',
        HEAD_OF_DEPARTMENT: 'hod-dashboard.html',
        FACULTY_MEMBER: 'staff-dashboard.html'
    };
    const target = dashboards[role];
    if (!target) return;

    document.querySelectorAll('a.navbar-brand').forEach(a => a.setAttribute('href', target));
    document.querySelectorAll('a').forEach(a => {
        if (a.textContent.indexOf('Back to Dashboard') !== -1) {
            a.setAttribute('href', target);
        }
    });
}

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

async function findMyEmpId() {
    try {
        const response = await fetch('/api/employees/me', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error('Failed to load own employee record');
        const me = await response.json();
        myEmpId = me.empId;
    } catch (err) {
        console.error('Error finding own employee record:', err);
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

async function loadMyTasks() {
    try {
        const response = await fetch('/api/tasks/me', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) throw new Error('Failed to load tasks');

        const tasks = await response.json();
        const tableBody = document.getElementById('tasksTableBody');
        tableBody.innerHTML = '';

        if (tasks.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">No tasks assigned to you yet.</td></tr>`;
            return;
        }

        tasks.sort((a, b) => b.taskId - a.taskId);

        tasks.forEach(task => {
            // Everyone assigned to this task EXCEPT me
            const others = (task.assignedEmployees || []).filter(a => Number(a.empId) !== Number(myEmpId));
            const workingWith = others.length > 0
                ? escapeHtml(others.map(a => a.name).join(', '))
                : '<span class="text-muted">Just you</span>';

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${Number(task.taskId)}</strong></td>
                <td>
                    <strong>${escapeHtml(task.title)}</strong>
                    <br><small class="text-muted">${escapeHtml(task.description) || '-'}</small>
                </td>
                <td>${getPriorityBadge(task.priority)}</td>
                <td><small>${escapeHtml(task.dueDate) || '-'}</small></td>
                <td>
                    <select class="form-select form-select-sm fw-bold border ${getStatusBadgeClass(task.status)}"
                            onchange="updateMyTaskStatus(${Number(task.taskId)}, this.value)" style="width: 140px;">
                        <option value="Pending" ${task.status === 'Pending' ? 'selected' : ''}>Pending</option>
                        <option value="In Progress" ${task.status === 'In Progress' ? 'selected' : ''}>In Progress</option>
                        <option value="Completed" ${task.status === 'Completed' ? 'selected' : ''}>Completed</option>
                    </select>
                </td>
                <td><small>${workingWith}</small></td>
            `;
            tableBody.appendChild(row);
        });
    } catch (err) {
        console.error('Error loading my tasks:', err);
    }
}

async function updateMyTaskStatus(taskId, newStatus) {
    try {
        const response = await fetch(`/api/tasks/${taskId}/status`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ status: newStatus })
        });

        if (!response.ok) throw new Error('Failed to update status');
        await loadMyTasks();
    } catch (err) {
        console.error('Error updating task status:', err);
        alert('Could not update task status.');
        await loadMyTasks();
    }
}

(async function init() {
    fixDashboardLinks();
    await findMyEmpId();
    await loadMyTasks();
})();