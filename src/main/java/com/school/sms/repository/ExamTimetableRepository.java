package com.school.sms.repository;

import com.school.sms.model.ExamTimetable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamTimetableRepository extends JpaRepository<ExamTimetable, Long> {
    List<ExamTimetable> findByClassGrade(String classGrade);
}
