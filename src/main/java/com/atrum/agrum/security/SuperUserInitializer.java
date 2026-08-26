package com.atrum.agrum.security;

import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SuperUserInitializer implements CommandLineRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${SUPERUSER_USERNAME}")
    private String superUsername;

    @Value("${SUPERUSER_EMAIL}")
    private String superEmail;

    @Value("${SUPERUSER_PASSWORD}")
    private String superPassword;

    public SuperUserInitializer(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Check if the super user already exists
        if (userRepository.findById(superUsername).isEmpty()) {
            AppUser superUser = new AppUser();
            superUser.setUsername(superUsername);
            superUser.setEmail(superEmail);
            // Securely hash the password coming from the .env file
            superUser.setPassword(passwordEncoder.encode(superPassword));

            userRepository.save(superUser);

            System.out.println("----------------------------------------------------------");
            System.out.println("System Super User '" + superUsername + "' successfully provisioned!");
            System.out.println("----------------------------------------------------------");
        }
    }
}