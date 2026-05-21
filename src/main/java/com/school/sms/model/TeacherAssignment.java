package com.school.sms.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "teacher_assignments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"classGrade", "section", "subject"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_grade", nullable = false)
    private String classGrade; // e.g. "Class 10"

    @Column(nullable = false)
    private String section; // e.g. "A"

    @Column(nullable = false)
    private String subject; // e.g. "Maths"

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;
}
