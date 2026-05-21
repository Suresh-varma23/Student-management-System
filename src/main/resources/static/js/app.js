// ==========================================================================
// SPA SESSION MANAGEMENT & CONFIG
// ==========================================================================
let currentUser = null;
let activeTab = 'dashboard';
let studentsList = [];
let academicChart = null;
let attendanceChart = null;

// Class cards and Workspace Hub states
let activeDashboardClass = 'LKG';
let activeDashboardSection = 'A';
let activeHubTab = 'students';

const ACADEMIC_CLASS_ORDER = [
    'LKG', 'UKG', 'Class 1', 'Class 2', 'Class 3', 'Class 4', 'Class 5',
    'Class 6', 'Class 7', 'Class 8', 'Class 9', 'Class 10', 'Class 11', 'Class 12'
];

// Pagination variables
let currentPage = 1;
const rowsPerPage = 5;

// Network source override (mocking Wifi sandbox state)
let mockWifiNetwork = true;

document.addEventListener('DOMContentLoaded', async () => {
    // 1. Verify Active User Session
    const sessionData = localStorage.getItem('currentUser');
    if (!sessionData) {
        window.location.href = 'login.html';
        return;
    }
    currentUser = JSON.parse(sessionData);

    // 2. Set Up Active Profile Dashboard Elements
    document.getElementById('sidebarUserName').textContent = currentUser.username;
    document.getElementById('sidebarUserRole').textContent = currentUser.role.toLowerCase();
    document.getElementById('avatarLetter').textContent = currentUser.username.charAt(0).toUpperCase();

    // 3. Persist Theme Mode
    const savedTheme = localStorage.getItem('themeMode');
    if (savedTheme === 'light') {
        document.body.classList.add('light-theme');
    }

    // 4. Role-based Workspace Rendering
    applyRoleAccessControls();

    // 5. Initialize Nav controls
    initNavbarControls();

    // Initialize dynamic class select systems from DB
    await loadDynamicClassDropdowns();

    // 6. Bootstrap active panel
    switchTab(activeTab);
});

// ==========================================================================
// ACCESS CONTROLS & SIDEBAR MODIFIERS
// ==========================================================================
function applyRoleAccessControls() {
    const role = currentUser.role;

    if (role === 'STUDENT') {
        // Hides standard Admin/Teacher operations
        document.getElementById('menuStudents').style.display = 'none';
        document.getElementById('menuAttendance').style.display = 'none';
        document.getElementById('addNewStudentBtn').style.display = 'none';
        document.getElementById('editHomeworkBtn').style.display = 'none';
        document.getElementById('editTimetableBtn').style.display = 'none';
        document.getElementById('addNewExamBtn').style.display = 'none';

        // Redirects active landing page to settings or custom student profile
        activeTab = 'settings';
        
        // Dynamic customization of Settings panel for student bio details
        document.getElementById('navbarTitle').textContent = "My Academic Profile";
        
        // Seed student's own bio data on dashboard startup
        fetchStudentOwnData();
    } else if (role === 'TEACHER') {
        // Teachers cannot add students or exams
        document.getElementById('addNewStudentBtn').style.display = 'none';
        document.getElementById('addNewExamBtn').style.display = 'none';
    }
}

// ==========================================================================
// GLOBAL WORKSPACE ACTIONS & TABS
// ==========================================================================
function initNavbarControls() {
    // Sidebar collapse action
    const sidebar = document.getElementById('sidebar');
    const toggle = document.getElementById('sidebarToggle');
    toggle.addEventListener('click', () => {
        sidebar.classList.toggle('collapsed');
    });

    // Sidebar items tap
    const menuItems = document.querySelectorAll('.sidebar-item[data-tab]');
    menuItems.forEach(item => {
        item.addEventListener('click', () => {
            const target = item.getAttribute('data-tab');
            
            // Student restricted checks
            if (currentUser.role === 'STUDENT' && (target === 'students' || target === 'attendance')) {
                showToast("Access Denied: Students are restricted from administrative views.", "error", "⛔");
                return;
            }

            menuItems.forEach(mi => mi.classList.remove('active'));
            item.classList.add('active');
            switchTab(target);
        });
    });

    // Theme toggler click
    document.getElementById('themeToggler').addEventListener('click', toggleThemeGlobal);

    // Global Search trigger
    document.getElementById('globalSearch').addEventListener('input', (e) => {
        const query = e.target.value.toLowerCase().trim();
        if (activeTab === 'students') {
            filterStudentsByQuery(query);
        }
    });
}

function switchTab(tabId) {
    activeTab = tabId;
    
    // Deactivate panels
    const panels = document.querySelectorAll('.tab-panel');
    panels.forEach(p => p.classList.remove('active'));

    const activePanel = document.getElementById(`tab-${tabId}`);
    if (activePanel) {
        activePanel.classList.add('active');
    }

    // Dynamic headers
    const titleHeader = document.getElementById('navbarTitle');
    const pageNames = {
        'dashboard': 'Dashboard Summary',
        'students': 'Students Profile Registry',
        'attendance': 'Daily Class Attendance roll-call',
        'homework': 'Daily Assignments board',
        'timetable': 'Weekly Class Timetable (7 Periods)',
        'exams': 'Exam Schedules & Timetables',
        'fees': 'Tuition Fee Transactions',
        'settings': 'Account Settings Profile'
    };
    titleHeader.textContent = pageNames[tabId] || 'Vedic Workspace';

    // Reload respective view details
    loadTabData(tabId);
}

function loadTabData(tabId) {
    if (tabId === 'dashboard') {
        loadDashboardStats();
    } else if (tabId === 'students') {
        loadStudents();
    } else if (tabId === 'attendance') {
        loadAttendanceSheet();
        loadSmsLogs();
    } else if (tabId === 'homework') {
        loadHomework();
    } else if (tabId === 'timetable') {
        loadClassTimetable();
    } else if (tabId === 'exams') {
        loadExams();
    } else if (tabId === 'fees') {
        loadFees();
    } else if (tabId === 'settings') {
        loadSettingsProfile();
    }
}

// Toggle Theme Mode
function toggleThemeGlobal() {
    const isLight = document.body.classList.toggle('light-theme');
    localStorage.setItem('themeMode', isLight ? 'light' : 'dark');
    showToast(`Switched to ${isLight ? 'Light' : 'Dark'} Mode`, "success", "🌓");
}

// Toggle Network Source
function toggleNetwork() {
    mockWifiNetwork = !mockWifiNetwork;
    const badge = document.getElementById('wifiIndicator');
    if (mockWifiNetwork) {
        badge.textContent = "🌐 Online Network";
        badge.style.background = "rgba(16, 185, 129, 0.15)";
        badge.style.borderColor = "rgba(16, 185, 129, 0.3)";
        badge.style.color = "var(--accent-green)";
        showToast("Connected to Internet.", "success", "📶");
    } else {
        badge.textContent = "🔒 School WiFi Network";
        badge.style.background = "rgba(139, 92, 246, 0.15)";
        badge.style.borderColor = "rgba(139, 92, 246, 0.3)";
        badge.style.color = "var(--primary-light)";
        showToast("Access restricted to School Local WiFi.", "warning", "🛡️");
    }
}

// Custom slide toast notifications
function showToast(msg, type = "success", icon = "✅") {
    const deck = document.getElementById('toastDeck');
    
    const panel = document.createElement('div');
    panel.className = `toast-panel ${type}`;
    panel.innerHTML = `<span>${icon}</span> <span>${msg}</span>`;
    
    deck.appendChild(panel);

    // Fade out and remove element
    setTimeout(() => {
        panel.style.transform = "translateX(120%)";
        panel.style.transition = "transform 0.4s ease";
        setTimeout(() => {
            panel.remove();
        }, 400);
    }, 3500);
}

// Sign Out
function logout() {
    fetch('/api/auth/logout', { method: 'POST' })
        .then(() => {
            localStorage.clear();
            window.location.href = 'login.html';
        });
}

// ==========================================================================
// DYNAMIC DASHBOARD DATA AND CHART ENGINE
// ==========================================================================
async function loadDashboardStats() {
    if (currentUser.role === 'STUDENT') return;

    try {
        // Fetch raw students for selected class
        const response = await fetch(`/api/students?classGrade=${encodeURIComponent(activeDashboardClass)}&section=${encodeURIComponent(activeDashboardSection)}`);
        const students = await response.json();
        
        document.getElementById('cardTotalStudents').textContent = students.length;

        // Fetch attendance ratios for selected class
        const attRes = await fetch(`/api/attendance/stats?classGrade=${encodeURIComponent(activeDashboardClass)}&section=${encodeURIComponent(activeDashboardSection)}`);
        const attStats = await attRes.json();
        
        document.getElementById('cardPresentToday').textContent = attStats.present;
        document.getElementById('cardActiveSections').textContent = `${activeDashboardClass} - ${activeDashboardSection}`;

        // Fetch grades for all students in the class dynamically
        const gradesPromises = students.map(s => fetch(`/api/students/${s.id}/grades`).then(r => r.ok ? r.json() : []));
        const gradesList = await Promise.all(gradesPromises);

        const studentsWithGrades = students.map((s, idx) => {
            const studentGrades = gradesList[idx] || [];
            const quarterly = studentGrades.find(g => g.examName === 'Quarterly Exam');
            return {
                name: s.name,
                totalMarks: quarterly ? quarterly.totalMarks : 0
            };
        });

        // Draw Academic and Attendance Chart panels
        renderDashboardCharts(studentsWithGrades, attStats);
    } catch (err) {
        console.error("Dashboard stats error:", err);
    }
}

function renderDashboardCharts(studentsWithGrades, attStats) {
    // 1. Grouped Bar Chart: Dynamic 7-Subject Obtained Scores per student
    const academicCanvas = document.getElementById('academicScoresChart');
    if (!academicCanvas) return;

    if (academicChart) academicChart.destroy();

    const studentNames = studentsWithGrades.map(s => s.name);
    const obtainedTotalScores = studentsWithGrades.map(s => s.totalMarks);
    
    // Average percentage calculations
    const percentages = obtainedTotalScores.map(t => Math.round((t / 700) * 100));
    
    // Update dashboard metrics
    let overallAvg = 0;
    if (percentages.length > 0) {
        overallAvg = percentages.reduce((a, b) => a + b, 0) / percentages.length;
    }
    document.getElementById('cardAvgMarks').textContent = Math.round(overallAvg) + "%";

    academicChart = new Chart(academicCanvas, {
        type: 'bar',
        data: {
            labels: studentNames,
            datasets: [{
                label: 'Total Obtained Marks (out of 700)',
                data: obtainedTotalScores,
                backgroundColor: [
                    'rgba(139, 92, 246, 0.65)',
                    'rgba(59, 130, 246, 0.65)',
                    'rgba(6, 182, 212, 0.65)',
                    'rgba(16, 185, 129, 0.65)',
                    'rgba(245, 158, 11, 0.65)',
                    'rgba(239, 68, 68, 0.65)',
                    'rgba(6, 182, 212, 0.65)'
                ],
                borderColor: [
                    'rgba(139, 92, 246, 1)',
                    'rgba(59, 130, 246, 1)',
                    'rgba(6, 182, 212, 1)',
                    'rgba(16, 185, 129, 1)',
                    'rgba(245, 158, 11, 1)',
                    'rgba(239, 68, 68, 1)',
                    'rgba(6, 182, 212, 1)'
                ],
                borderWidth: 1,
                borderRadius: 8
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: { max: 700, grid: { color: 'rgba(255,255,255,0.03)' } },
                x: { grid: { display: false } }
            }
        }
    });

    // 2. Attendance Doughnut ratio chart
    const attCanvas = document.getElementById('attendanceTidesChart');
    if (!attCanvas) return;

    if (attendanceChart) attendanceChart.destroy();

    attendanceChart = new Chart(attCanvas, {
        type: 'doughnut',
        data: {
            labels: ['Present Today', 'Absent', 'Half-day'],
            datasets: [{
                data: [attStats.present || 0, attStats.absent || 0, attStats.half || 0],
                backgroundColor: [
                    'rgba(16, 185, 129, 0.75)',
                    'rgba(239, 68, 68, 0.75)',
                    'rgba(245, 158, 11, 0.75)'
                ],
                borderColor: [
                    'rgba(16, 185, 129, 1)',
                    'rgba(239, 68, 68, 1)',
                    'rgba(245, 158, 11, 1)'
                ],
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'bottom', labels: { color: '#94a3b8' } }
            }
        }
    });
}

// ==========================================================================
// STUDENT PROFILE DIRECTORY & CALCULATOR
// ==========================================================================
async function loadStudents() {
    const classVal = document.getElementById('filterClass').value;
    const sectVal = document.getElementById('filterSection').value;

    const tbody = document.getElementById('studentsTableBody');
    tbody.innerHTML = `<tr><td colspan="6" class="skeleton" style="height:120px; border-radius:12px;"></td></tr>`;

    try {
        const response = await fetch(`/api/students?classGrade=${classVal}&section=${sectVal}`);
        studentsList = await response.json();
        currentPage = 1;
        renderStudentsTable();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; color:var(--accent-red);">Failed to load students.</td></tr>`;
    }
}

function renderStudentsTable() {
    const tbody = document.getElementById('studentsTableBody');
    tbody.innerHTML = '';

    if (studentsList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;">No students enrolled in this section.</td></tr>`;
        return;
    }

    // Apply pagination bounds
    const startIdx = (currentPage - 1) * rowsPerPage;
    const endIdx = Math.min(startIdx + rowsPerPage, studentsList.length);
    const paginatedList = studentsList.slice(startIdx, endIdx);

    paginatedList.forEach(student => {
        const row = document.createElement('tr');
        
        // Fee balance indicators
        let feeBadge = `<span class="badge badge-unpaid">UNPAID</span>`;
        if (student.id % 5 === 1) feeBadge = `<span class="badge badge-partial">PARTIAL</span>`;
        if (student.id % 5 === 2 || student.id % 5 === 3) feeBadge = `<span class="badge badge-paid">PAID</span>`;

        // Render Action buttons based on Role
        let actionButtons = ``;
        if (currentUser.role === 'ADMIN') {
            actionButtons = `
                <button class="btn btn-secondary" style="padding:0.3rem 0.6rem; font-size:0.75rem;" onclick="openEditStudentModal(${student.id})">✏️ Profile</button>
                <button class="btn btn-secondary" style="padding:0.3rem 0.6rem; font-size:0.75rem; background:rgba(139,92,246,0.15);" onclick="openMarksEntryModal(${student.id}, '${student.name}', '${student.rollNumber}')">📝 Add Marks</button>
                <button class="btn btn-secondary" style="padding:0.3rem 0.6rem; font-size:0.75rem; color:var(--accent-red);" onclick="deleteStudent(${student.id})">🗑️ Delete</button>
            `;
        } else if (currentUser.role === 'TEACHER') {
            actionButtons = `
                <button class="btn btn-secondary" style="padding:0.3rem 0.6rem; font-size:0.75rem; background:rgba(139,92,246,0.15);" onclick="openMarksEntryModal(${student.id}, '${student.name}', '${student.rollNumber}')">📝 Add Marks</button>
            `;
        } else {
            actionButtons = `<span style="font-size:0.8rem; color:var(--text-sub);">Read Only</span>`;
        }

        row.innerHTML = `
            <td><strong>${student.rollNumber}</strong></td>
            <td>${student.name}</td>
            <td>${student.optionalSubject1}, ${student.optionalSubject2}</td>
            <td>${student.parentPhone}</td>
            <td>${feeBadge}</td>
            <td>${actionButtons}</td>
        `;
        tbody.appendChild(row);
    });

    renderPaginationControls();
}

function filterStudentsByQuery(query) {
    // In-memory instant search filter
    const originalList = [...studentsList];
    studentsList = studentsList.filter(s => 
        s.name.toLowerCase().contains(query) || 
        s.rollNumber.toLowerCase().contains(query)
    );
    currentPage = 1;
    renderStudentsTable();
    studentsList = originalList; // Restore list to keep filters
}

// Pagination Builder
function renderPaginationControls() {
    const info = document.getElementById('paginationInfo');
    const controls = document.getElementById('paginationControls');
    
    const totalPages = Math.ceil(studentsList.length / rowsPerPage) || 1;
    const startIdx = (currentPage - 1) * rowsPerPage + 1;
    const endIdx = Math.min(startIdx + rowsPerPage - 1, studentsList.length);

    info.textContent = `Showing ${studentsList.length > 0 ? startIdx : 0} to ${endIdx} of ${studentsList.length} entries`;
    
    controls.innerHTML = '';
    
    // Prev button
    const prevBtn = document.createElement('button');
    prevBtn.className = 'pagination-btn';
    prevBtn.textContent = '«';
    prevBtn.disabled = currentPage === 1;
    prevBtn.addEventListener('click', () => {
        currentPage--;
        renderStudentsTable();
    });
    controls.appendChild(prevBtn);

    // Page Buttons
    for (let i = 1; i <= totalPages; i++) {
        const btn = document.createElement('button');
        btn.className = `pagination-btn ${i === currentPage ? 'active' : ''}`;
        btn.textContent = i;
        btn.addEventListener('click', () => {
            currentPage = i;
            renderStudentsTable();
        });
        controls.appendChild(btn);
    }

    // Next button
    const nextBtn = document.createElement('button');
    nextBtn.className = 'pagination-btn';
    nextBtn.textContent = '»';
    nextBtn.disabled = currentPage === totalPages;
    nextBtn.addEventListener('click', () => {
        currentPage++;
        renderStudentsTable();
    });
    controls.appendChild(nextBtn);
}

// Student CRUD Modal Operations
function openAddStudentModal() {
    document.getElementById('studentFormId').value = '';
    document.getElementById('studentForm').reset();
    document.getElementById('studentModalTitle').textContent = "Register New Student Profile";
    document.getElementById('studentModal').classList.add('active');
}

async function openEditStudentModal(id) {
    document.getElementById('studentModalTitle').textContent = "Edit Student Profile";
    document.getElementById('studentModal').classList.add('active');

    try {
        const res = await fetch(`/api/students/${id}`);
        const s = await res.json();
        
        document.getElementById('studentFormId').value = s.id;
        document.getElementById('sName').value = s.name;
        document.getElementById('sRoll').value = s.rollNumber;
        document.getElementById('sEmail').value = s.email;
        document.getElementById('sPhone').value = s.parentPhone;
        document.getElementById('sClass').value = s.classGrade;
        document.getElementById('sSection').value = s.section;
        document.getElementById('sGender').value = s.gender;
        document.getElementById('sDob').value = s.dob;
        document.getElementById('sOpt1').value = s.optionalSubject1;
        document.getElementById('sOpt2').value = s.optionalSubject2;
        document.getElementById('sParent').value = s.parentName;
        document.getElementById('sAddress').value = s.address;
    } catch (err) {
        showToast("Error retrieving student details.", "error", "⚠️");
    }
}

document.getElementById('studentForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const id = document.getElementById('studentFormId').value;
    const payload = {
        name: document.getElementById('sName').value,
        rollNumber: document.getElementById('sRoll').value,
        email: document.getElementById('sEmail').value,
        parentPhone: document.getElementById('sPhone').value,
        classGrade: document.getElementById('sClass').value,
        section: document.getElementById('sSection').value,
        gender: document.getElementById('sGender').value,
        dob: document.getElementById('sDob').value,
        optionalSubject1: document.getElementById('sOpt1').value,
        optionalSubject2: document.getElementById('sOpt2').value,
        parentName: document.getElementById('sParent').value,
        address: document.getElementById('sAddress').value
    };

    const method = id ? 'PUT' : 'POST';
    const url = id ? `/api/students/${id}` : '/api/students';

    try {
        const res = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showToast(`Student profile saved successfully!`, "success");
            closeStudentModal();
            loadStudents();
        } else {
            const data = await res.json();
            showToast(data.message || "Failed to save student profile", "error", "❌");
        }
    } catch (err) {
        showToast("Connection failed.", "error", "📡");
    }
});

function closeStudentModal() {
    document.getElementById('studentModal').classList.remove('active');
}

async function deleteStudent(id) {
    if (!confirm("Are you sure you want to permanently delete this student profile?")) return;

    try {
        const res = await fetch(`/api/students/${id}`, { method: 'DELETE' });
        if (res.ok) {
            showToast("Student profile successfully deleted.", "success");
            loadStudents();
        }
    } catch (err) {
        showToast("Deletion failed.", "error", "❌");
    }
}

// 7-Subject Marks Entry Dialog
async function openMarksEntryModal(studentId, name, roll) {
    document.getElementById('marksModalStudentName').textContent = name;
    document.getElementById('marksModalStudentRoll').textContent = `Roll No: ${roll}`;
    document.getElementById('marksFormStudentId').value = studentId;

    // Load active optional subject names from profile
    try {
        const sRes = await fetch(`/api/students/${studentId}`);
        const sData = await sRes.json();
        document.getElementById('labelOpt1').textContent = `💻 ${sData.optionalSubject1 || 'Optional 1'}`;
        document.getElementById('labelOpt2').textContent = `🕉️ ${sData.optionalSubject2 || 'Optional 2'}`;
        
        // Fetch existing grades if present
        const examName = document.getElementById('marksExamName').value;
        const res = await fetch(`/api/students/${studentId}/grades`);
        const grades = await res.json();
        
        const currentGrade = grades.find(g => g.examName === examName);
        if (currentGrade) {
            document.getElementById('markEnglish').value = currentGrade.english;
            document.getElementById('markTamil').value = currentGrade.tamil;
            document.getElementById('markMaths').value = currentGrade.maths;
            document.getElementById('markScience').value = currentGrade.science;
            document.getElementById('markSocial').value = currentGrade.socialScience;
            document.getElementById('markOpt1').value = currentGrade.optional1Marks;
            document.getElementById('markOpt2').value = currentGrade.optional2Marks;
            
            document.getElementById('calcTotalObtained').textContent = currentGrade.totalMarks;
            document.getElementById('calcAverageScore').textContent = currentGrade.averageMarks + "%";
            document.getElementById('calcRankPosition').textContent = `Rank ${currentGrade.classRank}`;
        } else {
            // Reset to defaults
            document.getElementById('markEnglish').value = 0;
            document.getElementById('markTamil').value = 0;
            document.getElementById('markMaths').value = 0;
            document.getElementById('markScience').value = 0;
            document.getElementById('markSocial').value = 0;
            document.getElementById('markOpt1').value = 0;
            document.getElementById('markOpt2').value = 0;
            
            document.getElementById('calcTotalObtained').textContent = "0";
            document.getElementById('calcAverageScore').textContent = "0%";
            document.getElementById('calcRankPosition').textContent = "Rank -";
        }
    } catch (err) {
        console.error(err);
    }

    document.getElementById('marksModal').classList.add('active');
}

// Instant local Obtained calculator
function calculateObtainedMarksRealtime() {
    const e = parseInt(document.getElementById('markEnglish').value) || 0;
    const t = parseInt(document.getElementById('markTamil').value) || 0;
    const m = parseInt(document.getElementById('markMaths').value) || 0;
    const s = parseInt(document.getElementById('markScience').value) || 0;
    const ss = parseInt(document.getElementById('markSocial').value) || 0;
    const o1 = parseInt(document.getElementById('markOpt1').value) || 0;
    const o2 = parseInt(document.getElementById('markOpt2').value) || 0;

    const total = e + t + m + s + ss + o1 + o2;
    const avg = total / 7.0;

    document.getElementById('calcTotalObtained').textContent = total;
    document.getElementById('calcAverageScore').textContent = (Math.round(avg * 100) / 100) + "%";
}

document.getElementById('marksForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const studentId = document.getElementById('marksFormStudentId').value;
    const payload = {
        examName: document.getElementById('marksExamName').value,
        english: parseInt(document.getElementById('markEnglish').value) || 0,
        tamil: parseInt(document.getElementById('markTamil').value) || 0,
        maths: parseInt(document.getElementById('markMaths').value) || 0,
        science: parseInt(document.getElementById('markScience').value) || 0,
        socialScience: parseInt(document.getElementById('markSocial').value) || 0,
        optional1Marks: parseInt(document.getElementById('markOpt1').value) || 0,
        optional2Marks: parseInt(document.getElementById('markOpt2').value) || 0
    };

    try {
        const res = await fetch(`/api/students/${studentId}/grades`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            const data = await res.json();
            showToast("Student grades registered successfully!", "success");
            
            // Render the calculated rank that returned from backend
            document.getElementById('calcRankPosition').textContent = `Rank ${data.classRank}`;
            setTimeout(() => {
                closeMarksModal();
                loadStudents(); // refreshes table list to update totals
            }, 1000);
        } else {
            showToast("Failed to save student grades.", "error", "❌");
        }
    } catch (err) {
        showToast("Connection failed.", "error", "📡");
    }
});

function closeMarksModal() {
    document.getElementById('marksModal').classList.remove('active');
}

// ==========================================================================
// CLIENT-SIDE EXPORT & COMPILATION UTILS
// ==========================================================================
function exportExcelStudents() {
    if (studentsList.length === 0) {
        showToast("Nothing to export.", "warning", "⚠️");
        return;
    }
    
    // Structure dynamic columns for sheetjs
    const data = studentsList.map(s => ({
        "Roll Number": s.rollNumber,
        "Student Name": s.name,
        "Email ID": s.email,
        "Class": s.classGrade,
        "Section": s.section,
        "Parent Phone": s.parentPhone,
        "Optional 1": s.optionalSubject1,
        "Optional 2": s.optionalSubject2
    }));

    const worksheet = XLSX.utils.json_to_sheet(data);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Students");
    
    // Save file
    XLSX.writeFile(workbook, "Student_Directory_Registry.xlsx");
    showToast("Downloaded Excel report card!", "success", "📥");
}

function exportPDFStudents() {
    if (studentsList.length === 0) {
        showToast("Nothing to print.", "warning", "⚠️");
        return;
    }

    const { jsPDF } = window.jspdf;
    const doc = new jsPDF();

    doc.setFont("Helvetica", "bold");
    doc.setFontSize(20);
    doc.setTextColor(139, 92, 246);
    doc.text("Vedic Academy Student Directory", 14, 20);

    doc.setFontSize(10);
    doc.setFont("Helvetica", "normal");
    doc.setTextColor(100, 116, 139);
    doc.text(`Generated on: ${new Date().toLocaleDateString()} | Class: Class 10A`, 14, 26);

    const tableRows = studentsList.map(s => [
        s.rollNumber,
        s.name,
        s.email,
        s.parentPhone,
        `${s.optionalSubject1}, ${s.optionalSubject2}`
    ]);

    doc.autoTable({
        startY: 32,
        head: [['Roll No', 'Full Name', 'Email', 'Parent Phone', 'Optional Subjects']],
        body: tableRows,
        theme: 'striped',
        headStyles: { fillColor: [139, 92, 246] },
        styles: { fontSize: 8, font: "Helvetica" }
    });

    doc.save("Student_Directory_Registry.pdf");
    showToast("Downloaded PDF directory report!", "success", "📥");
}

// ==========================================================================
// ATTENDANCE ROLL SHEET & TICKS GATEWAY
// ==========================================================================
async function loadAttendanceSheet() {
    const classVal = document.getElementById('attendanceClass').value;
    const sectVal = document.getElementById('attendanceSection').value;
    const tbody = document.getElementById('attendanceSheetBody');
    tbody.innerHTML = `<tr><td colspan="3" class="skeleton" style="height:120px; border-radius:12px;"></td></tr>`;

    try {
        const res = await fetch(`/api/attendance/class-sheet?classGrade=${classVal}&section=${sectVal}`);
        const data = await res.json();
        
        tbody.innerHTML = '';
        if (data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="3" style="text-align:center;">No students enrolled in this section.</td></tr>`;
            return;
        }

        data.forEach(row => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${row.rollNumber}</strong></td>
                <td>${row.name}</td>
                <td style="display:flex; justify-content:center;">
                    <div class="attendance-grid">
                        <button class="attendance-tick-btn present-box ${row.status === 'PRESENT' ? 'active' : ''}" 
                                onclick="tickAttendanceBox(this, ${row.studentId}, 'PRESENT')" title="Mark Present">✓</button>
                        <button class="attendance-tick-btn absent-box ${row.status === 'ABSENT' ? 'active' : ''}" 
                                onclick="tickAttendanceBox(this, ${row.studentId}, 'ABSENT')" title="Mark Absent">✗</button>
                        <button class="attendance-tick-btn half-box ${row.status === 'HALF' ? 'active' : ''}" 
                                onclick="tickAttendanceBox(this, ${row.studentId}, 'HALF')" title="Mark Half-day">½</button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
        });

        calculateRollCallStats();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="3" style="text-align:center; color:var(--accent-red);">Connection error loading roll sheet.</td></tr>`;
    }
}

async function tickAttendanceBox(btn, studentId, status) {
    const grid = btn.parentElement;
    const btns = grid.querySelectorAll('.attendance-tick-btn');
    const bypassCheck = document.getElementById('bypassTimeCheck').checked;

    // Visual state before API feedback for high-speed responsiveness
    const wasActive = btn.classList.contains('active');
    
    // Reset all ticks in this student's row
    btns.forEach(b => b.classList.remove('active'));

    if (!wasActive) {
        btn.classList.add('active');
    }

    try {
        const res = await fetch('/api/attendance/mark', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                studentId: studentId,
                status: wasActive ? 'PRESENT' : status, // toggles back to present if clicked twice
                bypassTimeCheck: bypassCheck
            })
        });

        const data = await res.json();
        
        if (res.ok) {
            if (status === 'ABSENT' && !wasActive) {
                showToast(`Student is marked ABSENT. Parental SMS warning dispatched immediately.`, "warning", "📲");
            } else {
                showToast(`Attendance marked successfully.`, "success");
            }
            
            // Reload logs and metrics
            loadSmsLogs();
            calculateRollCallStats();
        } else {
            // Revert state on business schedule violations (outside 9am-5pm Mon-Sat)
            showToast(data.message || "Attendance window closed!", "error", "🕒");
            btns.forEach(b => b.classList.remove('active'));
            // Re-fetch original values
            loadAttendanceSheet();
        }
    } catch (err) {
        showToast("Connection failed.", "error", "📡");
        loadAttendanceSheet();
    }
}

// Roll call totals calculator
function calculateRollCallStats() {
    const tbody = document.getElementById('attendanceSheetBody');
    const rows = tbody.querySelectorAll('tr');
    
    let present = 0;
    let absent = 0;
    let half = 0;
    let total = 0;

    rows.forEach(tr => {
        const activeBtn = tr.querySelector('.attendance-tick-btn.active');
        if (activeBtn) {
            total++;
            if (activeBtn.classList.contains('present-box')) present++;
            if (activeBtn.classList.contains('absent-box')) absent++;
            if (activeBtn.classList.contains('half-box')) half++;
        }
    });

    document.getElementById('sheetStatPresent').textContent = present;
    document.getElementById('sheetStatAbsent').textContent = absent;
    document.getElementById('sheetStatHalf').textContent = half;

    let percentage = 0;
    if (total > 0) {
        const score = present + (half * 0.5);
        percentage = Math.round((score / total) * 100);
    }
    document.getElementById('sheetStatPercentage').textContent = percentage + "%";
}

function submitCompleteAttendance() {
    showToast("Roll call attendance locked and synchronized successfully!", "success", "💾");
}

async function loadSmsLogs() {
    const tbody = document.getElementById('smsLogsTableBody');
    try {
        const res = await fetch('/api/attendance/logs');
        if (!res.ok) return;
        const logs = await res.json();

        tbody.innerHTML = '';
        if (logs.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" style="text-align: center;">No notifications triggered yet today.</td></tr>`;
            return;
        }

        logs.forEach(log => {
            const tr = document.createElement('tr');
            // Format timestamp cleanly
            const time = new Date(log.timestamp).toLocaleTimeString();
            tr.innerHTML = `
                <td><strong>${time}</strong></td>
                <td>${log.studentName} (${log.classSection})</td>
                <td>${log.parentPhone}</td>
                <td style="font-family: monospace; font-size:0.8rem; color:var(--accent-yellow);">${log.message}</td>
                <td><span class="badge badge-paid" style="background:rgba(16,185,129,0.15); color:var(--accent-green); border-radius:50px;">${log.status}</span></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        console.error(err);
    }
}

// ==========================================================================
// DAILY HOMEWORKS BOARD (7 Subjects)
// ==========================================================================
async function loadHomework() {
    const classVal = document.getElementById('hwClass').value;
    const sectVal = document.getElementById('hwSection').value;
    const dateInput = document.getElementById('hwDate');
    
    // Set default date value to today if empty
    if (!dateInput.value) {
        const todayStr = new Date().toISOString().split('T')[0];
        dateInput.value = todayStr;
    }

    const container = document.getElementById('homeworkCardsContainer');
    container.innerHTML = `<div class="skeleton" style="grid-column:1/-1; height:200px; border-radius:16px;"></div>`;

    try {
        const res = await fetch(`/api/homework?classGrade=${classVal}&section=${sectVal}&date=${dateInput.value}`);
        const data = await res.json();
        
        container.innerHTML = '';

        if (!data || data.message || Object.keys(data).length === 0) {
            container.innerHTML = `<div style="grid-column:1/-1; text-align:center; padding:3rem; color:var(--text-sub);">📝 No daily homework details registered for this date.</div>`;
            return;
        }

        // Render 7 subject cards
        const subjects = [
            { name: "Tamil", icon: "📕", desc: data.tamil || "No homework assigned.", opt: false },
            { name: "English", icon: "📘", desc: data.english || "No homework assigned.", opt: false },
            { name: "Maths", icon: "📐", desc: data.maths || "No homework assigned.", opt: false },
            { name: "Science", icon: "🔬", desc: data.science || "No homework assigned.", opt: false },
            { name: "Social Science", icon: "🌍", desc: data.socialScience || "No homework assigned.", opt: false },
            { name: "Optional Subject 1", icon: "💻", desc: data.optional1 || "No homework assigned.", opt: true },
            { name: "Optional Subject 2", icon: "🕉️", desc: data.optional2 || "No homework assigned.", opt: true }
        ];

        subjects.forEach(sub => {
            const card = document.createElement('div');
            card.className = "glass-card hw-card-subject";
            card.innerHTML = `
                <div class="hw-subject-title">
                    <span>${sub.icon} ${sub.name}</span>
                    ${sub.opt ? '<span class="opt-tag">Elective</span>' : ''}
                </div>
                <p class="hw-desc">${sub.desc}</p>
            `;
            container.appendChild(card);
        });

    } catch (err) {
        container.innerHTML = `<div style="grid-column:1/-1; text-align:center; color:var(--accent-red);">Failed to connect to homework service.</div>`;
    }
}

function openHomeworkModal() {
    document.getElementById('homeworkForm').reset();
    
    // Pre-fill class/section
    document.getElementById('hwFormClass').value = document.getElementById('hwClass').value;
    document.getElementById('hwFormSection').value = document.getElementById('hwSection').value;
    document.getElementById('hwFormDate').value = document.getElementById('hwDate').value;

    document.getElementById('homeworkModal').classList.add('active');
}

document.getElementById('homeworkForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const payload = {
        classGrade: document.getElementById('hwFormClass').value,
        section: document.getElementById('hwFormSection').value,
        date: document.getElementById('hwFormDate').value,
        tamil: document.getElementById('hwTamil').value,
        english: document.getElementById('hwEnglish').value,
        maths: document.getElementById('hwMaths').value,
        science: document.getElementById('hwScience').value,
        socialScience: document.getElementById('hwSocial').value,
        optional1: document.getElementById('hwOpt1').value,
        optional2: document.getElementById('hwOpt2').value
    };

    try {
        const res = await fetch('/api/homework', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showToast("Daily homework assignments published!", "success");
            closeHomeworkModal();
            loadHomework();
        } else {
            showToast("Failed to post daily homework.", "error", "❌");
        }
    } catch (err) {
        showToast("Connection failed.", "error", "📡");
    }
});

function closeHomeworkModal() {
    document.getElementById('homeworkModal').classList.remove('active');
}

// ==========================================================================
// CLASS TIMETABLES GRID (7 Periods, 5 Days)
// ==========================================================================
async function loadClassTimetable() {
    const classVal = document.getElementById('timetableClass').value;
    const sectVal = document.getElementById('timetableSection').value;
    const grid = document.getElementById('classTimetableGrid');
    
    grid.innerHTML = `<div class="skeleton" style="height:200px; border-radius:12px;"></div>`;

    try {
        const res = await fetch(`/api/timetable/class?classGrade=${classVal}&section=${sectVal}`);
        const data = await res.json();
        
        grid.innerHTML = '';
        
        // Define standard weekdays structure
        const days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];
        
        // Map elements for easy rows mapping
        const timetableMap = {};
        data.forEach(row => {
            timetableMap[row.dayOfWeek] = row;
        });

        // Header periods label row
        const headerRow = document.createElement('div');
        headerRow.className = "timetable-row";
        headerRow.style.borderBottom = "1px solid var(--border-glass)";
        headerRow.style.paddingBottom = "0.5rem";
        headerRow.style.marginBottom = "0.5rem";
        headerRow.innerHTML = `
            <div style="font-weight:800; font-size:0.85rem; color:var(--text-sub);">WEEKDAY</div>
            <div style="text-align:center; font-weight:800; font-size:0.85rem; color:var(--text-sub);">PD 1<br><span style="font-size:0.65rem;">9:30-10:20</span></div>
            <div style="text-align:center; font-weight:800; font-size:0.85rem; color:var(--text-sub);">PD 2<br><span style="font-size:0.65rem;">10:20-11:10</span></div>
            <div style="text-align:center; font-weight:800; font-size:0.85rem; color:var(--text-sub);">PD 3<br><span style="font-size:0.65rem;">11:10-12:00</span></div>
            <div style="text-align:center; font-weight:800; font-size:0.85rem; color:var(--text-sub);">PD 4<br><span style="font-size:0.65rem;">12:00-12:50</span></div>
            <div style="text-align:center; font-weight:800; font-size:0.85rem; color:var(--text-sub); color:var(--accent-yellow);">RECESS<br><span style="font-size:0.65rem;">12:50-1:40</span></div>
            <div style="text-align:center; font-weight:800; font-size:0.85rem; color:var(--text-sub);">PD 5<br><span style="font-size:0.65rem;">1:40-2:30</span></div>
            <div style="text-align:center; font-weight:800; font-size:0.85rem; color:var(--text-sub);">PD 6<br><span style="font-size:0.65rem;">2:30-3:20</span></div>
            <div style="text-align:center; font-weight:800; font-size:0.85rem; color:var(--text-sub);">PD 7<br><span style="font-size:0.65rem;">3:20-4:10</span></div>
        `;
        grid.appendChild(headerRow);

        days.forEach(day => {
            const trRow = document.createElement('div');
            trRow.className = "timetable-row";
            
            const slot = timetableMap[day] || {};

            trRow.innerHTML = `
                <div class="day-label">${day}</div>
                <div class="period-card">${slot.period1 || 'Free'}</div>
                <div class="period-card">${slot.period2 || 'Free'}</div>
                <div class="period-card">${slot.period3 || 'Free'}</div>
                <div class="period-card">${slot.period4 || 'Free'}</div>
                <div class="period-card lunch" style="color:var(--accent-yellow);">🍱 Lunch</div>
                <div class="period-card">${slot.period5 || 'Free'}</div>
                <div class="period-card">${slot.period6 || 'Free'}</div>
                <div class="period-card">${slot.period7 || 'Free'}</div>
            `;
            grid.appendChild(trRow);
        });

    } catch (err) {
        grid.innerHTML = `<div style="text-align:center; color:var(--accent-red);">Failed to retrieve timetable.</div>`;
    }
}

function openTimetableModal() {
    document.getElementById('timetableForm').reset();
    document.getElementById('timetableModal').classList.add('active');
}

document.getElementById('timetableForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const payload = {
        classGrade: document.getElementById('timetableClass').value,
        section: document.getElementById('timetableSection').value,
        dayOfWeek: document.getElementById('ttDay').value,
        period1: document.getElementById('ttP1').value || 'Free',
        period2: document.getElementById('ttP2').value || 'Free',
        period3: document.getElementById('ttP3').value || 'Free',
        period4: document.getElementById('ttP4').value || 'Free',
        period5: document.getElementById('ttP5').value || 'Free',
        period6: document.getElementById('ttP6').value || 'Free',
        period7: document.getElementById('ttP7').value || 'Free'
    };

    try {
        const res = await fetch('/api/timetable/class', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showToast("Class timetable updated!", "success");
            closeTimetableModal();
            loadClassTimetable();
        } else {
            showToast("Failed to save timetable slot.", "error", "❌");
        }
    } catch (err) {
        showToast("Connection failed.", "error", "📡");
    }
});

function closeTimetableModal() {
    document.getElementById('timetableModal').classList.remove('active');
}

// ==========================================================================
// EXAM SCHEDULER & LISTS
// ==========================================================================
async function loadExams() {
    const classVal = document.getElementById('examsClass').value;
    const tbody = document.getElementById('examsTableBody');
    
    tbody.innerHTML = `<tr><td colspan="6" class="skeleton" style="height:120px; border-radius:12px;"></td></tr>`;

    try {
        const res = await fetch(`/api/timetable/exams?classGrade=${classVal}`);
        const data = await res.json();
        
        tbody.innerHTML = '';

        if (data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;">No scheduled exams.</td></tr>`;
            return;
        }

        data.forEach(ex => {
            const tr = document.createElement('tr');
            
            let actionBtn = ``;
            if (currentUser.role === 'ADMIN') {
                actionBtn = `<button class="btn btn-secondary" style="padding:0.3rem 0.6rem; font-size:0.75rem; color:var(--accent-red);" onclick="deleteExam(${ex.id})">🗑️ Delete</button>`;
            } else {
                actionBtn = `<span style="font-size:0.8rem; color:var(--text-sub);">View Only</span>`;
            }

            tr.innerHTML = `
                <td><strong>${ex.subject}</strong></td>
                <td>${ex.examName}</td>
                <td>${ex.examDate}</td>
                <td>${ex.examTime || 'TBD'}</td>
                <td>${ex.maxMarks} Marks</td>
                <td class="admin-actions-col">${actionBtn}</td>
            `;
            tbody.appendChild(tr);
        });

        // Hide action column header for students/teachers
        const actionHeaders = document.querySelectorAll('.admin-actions-col');
        actionHeaders.forEach(el => {
            el.style.display = (currentUser.role === 'ADMIN') ? '' : 'none';
        });

    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; color:var(--accent-red);">Failed to connect.</td></tr>`;
    }
}

function openExamModal() {
    document.getElementById('examForm').reset();
    document.getElementById('eClass').value = document.getElementById('examsClass').value;
    document.getElementById('examModal').classList.add('active');
}

document.getElementById('examForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const payload = {
        examName: document.getElementById('eName').value,
        classGrade: document.getElementById('eClass').value,
        subject: document.getElementById('eSubject').value,
        examDate: document.getElementById('eDate').value,
        examTime: document.getElementById('eTime').value || '09:30 AM - 12:30 PM'
    };

    try {
        const res = await fetch('/api/timetable/exams', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showToast("Exam scheduled successfully!", "success");
            closeExamModal();
            loadExams();
        } else {
            showToast("Failed to schedule exam.", "error", "❌");
        }
    } catch (err) {
        showToast("Connection failed.", "error", "📡");
    }
});

function closeExamModal() {
    document.getElementById('examModal').classList.remove('active');
}

async function deleteExam(id) {
    if (!confirm("Delete this exam schedule slot?")) return;
    try {
        const res = await fetch(`/api/timetable/exams/${id}`, { method: 'DELETE' });
        if (res.ok) {
            showToast("Exam schedule slot deleted.", "success");
            loadExams();
        }
    } catch (err) {
        showToast("Deletion failed.", "error", "❌");
    }
}

// ==========================================================================
// FEE DETAILS SHEET
// ==========================================================================
async function loadFees() {
    const classVal = document.getElementById('feesClass').value;
    const sectVal = document.getElementById('feesSection').value;
    const tbody = document.getElementById('feesTableBody');
    
    tbody.innerHTML = `<tr><td colspan="7" class="skeleton" style="height:120px; border-radius:12px;"></td></tr>`;

    try {
        const res = await fetch(`/api/students?classGrade=${classVal}&section=${sectVal}`);
        const students = await res.json();
        
        tbody.innerHTML = '';
        if (students.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;">No student fees loaded.</td></tr>`;
            return;
        }

        students.forEach(s => {
            const tr = document.createElement('tr');
            
            // Fee balances mock mapping linked to initialization
            let total = 35000;
            let paid = 0;
            let status = "UNPAID";
            let date = "2026-06-30";

            if (s.id % 5 === 1) { paid = 25000; status = "PARTIAL"; }
            if (s.id % 5 === 2 || s.id % 5 === 3) { paid = 35000; status = "PAID"; }
            if (s.id % 5 === 4) { paid = 10000; status = "PARTIAL"; }

            let statusBadge = ``;
            if (status === 'PAID') statusBadge = `<span class="badge badge-paid">PAID</span>`;
            if (status === 'PARTIAL') statusBadge = `<span class="badge badge-partial">PARTIAL</span>`;
            if (status === 'UNPAID') statusBadge = `<span class="badge badge-unpaid">UNPAID</span>`;

            tr.innerHTML = `
                <td><strong>${s.rollNumber}</strong></td>
                <td>${s.name}</td>
                <td>Term Fees 2026</td>
                <td>₹${total.toLocaleString()}</td>
                <td>₹${paid.toLocaleString()}</td>
                <td>${statusBadge}</td>
                <td>${date}</td>
            `;
            tbody.appendChild(tr);
        });

    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:var(--accent-red);">Failed to retrieve fees.</td></tr>`;
    }
}

// ==========================================================================
// STUDENT PROFILE PORTAL LOGIC & BACKEND SYNC
// ==========================================================================
async function fetchStudentOwnData() {
    const sId = currentUser.studentId;
    if (!sId) return;

    try {
        const res = await fetch(`/api/students/${sId}`);
        const s = await res.json();
        
        // Render custom profile view for student in the Settings/Profile Panel
        document.getElementById('profileAccountId').textContent = currentUser.id;
        document.getElementById('profileUsername').textContent = currentUser.username;
        document.getElementById('profileRole').textContent = "Student";
        document.getElementById('profileStudentId').textContent = s.id;

        // Custom injection of Student Space details in place of Settings standard page
        injectStudentPortalSpace(s);
    } catch (err) {
        console.error(err);
    }
}

async function injectStudentPortalSpace(s) {
    const parentContainer = document.getElementById('tab-settings');
    
    // Fetch marks and fees for the logged in student
    const gRes = await fetch(`/api/students/${s.id}/grades`);
    const grades = await gRes.json();

    const fRes = await fetch(`/api/students/${s.id}/fees`);
    const fees = await fRes.json();
    const feeInfo = fees[0] || { totalAmount: 35000, paidAmount: 0, status: "UNPAID", dueDate: "2026-06-30" };

    let scoreHTML = ``;
    if (grades.length > 0) {
        const g = grades[0]; // quarterly marks
        scoreHTML = `
            <div class="glass-card" style="margin-top:1.5rem;">
                <h4 style="margin-bottom:1rem; border-bottom:1px solid var(--border-glass); padding-bottom:0.5rem;">📊 My Scores & Rankings (${g.examName})</h4>
                <div style="display:grid; grid-template-columns: repeat(2, 1fr); gap:1rem; margin-bottom:1.5rem;">
                    <div style="background:rgba(15,23,42,0.2); padding:1rem; border-radius:10px; border:1px solid var(--border-glass); text-align:center;">
                        <span style="font-size:0.75rem; color:var(--text-sub); text-transform:uppercase;">Obtained Total</span>
                        <h3 style="font-size:1.8rem; color:var(--primary-light); margin-top:0.25rem;">${g.totalMarks} <span style="font-size:0.9rem; color:var(--text-sub);">/ 700</span></h3>
                    </div>
                    <div style="background:rgba(15,23,42,0.2); padding:1rem; border-radius:10px; border:1px solid var(--border-glass); text-align:center;">
                        <span style="font-size:0.75rem; color:var(--text-sub); text-transform:uppercase;">Class Rank</span>
                        <h3 style="font-size:1.8rem; color:var(--accent-yellow); margin-top:0.25rem;">Rank ${g.classRank}</h3>
                    </div>
                </div>
                <div class="table-responsive">
                    <table class="table-custom">
                        <thead>
                            <tr><th>Subject Name</th><th>Max Marks</th><th>Obtained Marks</th></tr>
                        </thead>
                        <tbody>
                            <tr><td>English</td><td>100</td><td>${g.english}</td></tr>
                            <tr><td>Tamil</td><td>100</td><td>${g.tamil}</td></tr>
                            <tr><td>Maths</td><td>100</td><td>${g.maths}</td></tr>
                            <tr><td>Science</td><td>100</td><td>${g.science}</td></tr>
                            <tr><td>Social Science</td><td>100</td><td>${g.socialScience}</td></tr>
                            <tr><td>${g.optional1Name}</td><td>100</td><td>${g.optional1Marks}</td></tr>
                            <tr><td>${g.optional2Name}</td><td>100</td><td>${g.optional2Marks}</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        `;
    } else {
        scoreHTML = `<div class="glass-card" style="margin-top:1.5rem; text-align:center; padding:2rem; color:var(--text-sub);">📊 Exam results not published yet.</div>`;
    }

    // Render Student Portal Space
    parentContainer.innerHTML = `
        <div style="display:grid; grid-template-columns: 1fr 1fr; gap:1.5rem; align-items: start;">
            <div>
                <!-- Bio Bio Details Card -->
                <div class="glass-card" style="margin-bottom:1.5rem;">
                    <div style="text-align:center; margin-bottom:1.5rem;">
                        <div class="user-avatar" style="width:70px; height:70px; font-size:2rem; margin:0 auto 0.75rem auto;">${s.name.charAt(0)}</div>
                        <h3 style="font-size:1.4rem;">${s.name}</h3>
                        <p style="font-size:0.85rem; color:var(--text-sub);">${s.rollNumber} | ${s.classGrade} - ${s.section}</p>
                    </div>
                    
                    <h5 style="border-bottom:1px solid var(--border-glass); padding-bottom:0.25rem; font-size:0.9rem; text-transform:uppercase; margin-bottom:0.75rem;">Bio Information</h5>
                    <div style="display:flex; flex-direction:column; gap:0.5rem; font-size:0.85rem;">
                        <div style="display:flex;"><span style="width:110px; color:var(--text-sub);">Email Address:</span><span>${s.email}</span></div>
                        <div style="display:flex;"><span style="width:110px; color:var(--text-sub);">Date of Birth:</span><span>${s.dob}</span></div>
                        <div style="display:flex;"><span style="width:110px; color:var(--text-sub);">Gender Status:</span><span>${s.gender}</span></div>
                        <div style="display:flex;"><span style="width:110px; color:var(--text-sub);">Parent Name:</span><span>${s.parentName}</span></div>
                        <div style="display:flex;"><span style="width:110px; color:var(--text-sub);">Parent Phone:</span><span>${s.parentPhone}</span></div>
                        <div style="display:flex;"><span style="width:110px; color:var(--text-sub);">Address:</span><span>${s.address}</span></div>
                    </div>
                </div>

                <!-- Fees Due Card -->
                <div class="glass-card">
                    <h4 style="margin-bottom:1rem; border-bottom:1px solid var(--border-glass); padding-bottom:0.5rem;">💰 Fee Transactions</h4>
                    <div style="display:flex; justify-content:space-between; margin-bottom:0.5rem; font-size:0.9rem;"><span style="color:var(--text-sub);">Total Term Fees:</span><strong>₹${feeInfo.totalAmount.toLocaleString()}</strong></div>
                    <div style="display:flex; justify-content:space-between; margin-bottom:0.5rem; font-size:0.9rem;"><span style="color:var(--text-sub);">Paid Amount:</span><strong style="color:var(--accent-green);">₹${feeInfo.paidAmount.toLocaleString()}</strong></div>
                    <div style="display:flex; justify-content:space-between; margin-bottom:0.5rem; font-size:0.9rem;"><span style="color:var(--text-sub);">Due Balance:</span><strong style="color:var(--accent-red);">₹${(feeInfo.totalAmount - feeInfo.paidAmount).toLocaleString()}</strong></div>
                    <div style="display:flex; justify-content:space-between; margin-bottom:1rem; font-size:0.9rem;"><span style="color:var(--text-sub);">Status Indicator:</span><span class="badge ${feeInfo.status === 'PAID' ? 'badge-paid' : 'badge-unpaid'}">${feeInfo.status}</span></div>
                    <div style="font-size:0.75rem; color:var(--accent-red); background:rgba(239,68,68,0.05); padding:0.50rem; border-radius:6px; border:1px solid rgba(239,68,68,0.15); text-align:center;">Due Date Deadline: ${feeInfo.dueDate}</div>
                </div>
            </div>
            
            <div>
                <!-- Score Card -->
                ${scoreHTML}
            </div>
        </div>
    `;
}

function loadSettingsProfile() {
    if (currentUser.role === 'STUDENT') return;
    document.getElementById('profileAccountId').textContent = currentUser.id;
    document.getElementById('profileUsername').textContent = currentUser.username;
    document.getElementById('profileRole').textContent = currentUser.role;
    document.getElementById('profileStudentRefRow').style.display = 'none';
    
    // Load dynamic Twilio configuration
    loadSmsGatewaySettings();

    // Load admin class & assignment management panels
    const isAdmin = (currentUser.role === 'ADMIN');
    const adminCard = document.getElementById('adminClassMgmtCard');
    if (adminCard) {
        adminCard.style.display = isAdmin ? 'block' : 'none';
        if (isAdmin) {
            loadClassesList();
            loadTeachersList();
            loadAssignmentsList();
        }
    }
}

// ==========================================================================
// DYNAMIC TWILIO GATEWAY MANAGEMENT & LIVE DIAGNOSTICS
// ==========================================================================

// Toggle Twilio Token Field Visibility
function toggleTwTokenVisibility() {
    const input = document.getElementById('twAuthToken');
    const btn = document.getElementById('toggleTwTokenBtn');
    if (input.type === 'password') {
        input.type = 'text';
        btn.textContent = '🙈';
    } else {
        input.type = 'password';
        btn.textContent = '👁️';
    }
}

// Load SMS credentials on profile settings tab loading
async function loadSmsGatewaySettings() {
    const isAdmin = (currentUser.role === 'ADMIN');
    
    // Setup Admin-only restrictions on the inputs/buttons
    document.getElementById('twSid').disabled = !isAdmin;
    document.getElementById('twAuthToken').disabled = !isAdmin;
    document.getElementById('twPhone').disabled = !isAdmin;
    document.getElementById('saveSmsConfigBtn').style.display = isAdmin ? 'block' : 'none';

    try {
        const res = await fetch('/api/attendance/settings/sms');
        if (!res.ok) return;
        const data = await res.json();
        
        document.getElementById('twSid').value = data.twilioSid || '';
        document.getElementById('twAuthToken').value = data.twilioAuthToken || '';
        document.getElementById('twPhone').value = data.twilioPhoneNumber || '';
    } catch (err) {
        console.error("Failed to load SMS settings:", err);
    }
}

// Form Submission Event Listener
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('smsGatewayForm');
    if (form) {
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const btn = document.getElementById('saveSmsConfigBtn');
            const originalText = btn.textContent;
            btn.disabled = true;
            btn.textContent = "💾 Saving Configurations...";

            const payload = {
                twilioSid: document.getElementById('twSid').value.trim(),
                twilioAuthToken: document.getElementById('twAuthToken').value.trim(),
                twilioPhoneNumber: document.getElementById('twPhone').value.trim()
            };

            try {
                const res = await fetch('/api/attendance/settings/sms', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const data = await res.json();
                
                if (res.ok) {
                    showToast(data.message || "SMS Gateway credentials updated successfully!", "success", "📲");
                    // Reload to mask token
                    loadSmsGatewaySettings();
                } else {
                    showToast(data.message || "Failed to update configurations.", "error", "❌");
                }
            } catch (err) {
                showToast("Network dispatch failed.", "error", "📡");
            } finally {
                btn.disabled = false;
                btn.textContent = originalText;
            }
        });
    }
});

// Live Testing Action
async function sendTestSmsFromDashboard() {
    const target = document.getElementById('smsTestTargetPhone').value.trim();
    if (!target) {
        showToast("Please enter a target mobile phone number!", "warning", "⚠️");
        return;
    }

    const diagBox = document.getElementById('smsTestDiagnostics');
    const spinner = document.getElementById('smsTestDiagnosticsSpinner');
    const logBox = document.getElementById('smsTestDiagnosticsLog');

    diagBox.style.display = 'block';
    spinner.style.display = 'flex';
    logBox.innerHTML = `<span style="color:var(--text-sub);">[System] Initializing test packet...</span>`;
    logBox.style.borderColor = 'var(--border-glass)';

    const payload = {
        twilioSid: document.getElementById('twSid').value.trim(),
        twilioAuthToken: document.getElementById('twAuthToken').value.trim(),
        twilioPhoneNumber: document.getElementById('twPhone').value.trim(),
        targetPhone: target
    };

    try {
        const res = await fetch('/api/attendance/settings/sms/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        
        spinner.style.display = 'none';
        
        if (res.ok) {
            logBox.innerHTML = `
                <span style="color:var(--accent-green); font-weight:bold;">[SUCCESS] Test SMS Dispatched!</span><br>
                <span style="color:var(--text-sub);">${data.message}</span>
            `;
            logBox.style.borderColor = 'rgba(16, 185, 129, 0.4)';
            showToast("Test notification sent successfully!", "success", "⚡");
        } else {
            logBox.innerHTML = `
                <span style="color:var(--accent-red); font-weight:bold;">[FAILURE] Twilio Exception Encountered</span><br>
                <span style="color:#f87171;">${data.message}</span>
            `;
            logBox.style.borderColor = 'rgba(239, 68, 68, 0.4)';
            showToast("Test notification failed to dispatch.", "error", "❌");
        }
    } catch (err) {
        spinner.style.display = 'none';
        logBox.innerHTML = `<span style="color:var(--accent-red);">[ERROR] Fetch connection dropped: ${err.message}</span>`;
        logBox.style.borderColor = 'rgba(239, 68, 68, 0.4)';
        showToast("Connection to service lost.", "error", "📡");
    }
}

// ==========================================================================
// ADMIN CLASS & TEACHER ASSIGNMENTS BINDERS & STATE
// ==========================================================================
let cachedClassesList = [];

async function loadDynamicClassDropdowns() {
    try {
        const res = await fetch('/api/classes');
        if (!res.ok) return;
        cachedClassesList = await res.json();
        
        if (cachedClassesList.length > 0) {
            activeDashboardClass = cachedClassesList[0].className;
            activeDashboardSection = cachedClassesList[0].section;
        }
        
        // 1. Get unique class names
        const uniqueClassNames = [...new Set(cachedClassesList.map(c => c.className))];
        
        // Dropdown IDs that represent class selectors
        const classSelectIds = [
            'filterClass', 'sClass', 'attendanceClass', 'hwClass', 
            'hwFormClass', 'timetableClass', 'eClass', 'feesClass', 'assignClassSelect'
        ];
        
        classSelectIds.forEach(id => {
            const selectEl = document.getElementById(id);
            if (!selectEl) return;
            
            // Save current value if any
            const prevVal = selectEl.value;
            selectEl.innerHTML = '';
            
            uniqueClassNames.forEach(className => {
                const opt = document.createElement('option');
                opt.value = className;
                opt.textContent = className;
                selectEl.appendChild(opt);
            });
            
            // Try to restore previous value or select the first one
            if (uniqueClassNames.includes(prevVal)) {
                selectEl.value = prevVal;
            } else if (selectEl.options.length > 0) {
                selectEl.options[0].selected = true;
            }
            
            // Trigger section updates initially
            updateSectionDropdownForClass(id);
        });

        // Add event listeners to class dropdowns to update section dropdowns on change
        bindClassChangeListeners();

        // Automatically paint the interface on boot
        renderDashboardClassCards();
        loadHubData();

    } catch (err) {
        console.error("Failed to load dynamic classes:", err);
    }
}

function updateSectionDropdownForClass(classSelectId) {
    const classSelect = document.getElementById(classSelectId);
    if (!classSelect) return;
    
    const selectedClass = classSelect.value;
    
    // Map class select ID to section select ID
    const mapping = {
        'filterClass': 'filterSection',
        'sClass': 'sSection',
        'attendanceClass': 'attendanceSection',
        'hwClass': 'hwSection',
        'hwFormClass': 'hwFormSection',
        'timetableClass': 'timetableSection',
        'feesClass': 'feesSection',
        'assignClassSelect': 'assignSectionSelect'
    };
    
    const sectionSelectId = mapping[classSelectId];
    if (!sectionSelectId) return;
    
    const sectionSelect = document.getElementById(sectionSelectId);
    if (!sectionSelect) return;
    
    const prevVal = sectionSelect.value;
    sectionSelect.innerHTML = '';
    
    // Filter sections for the selected class grade
    const matchingSections = cachedClassesList
        .filter(c => c.className === selectedClass)
        .map(c => c.section);
        
    // Unique sections for safety
    const uniqueSections = [...new Set(matchingSections)];
    
    uniqueSections.forEach(sect => {
        const opt = document.createElement('option');
        opt.value = sect;
        opt.textContent = `Section ${sect}`;
        sectionSelect.appendChild(opt);
    });
    
    // Restore or select default
    if (uniqueSections.includes(prevVal)) {
        sectionSelect.value = prevVal;
    } else if (sectionSelect.options.length > 0) {
        sectionSelect.options[0].selected = true;
    }
}

function bindClassChangeListeners() {
    const mappings = {
        'filterClass': 'filterSection',
        'sClass': 'sSection',
        'attendanceClass': 'attendanceSection',
        'hwClass': 'hwSection',
        'hwFormClass': 'hwFormSection',
        'timetableClass': 'timetableSection',
        'feesClass': 'feesSection',
        'assignClassSelect': 'assignSectionSelect'
    };
    
    Object.keys(mappings).forEach(classSelectId => {
        const selectEl = document.getElementById(classSelectId);
        if (!selectEl) return;
        
        // Remove existing listener if already bound by checking custom attribute
        if (selectEl.dataset.listenerBound) return;
        
        selectEl.addEventListener('change', () => {
            updateSectionDropdownForClass(classSelectId);
            
            // Automatically trigger tab reloads
            if (classSelectId === 'filterClass') loadStudents();
            if (classSelectId === 'attendanceClass') loadAttendanceSheet();
            if (classSelectId === 'hwClass') loadHomework();
            if (classSelectId === 'timetableClass') loadClassTimetable();
            if (classSelectId === 'feesClass') loadFees();
        });
        
        selectEl.dataset.listenerBound = 'true';
    });
}

function switchSettingsSubTab(subTab) {
    const classPanel = document.getElementById('settingsClassPanel');
    const assignPanel = document.getElementById('settingsAssignmentPanel');
    const btnClass = document.getElementById('btnShowClassMgmt');
    const btnAssign = document.getElementById('btnShowTeacherAssign');

    if (subTab === 'classes') {
        classPanel.style.display = 'block';
        assignPanel.style.display = 'none';
        btnClass.style.background = 'rgba(139, 92, 246, 0.2)';
        btnClass.style.borderColor = 'var(--primary-light)';
        btnAssign.style.background = 'none';
        btnAssign.style.borderColor = 'var(--border-glass)';
    } else {
        classPanel.style.display = 'none';
        assignPanel.style.display = 'block';
        btnAssign.style.background = 'rgba(139, 92, 246, 0.2)';
        btnAssign.style.borderColor = 'var(--primary-light)';
        btnClass.style.background = 'none';
        btnClass.style.borderColor = 'var(--border-glass)';
    }
}

function checkCustomSubject(selectEl) {
    const customInput = document.getElementById('assignSubjectCustom');
    if (selectEl.value === 'CUSTOM') {
        customInput.style.display = 'block';
        customInput.required = true;
    } else {
        customInput.style.display = 'none';
        customInput.required = false;
    }
}

async function loadClassesList() {
    const tbody = document.getElementById('settingsClassesTableBody');
    if (!tbody) return;
    
    tbody.innerHTML = `<tr><td colspan="3" class="skeleton" style="height:40px;"></td></tr>`;
    
    try {
        const res = await fetch('/api/classes');
        const classes = await res.json();
        
        tbody.innerHTML = '';
        if (classes.length === 0) {
            tbody.innerHTML = `<tr><td colspan="3" style="text-align:center;">No classes created yet.</td></tr>`;
            return;
        }
        
        classes.forEach(c => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${c.className}</strong></td>
                <td>Section ${c.section}</td>
                <td style="text-align:right;">
                    <button class="btn btn-secondary" style="padding:0.25rem 0.5rem; font-size:0.7rem; color:var(--accent-red);" onclick="deleteSchoolClass(${c.id})">🗑️ Delete</button>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="3" style="text-align:center; color:var(--accent-red);">Failed to load class list.</td></tr>`;
    }
}

async function loadTeachersList() {
    const selectEl = document.getElementById('assignTeacherSelect');
    if (!selectEl) return;
    
    try {
        const res = await fetch('/api/classes/teachers');
        const teachers = await res.json();
        
        selectEl.innerHTML = '';
        teachers.forEach(t => {
            const opt = document.createElement('option');
            opt.value = t.id;
            opt.textContent = t.username;
            selectEl.appendChild(opt);
        });
    } catch (err) {
        console.error("Failed to load teachers selector:", err);
    }
}

async function loadAssignmentsList() {
    const tbody = document.getElementById('settingsAssignmentsTableBody');
    if (!tbody) return;
    
    tbody.innerHTML = `<tr><td colspan="4" class="skeleton" style="height:40px;"></td></tr>`;
    
    try {
        const res = await fetch('/api/classes/assignments');
        const assignments = await res.json();
        
        tbody.innerHTML = '';
        if (assignments.length === 0) {
            tbody.innerHTML = `<tr><td colspan="4" style="text-align:center;">No teacher assignments yet.</td></tr>`;
            return;
        }
        
        assignments.forEach(a => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${a.classGrade} - ${a.section}</strong></td>
                <td><span class="badge badge-partial" style="background:rgba(6, 182, 212, 0.15); color:var(--accent-cyan); border:1px solid rgba(6, 182, 212, 0.3); font-size:0.75rem;">${a.subject}</span></td>
                <td>${a.teacher.username}</td>
                <td style="text-align:right;">
                    <button class="btn btn-secondary" style="padding:0.25rem 0.5rem; font-size:0.7rem; color:var(--accent-red);" onclick="deleteTeacherAssignment(${a.id})">🗑️ Cancel</button>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:var(--accent-red);">Failed to load teacher assignments.</td></tr>`;
    }
}

async function deleteSchoolClass(id) {
    if (!confirm("Are you sure you want to delete this class? This could affect linked student listings.")) return;
    
    try {
        const res = await fetch(`/api/classes/${id}`, { method: 'DELETE' });
        if (res.ok) {
            showToast("Class deleted successfully", "success");
            await loadDynamicClassDropdowns();
            loadClassesList();
        } else {
            const data = await res.json();
            showToast(data.message || "Failed to delete class", "error");
        }
    } catch (err) {
        showToast("Delete failed", "error");
    }
}

async function deleteTeacherAssignment(id) {
    if (!confirm("Remove this teacher assignment?")) return;
    
    try {
        const res = await fetch(`/api/classes/assignments/${id}`, { method: 'DELETE' });
        if (res.ok) {
            showToast("Assignment cancelled", "success");
            loadAssignmentsList();
        } else {
            const data = await res.json();
            showToast(data.message || "Failed to remove assignment", "error");
        }
    } catch (err) {
        showToast("Delete failed", "error");
    }
}

// Bind form submissions on load
document.addEventListener('DOMContentLoaded', () => {
    // 1. Create Class Form
    const createClassForm = document.getElementById('createClassForm');
    if (createClassForm) {
        createClassForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                className: document.getElementById('newClassName').value.trim(),
                section: document.getElementById('newClassSection').value.trim()
            };
            
            try {
                const res = await fetch('/api/classes', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                
                if (res.ok) {
                    showToast("Class created successfully!", "success");
                    document.getElementById('newClassName').value = '';
                    document.getElementById('newClassSection').value = '';
                    await loadDynamicClassDropdowns();
                    loadClassesList();
                } else {
                    const data = await res.json();
                    showToast(data.message || "Failed to create class.", "error");
                }
            } catch (err) {
                showToast("Connection failed.", "error");
            }
        });
    }

    // 2. Create Assignment Form
    const createAssignmentForm = document.getElementById('createAssignmentForm');
    if (createAssignmentForm) {
        createAssignmentForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const classGrade = document.getElementById('assignClassSelect').value;
            const section = document.getElementById('assignSectionSelect').value;
            const teacherId = parseInt(document.getElementById('assignTeacherSelect').value);
            
            const subjectSelect = document.getElementById('assignSubjectSelect');
            let subject = subjectSelect.value;
            if (subject === 'CUSTOM') {
                subject = document.getElementById('assignSubjectCustom').value.trim();
            }
            
            if (!subject) {
                showToast("Please specify a subject", "warning");
                return;
            }
            
            const payload = {
                classGrade,
                section,
                teacherId,
                subject
            };
            
            try {
                const res = await fetch('/api/classes/assignments', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                
                if (res.ok) {
                    showToast("Teacher assigned successfully!", "success");
                    // Reset custom inputs
                    const customInput = document.getElementById('assignSubjectCustom');
                    customInput.value = '';
                    customInput.style.display = 'none';
                    subjectSelect.value = 'English';
                    
                    loadAssignmentsList();
                } else {
                    const data = await res.json();
                    showToast(data.message || "Failed to create assignment.", "error");
                }
            } catch (err) {
                showToast("Connection failed.", "error");
            }
        });
    }
});

// ==========================================================================
// CLASS DETAILS HUB & STATE COORDINATOR
// ==========================================================================
function renderDashboardClassCards() {
    if (currentUser.role === 'STUDENT') {
        const classCardsEl = document.getElementById('dashboardClassCards');
        if (classCardsEl) classCardsEl.style.display = 'none';
        const sectionSelectorEl = document.getElementById('dashboardSectionSelector');
        if (sectionSelectorEl) sectionSelectorEl.style.display = 'none';
        const classHubEl = document.getElementById('classDetailHub');
        if (classHubEl) classHubEl.style.display = 'none';
        return;
    }

    const container = document.getElementById('dashboardClassCards');
    if (!container) return;

    // Show containers
    container.style.display = 'flex';
    const classHubEl = document.getElementById('classDetailHub');
    if (classHubEl) classHubEl.style.display = 'block';

    container.innerHTML = '';

    if (cachedClassesList.length === 0) {
        container.innerHTML = `<div style="padding:1rem; color:var(--text-sub);">No classes available.</div>`;
        if (classHubEl) classHubEl.style.display = 'none';
        return;
    }

    // Extract unique class names that exist in cachedClassesList
    const availableClasses = [...new Set(cachedClassesList.map(c => c.className))];
    
    // Sort according to ACADEMIC_CLASS_ORDER
    const sortedClasses = ACADEMIC_CLASS_ORDER.filter(cls => availableClasses.includes(cls));
    
    // Append any extra classes that might have been dynamically added but aren't in standard order
    availableClasses.forEach(cls => {
        if (!sortedClasses.includes(cls)) {
            sortedClasses.push(cls);
        }
    });

    sortedClasses.forEach(className => {
        const card = document.createElement('div');
        const isActive = (className === activeDashboardClass);
        card.className = `class-card-item ${isActive ? 'active' : ''}`;
        card.innerHTML = `
            <span>${className}</span>
            <span class="section-lbl">${isActive ? ('Sec ' + activeDashboardSection) : 'Select Class'}</span>
        `;
        card.addEventListener('click', () => {
            selectDashboardClass(className);
        });
        container.appendChild(card);
    });

    // Automatically render the section pills for the active class grade
    renderDashboardSections(activeDashboardClass);
}

function selectDashboardClass(className) {
    activeDashboardClass = className;

    // Find first available section for this class to default to
    const matchingSections = cachedClassesList
        .filter(c => c.className === className)
        .map(c => c.section)
        .sort();
    
    if (matchingSections.length > 0 && !matchingSections.includes(activeDashboardSection)) {
        activeDashboardSection = matchingSections[0];
    }

    // Re-render class cards (which internally calls renderDashboardSections)
    renderDashboardClassCards();

    // Reload the main dashboard charts and statistics
    loadDashboardStats();

    // Reload the Details Hub workspace
    loadHubData();
}

function renderDashboardSections(className) {
    const sectionSelectorEl = document.getElementById('dashboardSectionSelector');
    if (!sectionSelectorEl) return;

    if (currentUser.role === 'STUDENT') {
        sectionSelectorEl.style.display = 'none';
        return;
    }

    const matchingSections = cachedClassesList
        .filter(c => c.className === className)
        .map(c => c.section)
        .sort();

    if (matchingSections.length === 0) {
        sectionSelectorEl.style.display = 'none';
        return;
    }

    sectionSelectorEl.style.display = 'flex';
    sectionSelectorEl.innerHTML = '';

    matchingSections.forEach(sec => {
        const pill = document.createElement('div');
        const isActive = (sec === activeDashboardSection);
        pill.className = `section-pill-item ${isActive ? 'active' : ''}`;
        pill.textContent = sec;
        pill.addEventListener('click', () => {
            activeDashboardSection = sec;
            
            // Re-render to update highlights (both class cards "Sec B" label and section pills active state)
            renderDashboardClassCards();
            
            // Reload stats and workspace below
            loadDashboardStats();
            loadHubData();
        });
        sectionSelectorEl.appendChild(pill);
    });
}

function loadHubData() {
    if (currentUser.role === 'STUDENT') return;

    const titleEl = document.getElementById('hubActiveClassName');
    if (titleEl) {
        titleEl.textContent = `${activeDashboardClass} - Section ${activeDashboardSection} Workspace`;
    }

    // Call individual loaders based on activeHubTab
    if (activeHubTab === 'students') {
        loadHubStudents();
    } else if (activeHubTab === 'attendance') {
        loadHubAttendance();
    } else if (activeHubTab === 'homework') {
        loadHubHomework();
    } else if (activeHubTab === 'leaderboard') {
        loadHubLeaderboard();
    }
}

function switchHubTab(tabName) {
    activeHubTab = tabName;

    // Update active tab button style
    const buttons = document.querySelectorAll('.hub-tab-btn');
    buttons.forEach(btn => {
        btn.classList.remove('active');
    });

    const activeBtn = document.getElementById(`hubTabBtn-${tabName}`);
    if (activeBtn) activeBtn.classList.add('active');

    // Update active hub panel visibility
    const panels = document.querySelectorAll('.hub-panel');
    panels.forEach(p => {
        p.classList.remove('active');
    });

    // Determine target panel
    const mapping = {
        'students': 'hubPanelStudents',
        'attendance': 'hubPanelAttendance',
        'homework': 'hubPanelHomework',
        'leaderboard': 'hubPanelLeaderboard'
    };
    const targetId = mapping[tabName];
    const targetPanel = document.getElementById(targetId);
    if (targetPanel) targetPanel.classList.add('active');

    // Load data
    loadHubData();
}

async function loadHubStudents() {
    const tbody = document.getElementById('hubStudentsTableBody');
    if (!tbody) return;

    tbody.innerHTML = `<tr><td colspan="6" class="skeleton" style="height:80px; border-radius:8px;"></td></tr>`;

    try {
        const response = await fetch(`/api/students?classGrade=${encodeURIComponent(activeDashboardClass)}&section=${encodeURIComponent(activeDashboardSection)}`);
        const students = await response.json();

        tbody.innerHTML = '';
        if (students.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;">No students enrolled in this class.</td></tr>`;
            return;
        }

        students.forEach(student => {
            let feeBadge = `<span class="badge badge-unpaid">UNPAID</span>`;
            if (student.id % 5 === 1) feeBadge = `<span class="badge badge-partial">PARTIAL</span>`;
            if (student.id % 5 === 2 || student.id % 5 === 3) feeBadge = `<span class="badge badge-paid">PAID</span>`;

            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${student.rollNumber}</strong></td>
                <td>${student.name}</td>
                <td>${student.gender}</td>
                <td>${student.email}</td>
                <td>${student.parentName} (${student.parentPhone})</td>
                <td>${feeBadge}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; color:var(--accent-red);">Failed to load students.</td></tr>`;
    }
}

async function loadHubAttendance() {
    const tbody = document.getElementById('hubAttendanceSheetBody');
    if (!tbody) return;

    tbody.innerHTML = `<tr><td colspan="3" class="skeleton" style="height:80px; border-radius:8px;"></td></tr>`;

    try {
        const res = await fetch(`/api/attendance/class-sheet?classGrade=${encodeURIComponent(activeDashboardClass)}&section=${encodeURIComponent(activeDashboardSection)}`);
        const data = await res.json();

        tbody.innerHTML = '';
        if (data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="3" style="text-align:center;">No students enrolled in this section.</td></tr>`;
            return;
        }

        data.forEach(row => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${row.rollNumber}</strong></td>
                <td>${row.name}</td>
                <td style="display:flex; justify-content:center;">
                    <div class="attendance-grid">
                        <button class="attendance-tick-btn present-box ${row.status === 'PRESENT' ? 'active' : ''}" 
                                onclick="tickHubAttendanceBox(this, ${row.studentId}, 'PRESENT')" title="Mark Present">✓</button>
                        <button class="attendance-tick-btn absent-box ${row.status === 'ABSENT' ? 'active' : ''}" 
                                onclick="tickHubAttendanceBox(this, ${row.studentId}, 'ABSENT')" title="Mark Absent">✗</button>
                        <button class="attendance-tick-btn half-box ${row.status === 'HALF' ? 'active' : ''}" 
                                onclick="tickHubAttendanceBox(this, ${row.studentId}, 'HALF')" title="Mark Half-day">½</button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
        });

        calculateHubRollCallStats();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="3" style="text-align:center; color:var(--accent-red);">Connection error loading roll sheet.</td></tr>`;
    }
}

async function tickHubAttendanceBox(btn, studentId, status) {
    const grid = btn.parentElement;
    const btns = grid.querySelectorAll('.attendance-tick-btn');

    // Visual state before API feedback for high-speed responsiveness
    const wasActive = btn.classList.contains('active');
    
    // Reset all ticks in this student's row
    btns.forEach(b => b.classList.remove('active'));

    if (!wasActive) {
        btn.classList.add('active');
    }

    try {
        const res = await fetch('/api/attendance/mark', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                studentId: studentId,
                status: wasActive ? 'PRESENT' : status,
                bypassTimeCheck: true
            })
        });

        const data = await res.json();
        
        if (res.ok) {
            if (status === 'ABSENT' && !wasActive) {
                showToast(`Student is marked ABSENT. Parental SMS warning dispatched immediately.`, "warning", "📲");
            } else {
                showToast(`Attendance marked successfully.`, "success");
            }
            
            // Reload logs and metrics
            loadSmsLogs();
            calculateHubRollCallStats();
            // Also refresh standard attendance tab if loaded
            if (activeTab === 'attendance') {
                loadAttendanceSheet();
            }
        } else {
            showToast(data.message || "Attendance marking failed!", "error", "🕒");
            btns.forEach(b => b.classList.remove('active'));
            loadHubAttendance();
        }
    } catch (err) {
        showToast("Connection failed.", "error", "📡");
        loadHubAttendance();
    }
}

function calculateHubRollCallStats() {
    const tbody = document.getElementById('hubAttendanceSheetBody');
    if (!tbody) return;
    const rows = tbody.querySelectorAll('tr');
    
    let present = 0;
    let absent = 0;
    let half = 0;
    let total = 0;

    rows.forEach(tr => {
        const activeBtn = tr.querySelector('.attendance-tick-btn.active');
        if (activeBtn) {
            total++;
            if (activeBtn.classList.contains('present-box')) present++;
            if (activeBtn.classList.contains('absent-box')) absent++;
            if (activeBtn.classList.contains('half-box')) half++;
        }
    });

    document.getElementById('hubStatPresent').textContent = present;
    document.getElementById('hubStatAbsent').textContent = absent;
    document.getElementById('hubStatHalf').textContent = half;

    let percentage = 0;
    if (total > 0) {
        const score = present + (half * 0.5);
        percentage = Math.round((score / total) * 100);
    }
    document.getElementById('hubStatPercentage').textContent = percentage + "%";
}

function submitHubAttendance() {
    showToast("Roll call attendance locked and synchronized successfully!", "success", "💾");
}

async function loadHubHomework() {
    const container = document.getElementById('hubHomeworkCardsContainer');
    if (!container) return;

    container.innerHTML = `<div class="skeleton" style="grid-column:1/-1; height:150px; border-radius:12px;"></div>`;

    const todayStr = new Date().toISOString().split('T')[0];

    try {
        const res = await fetch(`/api/homework?classGrade=${encodeURIComponent(activeDashboardClass)}&section=${encodeURIComponent(activeDashboardSection)}&date=${todayStr}`);
        const data = await res.json();
        
        container.innerHTML = '';

        if (!data || data.message || Object.keys(data).length === 0) {
            container.innerHTML = `<div style="grid-column:1/-1; text-align:center; padding:3rem; color:var(--text-sub);">📝 No daily homework details registered for today.</div>`;
            return;
        }

        // Render 7 subject cards
        const subjects = [
            { name: "Tamil", icon: "📕", desc: data.tamil || "No homework assigned.", opt: false },
            { name: "English", icon: "📘", desc: data.english || "No homework assigned.", opt: false },
            { name: "Maths", icon: "📐", desc: data.maths || "No homework assigned.", opt: false },
            { name: "Science", icon: "🔬", desc: data.science || "No homework assigned.", opt: false },
            { name: "Social Science", icon: "🌍", desc: data.socialScience || "No homework assigned.", opt: false },
            { name: "Optional Subject 1", icon: "💻", desc: data.optional1 || "No homework assigned.", opt: true },
            { name: "Optional Subject 2", icon: "🕉️", desc: data.optional2 || "No homework assigned.", opt: true }
        ];

        subjects.forEach(sub => {
            const card = document.createElement('div');
            card.className = "glass-card hw-card-subject";
            card.innerHTML = `
                <div class="hw-subject-title">
                    <span>${sub.icon} ${sub.name}</span>
                    ${sub.opt ? '<span class="opt-tag">Elective</span>' : ''}
                </div>
                <p class="hw-desc">${sub.desc}</p>
            `;
            container.appendChild(card);
        });

    } catch (err) {
        container.innerHTML = `<div style="grid-column:1/-1; text-align:center; color:var(--accent-red);">Failed to connect to homework service.</div>`;
    }
}

async function loadHubLeaderboard() {
    const container = document.getElementById('hubLeaderboardContainer');
    if (!container) return;

    container.innerHTML = `<div class="skeleton" style="height:150px; border-radius:12px;"></div>`;

    try {
        const response = await fetch(`/api/students?classGrade=${encodeURIComponent(activeDashboardClass)}&section=${encodeURIComponent(activeDashboardSection)}`);
        const students = await response.json();

        if (students.length === 0) {
            container.innerHTML = `<div style="text-align:center; padding:2rem; color:var(--text-sub);">No students enrolled.</div>`;
            return;
        }

        const gradesPromises = students.map(s => fetch(`/api/students/${s.id}/grades`).then(r => r.ok ? r.json() : []));
        const gradesList = await Promise.all(gradesPromises);

        const leaderboardData = students.map((s, idx) => {
            const studentGrades = gradesList[idx] || [];
            const quarterly = studentGrades.find(g => g.examName === 'Quarterly Exam');
            return {
                name: s.name,
                rollNumber: s.rollNumber,
                totalMarks: quarterly ? quarterly.totalMarks : 0
            };
        });

        // Sort by total marks descending
        leaderboardData.sort((a, b) => b.totalMarks - a.totalMarks);

        container.innerHTML = '';

        leaderboardData.forEach((student, index) => {
            const rank = index + 1;
            let rankClass = '';
            let rankBadge = '';

            if (rank === 1) {
                rankClass = 'rank-1';
                rankBadge = '🥇 Gold';
            } else if (rank === 2) {
                rankClass = 'rank-2';
                rankBadge = '🥈 Silver';
            } else if (rank === 3) {
                rankBadge = '🥉 Bronze';
            } else {
                rankBadge = 'Rank ' + rank;
            }

            const item = document.createElement('div');
            item.className = `leaderboard-item ${rankClass}`;
            item.innerHTML = `
                <div class="leaderboard-rank">${rank}</div>
                <div class="leaderboard-name">
                    ${student.name} 
                    <span style="font-size:0.75rem; color:var(--text-sub); font-weight:normal; margin-left:0.5rem;">Roll: ${student.rollNumber}</span>
                </div>
                <div style="display:flex; align-items:center; gap:1rem;">
                    <span class="leaderboard-badge" style="background:rgba(255,255,255,0.05); color:var(--text-sub);">${rankBadge}</span>
                    <span class="leaderboard-score">${student.totalMarks} / 700</span>
                </div>
            `;
            container.appendChild(item);
        });

    } catch (err) {
        container.innerHTML = `<div style="text-align:center; color:var(--accent-red); padding:1rem;">Failed to compile exam leaderboard.</div>`;
    }
}
