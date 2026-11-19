package com.screen.orders.user;

import com.screen.orders.user.dto.UpdateUserRoleRequest;
import com.screen.orders.user.dto.UserSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

  private final UserService userService;

  @GetMapping
  public ResponseEntity<List<UserSummaryResponse>> listUsers() {
    return ResponseEntity.ok(userService.findAllUsers());
  }

  @PatchMapping("/{id}/role")
  public ResponseEntity<UserSummaryResponse> updateUserRole(
      @PathVariable Integer id,
      @RequestBody UpdateUserRoleRequest request,
      Principal connectedUser
  ) {
    return ResponseEntity.ok(userService.updateUserRole(id, request, connectedUser));
  }
}
