package com.school.sms.controller;

import com.school.sms.model.Homework;
import com.school.sms.model.User;
import com.school.sms.repository.HomeworkRepository;
import com.school.sms.repository.TeacherAssignmentRepository;
import com.school.sms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/homework")
public class HomeworkController {

    @Autowired
    private HomeworkRepository homeworkRepository;

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

    /**
     * Gets homework for a specific class/section on a specific date (defaults to today)
     */
    @GetMapping
    public ResponseEntity<?> getHomework(
            @RequestParam String classGrade,
            @RequestParam String section,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
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

        LocalDate targetDate = date != null ? date : LocalDate.now();

        List<Homework> list = homeworkRepository.findByClassGradeAndSectionAndDate(classGrade, section, targetDate);
        if (list.isEmpty()) {
            // Try to get latest homework if none found for today
            List<Homework> all = homeworkRepository.findByClassGradeAndSection(classGrade, section);
            if (!all.isEmpty()) {
                // Return latest
                all.sort((h1, h2) -> h2.getDate().compareTo(h1.getDate()));
                return ResponseEntity.ok(all.get(0));
            }
            return ResponseEntity.ok(Map.of("message", "No homework assigned."));
        }

        return ResponseEntity.ok(list.get(0));
    }

    /**
     * Creates or updates a homework record
     */
    @PostMapping
    public ResponseEntity<?> saveHomework(@RequestBody Homework homework, HttpServletRequest request) {
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
                    user.getId(), homework.getClassGrade(), homework.getSection());
            if (!assigned) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this class"));
            }
        }

        String username = user.getUsername();

        if (homework.getDate() == null) {
            homework.setDate(LocalDate.now());
        }

        // Check if homework already exists for this class, section and date
        List<Homework> existing = homeworkRepository.findByClassGradeAndSectionAndDate(
                homework.getClassGrade(), homework.getSection(), homework.getDate());

        Homework saved;
        if (!existing.isEmpty()) {
            Homework old = existing.get(0);
            old.setTamil(homework.getTamil());
            old.setEnglish(homework.getEnglish());
            old.setMaths(homework.getMaths());
            old.setScience(homework.getScience());
            old.setSocialScience(homework.getSocialScience());
            old.setOptional1(homework.getOptional1());
            old.setOptional2(homework.getOptional2());
            old.setUpdatedBy(username);
            saved = homeworkRepository.save(old);
        } else {
            homework.setUpdatedBy(username);
            saved = homeworkRepository.save(homework);
        }

        return ResponseEntity.ok(saved);
    }

    /**
     * Deletes a homework record
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteHomework(@PathVariable Long id, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.TEACHER, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        User user = userOpt.get();

        Optional<Homework> hwOpt = homeworkRepository.findById(id);
        if (hwOpt.isPresent()) {
            Homework hw = hwOpt.get();
            if (user.getRole() == User.Role.TEACHER) {
                boolean assigned = teacherAssignmentRepository.existsByTeacherIdAndClassGradeAndSection(
                        user.getId(), hw.getClassGrade(), hw.getSection());
                if (!assigned) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this class"));
                }
            }
            homeworkRepository.delete(hw);
            return ResponseEntity.ok(Map.of("message", "Homework deleted successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Homework not found"));
        }
    }
}
