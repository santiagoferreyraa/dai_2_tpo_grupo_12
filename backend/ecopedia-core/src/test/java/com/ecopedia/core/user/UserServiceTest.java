package com.ecopedia.core.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.shared.ResourceNotFoundException;
import com.ecopedia.core.user.domain.*;
import com.ecopedia.core.user.service.LoginAttempts;
import com.ecopedia.core.user.service.UserServiceImpl;
import com.ecopedia.core.vehicle.domain.VehicleModel;
import com.ecopedia.core.vehicle.domain.VehicleService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private VehicleService vehicleService;

    @Spy
    private LoginAttempts loginAttempts = new LoginAttempts();

    @InjectMocks
    private UserServiceImpl userService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("conductor@ecopedia.com");
        mockUser.setPasswordHash("$2a$10$hashedpassword");
        mockUser.setFullName("Juan Conductor");
        mockUser.setRole(Role.CONDUCTOR);
        mockUser.setActive(true);
    }

    @Test
    void testRegisterUserSuccess() {
        when(userRepository.findByEmail("conductor@ecopedia.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$encodedhash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationData data = new RegistrationData("conductor@ecopedia.com", "secret123", "Juan Conductor", Role.CPO);
        User registered = userService.register(data);

        assertEquals("conductor@ecopedia.com", registered.getEmail());
        assertEquals("$2a$10$encodedhash", registered.getPasswordHash());
        assertEquals("Juan Conductor", registered.getFullName());
        assertEquals(Role.CPO, registered.getRole());
        assertTrue(registered.isActive());

        RegistrationData withDefaultRole = new RegistrationData("otro@ecopedia.com", "secret123", "Otro", null);
        when(userRepository.findByEmail("otro@ecopedia.com")).thenReturn(Optional.empty());
        User defaultRoleUser = userService.register(withDefaultRole);
        assertEquals(Role.CONDUCTOR, defaultRoleUser.getRole());
        assertTrue(defaultRoleUser.isActive());

        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void testRegisterUserDuplicateEmailThrowsException() {
        when(userRepository.findByEmail("conductor@ecopedia.com")).thenReturn(Optional.of(mockUser));

        RegistrationData data =
                new RegistrationData("conductor@ecopedia.com", "secret123", "Juan Conductor", Role.CONDUCTOR);

        assertThrows(IllegalArgumentException.class, () -> userService.register(data));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testAuthenticateSuccess() {
        when(userRepository.findByEmail("conductor@ecopedia.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("secret123", "$2a$10$hashedpassword")).thenReturn(true);
        when(tokenProvider.generateToken(1L, "conductor@ecopedia.com", Role.CONDUCTOR))
                .thenReturn("mocked.jwt.token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        Credentials credentials = new Credentials("conductor@ecopedia.com", "secret123");
        AuthToken token = userService.authenticate(credentials);

        assertNotNull(token);
        assertEquals("mocked.jwt.token", token.token());
        assertEquals("conductor@ecopedia.com", token.email());
        assertEquals(Role.CONDUCTOR, token.role());
    }

    @Test
    void testAuthenticateInvalidPasswordThrowsException() {
        when(userRepository.findByEmail("conductor@ecopedia.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hashedpassword")).thenReturn(false);

        Credentials credentials = new Credentials("conductor@ecopedia.com", "wrongpassword");

        assertThrows(IllegalArgumentException.class, () -> userService.authenticate(credentials));
    }

    @Test
    void testDeactivateUser() {
        User admin = new User();
        admin.setId(9L);
        admin.setEmail("admin@ecopedia.com");
        admin.setPasswordHash("$2a$10$adminhash");
        admin.setRole(Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(userRepository.findByEmail("admin@ecopedia.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("claveDelAdmin", "$2a$10$adminhash")).thenReturn(true);

        userService.deactivateUser(1L, new AdminConfirmation("admin@ecopedia.com", "claveDelAdmin"));

        assertFalse(mockUser.isActive());
        verify(userRepository, times(1)).save(mockUser);
    }

    @Test
    void testListUsersByRole() {
        when(userRepository.findByRole(Role.CONDUCTOR)).thenReturn(List.of(mockUser));

        List<User> conductors = userService.listUsers(Role.CONDUCTOR);

        assertEquals(1, conductors.size());
        assertEquals(Role.CONDUCTOR, conductors.get(0).getRole());
    }

    /*
     * Comprueba lo que hace el perfil propio: una consulta por email, no un listado que se
     * filtra después. Verificar que `findAll` no se llama es la mitad que importa —el mismo
     * resultado se obtenía trayendo el padrón entero, y esa versión pasaba cualquier test que
     * solo mirara el usuario devuelto—.
     */
    @Test
    void testGetProfileByEmailQueriesInsteadOfScanning() {
        when(userRepository.findByEmail("conductor@ecopedia.com")).thenReturn(Optional.of(mockUser));

        User found = userService.getProfileByEmail("conductor@ecopedia.com");

        assertEquals(1L, found.getId());
        assertEquals("conductor@ecopedia.com", found.getEmail());
        verify(userRepository, never()).findAll();
    }

    @Test
    void testGetProfileByEmailNotFoundThrowsException() {
        when(userRepository.findByEmail("fantasma@ecopedia.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.getProfileByEmail("fantasma@ecopedia.com"));
    }

    @Test
    void testAuthenticateInactiveUserAndLockedUser() {
        mockUser.setActive(false);
        when(userRepository.findByEmail("conductor@ecopedia.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("secret123", "$2a$10$hashedpassword")).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.authenticate(new Credentials("conductor@ecopedia.com", "secret123")));

        for (int i = 0; i < 4; i++) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.authenticate(new Credentials("conductor@ecopedia.com", "secret123")));
        }

        assertThrows(
                TooManyLoginAttemptsException.class,
                () -> userService.authenticate(new Credentials("conductor@ecopedia.com", "secret123")));
    }

    @Test
    void testUpdateProfileWithNameVehicleAndClearingVehicle() {
        VehicleModel model = new VehicleModel();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(vehicleService.getModel(15L)).thenReturn(model);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateProfile(1L, new ProfileData("Juan Actualizado", 15L));
        assertEquals("Juan Actualizado", updated.getFullName());
        assertSame(model, updated.getVehicleModel());

        User clearedVehicle = userService.updateProfile(1L, new ProfileData("   ", null));
        assertEquals("Juan Actualizado", clearedVehicle.getFullName());
        assertNull(clearedVehicle.getVehicleModel());

        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.getProfile(99L));
    }

    @Test
    void testChangePasswordSuccessAndInvalidCurrentPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("secret123", "$2a$10$hashedpassword")).thenReturn(true);
        when(passwordEncoder.matches("wrong", "$2a$10$hashedpassword")).thenReturn(false);
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$10$newhash");

        assertThrows(IllegalArgumentException.class, () -> userService.changePassword(1L, "wrong", "newpass123"));
        verify(userRepository, never()).save(any());

        userService.changePassword(1L, "secret123", "newpass123");
        assertEquals("$2a$10$newhash", mockUser.getPasswordHash());
        verify(userRepository).save(mockUser);
    }

    @Test
    void testUpdateRoleAdminProtectionWrongPasswordActivateAndListAll() {
        User admin = new User();
        admin.setId(9L);
        admin.setEmail("admin@ecopedia.com");
        admin.setPasswordHash("$2a$10$adminhash");
        admin.setRole(Role.ADMIN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(userRepository.findByEmail("admin@ecopedia.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("okAdmin", "$2a$10$adminhash")).thenReturn(true);
        when(passwordEncoder.matches("badAdmin", "$2a$10$adminhash")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(
                AdminAccountProtectedException.class,
                () -> userService.deactivateUser(9L, new AdminConfirmation("admin@ecopedia.com", "okAdmin")));

        assertThrows(
                AdminPasswordRejectedException.class,
                () -> userService.deactivateUser(1L, new AdminConfirmation("admin@ecopedia.com", "badAdmin")));

        assertThrows(
                AdminPasswordRejectedException.class,
                () -> userService.deactivateUser(1L, new AdminConfirmation("admin@ecopedia.com", null)));

        User promoted = userService.updateRole(1L, Role.CPO, new AdminConfirmation("admin@ecopedia.com", "okAdmin"));
        assertEquals(Role.CPO, promoted.getRole());

        mockUser.setActive(false);
        userService.activateUser(1L);
        assertTrue(mockUser.isActive());

        when(userRepository.findAll()).thenReturn(List.of(mockUser, admin));
        assertEquals(2, userService.listUsers(null).size());
    }
}
