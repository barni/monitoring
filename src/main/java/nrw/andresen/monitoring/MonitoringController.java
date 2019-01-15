package nrw.andresen.monitoring;

import java.util.concurrent.atomic.AtomicLong;

import nrw.andresen.monitoring.services.MonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Main rest-controller
 */
@RestController
public class MonitoringController {

    @Autowired
    private MonitoringService monitoringService;

    /**
     * Rest service receiving an heart beat
     *
     * @param name unique identifier
     * @return heatbeat object
     */
    @RequestMapping("/heartBeat")
    public HeartBeat heartBeat(@RequestParam(value="name") String name) {
        return monitoringService.monitor(name);
    }

    /**
     * Simple rest service returning the actual status of the servicess
     * @return Simple HTML string
     */
    @RequestMapping("/status")
    public String status() {
        return monitoringService.getStatus();
    }
}
