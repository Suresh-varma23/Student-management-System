package com.school.sms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmsApplication.class, args);
        System.out.println("====================================================================");
        System.out.println("  STUDENT MANAGEMENT SYSTEM IS UP AND RUNNING LOCALLY!");
        System.out.println("  Access: http://localhost:8080");
        System.out.println("  H2 Console (if using in-memory H2): http://localhost:8080/h2-console");
        System.out.println("====================================================================");
    }
}
