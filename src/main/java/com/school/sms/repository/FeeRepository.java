package com.school.sms.repository;

import com.school.sms.model.Fee;
import com.school.sms.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeeRepository extends JpaRepository<Fee, Long> {
    List<Fee> findByStudent(Student student);
}
