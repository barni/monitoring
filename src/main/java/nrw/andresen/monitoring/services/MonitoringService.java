package nrw.andresen.monitoring.services;

import nrw.andresen.monitoring.HeartBeat;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Main Service handling heartbeat
 */
@Component
public class MonitoringService {
    private class Event{
        LocalDateTime timestamp;
        String name;
        Boolean mailSend=false;

        Event(String name, LocalDateTime timestamp){
            this.timestamp = timestamp;
            this.name = name;
        }
    }

    @Autowired
    private EmailService emailService;
    private HashMap<String, Event> storage = new LinkedHashMap<>();
    private Logger logger = LoggerFactory.getLogger(MonitoringService.class);

    /**
     * Heartbeat received
     *
     * @param name Name of services
     * @return Heartbeat object
     */
    public synchronized HeartBeat monitor(String name){
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

                    emailService.sendSimpleMessage("admin@sonderrechte.de",
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

                    emailService.sendSimpleMessage("admin@sonderrechte.de",
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
        String values="";
        for (Map.Entry<String, Event> entry : storage.entrySet())
        {
            values += "Name: " + entry.getKey();
            values += "<br/>";
            values += "Last received: " + entry.getValue().timestamp.toString();
            values += "<br/>";
            values += "Duration: " + Duration.between(LocalDateTime.now(), entry.getValue().timestamp);
            values += "<br/>";
        }
        return values;
    }
}
