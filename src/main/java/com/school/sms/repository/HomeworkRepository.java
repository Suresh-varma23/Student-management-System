package com.school.sms.repository;

import com.school.sms.model.Homework;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {
    List<Homework> findByClassGradeAndSectionAndDate(String classGrade, String section, LocalDate date);
    List<Homework> findByClassGradeAndSection(String classGrade, String section);
}
