package ec.edu.ups.icc.events.users.services;

import ec.edu.ups.icc.events.users.entities.UserEntity;
import ec.edu.ups.icc.events.users.repositories.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(
            String email
    ) throws UsernameNotFoundException {

        String normalizedEmail = email == null
                ? ""
                : email.trim();

        UserEntity user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Usuario no encontrado: "
                                        + normalizedEmail
                        )
                );

        Set<GrantedAuthority> authorities = user.getRoles()
                .stream()
                .map(role -> role.getName())
                .filter(roleName ->
                        roleName != null && !roleName.isBlank()
                )
                .map(roleName ->
                        roleName.trim().toUpperCase(Locale.ROOT)
                )
                .map(roleName ->
                        roleName.startsWith("ROLE_")
                                ? roleName
                                : "ROLE_" + roleName
                )
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        if (authorities.isEmpty()) {
            throw new UsernameNotFoundException(
                    "El usuario no tiene roles asignados: "
                            + normalizedEmail
            );
        }

        return org.springframework.security.core.userdetails.User
                .builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(authorities)
                .accountLocked(
                        Boolean.TRUE.equals(
                                user.getAccountLocked()
                        )
                )
                .disabled(
                        !Boolean.TRUE.equals(user.getEnabled())
                )
                .build();
    }
}