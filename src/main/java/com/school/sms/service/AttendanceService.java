package com.school.sms.service;

import com.school.sms.model.Attendance;
import com.school.sms.model.Student;
import com.school.sms.repository.AttendanceRepository;
import com.school.sms.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AttendanceService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private StudentRepository studentRepository;

    @org.springframework.beans.factory.annotation.Value("${twilio.account.sid:}")
    private String twilioSid;

    @org.springframework.beans.factory.annotation.Value("${twilio.auth.token:}")
    private String twilioAuthToken;

    @org.springframework.beans.factory.annotation.Value("${twilio.phone.number:}")
    private String twilioPhoneNumber;

    private String activeTwilioSid;
    private String activeTwilioAuthToken;
    private String activeTwilioPhoneNumber;

    private static final String CONFIG_FILE = "sms_config.properties";

    @jakarta.annotation.PostConstruct
    public void init() {
        // First, set defaults from application.properties
        activeTwilioSid = twilioSid;
        activeTwilioAuthToken = twilioAuthToken;
        activeTwilioPhoneNumber = twilioPhoneNumber;

        // Load overrides from file if present
        java.io.File file = new java.io.File(CONFIG_FILE);
        if (file.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                Properties props = new Properties();
                props.load(fis);
                String sid = props.getProperty("twilio.account.sid");
                String token = props.getProperty("twilio.auth.token");
                String phone = props.getProperty("twilio.phone.number");

                if (sid != null) activeTwilioSid = sid.trim();
                if (token != null) activeTwilioAuthToken = token.trim();
                if (phone != null) activeTwilioPhoneNumber = phone.trim();
                System.out.println("Loaded dynamic Twilio credentials from " + CONFIG_FILE);
            } catch (Exception e) {
                System.err.println("Failed to load dynamic Twilio properties: " + e.getMessage());
            }
        }
    }

    public synchronized void saveSmsConfig(String sid, String token, String phone) {
        if (sid != null) activeTwilioSid = sid.trim();
        if (token != null) activeTwilioAuthToken = token.trim();
        if (phone != null) activeTwilioPhoneNumber = phone.trim();

        Properties props = new Properties();
        props.setProperty("twilio.account.sid", activeTwilioSid);
        props.setProperty("twilio.auth.token", activeTwilioAuthToken);
        props.setProperty("twilio.phone.number", activeTwilioPhoneNumber);

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Dynamic Twilio SMS Config overriding application.properties");
            System.out.println("Saved dynamic Twilio credentials to " + CONFIG_FILE);
        } catch (Exception e) {
            System.err.println("Failed to save dynamic Twilio properties: " + e.getMessage());
        }
    }

    public String getActiveTwilioSid() {
        return activeTwilioSid;
    }

    public String getActiveTwilioAuthToken() {
        return activeTwilioAuthToken;
    }

    public String getActiveTwilioPhoneNumber() {
        return activeTwilioPhoneNumber;
    }

    /**
     * Sends a test SMS dynamically without overwriting the saved configuration.
     */
    public void sendTestSms(String sid, String token, String phone, String targetPhone, String message) throws Exception {
        if (sid == null || sid.trim().isEmpty() ||
            token == null || token.trim().isEmpty() ||
            phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Twilio Account SID, Auth Token, and Phone Number cannot be blank!");
        }

        // Initialize temporary Twilio session
        com.twilio.Twilio.init(sid.trim(), token.trim());

        // Format phone number
        String formattedPhone = targetPhone.trim();
        if (!formattedPhone.startsWith("+")) {
            if (formattedPhone.length() == 10) {
                formattedPhone = "+91" + formattedPhone; // default to +91 country code for 10-digit Indian numbers
            } else {
                formattedPhone = "+" + formattedPhone;
            }
        }

        com.twilio.rest.api.v2010.account.Message.creator(
                new com.twilio.type.PhoneNumber(formattedPhone),
                new com.twilio.type.PhoneNumber(phone.trim()),
                message
        ).create();
    }

    // In-memory list to store parental SMS notification logs so they can be viewed on the front-end dashboard
    private final List<Map<String, Object>> smsNotificationLogs = new CopyOnWriteArrayList<>();

    /**
     * Checks if attendance marking is currently open (Mon-Sat, 9:00 AM - 5:00 PM)
     */
    public boolean isAttendanceWindowOpen(LocalDateTime dateTime, boolean bypassCheck) {
        if (bypassCheck) {
            return true;
        }
        DayOfWeek day = dateTime.getDayOfWeek();
        LocalTime time = dateTime.toLocalTime();

        // Closed on Sundays
        if (day == DayOfWeek.SUNDAY) {
            return false;
        }

        // Open Monday to Saturday from 9:00 AM to 5:00 PM (17:00)
        return !time.isBefore(LocalTime.of(9, 0)) && !time.isAfter(LocalTime.of(17, 0));
    }

    /**
     * Marks or updates attendance for a student, validating time limits and triggering SMS alerts if absent.
     */
    public Attendance markAttendance(Long studentId, Attendance.Status status, String markerUsername, boolean bypassTimeCheck) {
        LocalDateTime now = LocalDateTime.now();
        if (!isAttendanceWindowOpen(now, bypassTimeCheck)) {
            throw new IllegalStateException("Attendance marking is closed! Access is only permitted Monday to Saturday, between 9:00 AM and 5:00 PM.");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + studentId));

        LocalDate date = LocalDate.now();

        // Check if attendance already exists for this student today
        List<Attendance> existing = attendanceRepository.findByStudentAndDate(student, date);
        Attendance attendance;

        if (!existing.isEmpty()) {
            attendance = existing.get(0);
            attendance.setStatus(status);
            attendance.setMarkedBy(markerUsername);
            attendance.setMarkedAt(now);
        } else {
            attendance = Attendance.builder()
                    .student(student)
                    .date(date)
                    .status(status)
                    .markedBy(markerUsername)
                    .markedAt(now)
                    .build();
        }

        Attendance saved = attendanceRepository.save(attendance);

        // If the student is ABSENT, immediately trigger parent SMS alert!
        if (status == Attendance.Status.ABSENT) {
            sendParentSms(student);
        }

        return saved;
    }

    /**
     * Triggers immediate mock/real SMS alerts for absent students
     */
    private void sendParentSms(Student student) {
        String message = String.format("%s of class %s Section %s today absent to the class",
                student.getName(), student.getClassGrade(), student.getSection());
        
        // Print to backend system console
        System.out.println("==================================================");
        System.out.println("SMS ATTEMPT TO PARENT (" + student.getParentPhone() + ")");
        System.out.println("Message: \"" + message + "\"");
        System.out.println("==================================================");

        String dispatchStatus = "SENT (SIMULATOR)";

        // Try to send real SMS via Twilio if credentials are provided dynamically or in properties
        if (activeTwilioSid != null && !activeTwilioSid.trim().isEmpty() &&
            activeTwilioAuthToken != null && !activeTwilioAuthToken.trim().isEmpty() &&
            activeTwilioPhoneNumber != null && !activeTwilioPhoneNumber.trim().isEmpty()) {
            
            try {
                // Initialize Twilio client session
                com.twilio.Twilio.init(activeTwilioSid, activeTwilioAuthToken);
                
                // Format phone number. Twilio requires E.164 standard (+CountryCodeNumber)
                String formattedPhone = student.getParentPhone().trim();
                if (!formattedPhone.startsWith("+")) {
                    if (formattedPhone.length() == 10) {
                        formattedPhone = "+91" + formattedPhone; // default to +91 country code for 10-digit Indian numbers
                    } else {
                        formattedPhone = "+" + formattedPhone; // assume country code is present but missing '+'
                    }
                }
                
                com.twilio.rest.api.v2010.account.Message twilioMsg = com.twilio.rest.api.v2010.account.Message.creator(
                        new com.twilio.type.PhoneNumber(formattedPhone), // Destination phone number
                        new com.twilio.type.PhoneNumber(activeTwilioPhoneNumber), // From Twilio virtual number
                        message
                ).create();
                
                System.out.println("REAL SMS DISPATCHED SUCCESSFULLY VIA TWILIO! SID: " + twilioMsg.getSid());
                dispatchStatus = "SENT SUCCESS (REAL MOBILE)";
            } catch (Exception e) {
                System.err.println("FAILED TO DISPATCH REAL SMS VIA TWILIO: " + e.getMessage());
                dispatchStatus = "FAILED: " + e.getMessage();
            }
        } else {
            System.out.println("Twilio credentials not configured in application.properties or dynamic gateway settings. Simulated in-app dispatcher logging active.");
        }

        // Save log to show in UI dashboard
        Map<String, Object> log = new HashMap<>();
        log.put("id", UUID.randomUUID().toString());
        log.put("timestamp", LocalDateTime.now());
        log.put("studentName", student.getName());
        log.put("classSection", student.getClassGrade() + " - " + student.getSection());
        log.put("parentPhone", student.getParentPhone());
        log.put("message", message);
        log.put("status", dispatchStatus);
        smsNotificationLogs.add(0, log); // Add to the top
    }

    /**
     * Get parent SMS log history
     */
    public List<Map<String, Object>> getSmsLogs() {
        return smsNotificationLogs;
    }

    /**
     * Calculates present and absent students for a specific class on a specific date.
     */
    public Map<String, Object> calculateClassAttendanceStats(String classGrade, String section, LocalDate date) {
        List<Student> students = studentRepository.findByClassGradeAndSection(classGrade, section);
        if (students.isEmpty()) {
            return Map.of("present", 0, "absent", 0, "half", 0, "total", 0, "percentage", 0.0);
        }

        List<Long> studentIds = students.stream().map(Student::getId).toList();
        List<Attendance> attendanceRecords = attendanceRepository.findByStudentIdInAndDate(studentIds, date);

        int present = 0;
        int absent = 0;
        int half = 0;

        for (Attendance att : attendanceRecords) {
            if (att.getStatus() == Attendance.Status.PRESENT) {
                present++;
            } else if (att.getStatus() == Attendance.Status.ABSENT) {
                absent++;
            } else if (att.getStatus() == Attendance.Status.HALF) {
                half++;
            }
        }

        int totalMarked = present + absent + half;
        double presentPercentage = 0.0;
        if (totalMarked > 0) {
            // Half days count as 0.5 present
            double totalPresentWeight = present + (half * 0.5);
            presentPercentage = (totalPresentWeight / totalMarked) * 100.0;
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("classGrade", classGrade);
        stats.put("section", section);
        stats.put("totalStudents", students.size());
        stats.put("markedStudents", totalMarked);
        stats.put("present", present);
        stats.put("absent", absent);
        stats.put("half", half);
        stats.put("percentage", Math.round(presentPercentage * 10.0) / 10.0);

        return stats;
    }
}
