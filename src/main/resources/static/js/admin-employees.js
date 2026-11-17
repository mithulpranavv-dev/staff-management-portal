let editModal;

const ROLES_EXEMPT_FROM_DEPARTMENT = ['ADMINISTRATOR', 'DEAN'];

// Turns text into safe HTML so names/emails can never run as code.
function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// Starts a camera and always puts the button back in a usable state, even if it fails.
async function startCameraFor(btn, videoId, captureBtnId) {
    btn.disabled = true;
    btn.textContent = 'Loading camera & models...';

    let success = false;
    try {
        success = await startFaceCamera(videoId);
    } catch (err) {
        console.error('Camera start failed:', err);
        success = false;
    }

    if (success) {
        document.getElementById(captureBtnId).disabled = false;
        btn.textContent = 'Camera Ready';
    } else {
        btn.disabled = false;
        btn.textContent = 'Start Camera';
        alert('Could not access camera. Please check permissions and try again.');
    }
}

document.getElementById('startCameraBtn').addEventListener('click', function () {
    startCameraFor(this, 'enrollVideo', 'captureFaceBtn');
});

document.getElementById('captureFaceBtn').addEventListener('click', async function () {
    const statusEl = document.getElementById('faceCaptureStatus');
    statusEl.textContent = 'Detecting face...';

    let descriptor = null;
    try {
        descriptor = await captureFaceDescriptor('enrollVideo');
    } catch (err) {
        console.error('Face capture failed:', err);
    }

    if (!descriptor) {
        statusEl.textContent = 'No face detected. Please try again.';
        statusEl.className = 'small mt-1 text-danger';
        return;
    }

    currentDescriptor = descriptor;
    statusEl.textContent = 'Face captured successfully!';
    statusEl.className = 'small mt-1 text-success';
});

document.getElementById('editStartCameraBtn').addEventListener('click', function () {
    startCameraFor(this, 'editEnrollVideo', 'editCaptureFaceBtn');
});

document.getElementById('editCaptureFaceBtn').addEventListener('click', async function () {
    const statusEl = document.getElementById('editFaceCaptureStatus');
    statusEl.textContent = 'Detecting face...';

    let descriptor = null;
    try {
        descriptor = await captureFaceDescriptor('editEnrollVideo');
    } catch (err) {
        console.error('Face capture failed:', err);
    }

    if (!descriptor) {
        statusEl.textContent = 'No face detected. Please try again.';
        statusEl.className = 'small mt-1 text-danger';
        return;
    }

    const empId = document.getElementById('editEmpId').value;
    const faceDescriptor = descriptorToCsv(descriptor);

    try {
        const response = await fetch(`/api/employees/${empId}/enroll-face`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ faceDescriptor })
        });

        if (!response.ok) throw new Error('Failed to enroll face');

        statusEl.textContent = 'Face enrolled successfully!';
        statusEl.className = 'small mt-1 text-success';
    } catch (err) {
        console.error('Error enrolling face:', err);
        statusEl.textContent = 'Something went wrong saving the face.';
        statusEl.className = 'small mt-1 text-danger';
    }
});

function toggleDepartmentField() {
    const role = document.getElementById('empRole').value;
    const deptWrapper = document.getElementById('empDepartmentWrapper');
    const deptSelect = document.getElementById('empDepartment');
    const designationWrapper = document.getElementById('empDesignationWrapper');
    const designationSelect = document.getElementById('empDesignation');

    if (ROLES_EXEMPT_FROM_DEPARTMENT.includes(role)) {
        deptWrapper.classList.add('d-none');
        deptSelect.removeAttribute('required');
        deptSelect.value = '';
    } else {
        deptWrapper.classList.remove('d-none');
        deptSelect.setAttribute('required', 'required');
    }

    if (role === 'FACULTY_MEMBER') {
        designationWrapper.style.display = '';
    } else {
        designationWrapper.style.display = 'none';
        designationSelect.value = '';
    }
}

function toggleEditDepartmentField(role) {
    const deptWrapper = document.getElementById('editEmpDepartmentWrapper');
    const deptSelect = document.getElementById('editEmpDepartment');
    const designationWrapper = document.getElementById('editEmpDesignationWrapper');

    if (ROLES_EXEMPT_FROM_DEPARTMENT.includes(role)) {
        deptWrapper.classList.add('d-none');
        deptSelect.removeAttribute('required');
        deptSelect.value = '';
    } else {
        deptWrapper.classList.remove('d-none');
        deptSelect.setAttribute('required', 'required');
    }

    if (role === 'FACULTY_MEMBER') {
        designationWrapper.style.display = '';
    } else {
        designationWrapper.style.display = 'none';
    }
}

function departmentDisplayLabel(department, role) {
    if (role === 'ADMINISTRATOR') return 'Administrator';
    if (role === 'DEAN') return 'Dean';
    if (department && role === 'HEAD_OF_DEPARTMENT') return `${esc(department)} <span class="badge bg-info text-dark">HOD</span>`;
    if (department) return esc(department);
    return '<span class="text-muted">N/A</span>';
}

function formatCurrency(amount) {
    if (amount === null || amount === undefined) return '<span class="text-muted">N/A</span>';
    return '₹' + Math.round(amount).toLocaleString('en-IN');
}

function getDaysInCurrentMonth() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
}

// Builds one table row. Text is escaped; the Edit button carries its data in data-* attributes.
function buildEmployeeRow(emp, daysInMonth) {
    const perDay = emp.monthlySalary != null ? emp.monthlySalary / daysInMonth : null;

    const row = document.createElement('tr');
    row.innerHTML = `
        <td>${esc(emp.employeeCode) || '-'}</td>
        <td>${esc(emp.name)}</td>
        <td>${esc(emp.email)}</td>
        <td>${departmentDisplayLabel(emp.department, emp.role)}</td>
        <td>${formatCurrency(emp.monthlySalary)}</td>
        <td>${formatCurrency(perDay)}</td>
        <td class="text-end">
            <button class="btn btn-sm btn-outline-primary me-1 edit-btn"
                    data-id="${esc(emp.empId)}"
                    data-name="${esc(emp.name)}"
                    data-email="${esc(emp.email)}"
                    data-department="${esc(emp.department)}"
                    data-role="${esc(emp.role)}"
                    data-salary="${esc(emp.salaryPerAnnum)}"
                    data-designation="${esc(emp.designation)}">Edit</button>
            <button class="btn btn-sm btn-outline-danger delete-btn" data-id="${esc(emp.empId)}">Delete</button>
        </td>
    `;
    return row;
}

async function loadEmployees() {
    try {
        const response = await fetch('/api/employees', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) throw new Error('Failed to load employees');

        const employees = await response.json();
        const tableBody = document.getElementById('employeeTableBody');
        tableBody.innerHTML = '';

        const daysInMonth = getDaysInCurrentMonth();

        employees.forEach(emp => {
            tableBody.appendChild(buildEmployeeRow(emp, daysInMonth));
        });
    } catch (err) {
        console.error('Error loading employees:', err);
    }
}

// One click handler for the Edit and Delete buttons in the table.
document.getElementById('employeeTableBody').addEventListener('click', function (e) {
    const editBtn = e.target.closest('.edit-btn');
    if (editBtn) {
        const d = editBtn.dataset;
        openEditModal(
            Number(d.id),
            d.name,
            d.email,
            d.department,
            d.role,
            d.salary === '' ? null : Number(d.salary),
            d.designation
        );
        return;
    }

    const deleteBtn = e.target.closest('.delete-btn');
    if (deleteBtn) {
        deleteEmployee(Number(deleteBtn.dataset.id));
    }
});

document.getElementById('empRole').addEventListener('change', toggleDepartmentField);
document.addEventListener('DOMContentLoaded', toggleDepartmentField);

document.getElementById('addEmployeeForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const name = document.getElementById('empName').value;
    const email = document.getElementById('empEmail').value;
    const departmentValue = document.getElementById('empDepartment').value;
    const department = departmentValue === '' ? null : departmentValue;
    const role = document.getElementById('empRole').value;
    const salaryValue = document.getElementById('empSalary').value;
    const salaryPerAnnum = salaryValue === '' ? null : Number(salaryValue);
    const faceDescriptor = currentDescriptor ? descriptorToCsv(currentDescriptor) : null;
    const designationValue = document.getElementById('empDesignation').value;
    const designation = designationValue === '' ? null : designationValue;

    try {
        const response = await fetch('/api/employees', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ name, email, department, role, salaryPerAnnum, faceDescriptor, designation })
        });

        if (!response.ok) throw new Error('Failed to add employee');

        const created = await response.json();

        const credBox = document.getElementById('newEmployeeCredentials');
        credBox.classList.remove('d-none');
        credBox.innerHTML = `
            <strong>Employee created!</strong> Share these credentials with ${esc(created.name)}:<br>
            Username: <strong>${esc(created.generatedUsernameForResponse)}</strong><br>
            Temporary Password: <strong>${esc(created.tempPasswordForResponse)}</strong>
        `;

        document.getElementById('addEmployeeForm').reset();
        toggleDepartmentField();
        currentDescriptor = null;
        document.getElementById('faceCaptureStatus').textContent = 'No face captured yet';
        document.getElementById('faceCaptureStatus').className = 'small mt-1 text-muted';
        document.getElementById('startCameraBtn').disabled = false;
        document.getElementById('startCameraBtn').textContent = 'Start Camera';
        document.getElementById('captureFaceBtn').disabled = true;
        loadEmployees();
    } catch (err) {
        console.error('Error adding employee:', err);
        alert('Something went wrong while adding the employee.');
    }
});

function openEditModal(empId, name, email, department, role, salaryPerAnnum, designation) {
    document.getElementById('editEmpId').value = empId;
    document.getElementById('editEmpRole').value = role;
    document.getElementById('editEmpName').value = name;
    document.getElementById('editEmpEmail').value = email;
    document.getElementById('editEmpDepartment').value = department;
    document.getElementById('editEmpSalary').value = salaryPerAnnum ?? '';
    document.getElementById('editEmpDesignation').value = designation || '';
    toggleEditDepartmentField(role);
    editModal.show();
}

document.getElementById('editEmployeeForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const empId = document.getElementById('editEmpId').value;
    const role = document.getElementById('editEmpRole').value;
    const name = document.getElementById('editEmpName').value;
    const email = document.getElementById('editEmpEmail').value;
    const departmentValue = document.getElementById('editEmpDepartment').value;
    const department = ROLES_EXEMPT_FROM_DEPARTMENT.includes(role)
        ? null
        : (departmentValue === '' ? null : departmentValue);
    const salaryValue = document.getElementById('editEmpSalary').value;
    const salaryPerAnnum = salaryValue === '' ? null : Number(salaryValue);
    const designationValue = document.getElementById('editEmpDesignation').value;
    const designation = designationValue === '' ? null : designationValue;

    try {
        const response = await fetch(`/api/employees/${empId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ name, email, department, salaryPerAnnum, designation })
        });

        if (!response.ok) throw new Error('Failed to update employee');

        editModal.hide();
        loadEmployees();
    } catch (err) {
        console.error('Error updating employee:', err);
        alert('Something went wrong while updating the employee.');
    }
});

async function deleteEmployee(empId) {
    if (!confirm('Are you sure you want to delete this employee?')) return;

    try {
        const response = await fetch(`/api/employees/${empId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.status === 403) {
            alert('You do not have permission to delete employees.');
            return;
        }

        loadEmployees();
    } catch (err) {
        console.error('Error deleting employee:', err);
    }
}

document.getElementById('smartSearchBtn').addEventListener('click', performSmartSearch);
document.getElementById('smartSearchInput').addEventListener('keypress', (e) => {
    if (e.key === 'Enter') performSmartSearch();
});
document.getElementById('clearSearchBtn').addEventListener('click', () => {
    document.getElementById('smartSearchInput').value = '';
    document.getElementById('clearSearchBtn').classList.add('d-none');
    loadEmployees();
});

async function performSmartSearch() {
    const query = document.getElementById('smartSearchInput').value.trim();
    if (!query) return;

    const tableBody = document.getElementById('employeeTableBody');
    tableBody.innerHTML = `<tr><td colspan="7" class="text-center text-muted py-4">Searching...</td></tr>`;

    try {
        const response = await fetch(`/api/search/employees?q=${encodeURIComponent(query)}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) throw new Error('Search failed');

        const results = await response.json();
        document.getElementById('clearSearchBtn').classList.remove('d-none');

        tableBody.innerHTML = '';

        if (results.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="7" class="text-center text-muted py-4">No matching employees found.</td></tr>`;
            return;
        }

        const daysInMonth = getDaysInCurrentMonth();

        results.forEach(emp => {
            tableBody.appendChild(buildEmployeeRow(emp, daysInMonth));
        });
    } catch (err) {
        console.error('Smart search error:', err);
        tableBody.innerHTML = `<tr><td colspan="7" class="text-center text-danger py-4">Search failed. Please try again.</td></tr>`;
    }
}

(function init() {
    editModal = new bootstrap.Modal(document.getElementById('editEmployeeModal'));
    loadEmployees();
})();