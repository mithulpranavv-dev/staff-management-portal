// Uses the global `token` declared in dashboard.js. Do not redeclare it here.

async function loadMyAttendance() {
    try {
        const response = await fetch('/api/attendance/me', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) throw new Error('Failed to load attendance');

        const records = await response.json();
        const tableBody = document.getElementById('attendanceTableBody');
        tableBody.innerHTML = '';

        if (records.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-4">No attendance records found yet.</td></tr>`;
            return;
        }

        records.sort((a, b) => String(b.attDate).localeCompare(String(a.attDate)));

        records.forEach(att => {
            const badgeClass =
                att.status === 'Present' ? 'bg-success' :
                    att.status === 'Late' ? 'bg-warning text-dark' :
                        att.status === 'On Leave' ? 'bg-info text-dark' : 'bg-danger';

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${att.attId}</strong></td>
                <td>${att.attDate}</td>
                <td>${att.checkIn ?? '-'}</td>
                <td>${att.checkOut ?? '-'}</td>
                <td><span class="badge ${badgeClass}">${att.status}</span></td>
            `;
            tableBody.appendChild(row);
        });
    } catch (err) {
        console.error('Error loading my attendance:', err);
    }
}

loadMyAttendance();