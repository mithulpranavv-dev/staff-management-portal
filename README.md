# AI-Integrated Staff Management Portal

A role-based web portal for managing the staff of an institution: attendance with rotating QR codes and Face ID, leave management with a paid-leave quota, automatic payroll, task assignment, and AI features (chatbot, smart search, leave insights, attendance alerts) powered by Google Gemini.

**Course:** 21CSC203P – Advanced Programming Practice
**Institution:** SRM Institute of Science and Technology, Tiruchirappalli (B.Tech CSE-AIML)
**Guide:** Dr. Bhuvaneshwari Amma N G

**Team**

| Name | Register No. |
|---|---|
| Mithul Pranav M | RA2511026050133 |
| Nidhish Yogi K | RA2511026050146 |
| Rahavi Arulaprakasam | RA2511026050162 |

---

## 1. Problem Statement

Staff records, attendance, leave, salary and task tracking are often kept in separate registers and spreadsheets. This causes proxy attendance, manual salary errors, slow leave approval and no single view of staff performance. This project brings all of this into one secure portal, with AI to answer questions and highlight problems.

## 2. Key Features

### Authentication and Security
- Login with username/password (BCrypt-hashed passwords) and **JWT** tokens.
- **Face ID** second step (face-api.js, match threshold 0.45) with a short-lived pre-auth token (5 minutes) and 5 server-side attempts.
- **Forgot password with OTP by email** (Gmail SMTP); 5 wrong OTPs lock the account for 15 minutes.
- Role-based access enforced on the **server** (Spring Security), not only on the screen.
- Salary data visible only to Dean and Administrator; employee e-mail visible only to Dean, Administrator and HOD.

### Attendance
- Staff show a **QR code that changes every 30 seconds** (SHA-256 token); the admin scans it with the QR scanner page.
- Attendance windows: morning 07:00–10:00, evening 16:00–18:00.
- Statuses: Present, Late, Half Day, Absent, On Leave.
- Automatic sweeps at 10:00 and 18:01 (Asia/Kolkata) mark absentees, with a start-up catch-up if the server was off.
- Dean, Administrator and Sundays are skipped.
- **Attendance alerts**: 3 or more Late/Absent/Half Day entries in 30 days, or presence below 70 %.

### Leave Management
- **24 paid leave days per year**; only approved leave counts; Sundays are not counted.
- Leave beyond 24 days becomes **unpaid** and is deducted from salary (full-day deduction).
- Overlap and date validation; applicant is identified from the JWT (cannot apply for someone else).
- Only the **Dean** approves or rejects; a reason is required on rejection.
- Leave balance shown to each staff member.

### Payroll
- Per-day salary = monthly salary / actual days in the month.
- Half Day = 0.5 day deduction, Absent = 1 day, unpaid leave = 1 day.
- One payslip per employee per month, generated in the last two days of the month, with a deduction detail table.

### Task Management
- Dean/HOD assign tasks to one or many employees (many-to-many through `task_assignment`).
- Only the assignee can change the task status; the completed date is stored.
- **Performance Insights**: task completion rate, on-time rate and 30-day attendance.

### AI Features (Google Gemini)
- **Portal Assistant chatbot**: answers questions about the user's own data (leave balance, attendance, tasks); input limit of 1000 characters; chat history is kept when the window is closed.
- **Smart Search**: Gemini suggests matching employee ids and the server re-filters them for permission.
- **Leave Insights**: charts with click-through drill-down to names and details.
- **On Leave Today** and **Attendance Alerts** widgets on dashboards.

## 3. User Roles

| Role | Main abilities |
|---|---|
| **Dean** | Everything: approve/reject leave, assign tasks, performance insights, all dashboards and AI insights |
| **Administrator** | Manage employees, face enrolment, QR scanner, payroll, attendance |
| **HOD** | Department dashboard, department employees, tasks, own leave, chatbot, smart search |
| **Faculty** | Own dashboard, QR code, leave application, tasks, chatbot |

(Founder, PRO and Vice-Chancellor exist in the role list but have no portal screens.)

## 4. Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java, Spring Boot 4.0.7, Spring Security 7, Spring Data JPA / Hibernate 7 |
| Database | MySQL (`staff_portal_db`) |
| Auth | JWT (jjwt 0.12.6), BCrypt |
| Email | Gmail SMTP (OTP) |
| AI | Google Gemini API |
| Frontend | HTML, CSS, vanilla JavaScript, Bootstrap 5.3.3, Chart.js 4.4.3 |
| Face / QR | face-api.js, qrcodejs, jsQR |
| Tools | IntelliJ IDEA, Maven, Postman, Git/GitHub |

## 5. Architecture

Layered design under the package `com.staffportal.staff_management_portal`:

```
Browser (HTML/JS pages)
        |  REST + JWT
        v
Controller  ->  Service  ->  Repository (JPA)  ->  MySQL
        ^
  JwtAuthFilter + SecurityConfig (role rules, first matching rule wins)
        |
  GeminiService  ->  Google Gemini API
  Scheduler      ->  attendance sweeps (10:00 and 18:01)
```

## 6. Database

Eight tables:

| Table | Purpose |
|---|---|
| `users` | Login accounts, roles, face data |
| `employee` | Staff details, department, salary |
| `attendance` | Daily attendance records and status |
| `leave_request` | Leave applications and decisions |
| `payroll` | Monthly payslips |
| `payroll_deduction_detail` | Line-by-line deductions of each payslip |
| `tasks` | Task details, due date, status, completed date |
| `task_assignment` | Links tasks to the employees assigned |

## 7. Project Structure

```
staff-management-portal/
├── pom.xml
├── mvnw / mvnw.cmd
├── postman/                      # Postman collection for API testing
└── src/
    ├── main/
    │   ├── java/com/staffportal/staff_management_portal/
    │   │   ├── controller/       # REST controllers
    │   │   ├── service/          # Business logic (leave, payroll, attendance, AI ...)
    │   │   ├── repository/       # Spring Data JPA repositories
    │   │   ├── model/            # Entities
    │   │   └── security/         # JwtUtil, JwtAuthFilter, SecurityConfig
    │   └── resources/
    │       ├── static/           # HTML pages and JS (dashboards, login, chatbot ...)
    │       └── application.properties   # NOT in the repo (see setup)
    └── test/
```

## 8. Setup and Run

### Requirements
- JDK 17 or newer
- Maven (or use the included `mvnw`)
- MySQL 8
- A Gmail account with an **App Password** (for OTP mail)
- A Google **Gemini API key**
- A modern browser with camera access (for Face ID and the QR scanner)

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/mithulpranavv-dev/staff-management-portal.git
   cd staff-management-portal
   ```

2. **Create the database**
   ```sql
   CREATE DATABASE staff_portal_db;
   ```

3. **Create `src/main/resources/application.properties`**
   This file is kept out of GitHub because it holds secrets. Create it and fill in your own values:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/staff_portal_db
   spring.datasource.username=YOUR_DB_USER
   spring.datasource.password=YOUR_DB_PASSWORD
   spring.jpa.hibernate.ddl-auto=update

   jwt.secret=A_LONG_RANDOM_SECRET_AT_LEAST_32_CHARACTERS

   spring.mail.host=smtp.gmail.com
   spring.mail.port=587
   spring.mail.username=YOUR_GMAIL
   spring.mail.password=YOUR_GMAIL_APP_PASSWORD
   spring.mail.properties.mail.smtp.auth=true
   spring.mail.properties.mail.smtp.starttls.enable=true

   # Gemini API key (use the same property name your GeminiService reads)
   gemini.api.key=YOUR_GEMINI_KEY
   ```

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```
   or run the main class from IntelliJ.

5. **Open the portal** at `http://localhost:8080` and log in.

> If you change `jwt.secret`, old tokens stop working. Log out, clear the browser storage and log in again.

## 9. How to Use (Short Guide)

1. **Log in** with your username and password, then complete Face ID if enabled.
2. **Staff:** open *My QR Code* to mark attendance, apply leave, view tasks, ask the Portal Assistant.
3. **Administrator:** manage employees, enrol faces, scan QR codes, generate payroll.
4. **HOD:** view the department, assign and track tasks, apply leave.
5. **Dean:** approve or reject leave, view Performance Insights and Leave Insights charts.

## 10. Testing

The system was tested with manual and API (Postman) test cases, for example:

- 25 approved leave days gave 24 paid days and 1 unpaid day (deduction Rs. 2,222).
- Face distance 0.34 for the real owner against 0.54 and 0.585 for others, which are rejected at 0.45.
- Attendance for a non-existent employee id (9999) was refused and the record count did not change.
- Direct calls to protected APIs without a valid token or with the wrong role return **403**.

## 11. Security Notes

- Never commit `application.properties`. It holds the database password, Gmail app password, Gemini key and JWT secret.
- Passwords are stored only as BCrypt hashes.
- The public `/api/users/**` endpoint was removed; all APIs require a JWT.
- Every list the AI sees is re-checked on the server against the user's role.

## 12. Limitations and Future Scope

- HOD personal payroll page is not yet available.
- Resume screening and bulk CSV import of employees are planned.
- Mobile app and push notifications for leave decisions.
- Face recognition quality depends on lighting and camera.

## 13. Acknowledgement

Developed as a course project at SRM Institute of Science and Technology, Tiruchirappalli, under the guidance of Dr. Bhuvaneshwari Amma N G.
