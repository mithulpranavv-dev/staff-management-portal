let employeesMap = new Map();

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
        const leaves = await response.json();

        const tableBody = document.getElementById('leavesTableBody');
        tableBody.innerHTML = '';

        if (leaves.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">No leave requests submitted yet.</td></tr>`;
            return;
        }

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
                    <strong>${empName}</strong>
                    <br><small class="text-muted">${empCode}</small>
                </td>
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
        console.error('Error loading leaves:', err);
    }
}

(async function init() {
    await loadEmployeesForLookup();
    await loadLeaves();
})();