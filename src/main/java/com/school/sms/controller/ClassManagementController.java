package com.school.sms.controller;

import com.school.sms.model.SchoolClass;
import com.school.sms.model.TeacherAssignment;
import com.school.sms.model.User;
import com.school.sms.repository.SchoolClassRepository;
import com.school.sms.repository.TeacherAssignmentRepository;
import com.school.sms.repository.UserRepository;
import com.school.sms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/classes")
public class ClassManagementController {

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private TeacherAssignmentRepository teacherAssignmentRepository;

    @Autowired
    private UserRepository userRepository;

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

    @GetMapping
    public ResponseEntity<?> getAllClasses(HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN, User.Role.TEACHER, User.Role.STUDENT)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }
        
        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        User user = userOpt.get();

        List<SchoolClass> classes = schoolClassRepository.findAll();

        if (user.getRole() == User.Role.TEACHER) {
            List<TeacherAssignment> assignments = teacherAssignmentRepository.findByTeacherId(user.getId());
            Set<String> classSectionKeys = assignments.stream()
                    .map(a -> a.getClassGrade().toUpperCase() + "_" + a.getSection().toUpperCase())
                    .collect(Collectors.toSet());
            
            classes = classes.stream()
                    .filter(c -> classSectionKeys.contains(c.getClassName().toUpperCase() + "_" + c.getSection().toUpperCase()))
                    .collect(Collectors.toList());
        }

        // Sort by className then section for better UI display
        classes.sort(Comparator.comparing(SchoolClass::getClassName)
                .thenComparing(SchoolClass::getSection));
        return ResponseEntity.ok(classes);
    }

    @PostMapping
    public ResponseEntity<?> createClass(@RequestBody SchoolClass schoolClass, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: Only admins can manage classes"));
        }

        if (schoolClass.getClassName() == null || schoolClass.getClassName().trim().isEmpty() ||
            schoolClass.getSection() == null || schoolClass.getSection().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Class name and section are required"));
        }

        String classNameClean = schoolClass.getClassName().trim();
        String sectionClean = schoolClass.getSection().trim().toUpperCase();

        Optional<SchoolClass> existing = schoolClassRepository.findByClassNameAndSection(classNameClean, sectionClean);
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Class " + classNameClean + " Section " + sectionClean + " already exists"));
        }

        SchoolClass savedClass = schoolClassRepository.save(
            SchoolClass.builder()
                .className(classNameClean)
                .section(sectionClean)
                .build()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(savedClass);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClass(@PathVariable Long id, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: Only admins can manage classes"));
        }

        if (!schoolClassRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Class not found"));
        }

        schoolClassRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Class successfully deleted"));
    }

    @GetMapping("/teachers")
    public ResponseEntity<?> getAllTeachers(HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        List<User> teachers = userRepository.findByRole(User.Role.TEACHER);
        // Map to secure DTO without password
        List<Map<String, Object>> teacherDTOs = teachers.stream().map(teacher -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", teacher.getId());
            map.put("username", teacher.getUsername());
            map.put("role", teacher.getRole());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(teacherDTOs);
    }

    @GetMapping("/assignments")
    public ResponseEntity<?> getAllAssignments(HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN, User.Role.TEACHER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        List<TeacherAssignment> assignments = teacherAssignmentRepository.findAll();
        // Safe mapping to exclude teacher passwords
        List<Map<String, Object>> assignmentDTOs = assignments.stream().map(assign -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", assign.getId());
            map.put("classGrade", assign.getClassGrade());
            map.put("section", assign.getSection());
            map.put("subject", assign.getSubject());
            
            Map<String, Object> teacherMap = new HashMap<>();
            teacherMap.put("id", assign.getTeacher().getId());
            teacherMap.put("username", assign.getTeacher().getUsername());
            map.put("teacher", teacherMap);
            
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(assignmentDTOs);
    }

    @PostMapping("/assignments")
    public ResponseEntity<?> createAssignment(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: Only admins can assign teachers"));
        }

        String classGrade = (String) payload.get("classGrade");
        String section = (String) payload.get("section");
        String subject = (String) payload.get("subject");
        Number teacherIdNum = (Number) payload.get("teacherId");

        if (classGrade == null || section == null || subject == null || teacherIdNum == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing required fields"));
        }

        Long teacherId = teacherIdNum.longValue();

        Optional<User> teacherOpt = userRepository.findById(teacherId);
        if (teacherOpt.isEmpty() || teacherOpt.get().getRole() != User.Role.TEACHER) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid teacher ID or user is not a teacher"));
        }

        // Validate if subject slot is already occupied for that class/section
        Optional<TeacherAssignment> existing = teacherAssignmentRepository
                .findByClassGradeAndSectionAndSubject(classGrade, section, subject);
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Subject '" + subject + "' in class " + classGrade + " Section " + section + 
                    " is already assigned to teacher " + existing.get().getTeacher().getUsername()
            ));
        }

        TeacherAssignment assignment = TeacherAssignment.builder()
                .classGrade(classGrade)
                .section(section)
                .subject(subject)
                .teacher(teacherOpt.get())
                .build();

        TeacherAssignment saved = teacherAssignmentRepository.save(assignment);
        
        // Return secure mapped DTO
        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("classGrade", saved.getClassGrade());
        response.put("section", saved.getSection());
        response.put("subject", saved.getSubject());
        response.put("teacher", Map.of("id", saved.getTeacher().getId(), "username", saved.getTeacher().getUsername()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<?> deleteAssignment(@PathVariable Long id, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: Only admins can delete assignments"));
        }

        if (!teacherAssignmentRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Assignment not found"));
        }

        teacherAssignmentRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Assignment successfully deleted"));
    }
}
