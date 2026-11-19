package com.screen.orders.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.screen.orders.auth.dto.RegisterRequest;
import com.screen.orders.common.security.SessionRevocationHandler;
import com.screen.orders.user.dto.UpdateUserRoleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserAndSeederTest {

  @Mock
  private UserRepository userRepository;
  @Mock
  private SessionRevocationHandler sessionRevocationHandler;

  private PasswordEncoder passwordEncoder;
  private UserService userService;
  private AdminUserController adminUserController;
  private AdminBootstrapSeeder seeder;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder();
    userService = new UserService(passwordEncoder, userRepository, sessionRevocationHandler, Mappers.getMapper(UserMapper.class));
    adminUserController = new AdminUserController(userService);
    seeder = new AdminBootstrapSeeder(userRepository, passwordEncoder);
  }

  @Test
  void registerRequestIgnoresUnexpectedRolePropertyInJson() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String payload = """
        {
          "firstname": "Eve",
          "lastname": "Mallory",
          "email": "eve@example.com",
          "password": "secret",
          "role": "ADMIN"
        }
        """;
    RegisterRequest parsed = mapper.readValue(payload, RegisterRequest.class);
    assertEquals("eve@example.com", parsed.getEmail());
    assertEquals("Eve", parsed.getFirstname());
  }

  @Test
  void seederCreatesInitialAdminWhenNoAdminExists() {
    ReflectionTestUtils.setField(seeder, "initialAdminEmail", "admin@orders.local");
    ReflectionTestUtils.setField(seeder, "initialAdminPassword", "StrongAdminPass!");

    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
    when(userRepository.findByEmail("admin@orders.local")).thenReturn(Optional.empty());

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();
    assertEquals("admin@orders.local", saved.getEmail());
    assertEquals(Role.ADMIN, saved.getRole());
    assertTrue(passwordEncoder.matches("StrongAdminPass!", saved.getPassword()));
  }

  @Test
  void seederSkipsWhenAdminAlreadyExists() {
    ReflectionTestUtils.setField(seeder, "initialAdminEmail", "admin@orders.local");
    ReflectionTestUtils.setField(seeder, "initialAdminPassword", "StrongAdminPass!");

    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

    seeder.run(new DefaultApplicationArguments());

    verify(userRepository, never()).save(any());
  }

  @Test
  void adminCanPromoteUserToManagementAndRevokesRefreshTokenSession() {
    User admin = User.builder().id(1).email("admin@orders.local").role(Role.ADMIN).build();
    User target = User.builder().id(2).firstname("Bob").lastname("Smith").email("bob@orders.local").role(Role.USER).build();

    when(userRepository.findById(2)).thenReturn(Optional.of(target));
    when(userRepository.save(target)).thenReturn(target);

    Principal principal = new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
    var response = adminUserController.updateUserRole(2, new UpdateUserRoleRequest(Role.MANAGEMENT), principal);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(Role.MANAGEMENT, response.getBody().role());
    verify(sessionRevocationHandler).revokeUserSession(2);
  }

  @Test
  void adminCannotDemoteOwnAccount() {
    User admin = User.builder().id(1).email("admin@orders.local").role(Role.ADMIN).build();
    when(userRepository.findById(1)).thenReturn(Optional.of(admin));

    Principal principal = new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> adminUserController.updateUserRole(1, new UpdateUserRoleRequest(Role.USER), principal)
    );

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verify(userRepository, never()).save(any());
  }

  @Test
  void adminCannotDemoteLastRemainingAdmin() {
    User admin1 = User.builder().id(1).email("admin1@orders.local").role(Role.ADMIN).build();
    User admin2 = User.builder().id(2).email("admin2@orders.local").role(Role.ADMIN).build();

    when(userRepository.findById(2)).thenReturn(Optional.of(admin2));
    when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

    Principal principal = new UsernamePasswordAuthenticationToken(admin1, null, admin1.getAuthorities());

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> adminUserController.updateUserRole(2, new UpdateUserRoleRequest(Role.MANAGEMENT), principal)
    );

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    verify(userRepository, never()).save(any());
  }

  @Test
  void listUsersReturnsSummariesWithRoles() {
    User admin = User.builder().id(1).firstname("Ada").lastname("Admin").email("ada@orders.local").role(Role.ADMIN).build();
    User manager = User.builder().id(2).firstname("Max").lastname("Mgr").email("max@orders.local").role(Role.MANAGEMENT).build();
    when(userRepository.findAll()).thenReturn(List.of(admin, manager));

    var response = adminUserController.listUsers();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(2, response.getBody().size());
    assertEquals(Role.ADMIN, response.getBody().get(0).role());
    assertEquals(Role.MANAGEMENT, response.getBody().get(1).role());
  }
}
