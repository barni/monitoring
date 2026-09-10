package nrw.andresen.monitoring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Die Authentifizierung erfolgt im vorgelagerten Reverse Proxy (Login + HTTPS),
 * der Dienst selbst lauscht nur auf 127.0.0.1. Diese Chain gibt daher alle
 * Endpunkte frei, laesst aber die Security-Filter aktiv, damit Spring Security
 * die Standard-Response-Header (u.a. X-Content-Type-Options: nosniff) setzt.
 *
 * Bis Spring Boot 3 wurde das ueber
 * spring.autoconfigure.exclude=...SecurityAutoConfiguration geloest. Ab Boot 4
 * ist das nicht mehr moeglich, weil UserDetailsServiceAutoConfiguration eine
 * eigenstaendige Autokonfiguration ist und SecurityProperties benoetigt.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                // Ohne anwendungsseitige Session gibt es kein CSRF-Schutzziel;
                // Heartbeat-Clients sollen ohne Token posten koennen.
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        // /status liefert HTML. Die Namen sind zwar validiert und
                        // escaped, aber eine Seite, die weder Skripte noch externe
                        // Ressourcen braucht, sollte das auch ausdruecklich sagen.
                        .contentSecurityPolicy(csp ->
                                csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .build();
    }
}
