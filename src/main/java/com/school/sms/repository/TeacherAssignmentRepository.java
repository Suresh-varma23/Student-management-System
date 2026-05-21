package com.school.sms.repository;

import com.school.sms.model.TeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, Long> {
    List<TeacherAssignment> findByClassGradeAndSection(String classGrade, String section);
    Optional<TeacherAssignment> findByClassGradeAndSectionAndSubject(String classGrade, String section, String subject);
    List<TeacherAssignment> findByTeacherId(Long teacherId);
    boolean existsByTeacherIdAndClassGradeAndSection(Long teacherId, String classGrade, String section);
}
