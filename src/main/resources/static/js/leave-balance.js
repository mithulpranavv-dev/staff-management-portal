(async function loadLeaveBalance() {
    var box = document.getElementById('leaveBalance');
    if (!box) return;
    try {
        var response = await fetch('/api/leaves/balance', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!response.ok) throw new Error('balance failed');
        var b = await response.json();

        var html = '<strong>Paid leave (' + b.year + '):</strong> ' +
            b.paidUsed + ' of ' + b.paidQuota + ' used &middot; ' +
            '<strong>' + b.paidRemaining + '</strong> remaining';
        if (b.unpaidDays > 0) {
            html += ' &middot; <span class="text-danger">' + b.unpaidDays +
                ' unpaid day(s) will be deducted from salary</span>';
        }
        html += '<br><small class="text-muted">Only approved leave counts. Sundays are not counted.</small>';
        box.innerHTML = html;
    } catch (err) {
        console.error('Error loading leave balance:', err);
    }
})();