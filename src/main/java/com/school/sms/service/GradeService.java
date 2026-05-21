package com.school.sms.service;

import com.school.sms.model.Grade;
import com.school.sms.model.Student;
import com.school.sms.repository.GradeRepository;
import com.school.sms.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class GradeService {

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private StudentRepository studentRepository;

    /**
     * Saves or updates a student's marks, computes aggregates, and triggers real-time class ranking calculations.
     */
    @Transactional
    public Grade saveGrade(Long studentId, String examName, int english, int tamil, int science, int maths, int socialScience,
                           String optional1Name, int optional1Marks, String optional2Name, int optional2Marks) {
        
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + studentId));

        Optional<Grade> existing = gradeRepository.findByStudentAndExamName(student, examName);
        Grade grade;

        if (existing.isPresent()) {
            grade = existing.get();
        } else {
            grade = new Grade();
            grade.setStudent(student);
            grade.setExamName(examName);
        }

        // Apply grades
        grade.setEnglish(english);
        grade.setTamil(tamil);
        grade.setScience(science);
        grade.setMaths(maths);
        grade.setSocialScience(socialScience);
        grade.setOptional1Name(optional1Name != null ? optional1Name : student.getOptionalSubject1());
        grade.setOptional1Marks(optional1Marks);
        grade.setOptional2Name(optional2Name != null ? optional2Name : student.getOptionalSubject2());
        grade.setOptional2Marks(optional2Marks);

        // Calculate total obtained marks and average (7 subjects)
        int total = english + tamil + science + maths + socialScience + optional1Marks + optional2Marks;
        double average = total / 7.0;
        
        grade.setTotalMarks(total);
        // Round average to two decimal places
        grade.setAverageMarks(Math.round(average * 100.0) / 100.0);

        // Save grade initially
        Grade savedGrade = gradeRepository.save(grade);

        // Recalculate ranks for the whole class for this exam
        recalculateClassRanks(student.getClassGrade(), examName);

        // Fetch and return the updated grade containing the new class rank
        return gradeRepository.findById(savedGrade.getId()).orElse(savedGrade);
    }

    /**
     * Recalculates the rankings for all students in a class grade for a specific exam
     */
    @Transactional
    public void recalculateClassRanks(String classGrade, String examName) {
        // Find all student grades for this class and exam
        List<Grade> classGrades = gradeRepository.findByStudentClassGradeAndExamName(classGrade, examName);
        if (classGrades.isEmpty()) {
            return;
        }

        // Sort by total marks in descending order
        classGrades.sort(Comparator.comparingInt(Grade::getTotalMarks).reversed());

        // Assign ranks (handling equal marks with identical rank, e.g., standard competition ranking)
        int rank = 1;
        int count = 0;
        int previousTotal = -1;

        for (Grade g : classGrades) {
            count++;
            if (g.getTotalMarks() != previousTotal) {
                rank = count;
                previousTotal = g.getTotalMarks();
            }
            g.setClassRank(rank);
        }

        // Batch save the ranks
        gradeRepository.saveAll(classGrades);
    }

    /**
     * Get grades for a specific student
     */
    public List<Grade> getStudentGrades(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        return gradeRepository.findByStudent(student);
    }

    /**
     * Delete grades
     */
    @Transactional
    public void deleteGrade(Long gradeId) {
        Grade grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found"));
        
        String classGrade = grade.getStudent().getClassGrade();
        String examName = grade.getExamName();

        gradeRepository.delete(grade);

        // Recalculate rankings in the class without this student's grade
        recalculateClassRanks(classGrade, examName);
    }
}
