package com.school.sms.repository;

import com.school.sms.model.Grade;
import com.school.sms.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findByStudentClassGradeAndExamName(String classGrade, String examName);
    List<Grade> findByStudent(Student student);
    Optional<Grade> findByStudentAndExamName(Student student, String examName);
}
