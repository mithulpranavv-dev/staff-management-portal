var token = localStorage.getItem('token');

async function refreshMyQr() {
    try {
        const res = await fetch('/api/attendance/my-qr-token', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!res.ok) throw new Error('Failed to fetch QR token');

        const data = await res.json();
        const qrContent = `${data.empId}:${data.token}`;

        document.getElementById('qrBox').innerHTML = '';
        new QRCode(document.getElementById('qrBox'), {
            text: qrContent,
            width: 240,
            height: 240
        });

        document.getElementById('timer').textContent = `Refreshing in ${data.secondsRemaining}s`;
        document.getElementById('errorBox').classList.add('d-none');
    } catch (err) {
        document.getElementById('errorBox').textContent = 'Unable to load your QR code. Please refresh.';
        document.getElementById('errorBox').classList.remove('d-none');
    }
}

refreshMyQr();
setInterval(refreshMyQr, 5000); // stay synced with the 30s token window