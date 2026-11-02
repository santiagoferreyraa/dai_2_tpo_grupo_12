package com.ecopedia.core.user.domain;

import java.util.List;

/**
 * Interfaz pública del componente {@code ServicioDeUsuarios} (stateless).
 *
 * <p>Cubre RF01, RF03 y las tareas ECO-23, ECO-24, ECO-25.
 */
public interface UserService {

    /** Registro de un usuario nuevo (conductor, CPO u admin). */
    User register(RegistrationData data);

    /** Autenticación de credenciales y emisión de token JWT. */
    AuthToken authenticate(Credentials credentials);

    /** Consulta de perfil por ID. */
    User getProfile(Long userId);

    /**
     * Consulta de perfil por email, que es lo que identifica al usuario dentro del token.
     *
     * <p>Existe para que quien atiende una petición autenticada no tenga que recorrer el
     * padrón buscando quién la hizo: el email viaja en el token y la columna es única, así que
     * la consulta va directo. Es la operación sobre la que se apoya la pantalla de perfil.
     */
    User getProfileByEmail(String email);

    /** Actualización de datos de perfil. */
    User updateProfile(Long userId, ProfileData data);

    /**
     * Cambio de contraseña del propio usuario.
     *
     * <p><b>Pide la actual y no solo la nueva</b>, y esa es la regla: un token robado o una
     * sesión abierta en una máquina ajena alcanzan para llegar hasta acá, y sin la contraseña
     * vigente cualquiera de las dos cosas se convierte en quedarse con la cuenta para siempre.
     * Pedirla es lo único que distingue al dueño de quien pasaba por ahí.
     */
    void changePassword(Long userId, String currentPassword, String newPassword);

    /**
     * Cambio de rol de un usuario que no es {@code ADMIN}, confirmado con la contraseña del
     * {@code ADMIN} que lo pide (Manuel, 02/11). Promover a {@code ADMIN} está permitido; después,
     * esa cuenta ya no se toca desde la aplicación.
     *
     * @throws AdminAccountProtectedException si el usuario es {@code ADMIN}, también el propio.
     * @throws AdminPasswordRejectedException si la contraseña no es la del {@code ADMIN}.
     * @throws TooManyLoginAttemptsException si el {@code ADMIN} está bloqueado por intentos fallidos.
     */
    User updateRole(Long userId, Role newRole, AdminConfirmation confirmation);

    /**
     * Baja lógica de un usuario que no es {@code ADMIN}, con la misma confirmación y las mismas
     * reglas que {@link #updateRole}.
     */
    void deactivateUser(Long userId, AdminConfirmation confirmation);

    /** Alta/Reactivación lógica de un usuario (solo ADMIN). */
    void activateUser(Long userId);

    /** Listado de usuarios, opcionalmente filtrado por rol. */
    List<User> listUsers(Role roleFilter);
}
