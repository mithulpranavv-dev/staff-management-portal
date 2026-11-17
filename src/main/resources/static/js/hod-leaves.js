let employeesMap = new Map();

async function loadEmployeesForLookup() {
    try {
        const response = await fetch('/api/employees/my-department', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const employees = await response.json();
        employees.forEach(emp => employeesMap.set(Number(emp.empId), emp));
    } catch (err) {
        console.error('Error loading employees for lookup:', err);
    }
}

async function loadMyDepartmentLeaves() {
    try {
        const response = await fetch('/api/leaves/my-department', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.status === 403) {
            document.getElementById('leavesTableBody').innerHTML =
                `<tr><td colspan="6" class="text-center text-muted py-4">You do not have permission to view this.</td></tr>`;
            return;
        }

        if (!response.ok) throw new Error('Failed to load leave requests');

        const leaves = await response.json();
        const tableBody = document.getElementById('leavesTableBody');
        tableBody.innerHTML = '';

        if (leaves.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">No leave requests found.</td></tr>`;
            return;
        }

        leaves.forEach(leave => {
            const emp = employeesMap.get(Number(leave.empId));
            const empName = emp ? emp.name : `Employee #${leave.empId}`;

            const badgeClass =
                leave.status === 'Approved' ? 'bg-success' :
                    leave.status === 'Rejected' ? 'bg-danger' :
                        'bg-warning text-dark';

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${leave.leaveId}</strong></td>
                <td>${empName}</td>
                <td><span class="badge bg-secondary">${leave.leaveType}</span></td>
                <td>${leave.fromDate} <span class="text-muted">to</span> ${leave.toDate}</td>
                <td>
                    <small>${leave.reason || '-'}</small>
                    ${leave.status === 'Rejected' && leave.rejectionReason ? `<br><small class="text-danger">Reason: ${leave.rejectionReason}</small>` : ''}
                </td>
                <td><span class="badge ${badgeClass}">${leave.status}</span></td>
            `;
            tableBody.appendChild(row);
        });
    } catch (err) {
        console.error('Error loading department leaves:', err);
    }
}

(async function init() {
    await loadEmployeesForLookup();
    await loadMyDepartmentLeaves();
})();