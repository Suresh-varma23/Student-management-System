package com.school.sms.service;

import com.school.sms.model.*;
import com.school.sms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private HomeworkRepository homeworkRepository;

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private ExamTimetableRepository examTimetableRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private FeeRepository feeRepository;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private TeacherAssignmentRepository teacherAssignmentRepository;

    @Override
    public void run(String... args) throws Exception {
        // If users already exist, do not re-seed
        if (userRepository.count() > 0) {
            return;
        }

        System.out.println(">>> SEEDING STUDENT MANAGEMENT SYSTEM DATABASE WITH RICH DEMO DATA...");

        // 1. SEED USERS
        // Admin
        User admin = User.builder()
                .username("admin")
                .password("admin123")
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(admin);

        // Teachers
        User teacher1 = User.builder()
                .username("teacher1")
                .password("teacher123")
                .role(User.Role.TEACHER)
                .build();
        userRepository.save(teacher1);

        User teacher2 = User.builder()
                .username("teacher2")
                .password("teacher223")
                .role(User.Role.TEACHER)
                .build();
        userRepository.save(teacher2);

        // Seed classes dynamically (Optimized for cloud database network latency)
        String[] classNames = {
            "LKG", "UKG", "Class 1", "Class 5", "Class 10", "Class 12"
        };
        String[] sections = {"A", "B"};

        // Create Section A to K for all classes
        for (String className : classNames) {
            for (String sec : sections) {
                schoolClassRepository.save(SchoolClass.builder().className(className).section(sec).build());
            }
        }

        // Seed initial teacher assignments for Class 10A
        teacherAssignmentRepository.save(TeacherAssignment.builder()
                .classGrade("Class 10")
                .section("A")
                .subject("Maths")
                .teacher(teacher1)
                .build());
        teacherAssignmentRepository.save(TeacherAssignment.builder()
                .classGrade("Class 10")
                .section("A")
                .subject("Science")
                .teacher(teacher2)
                .build());

        // 2. SEED STUDENTS & ASSOCIATED USER CREDENTIALS
        List<Student> allStudents = new ArrayList<>();

        for (String className : classNames) {
            for (String sec : sections) {
                if (className.equals("Class 10") && sec.equals("A")) {
                    // Seed the 5 original students in Class 10 Section A
                    Student s1 = Student.builder()
                            .name("Arun Kumar")
                            .rollNumber("STU101")
                            .email("arun.kumar@school.com")
                            .classGrade("Class 10")
                            .section("A")
                            .dob("2011-04-12")
                            .gender("Male")
                            .parentName("Karthik Kumar")
                            .parentPhone("9876543210")
                            .address("No. 12, Gandhi Street, Chennai")
                            .optionalSubject1("Computer Science")
                            .optionalSubject2("Hindi")
                            .build();
                    s1 = studentRepository.save(s1);
                    allStudents.add(s1);

                    Student s2 = Student.builder()
                            .name("Bhavana Sen")
                            .rollNumber("STU102")
                            .email("bhavana.sen@school.com")
                            .classGrade("Class 10")
                            .section("A")
                            .dob("2011-08-23")
                            .gender("Female")
                            .parentName("Amit Sen")
                            .parentPhone("9876543211")
                            .address("Block B4, Riverdale Apts, Chennai")
                            .optionalSubject1("Computer Science")
                            .optionalSubject2("Sanskrit")
                            .build();
                    s2 = studentRepository.save(s2);
                    allStudents.add(s2);

                    Student s3 = Student.builder()
                            .name("Charan Raj")
                            .rollNumber("STU103")
                            .email("charan.raj@school.com")
                            .classGrade("Class 10")
                            .section("A")
                            .dob("2011-01-05")
                            .gender("Male")
                            .parentName("Rajalingam M")
                            .parentPhone("9876543212")
                            .address("No. 45, Temple View St, Chennai")
                            .optionalSubject1("Computer Science")
                            .optionalSubject2("Hindi")
                            .build();
                    s3 = studentRepository.save(s3);
                    allStudents.add(s3);

                    Student s4 = Student.builder()
                            .name("Divya Prakash")
                            .rollNumber("STU104")
                            .email("divya.prakash@school.com")
                            .classGrade("Class 10")
                            .section("A")
                            .dob("2011-11-30")
                            .gender("Female")
                            .parentName("Prakash Babu")
                            .parentPhone("9876543213")
                            .address("3rd Avenue, Anna Nagar, Chennai")
                            .optionalSubject1("Computer Science")
                            .optionalSubject2("French")
                            .build();
                    s4 = studentRepository.save(s4);
                    allStudents.add(s4);

                    Student s5 = Student.builder()
                            .name("Elango V")
                            .rollNumber("STU105")
                            .email("elango.v@school.com")
                            .classGrade("Class 10")
                            .section("A")
                            .dob("2011-05-18")
                            .gender("Male")
                            .parentName("Vasudevan K")
                            .parentPhone("9876543214")
                            .address("No. 88, Lakeview Lane, Chennai")
                            .optionalSubject1("Computer Science")
                            .optionalSubject2("Hindi")
                            .build();
                    s5 = studentRepository.save(s5);
                    allStudents.add(s5);

                    // Create user accounts `student1` to `student5`
                    for (int i = 1; i <= 5; i++) {
                        Student st = allStudents.get(allStudents.size() - 6 + i);
                        userRepository.save(User.builder()
                                .username("student" + i)
                                .password("student123")
                                .role(User.Role.STUDENT)
                                .studentId(st.getId())
                                .build());
                    }

                    // Seed grades, yesterday attendance, homework, timetables, exam timetables, fees for Class 10A
                    gradeService.saveGrade(s1.getId(), "Quarterly Exam", 85, 90, 88, 95, 80, s1.getOptionalSubject1(), 92, s1.getOptionalSubject2(), 82);
                    gradeService.saveGrade(s2.getId(), "Quarterly Exam", 75, 82, 70, 65, 78, s2.getOptionalSubject1(), 80, s2.getOptionalSubject2(), 74);
                    gradeService.saveGrade(s3.getId(), "Quarterly Exam", 92, 95, 96, 98, 94, s3.getOptionalSubject1(), 95, s3.getOptionalSubject2(), 91);
                    gradeService.saveGrade(s4.getId(), "Quarterly Exam", 60, 65, 58, 50, 62, s4.getOptionalSubject1(), 60, s4.getOptionalSubject2(), 55);
                    gradeService.saveGrade(s5.getId(), "Quarterly Exam", 80, 85, 82, 78, 80, s5.getOptionalSubject1(), 84, s5.getOptionalSubject2(), 79);

                    LocalDate yesterday = LocalDate.now().minusDays(1);
                    attendanceRepository.save(Attendance.builder().student(s1).date(yesterday).status(Attendance.Status.PRESENT).markedBy("teacher1").build());
                    attendanceRepository.save(Attendance.builder().student(s2).date(yesterday).status(Attendance.Status.PRESENT).markedBy("teacher1").build());
                    attendanceRepository.save(Attendance.builder().student(s3).date(yesterday).status(Attendance.Status.PRESENT).markedBy("teacher1").build());
                    attendanceRepository.save(Attendance.builder().student(s4).date(yesterday).status(Attendance.Status.ABSENT).markedBy("teacher1").build());
                    attendanceRepository.save(Attendance.builder().student(s5).date(yesterday).status(Attendance.Status.HALF).markedBy("teacher1").build());

                    LocalDate today = LocalDate.now();
                    Homework hw = Homework.builder()
                            .date(today).classGrade("Class 10").section("A")
                            .english("Read Chapter 4 and write a summary (150 words).")
                            .tamil("திருக்குறள் அதிகாரம் 5 மனப்பாடம் செய்யவும்.")
                            .maths("Solve exercises 3.2 (problems 1 to 10) in Algebra.")
                            .science("Draw the clean block diagram of Human Heart and label parts.")
                            .socialScience("Mark all major harbors on the Indian outline map.")
                            .optional1("Write a Java Program to print first N Fibonacci numbers.")
                            .optional2("Write 10 sentences about your favorite festival in Hindi.")
                            .updatedBy("teacher1").build();
                    homeworkRepository.save(hw);

                    // Timetables and Exam timetables
                    seedTimetableAndExams("Class 10", "A");

                    feeRepository.save(Fee.builder().student(s1).academicYear("2026").totalAmount(35000.0).paidAmount(25000.0).dueDate("2026-06-30").status("PARTIAL").build());
                    feeRepository.save(Fee.builder().student(s2).academicYear("2026").totalAmount(35000.0).paidAmount(35000.0).dueDate("2026-06-30").status("PAID").build());
                    feeRepository.save(Fee.builder().student(s3).academicYear("2026").totalAmount(35000.0).paidAmount(35000.0).dueDate("2026-06-30").status("PAID").build());
                    feeRepository.save(Fee.builder().student(s4).academicYear("2026").totalAmount(35000.0).paidAmount(10000.0).dueDate("2026-06-30").status("PARTIAL").build());
                    feeRepository.save(Fee.builder().student(s5).academicYear("2026").totalAmount(35000.0).paidAmount(0.0).dueDate("2026-06-30").status("UNPAID").build());

                } else {
                    // Seed 2 students dynamically for other classes/sections
                    String prefix = className.replace("Class ", "C").replace("Grade", "G").replace(" ", "");
                    String sectionPrefix = prefix + sec;

                    Student stu1 = Student.builder()
                            .name(className + " Sec " + sec + " Student One")
                            .rollNumber(sectionPrefix + "-01")
                            .email(sectionPrefix.toLowerCase() + ".student1@school.com")
                            .classGrade(className)
                            .section(sec)
                            .dob(getDOBForClass(className))
                            .gender("Male")
                            .parentName("Parent One")
                            .parentPhone("9876543210")
                            .address("Vedic Academy Campus, Chennai")
                            .optionalSubject1("Computer Science")
                            .optionalSubject2("Hindi")
                            .build();
                    stu1 = studentRepository.save(stu1);
                    allStudents.add(stu1);

                    Student stu2 = Student.builder()
                            .name(className + " Sec " + sec + " Student Two")
                            .rollNumber(sectionPrefix + "-02")
                            .email(sectionPrefix.toLowerCase() + ".student2@school.com")
                            .classGrade(className)
                            .section(sec)
                            .dob(getDOBForClass(className))
                            .gender("Female")
                            .parentName("Parent Two")
                            .parentPhone("9876543211")
                            .address("Vedic Academy Campus, Chennai")
                            .optionalSubject1("Computer Science")
                            .optionalSubject2("Sanskrit")
                            .build();
                    stu2 = studentRepository.save(stu2);
                    allStudents.add(stu2);

                    // Create user accounts for this class's students
                    userRepository.save(User.builder()
                            .username("student_" + sectionPrefix.toLowerCase() + "_1")
                            .password("student123")
                            .role(User.Role.STUDENT)
                            .studentId(stu1.getId())
                            .build());
                    userRepository.save(User.builder()
                            .username("student_" + sectionPrefix.toLowerCase() + "_2")
                            .password("student123")
                            .role(User.Role.STUDENT)
                            .studentId(stu2.getId())
                            .build());

                    // Seed marks & grades
                    gradeService.saveGrade(stu1.getId(), "Quarterly Exam", 80, 80, 85, 90, 80, "Computer Science", 85, "Hindi", 80);
                    gradeService.saveGrade(stu2.getId(), "Quarterly Exam", 90, 90, 85, 95, 90, "Computer Science", 90, "Sanskrit", 80);

                    // Seed yesterday attendance
                    LocalDate yesterday = LocalDate.now().minusDays(1);
                    attendanceRepository.save(Attendance.builder().student(stu1).date(yesterday).status(Attendance.Status.PRESENT).markedBy("teacher1").build());
                    attendanceRepository.save(Attendance.builder().student(stu2).date(yesterday).status(Attendance.Status.ABSENT).markedBy("teacher1").build());

                    // Seed today's homework
                    LocalDate today = LocalDate.now();
                    Homework hw = Homework.builder()
                            .date(today).classGrade(className).section(sec)
                            .english("Write a 1-page essay about your hobbies.")
                            .tamil("தமிழ் எழுத்து பயிற்சி 2 பக்கங்கள் எழுதவும்.")
                            .maths("Complete simple math problems.")
                            .science("Review solar system planets and write their names.")
                            .socialScience("List 5 major states of India and their capitals.")
                            .optional1("Revise basic computational thinking principles.")
                            .optional2("Write 5 words in optional language and translate them.")
                            .updatedBy("teacher1").build();
                    homeworkRepository.save(hw);

                    // Timetables and Exam timetables
                    seedTimetableAndExams(className, sec);

                    // Seed tuition fees
                    feeRepository.save(Fee.builder().student(stu1).academicYear("2026").totalAmount(35000.0).paidAmount(15000.0).dueDate("2026-06-30").status("PARTIAL").build());
                    feeRepository.save(Fee.builder().student(stu2).academicYear("2026").totalAmount(35000.0).paidAmount(35000.0).dueDate("2026-06-30").status("PAID").build());
                }
            }
        }

        System.out.println(">>> SEEDING COMPLETE! ALL SYSTEMS ARE POPULATED!");
    }

    private String getDOBForClass(String className) {
        int age = 10;
        if (className.equals("LKG")) age = 4;
        else if (className.equals("UKG")) age = 5;
        else {
            try {
                int classNum = Integer.parseInt(className.replaceAll("[^0-9]", ""));
                age = classNum + 5;
            } catch (Exception e) {}
        }
        int birthYear = 2026 - age;
        return birthYear + "-06-15";
    }

    private void seedTimetableAndExams(String classGrade, String section) {
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
        String[][] subjectsGrid = {
            {"Maths", "Tamil", "English", "Science", "Library", "Social Science", "CS"},
            {"English", "Maths", "Tamil", "Social Science", "Science", "PT", "Hindi/Sanskrit"},
            {"Science", "English", "Maths", "Tamil", "CS", "Social Science", "Music"},
            {"Tamil", "Science", "Social Science", "English", "Maths", "Hindi/Sanskrit", "Arts"},
            {"Maths", "Tamil", "Science", "English", "PT", "CS", "Social Science"}
        };

        for (int i = 0; i < days.length; i++) {
            Timetable tt = Timetable.builder()
                    .classGrade(classGrade)
                    .section(section)
                    .dayOfWeek(days[i])
                    .period1(subjectsGrid[i][0])
                    .period2(subjectsGrid[i][1])
                    .period3(subjectsGrid[i][2])
                    .period4(subjectsGrid[i][3])
                    .period5(subjectsGrid[i][4])
                    .period6(subjectsGrid[i][5])
                    .period7(subjectsGrid[i][6])
                    .build();
            timetableRepository.save(tt);
        }

        LocalDate examStart = LocalDate.now().plusWeeks(2);
        examTimetableRepository.save(ExamTimetable.builder().classGrade(classGrade).examName("Annual Exam").subject("Tamil").examDate(examStart).examTime("09:30 AM - 12:30 PM").build());
        examTimetableRepository.save(ExamTimetable.builder().classGrade(classGrade).examName("Annual Exam").subject("English").examDate(examStart.plusDays(1)).examTime("09:30 AM - 12:30 PM").build());
        examTimetableRepository.save(ExamTimetable.builder().classGrade(classGrade).examName("Annual Exam").subject("Maths").examDate(examStart.plusDays(2)).examTime("09:30 AM - 12:30 PM").build());
        examTimetableRepository.save(ExamTimetable.builder().classGrade(classGrade).examName("Annual Exam").subject("Science").examDate(examStart.plusDays(3)).examTime("09:30 AM - 12:30 PM").build());
        examTimetableRepository.save(ExamTimetable.builder().classGrade(classGrade).examName("Annual Exam").subject("Social Science").examDate(examStart.plusDays(4)).examTime("09:30 AM - 12:30 PM").build());
    }
}
