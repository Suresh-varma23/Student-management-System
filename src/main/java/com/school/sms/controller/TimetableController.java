package com.school.sms.controller;

import com.school.sms.model.ExamTimetable;
import com.school.sms.model.TeacherAssignment;
import com.school.sms.model.Timetable;
import com.school.sms.model.User;
import com.school.sms.repository.ExamTimetableRepository;
import com.school.sms.repository.TeacherAssignmentRepository;
import com.school.sms.repository.TimetableRepository;
import com.school.sms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private ExamTimetableRepository examTimetableRepository;

    @Autowired
    private TeacherAssignmentRepository teacherAssignmentRepository;

    @Autowired
    private AuthService authService;

    private boolean checkAuthorized(HttpServletRequest request, User.Role... allowedRoles) {
        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) return false;
        User user = userOpt.get();
        for (User.Role r : allowedRoles) {
            if (user.getRole() == r) return true;
        }
        return false;
    }

    // ==========================================
    // CLASS TIMETABLE (7 periods, 5 days - Mon to Fri)
    // ==========================================

    @GetMapping("/class")
    public ResponseEntity<?> getClassTimetable(
            @RequestParam String classGrade,
            @RequestParam String section,
            HttpServletRequest request) {

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

        List<Timetable> list = timetableRepository.findByClassGradeAndSection(classGrade, section);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/class")
    public ResponseEntity<?> saveClassTimetable(@RequestBody Timetable timetable, HttpServletRequest request) {
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
                    user.getId(), timetable.getClassGrade(), timetable.getSection());
            if (!assigned) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this class"));
            }
        }

        // Validate day of week
        String day = timetable.getDayOfWeek();
        if (day == null || day.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Day of week is required"));
        }

        // Normalize day string
        day = day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
        timetable.setDayOfWeek(day);

        // Check if there's already a row for this day
        Optional<Timetable> existing = timetableRepository.findByClassGradeAndSectionAndDayOfWeek(
                timetable.getClassGrade(), timetable.getSection(), day);

        Timetable saved;
        if (existing.isPresent()) {
            Timetable old = existing.get();
            old.setPeriod1(timetable.getPeriod1());
            old.setPeriod2(timetable.getPeriod2());
            old.setPeriod3(timetable.getPeriod3());
            old.setPeriod4(timetable.getPeriod4());
            old.setPeriod5(timetable.getPeriod5());
            old.setPeriod6(timetable.getPeriod6());
            old.setPeriod7(timetable.getPeriod7());
            saved = timetableRepository.save(old);
        } else {
            saved = timetableRepository.save(timetable);
        }

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/class/{id}")
    public ResponseEntity<?> deleteClassTimetable(@PathVariable Long id, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.TEACHER, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        User user = userOpt.get();

        Optional<Timetable> ttOpt = timetableRepository.findById(id);
        if (ttOpt.isPresent()) {
            Timetable tt = ttOpt.get();
            if (user.getRole() == User.Role.TEACHER) {
                boolean assigned = teacherAssignmentRepository.existsByTeacherIdAndClassGradeAndSection(
                        user.getId(), tt.getClassGrade(), tt.getSection());
                if (!assigned) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this class"));
                }
            }
            timetableRepository.delete(tt);
            return ResponseEntity.ok(Map.of("message", "Class timetable slot deleted successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Timetable not found"));
        }
    }

    // ==========================================
    // EXAM TIMETABLE
    // ==========================================

    @GetMapping("/exams")
    public ResponseEntity<?> getExamTimetable(@RequestParam String classGrade, HttpServletRequest request) {
        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        User user = userOpt.get();

        if (user.getRole() == User.Role.TEACHER) {
            List<TeacherAssignment> assignments = teacherAssignmentRepository.findByTeacherId(user.getId());
            boolean hasAssignment = assignments.stream()
                    .anyMatch(a -> a.getClassGrade().equalsIgnoreCase(classGrade));
            if (!hasAssignment) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this class"));
            }
        }

        List<ExamTimetable> list = examTimetableRepository.findByClassGrade(classGrade);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/exams")
    public ResponseEntity<?> saveExamTimetable(@RequestBody ExamTimetable examTimetable, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only Admin can add or edit Exam Timetables"));
        }

        if (examTimetable.getExamDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Exam date is required"));
        }

        ExamTimetable saved = examTimetableRepository.save(examTimetable);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/exams/{id}")
    public ResponseEntity<?> deleteExamTimetable(@PathVariable Long id, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only Admin can delete Exam Timetables"));
        }

        Optional<ExamTimetable> examOpt = examTimetableRepository.findById(id);
        if (examOpt.isPresent()) {
            examTimetableRepository.delete(examOpt.get());
            return ResponseEntity.ok(Map.of("message", "Exam slot deleted successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Exam slot not found"));
        }
    }
}
