package com.focuscomex.services;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class MailOrchestratorService {
    private final TemplateService templateService;
    private final EmailService emailService;

    public void sendMailWithTemplate(String to, String subject, String templateName, Map<String, Object> model, Map<String, Resource> inlines, List<File> attachments) {
        String html = templateService.render(templateName, model);
        
        if(inlines == null) {
			inlines = new HashMap<>();
		}
        // Agregar logo desde resources como InputStreamSource
        try {
        	ClassPathResource logo = new ClassPathResource("templates/logo.png");
            if (logo.exists()) {
            	inlines.put("logo", logo);
            }
        } catch (Exception e) {
            log.error("Error agregando logo", e);
        }
        emailService.send(to, subject, html, inlines, attachments);
    }
}
