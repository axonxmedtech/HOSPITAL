package com.hms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Hospital;
import com.hms.repository.HospitalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
public class PlatformHospitalUpdateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    public void testUpdateSpecificHospitalDetails() throws Exception {
        System.out.println("=================================================");
        System.out.println("RUNNING TARGETED HOSPITAL UPDATE DIAGNOSTIC...");

        String targetPublicId = "c1fc5852-b0be-4ab5-8741-b65e69351495";
        Hospital hospital = hospitalRepository.findByPublicId(targetPublicId).orElse(null);

        if (hospital == null) {
            System.out.println("HOSPITAL NOT FOUND IN DB: " + targetPublicId);
            return;
        }

        System.out.println("Found Hospital: " + hospital.getName() + " | Type: " + hospital.getType() + " | IsSingleDoctor: " + hospital.getIsSingleDoctor());

        // Perform the exact details update
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("name", hospital.getName() + " Updated");
        updateBody.put("adminEmail", "new_admin_email_" + System.currentTimeMillis() + "@test.com");
        updateBody.put("adminName", "New Admin Name");
        updateBody.put("reason", "Targeted diagnostic test");
        updateBody.put("isSingleDoctor", true);

        try {
            MvcResult result = mockMvc.perform(
                    put("/platform/hospitals/" + targetPublicId + "/details")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateBody))
            ).andReturn();

            System.out.println("Response Status: " + result.getResponse().getStatus());
            System.out.println("Response Content: " + result.getResponse().getContentAsString());
            
            if (result.getResolvedException() != null) {
                System.out.println("EXCEPTION ENCOUNTERED:");
                result.getResolvedException().printStackTrace();
            }
        } catch (Exception e) {
            System.out.println("Error performing request: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("=================================================");
    }
}
