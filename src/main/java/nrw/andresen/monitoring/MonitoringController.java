package nrw.andresen.monitoring;

import nrw.andresen.monitoring.services.MonitoringService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Main rest-controller
 */
@RestController
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    /**
     * Rest service receiving an heart beat
     *
     * @param name unique identifier
     * @return heatbeat object
     */
    @GetMapping("/heartBeat")
    public HeartBeat heartBeat(@RequestParam(value="name") String name) {
        return monitoringService.monitor(name);
    }

    /**
     * Simple rest service returning the actual status of the servicess
     * @return Simple HTML string
     */
    @GetMapping(value = "/status", produces = MediaType.TEXT_HTML_VALUE)
    public String status() {
        return monitoringService.getStatus();
    }

    /**
     * Abgewiesene Servicenamen werden mit 400 beantwortet. Die Meldungen sind
     * feste Zeichenketten und enthalten keine Eingabedaten des Aufrufers.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidRequest(IllegalArgumentException exception) {
        return exception.getMessage();
    }
}
