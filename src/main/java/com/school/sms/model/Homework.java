package com.school.sms.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "homework")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Homework {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "class_grade", nullable = false)
    private String classGrade;

    @Column(nullable = false)
    private String section;

    @Column(columnDefinition = "TEXT")
    private String tamil;

    @Column(columnDefinition = "TEXT")
    private String english;

    @Column(columnDefinition = "TEXT")
    private String maths;

    @Column(columnDefinition = "TEXT")
    private String science;

    @Column(columnDefinition = "TEXT")
    private String socialScience;

    @Column(columnDefinition = "TEXT")
    private String optional1; // Homework for optional subject 1

    @Column(columnDefinition = "TEXT")
    private String optional2; // Homework for optional subject 2

    private String updatedBy; // Teacher/Admin username
}
