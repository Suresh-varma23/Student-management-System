# API Integration Test Script for Student Management System
$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1. Login as Admin
Write-Host "1. Logging in as admin..." -ForegroundColor Cyan
$loginPayload = @{
    username = "admin"
    password = "admin123"
} | ConvertTo-Json

$loginResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $loginPayload -ContentType "application/json" -WebSession $session
Write-Host "Logged in successfully! Role: $($loginResponse.role)" -ForegroundColor Green

# 2. Get Seeded Classes
Write-Host "`n2. Getting seeded classes..." -ForegroundColor Cyan
$classes = Invoke-RestMethod -Uri "$baseUrl/api/classes" -Method Get -WebSession $session
Write-Host "Found $($classes.Count) seeded classes:" -ForegroundColor Green
$classes | ForEach-Object { Write-Host " - Class ID: $($_.id) | $($_.className) - Section $($_.section)" -ForegroundColor Gray }

# 3. Create a New Class (Class 12 Section L)
Write-Host "`n3. Creating new class: Class 12 Section L..." -ForegroundColor Cyan
$newClassPayload = @{
    className = "Class 12"
    section = "L"
} | ConvertTo-Json

$newClass = Invoke-RestMethod -Uri "$baseUrl/api/classes" -Method Post -Body $newClassPayload -ContentType "application/json" -WebSession $session
Write-Host "Class created successfully! ID: $($newClass.id) | $($newClass.className) - Section $($newClass.section)" -ForegroundColor Green

# 4. Try to Create Duplicate Class (Should Fail)
Write-Host "`n4. Attempting to create duplicate class Class 12 Section L..." -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "$baseUrl/api/classes" -Method Post -Body $newClassPayload -ContentType "application/json" -WebSession $session
    Write-Host "FAIL: Duplicate class creation did not throw an error!" -ForegroundColor Red
} catch {
    Write-Host "PASS: Duplicate class creation failed as expected: $_" -ForegroundColor Green
}

# 5. Get Teachers Selector
Write-Host "`n5. Fetching registered teachers..." -ForegroundColor Cyan
$teachers = Invoke-RestMethod -Uri "$baseUrl/api/classes/teachers" -Method Get -WebSession $session
Write-Host "Found $($teachers.Count) teachers:" -ForegroundColor Green
$teachers | ForEach-Object { Write-Host " - Teacher ID: $($_.id) | Username: $($_.username)" -ForegroundColor Gray }

# Find teacher1 ID dynamically
$teacher1 = $teachers | Where-Object { $_.username -eq "teacher1" }
$teacher1Id = $teacher1.id

# 6. Get Existing Teacher Assignments
Write-Host "`n6. Fetching teacher assignments..." -ForegroundColor Cyan
$assignments = Invoke-RestMethod -Uri "$baseUrl/api/classes/assignments" -Method Get -WebSession $session
Write-Host "Found $($assignments.Count) assignments:" -ForegroundColor Green
$assignments | ForEach-Object { Write-Host " - Assignment ID: $($_.id) | $($_.classGrade) Section $($_.section) | Subject: $($_.subject) | Teacher: $($_.teacher.username)" -ForegroundColor Gray }

# 7. Create a New Teacher Assignment
Write-Host "`n7. Assigning teacher1 to teach Maths to Class 12 Section L..." -ForegroundColor Cyan
$newAssignmentPayload = @{
    classGrade = "Class 12"
    section = "L"
    teacherId = $teacher1Id
    subject = "Maths"
} | ConvertTo-Json

$newAssignment = Invoke-RestMethod -Uri "$baseUrl/api/classes/assignments" -Method Post -Body $newAssignmentPayload -ContentType "application/json" -WebSession $session
Write-Host "Teacher assigned successfully! ID: $($newAssignment.id) | Subject: $($newAssignment.subject) | Teacher: $($newAssignment.teacher.username)" -ForegroundColor Green

# 8. Try to Create Duplicate Assignment for Same Subject/Class Slot (Should Fail)
Write-Host "`n8. Attempting to assign another teacher to the same subject slot..." -ForegroundColor Cyan
$dupAssignmentPayload = @{
    classGrade = "Class 12"
    section = "L"
    teacherId = 3 # teacher2 ID
    subject = "Maths"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$baseUrl/api/classes/assignments" -Method Post -Body $dupAssignmentPayload -ContentType "application/json" -WebSession $session
    Write-Host "FAIL: Duplicate assignment did not fail!" -ForegroundColor Red
} catch {
    Write-Host "PASS: Duplicate assignment failed as expected: $_" -ForegroundColor Green
}

# 9. Cleanup assignment and class
Write-Host "`n9. Cleaning up test class and assignment..." -ForegroundColor Cyan
Invoke-RestMethod -Uri "$baseUrl/api/classes/assignments/$($newAssignment.id)" -Method Delete -WebSession $session
Write-Host " - Assignment deleted successfully." -ForegroundColor Green

Invoke-RestMethod -Uri "$baseUrl/api/classes/$($newClass.id)" -Method Delete -WebSession $session
Write-Host " - Class deleted successfully." -ForegroundColor Green

Write-Host "`nALL TESTS COMPLETED SUCCESSFULLY!" -ForegroundColor Green
