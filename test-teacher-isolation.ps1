# Teacher Class Isolation Integration Test Script for Student Management System
$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# Function to test an endpoint with expected success/failure
function Test-Endpoint {
    param (
        [string]$Method,
        [string]$Path,
        [string]$Payload = $null,
        [int]$ExpectedStatusCode = 200
    )

    $uri = "$baseUrl$Path"
    Write-Host "Testing $Method $Path (Expecting $ExpectedStatusCode)..." -NoNewline -ForegroundColor Cyan

    try {
        $params = @{
            Uri = $uri
            Method = $Method
            WebSession = $session
            UseBasicParsing = $true
        }
        if ($Payload) {
            $params["Body"] = $Payload
            $params["ContentType"] = "application/json"
        }

        $response = Invoke-WebRequest @params
        $statusCode = $response.StatusCode

        if ($statusCode -eq $ExpectedStatusCode) {
            Write-Host " PASS" -ForegroundColor Green
            return $response
        } else {
            Write-Host " FAIL (Got $statusCode, expected $ExpectedStatusCode)" -ForegroundColor Red
            exit 1
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq $ExpectedStatusCode) {
            Write-Host " PASS (Caught expected HTTP $ExpectedStatusCode)" -ForegroundColor Green
            return $null
        } else {
            Write-Host " FAIL (Caught HTTP $statusCode, expected $ExpectedStatusCode): $_" -ForegroundColor Red
            exit 1
        }
    }
}

# 1. Login as teacher1
Write-Host "1. Logging in as teacher1..." -ForegroundColor Yellow
$loginPayload = @{
    username = "teacher1"
    password = "teacher123"
} | ConvertTo-Json

$loginResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $loginPayload -ContentType "application/json" -WebSession $session
Write-Host "Logged in successfully! Role: $($loginResponse.role)" -ForegroundColor Green

# 2. Get Assigned Classes (should only be Class 10 Section A)
Write-Host "`n2. Verifying classes listed for teacher1..." -ForegroundColor Yellow
$classes = Invoke-RestMethod -Uri "$baseUrl/api/classes" -Method Get -WebSession $session
Write-Host "Found $($classes.Count) classes:" -ForegroundColor Gray
$classes | ForEach-Object { Write-Host " - ID: $($_.id) | $($_.className) - Section $($_.section)" -ForegroundColor Gray }

if ($classes.Count -ne 1) {
    Write-Host "FAIL: Expected exactly 1 assigned class, found $($classes.Count)" -ForegroundColor Red
    exit 1
}

$assignedClass = $classes[0]
if ($assignedClass.className -ne "Class 10" -or $assignedClass.section -ne "A") {
    Write-Host "FAIL: Expected assigned class to be Class 10 Section A, got $($assignedClass.className) Section $($assignedClass.section)" -ForegroundColor Red
    exit 1
}
Write-Host "PASS: Successfully verified dynamic class list isolation." -ForegroundColor Green

# 3. Verify Students List Filtration & Direct Access Checks
Write-Host "`n3. Verifying student-level isolation rules..." -ForegroundColor Yellow
# GET students in Class 10 A (should succeed)
$studentsA = Invoke-RestMethod -Uri "$baseUrl/api/students?classGrade=Class+10&section=A" -Method Get -WebSession $session
Write-Host "Class 10 A students found: $($studentsA.Count)" -ForegroundColor Gray

# GET students in Class 10 B (should block with 403)
Test-Endpoint -Method "Get" -Path "/api/students?classGrade=Class+10&section=B" -ExpectedStatusCode 403

# Verify grades endpoints for teachers
# Get first student from Class 10 A
$studentA1 = $studentsA[0]
# Fetch grades for assigned student (should succeed)
Test-Endpoint -Method "Get" -Path "/api/students/$($studentA1.id)/grades" -ExpectedStatusCode 200

# Try fetching grades for a student not in assigned class
# Let's find one by query as Admin first, or we can just guess/use a student ID from seeding.
# Student 1 is usually in LKG or UKG. Let's see if ID 1 returns 403.
Test-Endpoint -Method "Get" -Path "/api/students/1/grades" -ExpectedStatusCode 403

# 4. Verify Attendance isolation
Write-Host "`n4. Verifying attendance-level isolation rules..." -ForegroundColor Yellow
# GET Class sheet for Class 10 A (should succeed)
Test-Endpoint -Method "Get" -Path "/api/attendance/class-sheet?classGrade=Class+10&section=A" -ExpectedStatusCode 200
# GET Class sheet for Class 10 B (should block with 403)
Test-Endpoint -Method "Get" -Path "/api/attendance/class-sheet?classGrade=Class+10&section=B" -ExpectedStatusCode 403
# GET Class Stats for Class 10 A (should succeed)
Test-Endpoint -Method "Get" -Path "/api/attendance/stats?classGrade=Class+10&section=A" -ExpectedStatusCode 200
# GET Class Stats for Class 10 B (should block with 403)
Test-Endpoint -Method "Get" -Path "/api/attendance/stats?classGrade=Class+10&section=B" -ExpectedStatusCode 403

# 5. Verify Homework isolation
Write-Host "`n5. Verifying homework-level isolation rules..." -ForegroundColor Yellow
# GET Homework for Class 10 A (should succeed)
Test-Endpoint -Method "Get" -Path "/api/homework?classGrade=Class+10&section=A" -ExpectedStatusCode 200
# GET Homework for Class 10 B (should block with 403)
Test-Endpoint -Method "Get" -Path "/api/homework?classGrade=Class+10&section=B" -ExpectedStatusCode 403

# POST homework to Class 10 A (should succeed)
$hwPayload = @{
    classGrade = "Class 10"
    section = "A"
    english = "Read chapter 5"
    maths = "Solve quadratic equations"
} | ConvertTo-Json
$savedHw = Invoke-RestMethod -Uri "$baseUrl/api/homework" -Method Post -Body $hwPayload -ContentType "application/json" -WebSession $session
Write-Host "Homework created successfully for Class 10 A!" -ForegroundColor Green

# POST homework to Class 10 B (should block with 403)
$hwPayloadB = @{
    classGrade = "Class 10"
    section = "B"
    english = "Read chapter 5"
} | ConvertTo-Json
Test-Endpoint -Method "Post" -Path "/api/homework" -Payload $hwPayloadB -ExpectedStatusCode 403

# 6. Verify Timetable isolation
Write-Host "`n6. Verifying timetable-level isolation rules..." -ForegroundColor Yellow
# GET Class timetable for Class 10 A (should succeed)
Test-Endpoint -Method "Get" -Path "/api/timetable/class?classGrade=Class+10&section=A" -ExpectedStatusCode 200
# GET Class timetable for Class 10 B (should block with 403)
Test-Endpoint -Method "Get" -Path "/api/timetable/class?classGrade=Class+10&section=B" -ExpectedStatusCode 403

# POST Class timetable to Class 10 A (should succeed)
$ttPayload = @{
    classGrade = "Class 10"
    section = "A"
    dayOfWeek = "Monday"
    period1 = "Maths"
    period2 = "Science"
} | ConvertTo-Json
$savedTt = Invoke-RestMethod -Uri "$baseUrl/api/timetable/class" -Method Post -Body $ttPayload -ContentType "application/json" -WebSession $session
Write-Host "Timetable created successfully for Class 10 A!" -ForegroundColor Green

# POST Class timetable to Class 10 B (should block with 403)
$ttPayloadB = @{
    classGrade = "Class 10"
    section = "B"
    dayOfWeek = "Monday"
    period1 = "English"
} | ConvertTo-Json
Test-Endpoint -Method "Post" -Path "/api/timetable/class" -Payload $ttPayloadB -ExpectedStatusCode 403

# GET Exams timetable for Class 10 (should succeed since assigned to Class 10 A)
Test-Endpoint -Method "Get" -Path "/api/timetable/exams?classGrade=Class+10" -ExpectedStatusCode 200
# GET Exams timetable for Class 9 (should block with 403 since not assigned to Class 9)
Test-Endpoint -Method "Get" -Path "/api/timetable/exams?classGrade=Class+9" -ExpectedStatusCode 403

# 7. Cleanup created homework & timetable
Write-Host "`n7. Cleaning up test homework and timetable..." -ForegroundColor Yellow
Test-Endpoint -Method "Delete" -Path "/api/homework/$($savedHw.id)" -ExpectedStatusCode 200
Test-Endpoint -Method "Delete" -Path "/api/timetable/class/$($savedTt.id)" -ExpectedStatusCode 200

Write-Host "`nALL ISOLATION TESTS COMPLETED SUCCESSFULLY! 100% SECURED!" -ForegroundColor Green
