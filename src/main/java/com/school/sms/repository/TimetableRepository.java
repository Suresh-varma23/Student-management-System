package com.school.sms.repository;

import com.school.sms.model.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {
    List<Timetable> findByClassGradeAndSection(String classGrade, String section);
    Optional<Timetable> findByClassGradeAndSectionAndDayOfWeek(String classGrade, String section, String dayOfWeek);
}
