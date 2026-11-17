(function () {
    var box = document.getElementById('performanceInsights');
    if (!box) return;

    var authToken = localStorage.getItem('token');
    var role = String(localStorage.getItem('userRole') || '').replace(/^ROLE_/, '').toUpperCase();
    var urls = { DEAN: '/api/performance/all', HEAD_OF_DEPARTMENT: '/api/performance/my-department' };
    var url = urls[role];
    if (!authToken || !url) return;

    var showDept = role === 'DEAN';
    var data = null;

    function esc(v) {
        if (v === null || v === undefined) return '';
        return String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    function pct(v) { return v === null || v === undefined ? '-' : v + '%'; }

    box.innerHTML =
        '<div class="card shadow-sm mt-4">' +
        '<div class="card-header">Performance Insights <small class="text-muted" id="perfSub"></small></div>' +
        '<div class="card-body">' +
        '<div id="perfMsg" class="text-muted">Loading...</div>' +
        '<div id="perfSummary" class="row g-3 mb-3"></div>' +
        '<div class="row g-3" id="perfCharts"></div>' +
        '<div id="perfDetail" class="mt-3"></div>' +
        '</div></div>';

    function loadChartLib(ok, fail) {
        if (window.Chart) { ok(); return; }
        var s = document.createElement('script');
        s.src = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.3/dist/chart.umd.min.js';
        s.onload = ok;
        s.onerror = fail;
        document.head.appendChild(s);
    }

    function statBox(label, value) {
        return '<div class="col-6 col-md-2"><div class="border rounded p-2 text-center">' +
            '<div class="small text-muted">' + label + '</div>' +
            '<div class="fs-4 fw-bold">' + value + '</div></div></div>';
    }

    function chartBox(title, id, colClass) {
        return '<div class="' + colClass + '"><div class="border rounded p-2">' +
            '<div class="small fw-bold mb-2">' + title + '</div>' +
            '<div style="position:relative;height:260px;"><canvas id="' + id + '"></canvas></div>' +
            '</div></div>';
    }

    function showPerson(p) {
        var d = document.getElementById('perfDetail');
        var html = '<div class="border rounded">' +
            '<div class="d-flex justify-content-between align-items-center bg-light border-bottom px-3 py-2">' +
            '<strong>' + esc(p.name) + ' <span class="text-muted">' + esc(p.employeeCode) + '</span></strong>' +
            '<button class="btn btn-sm btn-outline-secondary" id="perfClose">Close</button></div>' +
            '<table class="table table-sm mb-0"><tbody>' +
            (showDept ? '<tr><td>Department</td><td>' + esc(p.department) + '</td></tr>' : '') +
            '<tr><td>Tasks assigned</td><td>' + p.assigned + '</td></tr>' +
            '<tr><td>Completed</td><td>' + p.completed + ' (on time ' + p.onTime + ', late ' + p.late + ')</td></tr>' +
            '<tr><td>In progress</td><td>' + p.inProgress + '</td></tr>' +
            '<tr><td>Pending</td><td>' + p.pending + '</td></tr>' +
            '<tr><td>Completion rate</td><td>' + pct(p.completionRate) + '</td></tr>' +
            '<tr><td>On-time rate</td><td>' + pct(p.onTimeRate) + '</td></tr>' +
            '<tr><td>Attendance (last ' + data.attendanceDays + ' days)</td><td>' + pct(p.attendanceRate) + '</td></tr>' +
            '</tbody></table></div>';
        d.innerHTML = html;
        document.getElementById('perfClose').addEventListener('click', function () { d.innerHTML = ''; });
        d.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function hoverPointer(evt, els) {
        evt.native.target.style.cursor = els.length ? 'pointer' : 'default';
    }

    function barChart(id, labels, values, color, label) {
        var people = data.people;
        new Chart(document.getElementById(id), {
            type: 'bar',
            data: { labels: labels, datasets: [{ label: label, data: values, backgroundColor: color }] },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, max: 100, ticks: { callback: function (v) { return v + '%'; } } } },
                onClick: function (evt, els) { if (els.length) showPerson(people[els[0].index]); },
                onHover: hoverPointer
            }
        });
    }

    function statusChart(id, labels) {
        var people = data.people;
        new Chart(document.getElementById(id), {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    { label: 'Completed',   data: people.map(function (p) { return p.completed; }),  backgroundColor: '#198754' },
                    { label: 'In Progress', data: people.map(function (p) { return p.inProgress; }), backgroundColor: '#0d6efd' },
                    { label: 'Pending',     data: people.map(function (p) { return p.pending; }),    backgroundColor: '#ffc107' }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { position: 'bottom' } },
                scales: {
                    x: { stacked: true },
                    y: { stacked: true, beginAtZero: true, ticks: { precision: 0 } }
                },
                onClick: function (evt, els) { if (els.length) showPerson(people[els[0].index]); },
                onHover: hoverPointer
            }
        });
    }

    function render() {
        var s = data.summary;
        var totalInProgress = 0, totalPending = 0;
        data.people.forEach(function (p) {
            totalInProgress += p.inProgress;
            totalPending += p.pending;
        });

        document.getElementById('perfMsg').style.display = 'none';
        document.getElementById('perfSub').textContent = '(' + s.people + ' people)';
        document.getElementById('perfSummary').innerHTML =
            statBox('Tasks assigned', s.assigned) +
            statBox('In progress', totalInProgress) +
            statBox('Pending', totalPending) +
            statBox('Completion rate', pct(s.completionRate)) +
            statBox('On-time rate', pct(s.onTimeRate)) +
            statBox('Attendance rate', pct(s.attendanceRate));

        if (!data.people.length) {
            document.getElementById('perfMsg').style.display = 'block';
            document.getElementById('perfMsg').textContent = 'No staff to show yet.';
            return;
        }

        document.getElementById('perfCharts').innerHTML =
            chartBox('Task status per person (click a bar)', 'perfChartStatus', 'col-12') +
            chartBox('Task completion rate (click a bar)', 'perfChartCompletion', 'col-lg-6') +
            chartBox('Attendance rate, last ' + data.attendanceDays + ' days (click a bar)', 'perfChartAttendance', 'col-lg-6');

        var names = data.people.map(function (p) { return p.name; });
        statusChart('perfChartStatus', names);
        barChart('perfChartCompletion', names,
            data.people.map(function (p) { return p.completionRate || 0; }), '#0d6efd', 'Completion rate');
        barChart('perfChartAttendance', names,
            data.people.map(function (p) { return p.attendanceRate || 0; }), '#20c997', 'Attendance rate');
    }

    fetch(url, { headers: { 'Authorization': 'Bearer ' + authToken } })
        .then(function (r) { if (!r.ok) throw new Error('status ' + r.status); return r.json(); })
        .then(function (d) {
            data = d;
            loadChartLib(render, function () {
                document.getElementById('perfMsg').innerHTML =
                    '<span class="text-danger">Could not load the chart library. Check your internet connection.</span>';
            });
        })
        .catch(function (err) {
            console.error('Performance error:', err);
            document.getElementById('perfMsg').innerHTML = '<span class="text-danger">Could not load.</span>';
        });
})();