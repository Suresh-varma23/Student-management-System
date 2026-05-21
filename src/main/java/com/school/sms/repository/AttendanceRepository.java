package com.school.sms.repository;

import com.school.sms.model.Attendance;
import com.school.sms.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByStudentAndDate(Student student, LocalDate date);
    List<Attendance> findByStudent(Student student);
    List<Attendance> findByDate(LocalDate date);
    List<Attendance> findByStudentIdInAndDate(List<Long> studentIds, LocalDate date);
}
