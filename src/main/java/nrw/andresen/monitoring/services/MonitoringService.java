package nrw.andresen.monitoring.services;

import nrw.andresen.monitoring.HeartBeat;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * Nach so vielen aufeinanderfolgenden Fehlschlaegen wird eine Benachrichtigung
     * aufgegeben. Ohne diese Grenze wuerde ein dauerhaft gestoerter Mailserver
     * alle 10 Sekunden erneut angefragt.
     */
    private static final int MAX_SEND_ATTEMPTS = 3;

    private static class Event {
        LocalDateTime timestamp;
        String name;
        boolean mailSend = false;
        int failedSends = 0;
        /**
         * Eine Benachrichtigung fuer diesen Dienst ist gerade in Zustellung.
         * Der Versand laeuft ausserhalb des Locks; ohne diese Markierung koennte
         * ein zweiter Scheduler-Thread dieselbe Benachrichtigung erneut
         * einsammeln und doppelt verschicken.
         */
        boolean sending = false;

        Event(String name, LocalDateTime timestamp) {
            this.timestamp = timestamp;
            this.name = name;
        }
    }

    /**
     * Eine faellige Benachrichtigung. Wird unter dem Lock ermittelt und
     * anschliessend ausserhalb des Locks verschickt.
     *
     * @param alarm true = Ausfallmeldung, false = Entwarnung
     */
    private record Notification(String name, boolean alarm, String subject, String body) {}

    private final EmailService emailService;
    private final String alertRecipient;
    private final int maxServices;
    private final int timeoutSeconds;
    private final Set<String> knownServices;

    private final Map<String, Event> storage = new LinkedHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(MonitoringService.class);

    public MonitoringService(EmailService emailService,
                             @Value("${monitoring.alert-recipient}") String alertRecipient,
                             @Value("${monitoring.max-services:100}") int maxServices,
                             @Value("${monitoring.timeout-seconds:120}") int timeoutSeconds,
                             @Value("${monitoring.known-services:}") Set<String> knownServices) {
        this.emailService = emailService;
        this.alertRecipient = alertRecipient;
        this.maxServices = maxServices;
        this.timeoutSeconds = timeoutSeconds;
        this.knownServices = knownServices;
    }

    /**
     * Heartbeat received
     *
     * @param name Name of services
     * @return Heartbeat object
     * @throws InvalidHeartbeatException wenn der Name unzulaessig oder unbekannt
     *                                   ist oder die Obergrenze erreicht wurde
     */
    public synchronized HeartBeat monitor(String name){
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new InvalidHeartbeatException("Ungueltiger Servicename");
        }
        // Ab hier ist der Name gegen VALID_NAME geprueft und darf geloggt werden.
        if (!knownServices.isEmpty() && !knownServices.contains(name)) {
            logger.warn("Heartbeat fuer unbekannten Dienst abgewiesen, name: " + name);
            throw new InvalidHeartbeatException("Unbekannter Service");
        }
        if (!storage.containsKey(name) && storage.size() >= maxServices) {
            // Ohne diese Grenze kann jeder Aufrufer den Speicher fluten und ueber
            // check() beliebig viele Alarm-Mails ausloesen.
            logger.warn("Obergrenze von " + maxServices + " Diensten erreicht, weise ab: " + name);
            throw new InvalidHeartbeatException("Zu viele Dienste registriert");
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

        logger.debug("Received Heartbeat, name: "  + event.name);
        storage.put(name, event);

        return heartBeat;

    }

    /**
     * Check every 10 seconds if an email hast to send.
     *
     * Bewusst nicht synchronized: der Mailversand ist Netzwerk-I/O und darf das
     * Lock nicht halten. Sonst blockiert ein haengender SMTP-Server saemtliche
     * Heartbeats und Statusabfragen, und der Dienst faellt lautlos komplett aus.
     */
    @Scheduled(fixedRate = 10000)
    public void check(){
        logger.info("Check for alerts.");

        for (Notification notification : collectNotifications()) {
            try {
                emailService.sendSimpleMessage(alertRecipient,
                        notification.subject(),
                        notification.body());
                logger.warn(notification.body());
                confirmDelivered(notification);
            } catch (Exception exception) {
                // Pro Dienst abfangen, damit ein fehlgeschlagener Versand nicht
                // die Pruefung der uebrigen Dienste abbricht.
                logger.error("Mailversand fuer " + notification.name() + " fehlgeschlagen", exception);
                recordFailure(notification);
            }
        }
    }

    /**
     * Ermittelt unter dem Lock, welche Benachrichtigungen faellig sind. Enthaelt
     * ausdruecklich keine Ein-/Ausgabe.
     */
    private synchronized List<Notification> collectNotifications(){
        List<Notification> notifications = new ArrayList<>();

        for (Map.Entry<String, Event> entry : storage.entrySet())
        {
            Event event = entry.getValue();
            Duration duration = Duration.between(LocalDateTime.now(), event.timestamp);
            logger.debug("Check: " + event.name + " Duration: " + duration.getSeconds());

            if (event.sending) {
                // Zustellung laeuft bereits, in diesem Durchlauf ueberspringen.
                continue;
            }

            if (duration.getSeconds() < -timeoutSeconds){
                if ( !event.mailSend ){
                    String msg = "Kein Event für " + entry.getKey() +  " seit: " +
                            event.timestamp.toString() +
                            " Dauer: " +
                            duration.toString();

                    event.sending = true;
                    notifications.add(new Notification(entry.getKey(), true,
                            "Alarm! System: " + entry.getKey(), msg));
                }
            }else{
                if ( event.mailSend ){
                    String msg = "OK für " + entry.getKey() +  " Empfangen um: " +
                            event.timestamp.toString() +
                            " Dauer: " +
                            duration.toString();

                    event.sending = true;
                    notifications.add(new Notification(entry.getKey(), false,
                            "Wieder OK! System: " + entry.getKey(), msg));
                }
            }
        }
        return notifications;
    }

    /**
     * Uebernimmt das Ergebnis eines erfolgreichen Versands: nach einer
     * Ausfallmeldung wird nicht erneut alarmiert, nach einer Entwarnung ist der
     * Dienst wieder im Normalzustand.
     */
    private synchronized void confirmDelivered(Notification notification){
        Event event = storage.get(notification.name());
        if (event == null) {
            return;
        }
        event.mailSend = notification.alarm();
        event.failedSends = 0;
        event.sending = false;
    }

    private synchronized void recordFailure(Notification notification){
        Event event = storage.get(notification.name());
        if (event == null) {
            return;
        }
        event.sending = false;
        event.failedSends++;
        if (event.failedSends >= MAX_SEND_ATTEMPTS) {
            logger.error("Gebe Benachrichtigung fuer " + notification.name() +
                    " nach " + MAX_SEND_ATTEMPTS + " Versuchen auf.");
            event.mailSend = notification.alarm();
            event.failedSends = 0;
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
