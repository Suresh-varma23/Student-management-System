package com.school.sms.controller;

import com.school.sms.model.*;
import com.school.sms.repository.*;
import com.school.sms.service.AuthService;
import com.school.sms.service.GradeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private FeeRepository feeRepository;

    @Autowired
    private GradeService gradeService;

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

    @GetMapping
    public ResponseEntity<?> getAllStudents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String classGrade,
            @RequestParam(required = false) String section,
            HttpServletRequest request) {

        if (!checkAuthorized(request, User.Role.ADMIN, User.Role.TEACHER)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        User user = userOpt.get();

        if (user.getRole() == User.Role.TEACHER) {
            if (classGrade != null && !classGrade.isEmpty() && section != null && !section.isEmpty()) {
                boolean assigned = teacherAssignmentRepository.existsByTeacherIdAndClassGradeAndSection(
                        user.getId(), classGrade, section);
                if (!assigned) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this class"));
                }
            }
        }

        List<Student> students = studentRepository.findAll();

        if (user.getRole() == User.Role.TEACHER) {
            List<TeacherAssignment> assignments = teacherAssignmentRepository.findByTeacherId(user.getId());
            Set<String> classSectionKeys = assignments.stream()
                    .map(a -> a.getClassGrade().toUpperCase() + "_" + a.getSection().toUpperCase())
                    .collect(Collectors.toSet());
            
            students = students.stream()
                    .filter(s -> classSectionKeys.contains(s.getClassGrade().toUpperCase() + "_" + s.getSection().toUpperCase()))
                    .collect(Collectors.toList());
        }

        // Apply filters in memory for simplicity and robust cross-DB filtering
        return ResponseEntity.ok(students.stream()
                .filter(s -> classGrade == null || classGrade.isEmpty() || s.getClassGrade().equalsIgnoreCase(classGrade))
                .filter(s -> section == null || section.isEmpty() || s.getSection().equalsIgnoreCase(section))
                .filter(s -> search == null || search.isEmpty() || 
                             s.getName().toLowerCase().contains(search.toLowerCase()) || 
                             s.getRollNumber().toLowerCase().contains(search.toLowerCase()))
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStudentById(@PathVariable Long id, HttpServletRequest request) {
        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }

        User user = userOpt.get();
        // Students can only view their own profile
        if (user.getRole() == User.Role.STUDENT && !id.equals(user.getStudentId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isPresent()) {
            return ResponseEntity.ok(studentOpt.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Student not found"));
        }
    }

    @PostMapping
    public ResponseEntity<?> createStudent(@RequestBody Student student, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only Admin can register new students"));
        }

        if (studentRepository.findByRollNumber(student.getRollNumber()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Student with roll number already exists"));
        }

        Student saved = studentRepository.save(student);

        // Auto-create student user account for login
        long totalUsers = userRepository.count();
        String defaultUsername = "student" + (totalUsers - 1); // incremental default username
        User studentUser = User.builder()
                .username(defaultUsername)
                .password("student123")
                .role(User.Role.STUDENT)
                .studentId(saved.getId())
                .build();
        userRepository.save(studentUser);

        // Auto-initialize fees row
        Fee fee = Fee.builder()
                .student(saved)
                .academicYear("2026")
                .totalAmount(35000.0)
                .paidAmount(0.0)
                .dueDate("2026-06-30")
                .status("UNPAID")
                .build();
        feeRepository.save(fee);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "student", saved,
            "createdUsername", defaultUsername,
            "createdPassword", "student123"
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStudent(@PathVariable Long id, @RequestBody Student studentDetails, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only Admin can update student details"));
        }

        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Student not found"));
        }

        Student student = studentOpt.get();
        student.setName(studentDetails.getName());
        student.setEmail(studentDetails.getEmail());
        student.setClassGrade(studentDetails.getClassGrade());
        student.setSection(studentDetails.getSection());
        student.setDob(studentDetails.getDob());
        student.setGender(studentDetails.getGender());
        student.setParentName(studentDetails.getParentName());
        student.setParentPhone(studentDetails.getParentPhone());
        student.setAddress(studentDetails.getAddress());
        student.setOptionalSubject1(studentDetails.getOptionalSubject1());
        student.setOptionalSubject2(studentDetails.getOptionalSubject2());

        return ResponseEntity.ok(studentRepository.save(student));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStudent(@PathVariable Long id, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only Admin can delete student details"));
        }

        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Student not found"));
        }

        // Delete user credentials linked to student
        Optional<User> userOpt = userRepository.findAll().stream()
                .filter(u -> id.equals(u.getStudentId()))
                .findFirst();
        userOpt.ifPresent(user -> userRepository.delete(user));

        // Delete marks/grades
        List<Grade> grades = gradeRepository.findByStudent(studentOpt.get());
        gradeRepository.deleteAll(grades);

        // Delete fees
        List<Fee> fees = feeRepository.findByStudent(studentOpt.get());
        feeRepository.deleteAll(fees);

        studentRepository.delete(studentOpt.get());
        return ResponseEntity.ok(Map.of("message", "Student deleted successfully"));
    }

    // ==========================================
    // STUDENT MARKS / GRADES ENDPOINTS
    // ==========================================

    @GetMapping("/{id}/grades")
    public ResponseEntity<?> getStudentGrades(@PathVariable Long id, HttpServletRequest request) {
        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }

        User user = userOpt.get();
        if (user.getRole() == User.Role.STUDENT && !id.equals(user.getStudentId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        if (user.getRole() == User.Role.TEACHER) {
            Optional<Student> studentOpt = studentRepository.findById(id);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Student not found"));
            }
            Student student = studentOpt.get();
            boolean assigned = teacherAssignmentRepository.existsByTeacherIdAndClassGradeAndSection(
                    user.getId(), student.getClassGrade(), student.getSection());
            if (!assigned) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this student's class"));
            }
        }

        return ResponseEntity.ok(gradeService.getStudentGrades(id));
    }

    @PostMapping("/{studentId}/grades")
    public ResponseEntity<?> saveStudentGrades(@PathVariable Long studentId, @RequestBody Grade grade, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.TEACHER, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        User user = userOpt.get();

        if (user.getRole() == User.Role.TEACHER) {
            Optional<Student> studentOpt = studentRepository.findById(studentId);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Student not found"));
            }
            Student student = studentOpt.get();
            boolean assigned = teacherAssignmentRepository.existsByTeacherIdAndClassGradeAndSection(
                    user.getId(), student.getClassGrade(), student.getSection());
            if (!assigned) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this student's class"));
            }
        }

        try {
            Grade saved = gradeService.saveGrade(
                    studentId,
                    grade.getExamName(),
                    grade.getEnglish(),
                    grade.getTamil(),
                    grade.getScience(),
                    grade.getMaths(),
                    grade.getSocialScience(),
                    grade.getOptional1Name(),
                    grade.getOptional1Marks(),
                    grade.getOptional2Name(),
                    grade.getOptional2Marks()
            );
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/grades/{gradeId}")
    public ResponseEntity<?> deleteStudentGrades(@PathVariable Long gradeId, HttpServletRequest request) {
        if (!checkAuthorized(request, User.Role.TEACHER, User.Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        User user = userOpt.get();

        if (user.getRole() == User.Role.TEACHER) {
            Optional<Grade> gradeOpt = gradeRepository.findById(gradeId);
            if (gradeOpt.isPresent()) {
                Student student = gradeOpt.get().getStudent();
                boolean assigned = teacherAssignmentRepository.existsByTeacherIdAndClassGradeAndSection(
                        user.getId(), student.getClassGrade(), student.getSection());
                if (!assigned) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied: You are not assigned to this student's class"));
                }
            }
        }

        try {
            gradeService.deleteGrade(gradeId);
            return ResponseEntity.ok(Map.of("message", "Grades deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ==========================================
    // STUDENT FEES ENDPOINTS
    // ==========================================

    @GetMapping("/{id}/fees")
    public ResponseEntity<?> getStudentFees(@PathVariable Long id, HttpServletRequest request) {
        Optional<User> userOpt = authService.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }

        User user = userOpt.get();
        if (user.getRole() == User.Role.STUDENT && !id.equals(user.getStudentId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Student not found"));
        }

        return ResponseEntity.ok(feeRepository.findByStudent(studentOpt.get()));
    }
}
