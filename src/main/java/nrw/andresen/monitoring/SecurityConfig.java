package nrw.andresen.monitoring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Die Anwendung authentifiziert selbst, mit dem Benutzer aus
 * spring.security.user.name und spring.security.user.password. Der vorgelagerte
 * Reverse Proxy liefert HTTPS; der Dienst lauscht nur auf 127.0.0.1, damit die
 * Zugangsdaten nie unverschluesselt ueber das Netz gehen.
 *
 * HTTP Basic, weil sich die ueberwachten Dienste ohne interaktives Formular
 * anmelden koennen muessen.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .httpBasic(basic -> {})
                // CSRF-Token wuerden hier nichts schuetzen: Beide Endpunkte sind
                // GET, und der CsrfFilter prueft ausschliesslich veraendernde
                // Methoden. Die Heartbeat-Clients koennen zudem kein Token holen.
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
