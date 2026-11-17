var token = localStorage.getItem('token');

async function loadEmployees() {
    const response = await fetch('/api/employees', {
        headers: { 'Authorization': `Bearer ${token}` }
    });

    if (!response.ok) {
        console.error('Failed to load employees:', response.status);
        return;
    }

    const employees = await response.json();

    const tableBody = document.getElementById('employeeTableBody');
    tableBody.innerHTML = '';

    employees.forEach(emp => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${emp.employeeCode || '-'}</td>
            <td>${emp.name}</td>
            <td>${emp.email}</td>
            <td>${emp.department}</td>
            <td>
                <button class="btn btn-sm btn-outline-danger" onclick="deleteEmployee(${emp.empId})">Delete</button>
            </td>
        `;
        tableBody.appendChild(row);
    });
}

document.getElementById('addEmployeeForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const name = document.getElementById('empName').value;
    const email = document.getElementById('empEmail').value;
    const department = document.getElementById('empDepartment').value;

    await fetch('/api/employees', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ name, email, department })
    });

    document.getElementById('addEmployeeForm').reset();
    loadEmployees();
});

async function deleteEmployee(empId) {
    if (!confirm('Are you sure you want to delete this employee?')) return;

    const response = await fetch(`/api/employees/${empId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
    });

    if (response.status === 403) {
        alert('You do not have permission to delete employees.');
        return;
    }

    loadEmployees();
}

loadEmployees();