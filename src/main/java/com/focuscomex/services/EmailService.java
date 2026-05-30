package com.focuscomex.services;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class EmailService {

    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.properties.mail.from}")
    private String from;

    public void send(String to, String subject, String htmlBody, Map<String, Resource> inlines, List<File> attachments) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            
            // Establecer el HTML primero
            helper.setText(htmlBody, true);
            
            // Agregar otros recursos inline si existen
            if (inlines != null) {
                for (Map.Entry<String, Resource> inline : inlines.entrySet()) {
                    helper.addInline(inline.getKey(), inline.getValue());
                }
            }
            
            // Agregar adjuntos si existen
            if (attachments != null) {
                for (File file : attachments) {
                    if (file != null && file.exists()) {
                    	String fileName = file.getName();
        				if(fileName.contains("__")) {
        					fileName = fileName.substring(fileName.lastIndexOf("__")+2);
        				}
                        helper.addAttachment(fileName, file);
                    }
                }
            }
            
            mailSender.send(message);
            log.debug("Correo enviado exitosamente a: " + to);
        } catch (Exception e) {
            log.error("Error enviando correo a: " + to + ": " + e.getMessage());
            throw new RuntimeException("Error enviando correo: " + e.getMessage(), e);
        }
    }

}