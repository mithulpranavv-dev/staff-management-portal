package com.staffportal.staff_management_portal;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableScheduling
public class StaffManagementPortalApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(StaffManagementPortalApplication.class, args);
	}

}
