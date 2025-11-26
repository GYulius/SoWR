package com.cruise.recommender.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

/**
 * Service for sending emails
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.username:}")
    private String fromEmail;
    
    @Value("${spring.mail.from:${spring.mail.username}}")
    private String fromAddress;
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    
    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;
    
    @Value("${app.name:Social Web Recommender}")
    private String appName;
    
    /**
     * Check if email service is configured
     */
    public boolean isEmailConfigured() {
        return fromEmail != null && !fromEmail.isEmpty();
    }
    
    /**
     * Send verification code email
     */
    @Async
    public void sendVerificationCode(String toEmail, String firstName, String verificationCode) {
        // Check if email is configured
        if (fromEmail == null || fromEmail.isEmpty()) {
            log.warn("Email service is not configured. Skipping verification code email to: {}. " +
                    "Please configure MAIL_USERNAME and MAIL_PASSWORD environment variables.", toEmail);
            log.warn("Verification code for {}: {}", toEmail, verificationCode);
            throw new RuntimeException("Email service is not configured");
        }
        
        try {
            log.info("Attempting to send verification code email to: {} from: {}", toEmail, fromEmail);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            // Use custom from address if configured, otherwise use username
            String from = (fromAddress != null && !fromAddress.isEmpty()) ? fromAddress : fromEmail;
            helper.setFrom(from, appName);
            helper.setTo(toEmail);
            helper.setSubject("Your Verification Code - " + appName);
            
            // Create HTML email content with verification code
            String htmlContent = buildVerificationCodeEmailHtml(firstName, verificationCode);
            
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Verification code email sent successfully to: {} from: {}", toEmail, from);
        } catch (MessagingException e) {
            log.error("Failed to send verification code email to: {} from: {}. Error: {}", 
                    toEmail, fromEmail, e.getMessage(), e);
            log.error("SMTP Configuration - Host: {}, Port: {}, Username: {}", 
                    System.getProperty("spring.mail.host"), 
                    System.getProperty("spring.mail.port"), 
                    fromEmail);
            throw new RuntimeException("Failed to send verification code email: " + e.getMessage(), e);
        } catch (UnsupportedEncodingException e) {
            log.error("Encoding error when sending verification code email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification code email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error when sending verification code email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification code email: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build HTML content for verification code email
     */
    private String buildVerificationCodeEmailHtml(String firstName, String verificationCode) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }" +
                ".content { background-color: #f9f9f9; padding: 30px; border-radius: 0 0 5px 5px; }" +
                ".code-box { background-color: #fff; border: 2px dashed #4CAF50; padding: 20px; text-align: center; margin: 20px 0; border-radius: 5px; }" +
                ".code { font-size: 32px; font-weight: bold; color: #4CAF50; letter-spacing: 5px; font-family: 'Courier New', monospace; }" +
                ".footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }" +
                ".warning { background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 10px; margin: 15px 0; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>" + appName + "</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<p>Hello " + firstName + ",</p>" +
                "<p>For security reasons, we need to verify your identity. A unique verification code has been sent to your registered email address.</p>" +
                "<div class='code-box'>" +
                "<p style='margin: 0 0 10px 0; color: #666;'>Your verification code is:</p>" +
                "<div class='code'>" + verificationCode + "</div>" +
                "</div>" +
                "<div class='warning'>" +
                "<p style='margin: 0;'><strong>Important:</strong> This code will expire in 10 minutes. Do not share this code with anyone.</p>" +
                "</div>" +
                "<p>Please enter this code in the verification popup window to complete your registration/login process.</p>" +
                "<p>If you did not request this code, please ignore this email or contact support if you have concerns.</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>&copy; 2024 " + appName + ". All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
    
    /**
     * Send email verification email
     */
    @Async
    public void sendVerificationEmail(String toEmail, String firstName, String token) {
        // Check if email is configured
        if (fromEmail == null || fromEmail.isEmpty()) {
            log.warn("Email service is not configured. Skipping verification email to: {}. " +
                    "Please configure MAIL_USERNAME and MAIL_PASSWORD environment variables.", toEmail);
            log.warn("Verification token for {}: {}", toEmail, token);
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, appName);
            helper.setTo(toEmail);
            helper.setSubject("Verify Your Email Address");
            
            // Build verification URL
            String verificationUrl = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path(contextPath)
                    .path("/auth/verify-email")
                    .queryParam("token", token)
                    .toUriString();
            
            // Create HTML email content
            String htmlContent = buildVerificationEmailHtml(firstName, verificationUrl);
            
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Verification email sent successfully to: {}", toEmail);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send verification email to: {}", toEmail, e);
            // Don't throw exception - allow registration to complete even if email fails
            log.warn("Registration completed but email verification failed. Token: {}", token);
        }
    }
    
    /**
     * Build HTML content for verification email
     */
    private String buildVerificationEmailHtml(String firstName, String verificationUrl) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }" +
                ".content { background-color: #f9f9f9; padding: 30px; border-radius: 0 0 5px 5px; }" +
                ".button { display: inline-block; padding: 12px 30px; background-color: #4CAF50; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; }" +
                ".button:hover { background-color: #45a049; }" +
                ".footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>Welcome to " + appName + "!</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<p>Hello " + firstName + ",</p>" +
                "<p>Thank you for registering with us. To complete your registration and activate your account, please verify your email address by clicking the button below:</p>" +
                "<div style='text-align: center;'>" +
                "<a href='" + verificationUrl + "' class='button'>Verify Email Address</a>" +
                "</div>" +
                "<p>If the button doesn't work, you can copy and paste the following link into your browser:</p>" +
                "<p style='word-break: break-all; color: #666;'>" + verificationUrl + "</p>" +
                "<p>This verification link will expire in 24 hours.</p>" +
                "<p>If you didn't create an account with us, please ignore this email.</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>&copy; 2024 " + appName + ". All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}

