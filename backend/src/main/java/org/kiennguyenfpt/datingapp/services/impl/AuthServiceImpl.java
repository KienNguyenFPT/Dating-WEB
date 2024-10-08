package org.kiennguyenfpt.datingapp.services.impl;

import org.kiennguyenfpt.datingapp.entities.User;
import org.kiennguyenfpt.datingapp.enums.UserStatus;
import org.kiennguyenfpt.datingapp.exceptions.InvalidEmailException;
import org.kiennguyenfpt.datingapp.responses.CommonResponse;
import org.kiennguyenfpt.datingapp.security.JwtUtil;
import org.kiennguyenfpt.datingapp.services.AuthService;
import org.kiennguyenfpt.datingapp.services.UserService;
import org.kiennguyenfpt.datingapp.utils.PasswordUtil;
import org.kiennguyenfpt.datingapp.validation.EmailValidator;
import org.kiennguyenfpt.datingapp.validation.PasswordValidator;
import org.kiennguyenfpt.datingapp.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;


@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailServiceImpl emailService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(BCryptPasswordEncoder passwordEncoder, EmailServiceImpl emailService, UserService userService, JwtUtil jwtUtil) {
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public User register(String email) {
        validateEmail(email);
        checkEmailExists(email);

        User user = createUser(email);
        String randomPassword = PasswordUtil.generateRandomPassword();
        user.setPasswordHash(passwordEncoder.encode(randomPassword));

        try {
            User savedUser = userService.save(user);
            sendEmail(user, randomPassword);
            return savedUser;
        } catch (Exception e) {
            logger.error("Error during registration: {}", e.getMessage(), e);
            throw new RuntimeException("Error during registration: " + e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<CommonResponse<String>> login(String email, String password) {
        CommonResponse<String> response = new CommonResponse<>();
        User user = userService.findByEmail(email);
        if (user != null && passwordEncoder.matches(password, user.getPasswordHash())) {
            String token = jwtUtil.generateToken(email, user.getUserId());
            logger.info("User logged in: {}", email);

            if (user.isFirstLogin()) {
                user.setFirstLogin(false);
                user.setLoginCount(user.getLoginCount() + 1);
                userService.save(user);
                response.setStatus(HttpStatus.OK.value());
                response.setMessage("First login");
                response.setData(token);
                return ResponseEntity.ok(response);
            } else if (user.getLoginCount() == 1) {
                user.setLoginCount(user.getLoginCount() + 1);
                userService.save(user);
                response.setStatus(HttpStatus.OK.value());
                response.setMessage("Second login");
                response.setData(token);
                return ResponseEntity.ok(response);
            } else {
                user.setLoginCount(user.getLoginCount() + 1);
                userService.save(user);
                response.setStatus(HttpStatus.OK.value());
                response.setMessage("Login successful");
                response.setData(token);
                return ResponseEntity.ok(response);
            }
        }
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setMessage("Invalid email or password");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }



    @Override
    public User forgotPassword(String email) {
        User user = userService.findByEmail(email);
        if (user == null) {
            throw new InvalidEmailException("Email not found.");
        }
        String randomPassword = PasswordUtil.generateRandomPassword();
        user.setPasswordHash(passwordEncoder.encode(randomPassword));
        userService.save(user);
        emailService.sendEmail(user.getEmail(), "Password Reset", "Your new password is: " + randomPassword);
        return user;
    }

    @Override
    public User changePassword(String email, String oldPassword, String newPassword) {
        logger.info("Attempting to change password for email: {}", email);

        ValidationResult emailValidationResult = EmailValidator.validate(email);
        if (!emailValidationResult.valid()) {
            logger.warn("Invalid email format for email: {}", email);
            throw new IllegalArgumentException(emailValidationResult.message());
        }

        ValidationResult passwordValidationResult = PasswordValidator.validate(newPassword);
        if (!passwordValidationResult.valid()) {
            logger.warn("Invalid new password format for email: {}", email);
            throw new IllegalArgumentException(passwordValidationResult.message());
        }

        User user = userService.findByEmail(email);
        if (user == null) {
            throw new InvalidEmailException("Email not found.");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid old password.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);
        User updatedUser = userService.save(user);
        logger.info("Password changed successfully for email: {}", email);
        return updatedUser;
    }

    @Override
    public void validateEmail(String email) {
        ValidationResult result = EmailValidator.validate(email);
        if (!result.valid()) {
            throw new IllegalArgumentException(result.message());
        }
    }

    private void checkEmailExists(String email) {
        if (userService.findByEmail(email) != null) {
            logger.error("Email already exists: {}", email);
            throw new IllegalArgumentException("Email already exists!");
        }
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        user.setFirstLogin(true);
        return user;
    }

    private void sendEmail(User user, String randomPassword) {
        emailService.sendEmail(user.getEmail(), "Your Temporary Password from our dating system", "Your temporary password is: " + randomPassword);
    }
}
