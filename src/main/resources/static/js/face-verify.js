const pendingUsername = sessionStorage.getItem('pendingFaceUsername');
const pendingPreAuthToken = sessionStorage.getItem('pendingPreAuthToken');

if (!pendingUsername || !pendingPreAuthToken) {
    // No pending login in progress — send back to the login page
    window.location.href = 'index.html';
}

const statusEl = document.getElementById('verifyStatus');
const verifyBtn = document.getElementById('verifyBtn');

function clearPendingLogin() {
    sessionStorage.removeItem('pendingFaceUsername');
    sessionStorage.removeItem('pendingPreAuthToken');
}

function backToLoginSoon(message) {
    clearPendingLogin();
    statusEl.textContent = message;
    statusEl.className = 'alert alert-danger mt-3';
    verifyBtn.classList.add('d-none');
    setTimeout(() => { window.location.href = 'index.html'; }, 3000);
}

async function initCamera() {
    try {
        await startFaceCamera('verifyVideo');
        statusEl.textContent = 'Camera ready. Click "Verify Face" when ready.';
        statusEl.className = 'alert alert-info mt-3';
        verifyBtn.classList.remove('d-none');
    } catch (err) {
        statusEl.textContent = 'Could not start camera or load face models. Please refresh and try again.';
        statusEl.className = 'alert alert-danger mt-3';
    }
}

async function attemptVerification() {
    verifyBtn.disabled = true;
    statusEl.textContent = 'Verifying...';
    statusEl.className = 'alert alert-info mt-3';

    const descriptor = await captureFaceDescriptor('verifyVideo');

    if (!descriptor) {
        statusEl.textContent = 'No face detected. Please make sure your face is visible and try again.';
        statusEl.className = 'alert alert-warning mt-3';
        verifyBtn.disabled = false;
        return;
    }

    try {
        const response = await fetch('/api/auth/verify-face', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ preAuthToken: pendingPreAuthToken, descriptor: descriptor })
        });

        let data = {};
        try { data = await response.json(); } catch (ignored) { /* no JSON body */ }

        if (!response.ok || !data.matched) {
            // The server decides when the session is over (expired, or 5 wrong faces)
            if (data.expired) {
                backToLoginSoon(data.error || 'Your login session has expired. Please log in again.');
                return;
            }
            statusEl.textContent = data.error || 'Face did not match. Please try again.';
            statusEl.className = 'alert alert-warning mt-3';
            verifyBtn.disabled = false;
            return;
        }

        // Success — store real session and proceed
        clearPendingLogin();
        localStorage.setItem('token', data.token);
        localStorage.setItem('username', data.username);
        localStorage.setItem('userRole', data.role);

        statusEl.textContent = 'Verified! Redirecting...';
        statusEl.className = 'alert alert-success mt-3';

        if (data.mustChangePassword) {
            window.location.href = 'change-password.html';
            return;
        }

        window.location.href = getDashboardForRole(data.role);
    } catch (err) {
        console.error('Verification error:', err);
        statusEl.textContent = 'Something went wrong. Please try again.';
        statusEl.className = 'alert alert-danger mt-3';
        verifyBtn.disabled = false;
    }
}

function getDashboardForRole(role) {
    switch (role) {
        case 'DEAN': return 'dean-dashboard.html';
        case 'ADMINISTRATOR': return 'admin-dashboard.html';
        case 'HEAD_OF_DEPARTMENT': return 'hod-dashboard.html';
        case 'FACULTY_MEMBER': return 'staff-dashboard.html';
        default: return 'dashboard.html';
    }
}

verifyBtn.addEventListener('click', attemptVerification);
initCamera();