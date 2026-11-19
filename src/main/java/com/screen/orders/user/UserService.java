package com.screen.orders.user;

import com.screen.orders.common.security.SessionRevocationHandler;
import com.screen.orders.user.dto.ChangePasswordRequest;
import com.screen.orders.user.dto.UpdateUserRoleRequest;
import com.screen.orders.user.dto.UserSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

  private final PasswordEncoder passwordEncoder;
  private final UserRepository repository;
  private final SessionRevocationHandler sessionRevocationHandler;
  private final UserMapper userMapper;

  @Transactional
  public void changePassword(ChangePasswordRequest request, Principal connectedUser) {
    var user = extractAuthenticatedUser(connectedUser);

    if (request.getCurrentPassword() == null
        || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong password");
    }
    if (request.getNewPassword() == null || request.getNewPassword().isBlank()
        || !request.getNewPassword().equals(request.getConfirmationPassword())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password are not the same");
    }

    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
    repository.save(user);
    sessionRevocationHandler.revokeUserSession(user.getId());
  }

  @Transactional(readOnly = true)
  public List<UserSummaryResponse> findAllUsers() {
    return userMapper.toSummaryResponses(repository.findAll());
  }

  @Transactional
  public UserSummaryResponse updateUserRole(
      Integer targetUserId,
      UpdateUserRoleRequest request,
      Principal connectedUser
  ) {
    if (request == null || request.role() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
    }
    Role newRole = request.role();
    User currentAdmin = extractAuthenticatedUser(connectedUser);

    User targetUser = repository.findById(targetUserId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    // Guard (a): Prevent an ADMIN from demoting their own account
    if (currentAdmin.getId() != null
        && currentAdmin.getId().equals(targetUser.getId())
        && newRole != Role.ADMIN) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Admins cannot demote their own account"
      );
    }

    // Guard (b): Prevent demoting the last remaining ADMIN in the database
    if (targetUser.getRole() == Role.ADMIN
        && newRole != Role.ADMIN
        && repository.countByRole(Role.ADMIN) <= 1) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot demote the last remaining ADMIN"
      );
    }

    Role previousRole = targetUser.getRole();
    targetUser.setRole(newRole);
    User savedUser = repository.save(targetUser);

    // When a user's role changes, revoke their active refresh token family
    if (previousRole != newRole && savedUser.getId() != null) {
      sessionRevocationHandler.revokeUserSession(savedUser.getId());
    }

    return userMapper.toSummaryResponse(savedUser);
  }

  private static User extractAuthenticatedUser(Principal connectedUser) {
    if (connectedUser instanceof UsernamePasswordAuthenticationToken authToken
        && authToken.getPrincipal() instanceof User user) {
      return user;
    }
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
  }
}
