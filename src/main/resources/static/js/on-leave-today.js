(function () {
    var box = document.getElementById('onLeaveToday');
    if (!box) return;

    var authToken = localStorage.getItem('token');
    var role = String(localStorage.getItem('userRole') || '').replace(/^ROLE_/, '').toUpperCase();
    if (!authToken || ['DEAN', 'ADMINISTRATOR', 'HEAD_OF_DEPARTMENT'].indexOf(role) === -1) return;

    function esc(v) {
        if (v === null || v === undefined) return '';
        return String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    var showDept = role !== 'HEAD_OF_DEPARTMENT';

    box.innerHTML =
        '<div class="card shadow-sm mt-4">' +
        '<div class="card-header">On Leave Today <small class="text-muted">(approved leave only)</small></div>' +
        '<div class="card-body" id="onLeaveBody"><span class="text-muted">Loading...</span></div>' +
        '</div>';

    fetch('/api/on-leave/today', { headers: { 'Authorization': 'Bearer ' + authToken } })
        .then(function (r) {
            if (!r.ok) throw new Error('status ' + r.status);
            return r.json();
        })
        .then(function (rows) {
            var body = document.getElementById('onLeaveBody');
            if (!rows.length) {
                body.innerHTML = '<span class="text-success">Nobody is on leave today.</span>';
                return;
            }
            var html = '<table class="table table-sm mb-0"><thead><tr><th>Name</th>' +
                (showDept ? '<th>Department</th>' : '') +
                '<th>Leave</th><th>Dates</th><th>Back on</th></tr></thead><tbody>';
            rows.forEach(function (r) {
                html += '<tr><td><strong>' + esc(r.name) + '</strong><br><small class="text-muted">' + esc(r.employeeCode) + '</small></td>' +
                    (showDept ? '<td>' + esc(r.department) + '</td>' : '') +
                    '<td>' + esc(r.leaveType) + '</td>' +
                    '<td>' + esc(r.fromDate) + ' to ' + esc(r.toDate) + '</td>' +
                    '<td>' + esc(r.returnDate) + '</td></tr>';
            });
            html += '</tbody></table>';
            body.innerHTML = html;
        })
        .catch(function (err) {
            console.error('On leave today error:', err);
            document.getElementById('onLeaveBody').innerHTML = '<span class="text-danger">Could not load.</span>';
        });
})();