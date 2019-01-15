package nrw.andresen.monitoring;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * Hartbeat return object
 */
public class HeartBeat {

    /**
     * Last timestamp service called
     */
    private LocalDateTime lastCall;
    /**
     * Service name
     */
    private final String name;

    public HeartBeat(LocalDateTime lastCall, String name) {
        this.lastCall = lastCall;
        this.name = name;
    }
    public HeartBeat(String name) {
        this.name = name;
        this.lastCall = null;
    }
    public LocalDateTime getLastCall() {
        return lastCall;
    }

    public void setLastCall(LocalDateTime lastCall){this.lastCall = lastCall;}

    public String getName() {
        return name;
    }
}
