package com.screen.orders.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapSeeder implements ApplicationRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${application.security.initial-admin.email:}")
  private String initialAdminEmail;

  @Value("${application.security.initial-admin.password:}")
  private String initialAdminPassword;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userRepository.existsByRole(Role.ADMIN)) {
      return;
    }

    if (initialAdminEmail == null || initialAdminEmail.isBlank()
        || initialAdminPassword == null || initialAdminPassword.isBlank()) {
      log.warn("No ADMIN user exists in orders_db and APP_INITIAL_ADMIN_EMAIL / APP_INITIAL_ADMIN_PASSWORD are not set; skipping initial ADMIN bootstrap.");
      return;
    }

    String email = initialAdminEmail.trim();
    var existingUser = userRepository.findByEmail(email);
    if (existingUser.isPresent()) {
      User user = existingUser.get();
      user.setRole(Role.ADMIN);
      user.setPassword(passwordEncoder.encode(initialAdminPassword));
      userRepository.save(user);
      log.info("Promoted existing user '{}' to initial ADMIN.", email);
      return;
    }

    User initialAdmin = User.builder()
        .firstname("System")
        .lastname("Admin")
        .email(email)
        .password(passwordEncoder.encode(initialAdminPassword))
        .role(Role.ADMIN)
        .build();
    userRepository.save(initialAdmin);
    log.info("Seeded initial ADMIN account '{}'.", email);
  }
}
