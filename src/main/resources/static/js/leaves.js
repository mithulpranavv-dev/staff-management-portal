// Uses the global `token` declared in dashboard.js. Do not redeclare it here.
let employeesMap = new Map();

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// Name lookup for the table (Admin is allowed to read the full employee list)
async function loadEmployeesForLookup() {
    try {
        const response = await fetch('/api/employees', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error('Failed to load employees');
        const employees = await response.json();
        employeesMap.clear();
        employees.forEach(emp => employeesMap.set(Number(emp.empId), emp));
    } catch (err) {
        console.error('Error loading employees for lookup:', err);
    }
}

// Load Leave Requests Table
async function loadLeaves() {
    try {
        const response = await fetch('/api/leaves', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error('Failed to load leaves');
        const leaves = await response.json();

        const tableBody = document.getElementById('leavesTableBody');
        tableBody.innerHTML = '';

        if (leaves.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="7" class="text-center text-muted py-4">No leave requests submitted yet.</td></tr>`;
            return;
        }

        leaves.sort((a, b) => b.leaveId - a.leaveId);

        leaves.forEach(leave => {
            const emp = employeesMap.get(Number(leave.empId));
            const empName = emp ? emp.name : `Employee #${leave.empId}`;
            const empCode = emp && emp.employeeCode ? emp.employeeCode : `ID: ${leave.empId}`;

            const badgeClass =
                leave.status === 'Approved' ? 'bg-success' :
                    leave.status === 'Rejected' ? 'bg-danger' :
                        'bg-warning text-dark';

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${leave.leaveId}</strong></td>
                <td>
                    <strong>${escapeHtml(empName)}</strong>
                    <br><small class="text-muted">${escapeHtml(empCode)}</small>
                </td>
                <td><span class="badge bg-secondary">${escapeHtml(leave.leaveType)}</span></td>
                <td>${escapeHtml(leave.fromDate)} <span class="text-muted">to</span> ${escapeHtml(leave.toDate)}</td>
                <td>
                    <small>${escapeHtml(leave.reason) || '-'}</small>
                    ${leave.status === 'Rejected' && leave.rejectionReason ? `<br><small class="text-danger">Reason: ${escapeHtml(leave.rejectionReason)}</small>` : ''}
                </td>
                <td><span class="badge ${badgeClass}">${escapeHtml(leave.status)}</span></td>
                <td class="text-end">
                    <button class="btn btn-sm btn-outline-secondary" onclick="deleteLeave(${Number(leave.leaveId)})">Delete</button>
                </td>
            `;
            tableBody.appendChild(row);
        });
    } catch (err) {
        console.error('Error loading leaves:', err);
    }
}

// Delete Leave Request (Admin only)
async function deleteLeave(leaveId) {
    if (!confirm('Are you sure you want to delete this leave request?')) return;

    try {
        const response = await fetch(`/api/leaves/${leaveId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.status === 403) {
            alert('You do not have permission to delete leave requests.');
            return;
        }
        if (!response.ok) {
            alert('Could not delete this leave request. Please refresh and try again.');
            return;
        }

        loadLeaves();
    } catch (err) {
        console.error('Error deleting leave:', err);
        alert('Something went wrong while deleting the leave request.');
    }
}

// Initial Load
(async function init() {
    await loadEmployeesForLookup();
    await loadLeaves();
})();