package nrw.andresen.monitoring;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import nrw.andresen.monitoring.services.EmailService;
import nrw.andresen.monitoring.services.MonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * monitoring.timeout-seconds=0 laesst jeden registrierten Dienst sofort als
 * ueberfaellig gelten, damit check() ohne Wartezeit Benachrichtigungen erzeugt.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties",
        properties = "monitoring.timeout-seconds=0")
public class MonitoringServiceAlertTests {

    @Autowired
    private MonitoringService monitoringService;

    @MockitoBean
    private EmailService emailService;

    /**
     * Ein fehlgeschlagener Versand darf weder aus check() herausschlagen noch
     * die Benachrichtigung der uebrigen Dienste verhindern.
     */
    @Test
    public void failingMailMustNotSuppressAlertsForOtherServices() {
        monitoringService.monitor("SERVICE-A");
        monitoringService.monitor("SERVICE-B");

        doThrow(new MailSendException("SMTP nicht erreichbar"))
                .when(emailService).sendSimpleMessage(any(), contains("SERVICE-A"), any());

        assertDoesNotThrow(() -> monitoringService.check());

        // Trotz Fehler bei A muss B benachrichtigt worden sein.
        verify(emailService).sendSimpleMessage(eq("test@example.com"),
                contains("SERVICE-B"), any());
    }
}
