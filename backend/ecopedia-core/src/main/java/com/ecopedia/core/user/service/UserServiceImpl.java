package com.ecopedia.core.user.service;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.shared.ResourceNotFoundException;
import com.ecopedia.core.user.domain.*;
import com.ecopedia.core.vehicle.domain.VehicleService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginAttempts loginAttempts;
    private final VehicleService vehicleService;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            LoginAttempts loginAttempts,
            VehicleService vehicleService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.loginAttempts = loginAttempts;
        this.vehicleService = vehicleService;
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
     * 3. Una cuenta dada de baja también recibe ese mismo mensaje, aunque la contraseña sea la
     *    correcta. Antes tenía uno propio, y con él cualquiera averiguaba que el mail estaba
     *    registrado. Al dueño legítimo no le cambia nada: no puede entrar de ninguna de las dos
     *    formas, y la reactivación la hace un ADMIN.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthToken authenticate(Credentials credentials) {
        loginAttempts.lockRemaining(credentials.email()).ifPresent(remaining -> {
            throw new TooManyLoginAttemptsException(remaining);
        });

        User user = userRepository.findByEmail(credentials.email()).orElse(null);

        if (user == null
                || !passwordEncoder.matches(credentials.rawPassword(), user.getPasswordHash())
                || !user.isActive()) {
            loginAttempts.recordFailure(credentials.email());
            throw new IllegalArgumentException("Credenciales inválidas");
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
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));
    }

    @Override
    @Transactional(readOnly = true)
    public User getProfileByEmail(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));
    }

    /**
     * <p><b>El auto se resuelve contra el catálogo antes de guardarse, y no se confía en el id
     * que llegó.</b> Guardarlo derecho dejaría que la base rechazara la clave foránea, y eso
     * llega al usuario como un 500 sin explicación; peor, un id de un modelo dado de baja sí
     * pasaría la clave foránea y quedaría elegido un auto que ya no se ofrece. El catálogo
     * contesta las dos cosas de una.
     *
     * <p>El nombre sigue siendo un parche —un nulo o un blanco no lo borran, porque la columna
     * es obligatoria y un usuario sin nombre no es un estado válido—, mientras que el auto es un
     * reemplazo. La asimetría no es un descuido: está explicada en {@link ProfileData}.
     */
    @Override
    public User updateProfile(Long userId, ProfileData data) {
        User user = getProfile(userId);
        if (data.fullName() != null && !data.fullName().isBlank()) {
            user.setFullName(data.fullName());
        }
        user.setVehicleModel(data.vehicleModelId() == null ? null : vehicleService.getModel(data.vehicleModelId()));
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

    @Override
    public User updateRole(Long userId, Role newRole, AdminConfirmation confirmation) {
        User user = getProfile(userId);
        Long adminId = confirm(user, confirmation, "cambiar el rol");
        user.setRole(newRole);
        User saved = userRepository.save(user);
        log.info("El ADMIN {} cambió el rol del usuario {} a {}", adminId, userId, newRole);
        return saved;
    }

    @Override
    public void deactivateUser(Long userId, AdminConfirmation confirmation) {
        User user = getProfile(userId);
        Long adminId = confirm(user, confirmation, "dar de baja");
        user.setActive(false);
        userRepository.save(user);
        log.info("El ADMIN {} dio de baja al usuario {}", adminId, userId);
    }

    /*
     * Las dos reglas de las bajas y los cambios de rol (Manuel, 02/11), en este orden:
     *
     * 1. Una cuenta ADMIN no se toca desde la aplicación, tampoco la propia: se hace por la base.
     *    Va primero porque no depende de la contraseña, y así un pedido contra un ADMIN no cuenta
     *    como intento fallido.
     * 2. El ADMIN que actúa confirma con SU contraseña. Usa el mismo contador que el login: con
     *    cinco equivocadas seguidas queda bloqueado, para que un token robado no sirva para probar
     *    contraseñas acá. El bloqueo frena también su login, a propósito: es la misma contraseña.
     *
     * Los rechazos dejan un WARN con ids, nunca con mails (DEF-03).
     */
    private Long confirm(User target, AdminConfirmation confirmation, String action) {
        User admin = getProfileByEmail(confirmation.adminEmail());

        if (target.getRole() == Role.ADMIN) {
            log.warn(
                    "Rechazado: el ADMIN {} quiso {} a la cuenta ADMIN {}, que solo se cambia por la base",
                    admin.getId(),
                    action,
                    target.getId());
            throw new AdminAccountProtectedException();
        }

        loginAttempts.lockRemaining(admin.getEmail()).ifPresent(remaining -> {
            throw new TooManyLoginAttemptsException(remaining);
        });

        String password = confirmation.password();
        if (password == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            loginAttempts.recordFailure(admin.getEmail());
            log.warn(
                    "Rechazado: contraseña equivocada del ADMIN {} al {} al usuario {}",
                    admin.getId(),
                    action,
                    target.getId());
            throw new AdminPasswordRejectedException();
        }
        loginAttempts.recordSuccess(admin.getEmail());
        return admin.getId();
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
