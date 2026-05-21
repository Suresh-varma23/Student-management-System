package com.school.sms.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String rollNumber;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "class_grade", nullable = false)
    private String classGrade; // e.g. "10th Grade" or "Class 10"

    @Column(nullable = false)
    private String section; // e.g. "A", "B"

    private String dob; // Date of birth
    private String gender;
    private String parentName;
    
    @Column(nullable = false)
    private String parentPhone; // Used to send mock SMS alerts immediately if student is absent

    private String address;

    @Builder.Default
    private String optionalSubject1 = "Computer Science";

    @Builder.Default
    private String optionalSubject2 = "Hindi";
}
