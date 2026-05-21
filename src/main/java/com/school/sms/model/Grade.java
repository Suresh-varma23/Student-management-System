package com.school.sms.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "grades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Grade {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private String examName; // e.g. "Quarterly Exam", "Final Exam"

    // Default max mark is 100 per subject, entered by teacher in obtained marks box.
    private int english;
    private int tamil;
    private int science;
    private int maths;
    private int socialScience;

    // Optional Subject 1
    private String optional1Name;
    private int optional1Marks;

    // Optional Subject 2
    private String optional2Name;
    private int optional2Marks;

    // Calculated Fields
    private int totalMarks;
    private double averageMarks;
    private int classRank; // Automatically calculated rank among students of the same class for this exam
}
