// Reads `token` and `userRole` from localStorage directly. Loads Chart.js itself, so no HTML change is needed.
(function () {
    var box = document.getElementById('aiInsights');
    if (!box) return;

    var authToken = localStorage.getItem('token');
    var currentRole = String(localStorage.getItem('userRole') || '').replace(/^ROLE_/, '').toUpperCase();

    var SCOPES = {
        FACULTY_MEMBER:     { alerts: '/api/attendance/anomalies/me',            stats: '/api/leave-stats/me',            details: '/api/leave-stats/me/details',            summary: '/api/leaves/insights/me',            title: 'My' },
        HEAD_OF_DEPARTMENT: { alerts: '/api/attendance/anomalies/my-department', stats: '/api/leave-stats/my-department', details: '/api/leave-stats/my-department/details', summary: '/api/leaves/insights/my-department', title: 'Department' },
        DEAN:               { alerts: '/api/attendance/anomalies/all',           stats: '/api/leave-stats/all',           details: '/api/leave-stats/all/details',           summary: '/api/leaves/insights/all',           title: 'Organization' },
        ADMINISTRATOR:      { alerts: '/api/attendance/anomalies/all',           stats: '/api/leave-stats/all',           details: '/api/leave-stats/all/details',           summary: '/api/leaves/insights/all',           title: 'Organization' }
    };

    var scope = SCOPES[currentRole];
    if (!scope || !authToken) {
        console.warn('ai-insights: no scope for role "' + currentRole + '" or no token; cards not shown.');
        return;
    }

    var showEmployeeColumn = currentRole !== 'FACULTY_MEMBER';
    var showDepartmentColumn = currentRole === 'DEAN' || currentRole === 'ADMINISTRATOR';

    var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    var STATUS_COLORS = { Approved: '#198754', Pending: '#ffc107', Rejected: '#dc3545' };

    var statsData = null;
    var detailRows = null;

    function escapeHtml(value) {
        if (value === null || value === undefined) return '';
        return String(value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function authFetch(url) {
        return fetch(url, { headers: { 'Authorization': 'Bearer ' + authToken } });
    }

    box.innerHTML =
        '<div class="card shadow-sm mt-1">' +
        '  <div class="card-header d-flex justify-content-between align-items-center">' +
        '    <span>' + scope.title + ' Leave Insights <small class="text-muted" id="aiChartYear"></small></span>' +
        '    <button class="btn btn-sm btn-outline-primary" id="aiSummaryBtn">AI summary</button>' +
        '  </div>' +
        '  <div class="card-body">' +
        '    <div id="aiChartsMsg" class="text-muted">Loading charts...</div>' +
        '    <div class="small text-muted mb-2" id="aiClickHint" style="display:none;">Tip: click any bar or slice to see the people and leave details behind it.</div>' +
        '    <div class="row g-3" id="aiCharts"></div>' +
        '    <div id="aiDetails" class="mt-3"></div>' +
        '    <div id="aiSummaryBody" class="mt-3"></div>' +
        '  </div>' +
        '</div>' +
        '<div class="card shadow-sm mt-4">' +
        '  <div class="card-header">' + scope.title + ' Attendance Alerts <small class="text-muted">(last 30 days)</small></div>' +
        '  <div class="card-body" id="aiAlertsBody"><span class="text-muted">Loading alerts...</span></div>' +
        '</div>';

    function loadChartLib(callback, onError) {
        if (window.Chart) { callback(); return; }
        var s = document.createElement('script');
        s.src = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.3/dist/chart.umd.min.js';
        s.onload = callback;
        s.onerror = onError;
        document.head.appendChild(s);
    }

    function chartBox(title, id) {
        return '<div class="col-md-6 col-xl-4">' +
            '<div class="border rounded p-2 h-100">' +
            '<div class="small fw-bold mb-2">' + title + '</div>' +
            '<div style="position: relative; height: 220px;"><canvas id="' + id + '"></canvas></div>' +
            '</div></div>';
    }

    // ---- Click-to-expand details ----

    function badgeClass(status) {
        return status === 'Approved' ? 'bg-success' : status === 'Rejected' ? 'bg-danger' : 'bg-warning text-dark';
    }

    function renderDetails(title, rows) {
        var panel = document.getElementById('aiDetails');

        var head = '<th>Leave</th>';
        if (showEmployeeColumn) head += '<th>Employee</th>';
        if (showDepartmentColumn) head += '<th>Department</th>';
        head += '<th>Category</th><th>Dates</th><th>Days</th><th>Status</th><th>Reason</th>';

        var body = '';
        if (!rows.length) {
            var cols = 6 + (showEmployeeColumn ? 1 : 0) + (showDepartmentColumn ? 1 : 0);
            body = '<tr><td colspan="' + cols + '" class="text-center text-muted py-3">No leave requests in this group.</td></tr>';
        }
        rows.forEach(function (r) {
            body += '<tr>' +
                '<td><strong>#' + escapeHtml(r.leaveId) + '</strong></td>' +
                (showEmployeeColumn
                    ? '<td><strong>' + escapeHtml(r.name) + '</strong><br><small class="text-muted">' + escapeHtml(r.employeeCode) + '</small></td>'
                    : '') +
                (showDepartmentColumn ? '<td>' + escapeHtml(r.department) + '</td>' : '') +
                '<td><span class="badge bg-secondary">' + escapeHtml(r.leaveType) + '</span></td>' +
                '<td>' + escapeHtml(r.fromDate) + ' <span class="text-muted">to</span> ' + escapeHtml(r.toDate) + '</td>' +
                '<td>' + escapeHtml(r.days) + '</td>' +
                '<td><span class="badge ' + badgeClass(r.status) + '">' + escapeHtml(r.status) + '</span></td>' +
                '<td><small>' + (escapeHtml(r.reason) || '-') + '</small>' +
                (r.status === 'Rejected' && r.rejectionReason
                    ? '<br><small class="text-danger">Rejected: ' + escapeHtml(r.rejectionReason) + '</small>' : '') +
                '</td></tr>';
        });

        panel.innerHTML =
            '<div class="border rounded">' +
            '  <div class="d-flex justify-content-between align-items-center bg-light border-bottom px-3 py-2">' +
            '    <strong>' + escapeHtml(title) + ' <span class="text-muted">(' + rows.length + ')</span></strong>' +
            '    <button class="btn btn-sm btn-outline-secondary" id="aiDetailsClose">Close</button>' +
            '  </div>' +
            '  <div class="table-responsive">' +
            '    <table class="table table-sm table-hover mb-0 align-middle">' +
            '      <thead class="table-light"><tr>' + head + '</tr></thead>' +
            '      <tbody>' + body + '</tbody>' +
            '    </table>' +
            '  </div>' +
            '</div>';

        document.getElementById('aiDetailsClose').addEventListener('click', function () {
            panel.innerHTML = '';
        });
        panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    async function showDetails(title, filterFn) {
        var panel = document.getElementById('aiDetails');
        if (!detailRows) {
            panel.innerHTML = '<span class="text-muted">Loading details...</span>';
            try {
                var response = await authFetch(scope.details);
                if (!response.ok) throw new Error('status ' + response.status);
                detailRows = await response.json();
            } catch (err) {
                console.error('Error loading leave details:', err);
                panel.innerHTML = '<span class="text-danger">Could not load the details.</span>';
                return;
            }
        }
        renderDetails(title, detailRows.filter(filterFn));
    }

    // ---- Charts ----

    function clickHandler(onPick) {
        return function (evt, elements, chart) {
            if (!elements.length) return;
            var index = elements[0].index;
            onPick(chart.data.labels[index], index);
        };
    }

    function pointerOnHover(evt, elements) {
        evt.native.target.style.cursor = elements.length ? 'pointer' : 'default';
    }

    function doughnut(id, labels, values, colors, onPick) {
        new Chart(document.getElementById(id), {
            type: 'doughnut',
            data: { labels: labels, datasets: [{ data: values, backgroundColor: colors }] },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { position: 'bottom' } },
                onClick: clickHandler(onPick),
                onHover: pointerOnHover
            }
        });
    }

    function bar(id, labels, values, color, onPick) {
        new Chart(document.getElementById(id), {
            type: 'bar',
            data: { labels: labels, datasets: [{ label: 'Requests', data: values, backgroundColor: color }] },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
                onClick: clickHandler(onPick),
                onHover: pointerOnHover
            }
        });
    }

    function pad2(n) { return n < 10 ? '0' + n : String(n); }

    function renderCharts(data) {
        var msg = document.getElementById('aiChartsMsg');
        var holder = document.getElementById('aiCharts');
        document.getElementById('aiChartYear').textContent = '(' + data.year + ')';

        if (!data.totalRequests && !data.balance) {
            msg.innerHTML = 'No leave requests yet, so there is nothing to chart.';
            return;
        }
        msg.style.display = 'none';
        document.getElementById('aiClickHint').style.display = 'block';

        var html = '';
        if (data.totalRequests) {
            html += chartBox('Leave status', 'aiChartStatus');
            html += chartBox('Leave by category', 'aiChartType');
            html += chartBox('Leave requests by month', 'aiChartMonth');
        }
        if (data.byDepartment && Object.keys(data.byDepartment).length) {
            html += chartBox('Leave by department', 'aiChartDept');
        }
        if (data.balance) {
            html += chartBox('Paid leave (' + data.balance.paidQuota + ' days / year)', 'aiChartBalance');
        }
        holder.innerHTML = html;

        if (data.totalRequests) {
            var statusLabels = Object.keys(data.byStatus);
            doughnut('aiChartStatus', statusLabels,
                statusLabels.map(function (k) { return data.byStatus[k]; }),
                statusLabels.map(function (k) { return STATUS_COLORS[k] || '#6c757d'; }),
                function (label) {
                    showDetails('Leave status: ' + label, function (r) { return r.status === label; });
                });

            var typeLabels = Object.keys(data.byType);
            bar('aiChartType', typeLabels, typeLabels.map(function (k) { return data.byType[k]; }), '#0d6efd',
                function (label) {
                    showDetails('Category: ' + label, function (r) { return r.leaveType === label; });
                });

            bar('aiChartMonth', MONTHS, data.byMonth, '#20c997', function (label, index) {
                var prefix = data.year + '-' + pad2(index + 1);
                showDetails('Leaves starting in ' + label + ' ' + data.year, function (r) {
                    return r.fromDate && r.fromDate.indexOf(prefix) === 0;
                });
            });
        }
        if (document.getElementById('aiChartDept')) {
            var deptLabels = Object.keys(data.byDepartment);
            bar('aiChartDept', deptLabels, deptLabels.map(function (k) { return data.byDepartment[k]; }), '#6f42c1',
                function (label) {
                    showDetails('Department: ' + label, function (r) { return r.department === label; });
                });
        }
        if (document.getElementById('aiChartBalance')) {
            doughnut('aiChartBalance', ['Used', 'Remaining'],
                [data.balance.paidUsed, data.balance.paidRemaining], ['#fd7e14', '#198754'],
                function (label) {
                    if (label === 'Remaining') {
                        document.getElementById('aiDetails').innerHTML =
                            '<div class="alert alert-success mb-0">You have <strong>' + data.balance.paidRemaining +
                            '</strong> paid leave days remaining this year.</div>';
                        return;
                    }
                    var yearText = String(data.year);
                    showDetails('Approved leave counted against the paid quota (' + data.year + ')', function (r) {
                        return r.status === 'Approved' &&
                            ((r.fromDate && r.fromDate.indexOf(yearText) === 0) ||
                                (r.toDate && r.toDate.indexOf(yearText) === 0));
                    });
                });
        }
    }

    async function loadStats() {
        var msg = document.getElementById('aiChartsMsg');
        try {
            var response = await authFetch(scope.stats);
            if (!response.ok) throw new Error('status ' + response.status);
            statsData = await response.json();
            loadChartLib(function () { renderCharts(statsData); }, function () {
                msg.innerHTML = '<span class="text-danger">Could not load the chart library. Check your internet connection.</span>';
            });
        } catch (err) {
            console.error('Error loading leave stats:', err);
            msg.innerHTML = '<span class="text-danger">Could not load leave charts.</span>';
        }
    }

    async function loadSummary() {
        var btn = document.getElementById('aiSummaryBtn');
        var body = document.getElementById('aiSummaryBody');
        btn.disabled = true;
        btn.textContent = 'Generating...';
        body.innerHTML = '<span class="text-muted">Thinking...</span>';
        try {
            var response = await authFetch(scope.summary);
            if (!response.ok) throw new Error('status ' + response.status);
            var data = await response.json();
            body.innerHTML = '<div class="border rounded p-2 bg-light" style="white-space: pre-wrap;">' + escapeHtml(data.insights) + '</div>';
            btn.textContent = 'Refresh AI summary';
        } catch (err) {
            console.error('Error loading AI summary:', err);
            body.innerHTML = '<span class="text-danger">The AI could not answer right now. Please try again in a moment.</span>';
            btn.textContent = 'Try again';
        } finally {
            btn.disabled = false;
        }
    }

    async function loadAlerts() {
        var body = document.getElementById('aiAlertsBody');
        try {
            var response = await authFetch(scope.alerts);
            if (!response.ok) throw new Error('status ' + response.status);
            var alerts = await response.json();

            if (!alerts.length) {
                body.innerHTML = '<span class="text-success">No attendance alerts. Everything looks normal.</span>';
                return;
            }

            var html = '';
            alerts.forEach(function (a) {
                html += '<div class="border rounded p-2 mb-2">' +
                    '<strong>' + escapeHtml(a.name) + '</strong> ' +
                    '<small class="text-muted">' + escapeHtml(a.employeeCode) + '</small>' +
                    '<ul class="mb-0 small">';
                (a.reasons || []).forEach(function (r) {
                    html += '<li>' + escapeHtml(r) + '</li>';
                });
                html += '</ul></div>';
            });
            body.innerHTML = html;
        } catch (err) {
            console.error('Error loading attendance alerts:', err);
            body.innerHTML = '<span class="text-danger">Could not load alerts.</span>';
        }
    }

    document.getElementById('aiSummaryBtn').addEventListener('click', loadSummary);
    loadStats();
    loadAlerts();
})();