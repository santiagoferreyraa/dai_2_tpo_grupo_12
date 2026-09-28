package com.ecopedia.core.user.service;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.user.domain.*;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginAttempts loginAttempts;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            LoginAttempts loginAttempts) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.loginAttempts = loginAttempts;
    }

    @Override
    public User register(RegistrationData data) {
        if (userRepository.findByEmail(data.email()).isPresent()) {
            throw new IllegalArgumentException("El email ya está registrado: " + data.email());
        }

        User user = new User();
        user.setEmail(data.email());
        user.setPasswordHash(passwordEncoder.encode(data.rawPassword()));
        user.setFullName(data.fullName());
        user.setRole(data.role() != null ? data.role() : Role.CONDUCTOR);
        user.setActive(true);

        return userRepository.save(user);
    }

    /*
     * Tres cosas, en este orden:
     *
     * 1. Si la cuenta está bloqueada por intentos fallidos, se rechaza sin mirar la contraseña.
     *    Ver LoginAttempts.
     * 2. Mail inexistente y contraseña equivocada dan el mismo mensaje y cuentan igual como fallo:
     *    no se le dice a nadie qué cuentas existen.
     * 3. La baja se informa recién DESPUÉS de verificar la contraseña. Antes se informaba primero,
     *    y cualquiera averiguaba qué cuentas estaban dadas de baja con solo escribir el mail.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthToken authenticate(Credentials credentials) {
        loginAttempts.lockRemaining(credentials.email()).ifPresent(remaining -> {
            throw new TooManyLoginAttemptsException(remaining);
        });

        User user = userRepository.findByEmail(credentials.email()).orElse(null);

        if (user == null || !passwordEncoder.matches(credentials.rawPassword(), user.getPasswordHash())) {
            loginAttempts.recordFailure(credentials.email());
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        if (!user.isActive()) {
            throw new IllegalArgumentException("El usuario está dado de baja");
        }

        loginAttempts.recordSuccess(credentials.email());

        String token = tokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        long expiresInSeconds = tokenProvider.getExpirationMs() / 1000;

        return new AuthToken(token, user.getId(), user.getEmail(), user.getRole(), expiresInSeconds);
    }

    @Override
    @Transactional(readOnly = true)
    public User getProfile(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));
    }

    @Override
    @Transactional(readOnly = true)
    public User getProfileByEmail(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + email));
    }

    @Override
    public User updateProfile(Long userId, ProfileData data) {
        User user = getProfile(userId);
        if (data.fullName() != null && !data.fullName().isBlank()) {
            user.setFullName(data.fullName());
        }
        return userRepository.save(user);
    }

    @Override
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getProfile(userId);

        /*
         * La actual se compara con el hash guardado, igual que en el login. Si no coincide, el
         * mensaje no dice cuál de las dos falló: eso sería contarle a quien está probando si el
         * usuario existe.
         */
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public User updateRole(Long userId, Role newRole) {
        User user = getProfile(userId);
        user.setRole(newRole);
        return userRepository.save(user);
    }

    @Override
    public void deactivateUser(Long userId) {
        User user = getProfile(userId);
        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    public void activateUser(Long userId) {
        User user = getProfile(userId);
        user.setActive(true);
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> listUsers(Role roleFilter) {
        if (roleFilter != null) {
            return userRepository.findByRole(roleFilter);
        }
        return userRepository.findAll();
    }
}
