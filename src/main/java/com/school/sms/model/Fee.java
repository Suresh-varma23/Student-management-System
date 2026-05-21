package com.school.sms.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fees")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fee {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    private String academicYear; // e.g. "2026-2027"
    private double totalAmount;
    private double paidAmount;
    private String dueDate;
    private String status; // PAID, PARTIAL, UNPAID
}
