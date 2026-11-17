async function loadEmployees() {
    try {
        const response = await fetch('/api/employees', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) throw new Error('Failed to load employees');

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
            `;
            tableBody.appendChild(row);
        });
    } catch (err) {
        console.error('Error loading employees:', err);
    }
}

loadEmployees();