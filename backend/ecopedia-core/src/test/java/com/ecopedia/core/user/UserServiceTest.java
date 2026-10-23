package com.ecopedia.core.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.user.domain.*;
import com.ecopedia.core.user.service.LoginAttempts;
import com.ecopedia.core.user.service.UserServiceImpl;
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
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        RegistrationData data =
                new RegistrationData("conductor@ecopedia.com", "secret123", "Juan Conductor", Role.CONDUCTOR);
        User registered = userService.register(data);

        assertNotNull(registered);
        assertEquals("conductor@ecopedia.com", registered.getEmail());
        assertEquals(Role.CONDUCTOR, registered.getRole());
        verify(userRepository, times(1)).save(any(User.class));
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        userService.deactivateUser(1L);

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
}
