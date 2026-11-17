var token = localStorage.getItem('token');
var userRole = localStorage.getItem('userRole');
var isDean = (userRole === 'DEAN');
var isAdministrator = (userRole === 'ADMINISTRATOR');
var isViewOnly = isDean || isAdministrator;

// Store employees map for quick name lookup in table
let employeesMap = new Map();

// Role-based dashboard link + hide Mark Attendance form for view-only roles
function applyRoleRestrictions() {
    const dashboardUrl = isDean ? 'dean-dashboard.html' : 'admin-dashboard.html';
    document.getElementById('navbarBrandLink').setAttribute('href', dashboardUrl);
    document.getElementById('backToDashboardLink').setAttribute('href', dashboardUrl);

    if (isViewOnly) {
        document.getElementById('markAttendanceCard').classList.add('d-none');
        document.getElementById('actionColHeader').classList.add('d-none');
    }
}

// Populate Employee Dropdown from REST API
async function loadEmployeeDropdown() {
    if (isViewOnly) return; // View-only roles don't mark attendance, no need to load this

    try {
        const response = await fetch('/api/employees', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const employees = await response.json();

        const select = document.getElementById('attEmpSelect');
        select.innerHTML = '<option value="">-- Select Staff Member --</option>';

        employeesMap.clear();

        employees.forEach(emp => {
            employeesMap.set(Number(emp.empId), emp);

            const option = document.createElement('option');
            option.value = emp.empId;
            const codeDisplay = emp.employeeCode ? ` (${emp.employeeCode})` : '';
            option.textContent = `${emp.name}${codeDisplay} - ${emp.department || 'General'}`;
            select.appendChild(option);
        });
    } catch (err) {
        console.error('Error loading employees dropdown:', err);
    }
}

// Load Attendance Table with Employee Names
async function loadAttendance() {
    try {
        const response = await fetch('/api/attendance', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const records = await response.json();

        // If view-only role (didn't load employeesMap via dropdown), build name lookup from /api/employees anyway
        if (isViewOnly && employeesMap.size === 0) {
            try {
                const empResponse = await fetch('/api/employees', {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const employees = await empResponse.json();
                employees.forEach(emp => employeesMap.set(Number(emp.empId), emp));
            } catch (err) {
                console.error('Error loading employees for name lookup:', err);
            }
        }

        const tableBody = document.getElementById('attendanceTableBody');
        tableBody.innerHTML = '';

        if (records.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="8" class="text-center text-muted py-4">No attendance records found.</td></tr>`;
            return;
        }

        records.forEach(att => {
            const emp = employeesMap.get(Number(att.empId));
            const empName = emp ? emp.name : `Employee #${att.empId}`;
            const empCode = emp && emp.employeeCode ? emp.employeeCode : `ID: ${att.empId}`;

            const actionCell = isViewOnly
                ? ''
                : `<td class="text-end"><button class="btn btn-sm btn-outline-danger" onclick="deleteAttendance(${att.attId})">Delete</button></td>`;

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${att.attId}</strong></td>
                <td><strong>${empName}</strong></td>
                <td><span class="badge bg-secondary">${empCode}</span></td>
                <td>${att.attDate}</td>
                <td>${att.checkIn ?? '-'}</td>
                <td>${att.checkOut ?? '-'}</td>
                <td>
                    <span class="badge ${
                att.status === 'Present' ? 'bg-success' :
                    att.status === 'Late' ? 'bg-warning text-dark' :
                        att.status === 'On Leave' ? 'bg-info text-dark' : 'bg-danger'
            }">${att.status}</span>
                </td>
                ${actionCell}
            `;
            tableBody.appendChild(row);
        });
    } catch (err) {
        console.error('Error loading attendance records:', err);
    }
}

// Default date input to today's date
document.addEventListener('DOMContentLoaded', () => {
    const today = new Date().toISOString().split('T')[0];
    const dateInput = document.getElementById('attDate');
    if (dateInput) {
        dateInput.value = today;
    }
});

// Form Submit Handler (only relevant for non-view-only roles, since form is hidden otherwise)
document.getElementById('markAttendanceForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const empId = document.getElementById('attEmpSelect').value;
    const attDate = document.getElementById('attDate').value;
    const checkIn = document.getElementById('checkIn').value;
    const checkOut = document.getElementById('checkOut').value;
    const status = document.getElementById('attStatus').value;

    if (!empId) {
        alert('Please select a staff member!');
        return;
    }

    try {
        await fetch('/api/attendance', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                empId: Number(empId),
                attDate,
                checkIn: checkIn ? (checkIn.length === 5 ? `${checkIn}:00` : checkIn) : null,
                checkOut: checkOut ? (checkOut.length === 5 ? `${checkOut}:00` : checkOut) : null,
                status
            })
        });

        document.getElementById('checkIn').value = '';
        document.getElementById('checkOut').value = '';
        document.getElementById('attEmpSelect').value = '';

        loadAttendance();
    } catch (err) {
        console.error('Error submitting attendance:', err);
    }
});

// Delete Attendance Record
async function deleteAttendance(attId) {
    if (!confirm('Are you sure you want to delete this attendance record?')) return;

    try {
        const response = await fetch(`/api/attendance/${attId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.status === 403) {
            alert('You do not have permission to delete attendance records.');
            return;
        }

        loadAttendance();
    } catch (err) {
        console.error('Error deleting attendance:', err);
    }
}

// Initial Load
(async function init() {
    applyRoleRestrictions();
    await loadEmployeeDropdown();
    await loadAttendance();
})();