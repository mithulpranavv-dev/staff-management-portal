const errorBox = document.getElementById('loginAlert');
const successBox = document.getElementById('loginSuccessAlert');

// Show a message passed in the URL (e.g. after a password reset)
const urlMessage = new URLSearchParams(window.location.search).get('msg');
if (urlMessage) {
    successBox.textContent = urlMessage;
    successBox.classList.remove('d-none');
}

function showError(message) {
    errorBox.textContent = message;
    errorBox.classList.remove('d-none');
}

document.getElementById('loginForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    errorBox.classList.add('d-none');

    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    try {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) {
            showError(response.status === 401
                ? 'Invalid username or password.'
                : 'Login failed. Please try again.');
            return;
        }

        const result = await response.json();

        if (result.requiresFaceVerification) {
            // The pre-auth token proves the password step passed. The face page must send it.
            sessionStorage.setItem('pendingFaceUsername', result.username);
            sessionStorage.setItem('pendingPreAuthToken', result.preAuthToken);
            window.location.href = 'face-verify.html';
            return;
        }

        // Store the JWT token and user info — every future API call needs this token
        localStorage.setItem('token', result.token);
        localStorage.setItem('username', result.username);
        localStorage.setItem('userRole', result.role);

        if (result.mustChangePassword) {
            window.location.href = 'change-password.html';
            return;
        }

        // Route to the correct dashboard based on role
        window.location.href = getDashboardForRole(result.role);

    } catch (error) {
        showError('Something went wrong. Please try again.');
    }
});

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
        // Founder/Pro/Vice Chancellor roles exist in your Role enum but don't have
        // dedicated portals yet — send them somewhere safe for now
        default:
            return 'dashboard.html';
    }
}