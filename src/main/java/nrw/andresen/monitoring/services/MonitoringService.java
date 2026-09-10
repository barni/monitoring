package nrw.andresen.monitoring.services;

import nrw.andresen.monitoring.HeartBeat;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;

/**
 * Main Service handling heartbeat
 */
@Component
public class MonitoringService {

    /**
     * Erlaubte Zeichen fuer einen Servicenamen. Der Name wird geloggt, auf der
     * Statusseite ausgegeben und in den Mail-Betreff uebernommen. Die
     * Beschraenkung unterbindet HTML-, Log- und Header-Injection an der Quelle,
     * statt an jeder Ausgabestelle einzeln.
     */
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private static class Event {
        LocalDateTime timestamp;
        String name;
        Boolean mailSend = false;

        Event(String name, LocalDateTime timestamp) {
            this.timestamp = timestamp;
            this.name = name;
        }
    }

    private final EmailService emailService;
    private final String alertRecipient;
    private final int maxServices;
    private final Set<String> knownServices;

    private final Map<String, Event> storage = new LinkedHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(MonitoringService.class);

    public MonitoringService(EmailService emailService,
                             @Value("${monitoring.alert-recipient}") String alertRecipient,
                             @Value("${monitoring.max-services:100}") int maxServices,
                             @Value("${monitoring.known-services:}") Set<String> knownServices) {
        this.emailService = emailService;
        this.alertRecipient = alertRecipient;
        this.maxServices = maxServices;
        this.knownServices = knownServices;
    }

    /**
     * Heartbeat received
     *
     * @param name Name of services
     * @return Heartbeat object
     * @throws IllegalArgumentException wenn der Name unzulaessig oder unbekannt
     *                                  ist oder die Obergrenze erreicht wurde
     */
    public synchronized HeartBeat monitor(String name){
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Ungueltiger Servicename");
        }
        // Ab hier ist der Name gegen VALID_NAME geprueft und darf geloggt werden.
        if (!knownServices.isEmpty() && !knownServices.contains(name)) {
            logger.warn("Heartbeat fuer unbekannten Dienst abgewiesen, name: " + name);
            throw new IllegalArgumentException("Unbekannter Service");
        }
        if (!storage.containsKey(name) && storage.size() >= maxServices) {
            // Ohne diese Grenze kann jeder Aufrufer den Speicher fluten und ueber
            // check() beliebig viele Alarm-Mails ausloesen.
            logger.warn("Obergrenze von " + maxServices + " Diensten erreicht, weise ab: " + name);
            throw new IllegalArgumentException("Zu viele Dienste registriert");
        }

        Event event = null;
        HeartBeat heartBeat = new HeartBeat(name);
        if ( storage.containsKey(name)){
            event = storage.get(name);
            heartBeat.setLastCall(event.timestamp);
            event.timestamp = LocalDateTime.now();
        }else{
            event = new Event(name, LocalDateTime.now());
        }

        logger.info("Received Heartbeat, name: "  + event.name);
        storage.put(name, event);

        return heartBeat;

    }

    /**
     * Check every 10 seconds if an email hast to send
     */
    @Scheduled(fixedRate = 10000)
    public synchronized void check(){
        logger.info("Check for alerts.");

        for (Map.Entry<String, Event> entry : storage.entrySet())
        {
            Event event = entry.getValue();
            Duration duration = Duration.between(LocalDateTime.now(), event.timestamp);
            logger.debug("Check: " + event.name + " Duration: " + duration.getSeconds());

            if (duration.getSeconds() < - 120){
                if ( !event.mailSend ){
                    String msg = "Kein Event für " + entry.getKey() +  " seit: " +
                            event.timestamp.toString() +
                            " Dauer: " +
                            duration.toString();

                    emailService.sendSimpleMessage(alertRecipient,
                            "Alarm! System: " + entry.getKey(),
                            msg);
                    logger.warn(msg);
                    event.mailSend = true;
                }
            }else{
                if ( event.mailSend == true ){
                    String msg = "OK für " + entry.getKey() +  " Empfangen um: " +
                            event.timestamp.toString() +
                            " Dauer: " +
                            duration.toString();

                    emailService.sendSimpleMessage(alertRecipient,
                            "Wieder OK! System: " + entry.getKey(),
                            msg);
                    logger.warn(msg);
                }
                event.mailSend = false;
            }
            storage.put(entry.getKey(), event);
        }
    }

    /**
     * Returns actual status
     *
     * @return Simple HTML String
     */
    public synchronized String getStatus(){
        StringBuilder values = new StringBuilder();
        for (Map.Entry<String, Event> entry : storage.entrySet())
        {
            // Zweite Verteidigungslinie: der Name ist bereits validiert, wird hier
            // aber zusaetzlich escaped, damit die Seite auch dann sicher bleibt,
            // wenn die Eingangspruefung spaeter gelockert wird.
            values.append("Name: ").append(HtmlUtils.htmlEscape(entry.getKey()));
            values.append("<br/>");
            values.append("Last received: ").append(entry.getValue().timestamp.toString());
            values.append("<br/>");
            values.append("Duration: ").append(Duration.between(LocalDateTime.now(), entry.getValue().timestamp));
            values.append("<br/>");
        }
        return values.toString();
    }
}
