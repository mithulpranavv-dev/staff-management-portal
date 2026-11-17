// Map for employee details lookup
let employeesMap = new Map();

// Populate Employee Dropdown
async function loadEmployeeDropdown() {
    try {
        const response = await fetch('/api/employees', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const employees = await response.json();

        const select = document.getElementById('payrollEmpSelect');
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
        console.error('Error loading employee dropdown:', err);
    }
}

// Format Currency
function formatCurrency(amount) {
    if (amount === undefined || amount === null) return '₹0.00';
    return '₹' + Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// Auto-select Current Month and Year in Dropdowns
document.addEventListener('DOMContentLoaded', () => {
    const today = new Date();
    const monthNames = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
    const currentMonth = monthNames[today.getMonth()];
    const currentYear = today.getFullYear().toString();

    const monthSelect = document.getElementById('payMonthSelect');
    const yearSelect = document.getElementById('payYearSelect');

    if (monthSelect) monthSelect.value = currentMonth;
    if (yearSelect) yearSelect.value = currentYear;

    // Add the first empty deduction line by default
    addDeductionLine();
});

// ---- Deduction line management ----

function addDeductionLine() {
    const template = document.getElementById('deductionLineTemplate');
    const container = document.getElementById('deductionLinesContainer');
    const clone = template.content.cloneNode(true);

    clone.querySelector('.remove-deduction-line').addEventListener('click', function () {
        this.closest('.deduction-line').remove();
    });

    container.appendChild(clone);
}

document.getElementById('addDeductionLineBtn').addEventListener('click', addDeductionLine);

function collectDeductionLines() {
    const lines = [];
    document.querySelectorAll('#deductionLinesContainer .deduction-line').forEach(row => {
        const date = row.querySelector('.deduction-date').value;
        const amount = row.querySelector('.deduction-amount').value;
        const reason = row.querySelector('.deduction-reason').value;

        // Only include rows where the admin actually filled something in
        if (date && amount) {
            lines.push({ date, amount: Number(amount), reason: reason || null });
        }
    });
    return lines;
}

function clearDeductionLines() {
    document.getElementById('deductionLinesContainer').innerHTML = '';
    addDeductionLine();
}

// Load All Payroll Records — ADMINISTRATOR only
async function loadPayroll() {
    try {
        const response = await fetch('/api/payroll', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.status === 403) {
            document.getElementById('payrollTableBody').innerHTML =
                `<tr><td colspan="7" class="text-center text-muted py-4">You do not have permission to view payroll records.</td></tr>`;
            return;
        }

        const records = await response.json();

        const tableBody = document.getElementById('payrollTableBody');
        tableBody.innerHTML = '';

        if (records.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="7" class="text-center text-muted py-4">No payslips generated yet.</td></tr>`;
            return;
        }

        records.forEach(pay => {
            const emp = employeesMap.get(Number(pay.empId));
            const empName = emp ? emp.name : `Employee #${pay.empId}`;
            const empCode = emp && emp.employeeCode ? emp.employeeCode : `ID: ${pay.empId}`;

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${pay.payrollId}</strong></td>
                <td>
                    <strong>${empName}</strong>
                    <br><small class="text-muted">${empCode}</small>
                </td>
                <td><span class="badge bg-secondary">${pay.month}</span></td>
                <td>${formatCurrency(pay.basicSalary)}</td>
                <td class="text-danger">
                    -${formatCurrency(pay.deductions)}
                    ${pay.deductions > 0 ? `<br><a href="#" class="small view-deduction-details" data-payroll-id="${pay.payrollId}">View details</a>` : ''}
                </td>
                <td class="text-success font-bold"><strong>${formatCurrency(pay.netSalary)}</strong></td>
                <td class="text-end">
                    <button class="btn btn-sm btn-outline-danger" onclick="deletePayroll(${pay.payrollId})">Delete</button>
                </td>
            `;
            tableBody.appendChild(row);
        });

        // Wire up "View details" links
        document.querySelectorAll('.view-deduction-details').forEach(link => {
            link.addEventListener('click', async function (e) {
                e.preventDefault();
                await showDeductionDetails(this.dataset.payrollId);
            });
        });
    } catch (err) {
        console.error('Error loading payroll records:', err);
    }
}

// Fetch and display deduction breakdown for one payroll record
async function showDeductionDetails(payrollId) {
    try {
        const response = await fetch(`/api/payroll/${payrollId}/deduction-details`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) throw new Error('Failed to load deduction details');

        const details = await response.json();

        if (details.length === 0) {
            alert('No deduction breakdown available for this payslip.');
            return;
        }

        let message = 'Deduction Breakdown:\n\n';
        details.forEach(d => {
            message += `${d.deductionDate} — ${formatCurrency(d.amount)} — ${d.reason || 'No reason given'}\n`;
        });

        alert(message);
    } catch (err) {
        console.error('Error loading deduction details:', err);
        alert('Could not load deduction details.');
    }
}

// Form Submit Handler
document.getElementById('generatePayrollForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const empId = document.getElementById('payrollEmpSelect').value;
    const selectedMonth = document.getElementById('payMonthSelect').value;
    const selectedYear = document.getElementById('payYearSelect').value;
    const payPeriod = `${selectedMonth} ${selectedYear}`;

    const basicSalary = document.getElementById('basicSalary').value;
    const deductionLines = collectDeductionLines();

    if (!empId) {
        alert('Please select a staff member!');
        return;
    }

    try {
        const response = await fetch('/api/payroll', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                empId: Number(empId),
                month: payPeriod,
                basicSalary: Number(basicSalary),
                deductionLines: deductionLines
            })
        });

        if (response.status === 403) {
            alert('You do not have permission to generate payroll.');
            return;
        }

        if (!response.ok) {
            const errorData = await response.json();
            alert(errorData.error || 'Failed to generate payroll.');
            return;
        }

        // Reset inputs
        document.getElementById('basicSalary').value = '';
        document.getElementById('payrollEmpSelect').value = '';
        clearDeductionLines();

        loadPayroll();
    } catch (err) {
        console.error('Error generating payroll:', err);
        alert('Something went wrong while generating payroll.');
    }
});

// Delete Payroll Record
async function deletePayroll(payrollId) {
    if (!confirm('Are you sure you want to delete this payslip record?')) return;

    try {
        const response = await fetch(`/api/payroll/${payrollId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.status === 403) {
            alert('You do not have permission to delete payroll records.');
            return;
        }

        loadPayroll();
    } catch (err) {
        console.error('Error deleting payroll:', err);
    }
}

async function loadSuggestedDeductions() {
    const empId = document.getElementById('payrollEmpSelect').value;
    const month = document.getElementById('payMonthSelect').value;
    const year = document.getElementById('payYearSelect').value;

    if (!empId || !month || !year) return;

    try {
        const response = await fetch(`/api/payroll/suggest-deductions?empId=${empId}&month=${month}&year=${year}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) return;

        const data = await response.json();

        if (data.monthlySalary != null) {
            document.getElementById('basicSalary').value = Math.round(data.monthlySalary);
        }

        // Clear existing lines and repopulate with suggestions
        document.getElementById('deductionLinesContainer').innerHTML = '';

        if (data.suggestedDeductions && data.suggestedDeductions.length > 0) {
            data.suggestedDeductions.forEach(line => {
                addDeductionLine();
                const rows = document.querySelectorAll('#deductionLinesContainer .deduction-line');
                const lastRow = rows[rows.length - 1];
                lastRow.querySelector('.deduction-date').value = line.date;
                lastRow.querySelector('.deduction-amount').value = line.amount;
                lastRow.querySelector('.deduction-reason').value = line.reason;
            });
        } else {
            addDeductionLine(); // keep one empty row if nothing was suggested
        }
    } catch (err) {
        console.error('Error loading suggested deductions:', err);
    }
}

document.getElementById('payrollEmpSelect').addEventListener('change', loadSuggestedDeductions);
document.getElementById('payMonthSelect').addEventListener('change', loadSuggestedDeductions);
document.getElementById('payYearSelect').addEventListener('change', loadSuggestedDeductions);

// Initial Load
(async function init() {
    await loadEmployeeDropdown();
    await loadPayroll();
})();