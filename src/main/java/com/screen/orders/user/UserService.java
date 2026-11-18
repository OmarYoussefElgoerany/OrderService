package com.screen.orders.user;

import com.screen.orders.token.TokenRepository;
import com.screen.orders.token.TokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository repository;
    private final TokenRepository tokenRepository;

    @Transactional
    public void changePassword(ChangePasswordRequest request, Principal connectedUser) {

        var user = (User) ((UsernamePasswordAuthenticationToken) connectedUser).getPrincipal();

        // check if the current password is correct
        if (request.getCurrentPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong password");
        }
        // check if the two new passwords are the same
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()
                || !request.getNewPassword().equals(request.getConfirmationPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password are not the same");
        }

        // update the password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // save the new password
        repository.save(user);
        tokenRepository.findByUser_IdAndTokenType(user.getId(), TokenType.REFRESH).ifPresent(session -> {
            session.setExpired(true);
            session.setRevoked(true);
            session.setPreviousTokenHash(null);
            session.setRotatedAt(null);
            tokenRepository.save(session);
        });
    }
}
