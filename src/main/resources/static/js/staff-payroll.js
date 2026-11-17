let myEmpId = null;

async function findMyEmpId() {
    try {
        const response = await fetch('/api/employees/me', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error('Failed to load own employee record');
        const me = await response.json();
        myEmpId = me.empId;
    } catch (err) {
        console.error('Error finding own employee record:', err);
    }
}

function formatCurrency(amount) {
    if (amount === undefined || amount === null) return '₹0.00';
    return '₹' + Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

async function loadMyPayroll() {
    try {
        const response = await fetch('/api/payroll/me', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) throw new Error('Failed to load payroll');

        const records = await response.json();
        const tableBody = document.getElementById('payrollTableBody');
        tableBody.innerHTML = '';

        if (records.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-4">No payslips generated yet.</td></tr>`;
            return;
        }

        records.forEach(pay => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${pay.payrollId}</strong></td>
                <td><span class="badge bg-secondary">${pay.month}</span></td>
                <td>${formatCurrency(pay.basicSalary)}</td>
                <td class="text-danger">
                    -${formatCurrency(pay.deductions)}
                    ${pay.deductions > 0 ? `<br><a href="#" class="small view-deduction-details" data-payroll-id="${pay.payrollId}">View details</a>` : ''}
                </td>
                <td class="text-success font-bold"><strong>${formatCurrency(pay.netSalary)}</strong></td>
            `;
            tableBody.appendChild(row);
        });

        document.querySelectorAll('.view-deduction-details').forEach(link => {
            link.addEventListener('click', async function (e) {
                e.preventDefault();
                await showDeductionDetails(this.dataset.payrollId);
            });
        });
    } catch (err) {
        console.error('Error loading my payroll:', err);
    }
}

async function showDeductionDetails(payrollId) {
    try {
        const response = await fetch(`/api/payroll/me/${payrollId}/deduction-details`, {
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

(async function init() {
    await findMyEmpId();
    if (myEmpId) await loadMyPayroll();
})();