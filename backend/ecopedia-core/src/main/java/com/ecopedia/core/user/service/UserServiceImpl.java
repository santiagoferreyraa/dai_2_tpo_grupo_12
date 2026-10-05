package com.ecopedia.core.user.service;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.user.domain.*;
import com.ecopedia.core.vehicle.domain.VehicleService;
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
    private final VehicleService vehicleService;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            VehicleService vehicleService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
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

    @Override
    @Transactional(readOnly = true)
    public AuthToken authenticate(Credentials credentials) {
        User user = userRepository
                .findByEmail(credentials.email())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

        if (!user.isActive()) {
            throw new IllegalArgumentException("El usuario está dado de baja");
        }

        if (!passwordEncoder.matches(credentials.rawPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

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
