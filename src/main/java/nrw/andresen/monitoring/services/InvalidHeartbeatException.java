package nrw.andresen.monitoring.services;

/**
 * Ein Heartbeat wurde abgewiesen. Eigener Typ statt IllegalArgumentException,
 * damit der ExceptionHandler im Controller ausschliesslich die hier bewusst
 * gesetzten Meldungen nach aussen gibt und nicht versehentlich die Meldung
 * einer beliebigen anderen Ausnahme aus dem Request-Pfad.
 */
public class InvalidHeartbeatException extends RuntimeException {

    public InvalidHeartbeatException(String message) {
        // Ohne Stacktrace: Die Ausnahme steuert nur den Statuscode und wird bei
        // einer Flut ungueltiger Namen sehr oft erzeugt. fillInStackTrace ist
        // der teuerste Teil davon und traegt hier nichts bei.
        super(message, null, false, false);
    }
}
