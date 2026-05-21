package com.school.sms.controller;

import com.school.sms.model.*;
import com.school.sms.repository.*;
import com.school.sms.service.AttendanceService;
import com.school.sms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private TeacherAssignmentRepository teacherAssignmentRepository;

    private boolean checkAuthorized(HttpServletRequest request, User.Role... allowedRoles) {
        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) return false;
        User user = userOpt.get();
        for (User.Role r : allowedRoles) {
            if (user.getRole() == r) return true;
        }
        return false;
    }

    /**
     * Lists students and merges their today's attendance record (if exists)
     */
    @GetMapping("/class-sheet")
    public ResponseEntity<?> getClassAttendanceSheet(
            @RequestParam String classGrade,
            @RequestParam String section,
            HttpServletRequest request) {

        if (!checkAuthorized(request, User.Role.TEACHER, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        User user = userOpt.get();

        if (user.getRole() == User.Role.TEACHER) {
            boolean assigned = teacherAssignmentRepository.existsByTeacherIdAndClassGradeAndSection(
                    user.getId(), classGrade, section);
            if (!assigned) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this class"));
            }
        }

        List<Student> students = studentRepository.findByClassGradeAndSection(classGrade, section);
        LocalDate today = LocalDate.now();

        // Get student ids
        List<Long> ids = students.stream().map(Student::getId).toList();
        List<Attendance> todayRecords = attendanceRepository.findByStudentIdInAndDate(ids, today);
        Map<Long, Attendance> attendanceMap = todayRecords.stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), a -> a));

        List<Map<String, Object>> sheet = students.stream().map(student -> {
            Map<String, Object> row = new HashMap<>();
            row.put("studentId", student.getId());
            row.put("name", student.getName());
            row.put("rollNumber", student.getRollNumber());
            row.put("classGrade", student.getClassGrade());
            row.put("section", student.getSection());

            Attendance att = attendanceMap.get(student.getId());
            if (att != null) {
                row.put("attendanceId", att.getId());
                row.put("status", att.getStatus().toString());
                row.put("markedBy", att.getMarkedBy());
            } else {
                row.put("attendanceId", null);
                row.put("status", null);
                row.put("markedBy", null);
            }
            return row;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(sheet);
    }

    /**
     * Submits or updates an attendance record
     */
    @PostMapping("/mark")
    public ResponseEntity<?> markAttendance(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.TEACHER, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        User user = userOpt.get();

        try {
            Long studentId = Long.parseLong(payload.get("studentId").toString());
            Optional<Student> studentOpt = studentRepository.findById(studentId);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Student not found"));
            }
            Student student = studentOpt.get();

            if (user.getRole() == User.Role.TEACHER) {
                boolean assigned = teacherAssignmentRepository.existsByTeacherIdAndClassGradeAndSection(
                        user.getId(), student.getClassGrade(), student.getSection());
                if (!assigned) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this student's class"));
                }
            }

            Attendance.Status status = Attendance.Status.valueOf(payload.get("status").toString().toUpperCase());
            boolean bypassTimeCheck = payload.containsKey("bypassTimeCheck") && Boolean.parseBoolean(payload.get("bypassTimeCheck").toString());

            String markerUsername = user.getUsername();
            Attendance marked = attendanceService.markAttendance(studentId, status, markerUsername, bypassTimeCheck);
            return ResponseEntity.ok(marked);
        } catch (IllegalStateException e) {
            // Time restriction violations
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Failed to mark attendance: " + e.getMessage()));
        }
    }

    /**
     * Gets statistics for dashboard
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getAttendanceStats(
            @RequestParam String classGrade,
            @RequestParam String section,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {

        if (!checkAuthorized(request, User.Role.TEACHER, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        User user = userOpt.get();

        if (user.getRole() == User.Role.TEACHER) {
            boolean assigned = teacherAssignmentRepository.existsByTeacherIdAndClassGradeAndSection(
                    user.getId(), classGrade, section);
            if (!assigned) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this class"));
            }
        }

        LocalDate targetDate = date != null ? date : LocalDate.now();
        Map<String, Object> stats = attendanceService.calculateClassAttendanceStats(classGrade, section, targetDate);
        return ResponseEntity.ok(stats);
    }

    /**
     * Lists parental SMS logs
     */
    @GetMapping("/logs")
    public ResponseEntity<?> getSmsLogs(HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.TEACHER, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }
        return ResponseEntity.ok(attendanceService.getSmsLogs());
    }

    /**
     * Retrieves active dynamic Twilio SMS settings
     */
    @GetMapping("/settings/sms")
    public ResponseEntity<?> getSmsSettings(HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN, User.Role.TEACHER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }
        
        Map<String, String> settings = new HashMap<>();
        settings.put("twilioSid", attendanceService.getActiveTwilioSid() != null ? attendanceService.getActiveTwilioSid() : "");
        
        String token = attendanceService.getActiveTwilioAuthToken();
        if (token != null && !token.isEmpty()) {
            settings.put("twilioAuthToken", "********"); // mask for security
        } else {
            settings.put("twilioAuthToken", "");
        }
        
        settings.put("twilioPhoneNumber", attendanceService.getActiveTwilioPhoneNumber() != null ? attendanceService.getActiveTwilioPhoneNumber() : "");
        
        return ResponseEntity.ok(settings);
    }

    /**
     * Updates and persists Twilio credentials
     */
    @PostMapping("/settings/sms")
    public ResponseEntity<?> saveSmsSettings(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only Administrators can modify SMS Gateway configurations."));
        }

        String sid = payload.get("twilioSid");
        String token = payload.get("twilioAuthToken");
        String phone = payload.get("twilioPhoneNumber");

        // Safety: if the token is masked, retain the existing one
        if ("********".equals(token)) {
            token = attendanceService.getActiveTwilioAuthToken();
        }

        attendanceService.saveSmsConfig(sid, token, phone);
        return ResponseEntity.ok(Map.of("message", "SMS Gateway settings successfully saved!"));
    }

    /**
     * Sends a test parental SMS notification in-flight to verify status
     */
    @PostMapping("/settings/sms/test")
    public ResponseEntity<?> testSmsSettings(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN, User.Role.TEACHER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        String sid = payload.get("twilioSid");
        String token = payload.get("twilioAuthToken");
        String phone = payload.get("twilioPhoneNumber");
        String targetPhone = payload.get("targetPhone");

        if ("********".equals(token)) {
            token = attendanceService.getActiveTwilioAuthToken();
        }

        try {
            attendanceService.sendTestSms(sid, token, phone, targetPhone, "Vedic Academy Parental SMS Gateway is fully operational!");
            return ResponseEntity.ok(Map.of("message", "Test SMS dispatched successfully! Please check your mobile phone."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "SMS Dispatch failed: " + e.getMessage()));
        }
    }
}
