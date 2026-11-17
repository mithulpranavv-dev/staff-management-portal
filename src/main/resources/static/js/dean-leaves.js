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

async function loadEmployeesForLookup() {
    try {
        const response = await fetch('/api/employees', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const employees = await response.json();
        employees.forEach(emp => employeesMap.set(Number(emp.empId), emp));
    } catch (err) {
        console.error('Error loading employees for lookup:', err);
    }
}

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
                    ${leave.status === 'Pending' ? `
                        <button class="btn btn-sm btn-success me-1 font-bold" onclick="updateStatus(${Number(leave.leaveId)}, 'Approved')">Approve</button>
                        <button class="btn btn-sm btn-danger font-bold" onclick="updateStatus(${Number(leave.leaveId)}, 'Rejected')">Reject</button>
                    ` : ''}
                </td>
            `;
            tableBody.appendChild(row);
        });
    } catch (err) {
        console.error('Error loading leaves:', err);
    }
}

async function updateStatus(leaveId, newStatus) {
    let rejectionReason = null;

    if (newStatus === 'Rejected') {
        rejectionReason = prompt('Please provide a reason for rejecting this leave request:');
        if (rejectionReason === null || rejectionReason.trim() === '') {
            alert('A rejection reason is required.');
            return;
        }
    }

    try {
        const response = await fetch(`/api/leaves/${leaveId}/status`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ status: newStatus, rejectionReason })
        });

        if (response.status === 403) {
            alert('Only the Dean can approve or reject leave requests.');
            return;
        }
        if (!response.ok) {
            alert('Could not update this leave request. Please refresh and try again.');
            return;
        }

        loadLeaves();
    } catch (err) {
        console.error('Error updating leave status:', err);
        alert('Something went wrong while updating the leave request.');
    }
}

(async function init() {
    await loadEmployeesForLookup();
    await loadLeaves();
})();