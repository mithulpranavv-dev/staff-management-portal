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

async function loadMyDepartmentAttendance() {
    try {
        const response = await fetch('/api/attendance/my-department', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.status === 403) {
            document.getElementById('attendanceTableBody').innerHTML =
                `<tr><td colspan="4" class="text-center text-muted py-4">You do not have permission to view this.</td></tr>`;
            return;
        }

        if (!response.ok) throw new Error('Failed to load attendance');

        const records = await response.json();
        const tableBody = document.getElementById('attendanceTableBody');
        tableBody.innerHTML = '';

        if (records.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="4" class="text-center text-muted py-4">No attendance records found.</td></tr>`;
            return;
        }

        records.forEach(att => {
            const emp = employeesMap.get(Number(att.empId));
            const empName = emp ? emp.name : `Employee #${att.empId}`;

            const badgeClass =
                att.status === 'Present' ? 'bg-success' :
                    att.status === 'Late' ? 'bg-warning text-dark' :
                        att.status === 'On Leave' ? 'bg-info text-dark' : 'bg-danger';

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${att.attId}</strong></td>
                <td>${empName}</td>
                <td>${att.attDate}</td>
                <td><span class="badge ${badgeClass}">${att.status}</span></td>
            `;
            tableBody.appendChild(row);
        });
    } catch (err) {
        console.error('Error loading department attendance:', err);
    }
}

(async function init() {
    await loadEmployeesForLookup();
    await loadMyDepartmentAttendance();
})();