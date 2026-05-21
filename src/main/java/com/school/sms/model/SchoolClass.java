package com.school.sms.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "school_classes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"className", "section"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String className; // e.g., "Class 10"

    @Column(nullable = false)
    private String section; // e.g., "A", "B"
}
