var token = localStorage.getItem('token');

(function () {
    const username = localStorage.getItem('username');
    const role = localStorage.getItem('userRole');
    const token = localStorage.getItem('token');

    if (!username || !token) {
        window.location.href = 'index.html';
        return;
    }

    document.getElementById('welcomeText').textContent = `${username} (${role})`;

    document.getElementById('logoutBtn').addEventListener('click', function () {
        localStorage.removeItem('username');
        localStorage.removeItem('userRole');
        localStorage.removeItem('token');
        localStorage.removeItem('userEmail');
        localStorage.removeItem('mustChangePassword');
        window.location.href = 'index.html';
    });

    // Single source of truth: role -> correct dashboard page
    function getDashboardForRole(role) {
        switch (role) {
            case 'DEAN':
                return 'dean-dashboard.html';
            case 'ADMINISTRATOR':
                return 'admin-dashboard.html';
            case 'HEAD_OF_DEPARTMENT':
                return 'hod-dashboard.html';
            case 'FACULTY_MEMBER':
                return 'staff-dashboard.html';
            default:
                return 'dashboard.html';
        }
    }

    const dashboardUrl = getDashboardForRole(role);

    // Auto-wire any navbar brand / back-to-dashboard link on the page, if present
    const navbarBrand = document.getElementById('navbarBrandLink');
    if (navbarBrand) {
        navbarBrand.setAttribute('href', dashboardUrl);
    }

    const backLink = document.getElementById('backToDashboardLink');
    if (backLink) {
        backLink.setAttribute('href', dashboardUrl);
    }
})();