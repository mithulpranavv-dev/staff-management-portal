var token = localStorage.getItem('token');

function showSection(id, bodyId, rows, rowRenderer) {
    const section = document.getElementById(id);
    const body = document.getElementById(bodyId);
    body.innerHTML = '';

    if (!rows || rows.length === 0) {
        section.classList.add('d-none');
        return;
    }

    rows.forEach(row => body.appendChild(rowRenderer(row)));
    section.classList.remove('d-none');
}

function renderEmployeeRow(emp) {
    const tr = document.createElement('tr');
    tr.innerHTML = `
        <td>${emp.employeeCode || emp.empId}</td>
        <td>${emp.name}</td>
        <td>${emp.department || 'N/A'}</td>
        <td>${emp.role || ''}</td>
    `;
    return tr;
}

function renderTaskRow(task) {
    const tr = document.createElement('tr');
    tr.innerHTML = `
        <td>${task.title}</td>
        <td>${task.priority}</td>
        <td>${task.dueDate || '-'}</td>
        <td>${task.status}</td>
    `;
    return tr;
}

function renderLeaveRow(leave) {
    const tr = document.createElement('tr');
    tr.innerHTML = `
        <td>${leave.leaveType}</td>
        <td>${leave.fromDate} to ${leave.toDate}</td>
        <td>${leave.status}</td>
    `;
    return tr;
}

document.getElementById('searchForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const query = document.getElementById('searchQuery').value.trim();
    const statusEl = document.getElementById('searchStatus');

    if (!query) return;

    statusEl.textContent = 'Searching...';
    ['employeeResults', 'taskResults', 'leaveResults'].forEach(id =>
        document.getElementById(id).classList.add('d-none')
    );

    const headers = { 'Authorization': `Bearer ${token}` };
    const encodedQuery = encodeURIComponent(query);

    try {
        const [empRes, taskRes, leaveRes] = await Promise.all([
            fetch(`/api/search/employees?q=${encodedQuery}`, { headers }),
            fetch(`/api/search/tasks?q=${encodedQuery}`, { headers }),
            fetch(`/api/search/leaves?q=${encodedQuery}`, { headers })
        ]);

        const [employees, tasks, leaves] = await Promise.all([
            empRes.ok ? empRes.json() : [],
            taskRes.ok ? taskRes.json() : [],
            leaveRes.ok ? leaveRes.json() : []
        ]);

        showSection('employeeResults', 'employeeResultsBody', employees, renderEmployeeRow);
        showSection('taskResults', 'taskResultsBody', tasks, renderTaskRow);
        showSection('leaveResults', 'leaveResultsBody', leaves, renderLeaveRow);

        const totalResults = employees.length + tasks.length + leaves.length;
        statusEl.textContent = totalResults === 0
            ? 'No results found.'
            : `Found ${totalResults} result(s).`;
    } catch (err) {
        console.error('Search error:', err);
        statusEl.textContent = 'Something went wrong while searching.';
    }
});