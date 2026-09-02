package tn.defense.gamma3.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationConfigTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationConfiguration authenticationConfiguration;

    @InjectMocks
    private ApplicationConfig applicationConfig;

    @Test
    void userDetailsService_UserExists_ReturnsUserDetails() {
        // Arrange
        String username = "testUser";
        User mockUser = new User();
        mockUser.setMatricule(username);
        when(userRepository.findByMatricule(username)).thenReturn(Optional.of(mockUser));

        // Act
        UserDetailsService userDetailsService = applicationConfig.userDetailsService();
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Assert
        assertNotNull(userDetails);
        assertEquals(username, userDetails.getUsername());
        verify(userRepository, times(1)).findByMatricule(username);
    }

    @Test
    void userDetailsService_UserDoesNotExist_ThrowsUsernameNotFoundException() {
        // Arrange
        String username = "unknownUser";
        when(userRepository.findByMatricule(username)).thenReturn(Optional.empty());

        // Act
        UserDetailsService userDetailsService = applicationConfig.userDetailsService();

        // Assert
        Exception exception = assertThrows(UsernameNotFoundException.class, () -> {
            userDetailsService.loadUserByUsername(username);
        });

        assertEquals("Utilisateur non trouvé", exception.getMessage());
        verify(userRepository, times(1)).findByMatricule(username);
    }

    @Test
    void authenticationProvider_ReturnsDaoAuthenticationProvider() {
        // Act
        AuthenticationProvider provider = applicationConfig.authenticationProvider();

        // Assert
        assertNotNull(provider);
        assertInstanceOf(DaoAuthenticationProvider.class, provider);
    }

    @Test
    void authenticationManager_ReturnsAuthenticationManager() throws Exception {
        // Arrange
        AuthenticationManager mockManager = mock(AuthenticationManager.class);
        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(mockManager);

        // Act
        AuthenticationManager result = applicationConfig.authenticationManager(authenticationConfiguration);

        // Assert
        assertNotNull(result);
        assertEquals(mockManager, result);
        verify(authenticationConfiguration, times(1)).getAuthenticationManager();
    }

    @Test
    void passwordEncoder_ReturnsBCryptPasswordEncoder() {
        // Act
        PasswordEncoder encoder = applicationConfig.passwordEncoder();

        // Assert
        assertNotNull(encoder);
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
    }
}
