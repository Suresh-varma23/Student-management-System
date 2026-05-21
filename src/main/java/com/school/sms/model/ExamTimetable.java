package com.school.sms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "exam_timetables")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamTimetable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_grade", nullable = false)
    private String classGrade;

    @Column(nullable = false)
    private String examName; // e.g. "Quarterly Exam 2026", "Finals"

    @Column(nullable = false)
    private String subject; // Tamil, English, Maths, Science, etc.

    @Column(nullable = false)
    private LocalDate examDate;

    private String examTime; // e.g. "09:30 AM - 12:30 PM"
    
    @Builder.Default
    private int maxMarks = 100;
}
