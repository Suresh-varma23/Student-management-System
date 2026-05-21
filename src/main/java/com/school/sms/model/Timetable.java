package com.school.sms.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "timetables")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Timetable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_grade", nullable = false)
    private String classGrade;

    @Column(nullable = false)
    private String section;

    @Column(name = "day_of_week", nullable = false)
    private String dayOfWeek; // Monday, Tuesday, Wednesday, Thursday, Friday

    private String period1;
    private String period2;
    private String period3;
    private String period4;
    private String period5;
    private String period6;
    private String period7;
}
