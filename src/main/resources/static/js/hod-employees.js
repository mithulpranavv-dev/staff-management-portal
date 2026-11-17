// Uses the global `token` declared in dashboard.js. Do not redeclare it here.
function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

async function loadMyDepartmentEmployees() {
    try {
        const response = await fetch('/api/employees/my-department', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.status === 403) {
            document.getElementById('employeeTableBody').innerHTML =
                `<tr><td colspan="4" class="text-center text-muted py-4">You do not have permission to view this.</td></tr>`;
            return;
        }

        if (!response.ok) throw new Error('Failed to load department staff');

        const employees = await response.json();

        const tableBody = document.getElementById('employeeTableBody');
        tableBody.innerHTML = '';

        if (employees.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="4" class="text-center text-muted py-4">No staff found in your department.</td></tr>`;
            return;
        }

        if (employees[0].department) {
            document.getElementById('deptBadge').textContent = employees[0].department;
        }

        employees.forEach(emp => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${escapeHtml(emp.employeeCode) || '-'}</td>
                <td>${escapeHtml(emp.name)}</td>
                <td>${escapeHtml(emp.email) || '-'}</td>
                <td>${escapeHtml(emp.department)}</td>
            `;
            tableBody.appendChild(row);
        });
    } catch (err) {
        console.error('Error loading department staff:', err);
    }
}

loadMyDepartmentEmployees();