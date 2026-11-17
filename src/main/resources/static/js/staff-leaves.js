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

async function loadMyLeaves() {
    try {
        const response = await fetch('/api/leaves/me', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error('Failed to load leaves');
        const leaves = await response.json();

        const tableBody = document.getElementById('leavesTableBody');
        tableBody.innerHTML = '';

        if (leaves.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-4">You haven't applied for any leave yet.</td></tr>`;
            return;
        }

        leaves.sort((a, b) => b.leaveId - a.leaveId);

        leaves.forEach(leave => {
            const badgeClass =
                leave.status === 'Approved' ? 'bg-success' :
                    leave.status === 'Rejected' ? 'bg-danger' :
                        'bg-warning text-dark';

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${leave.leaveId}</strong></td>
                <td><span class="badge bg-secondary">${escapeHtml(leave.leaveType)}</span></td>
                <td>${escapeHtml(leave.fromDate)} <span class="text-muted">to</span> ${escapeHtml(leave.toDate)}</td>
                <td>
                    <small>${escapeHtml(leave.reason) || '-'}</small>
                    ${leave.status === 'Rejected' && leave.rejectionReason ? `<br><small class="text-danger">Reason: ${escapeHtml(leave.rejectionReason)}</small>` : ''}
                </td>
                <td><span class="badge ${badgeClass}">${escapeHtml(leave.status)}</span></td>
            `;
            tableBody.appendChild(row);
        });
    } catch (err) {
        console.error('Error loading my leaves:', err);
    }
}

document.getElementById('applyLeaveForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const leaveType = document.getElementById('leaveType').value;
    const fromDate = document.getElementById('fromDate').value;
    const toDate = document.getElementById('toDate').value;
    const reason = document.getElementById('leaveReason').value;

    try {
        const response = await fetch('/api/leaves', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ leaveType, fromDate, toDate, reason })
        });

        if (!response.ok) {
            let message = 'Could not submit your leave request.';
            try {
                const data = await response.json();
                if (data && data.error) message = data.error;
            } catch (ignored) { /* response had no JSON body */ }
            alert(message);
            return;
        }

        document.getElementById('leaveReason').value = '';
        document.getElementById('fromDate').value = '';
        document.getElementById('toDate').value = '';

        loadMyLeaves();
    } catch (err) {
        console.error('Error submitting leave request:', err);
        alert('Something went wrong while submitting your leave request.');
    }
});

loadMyLeaves();