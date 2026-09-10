# monitoring
Monitoring service which receives rest calls. If no rest call is received for 2 minutes an email is send.

## Getting Started

### Prerequisites
* Java 17 or newer (Spring Boot 4 baseline)

### Configuration
Copy `application.properties.example` to `application.properties` next to the
jar and fill it in. The real file is deliberately not tracked in git.

Because it holds the SMTP password in clear text, restrict it on the server:

```
chmod 600 /srv/monitoring/application.properties
```

Alternatively pass secrets as environment variables instead of writing them to
disk, e.g. `SPRING_MAIL_PASSWORD`.

Relevant monitoring settings:

* `monitoring.alert-recipient` - recipient of the alert mails (required)
* `monitoring.max-services` - upper bound of monitored services, default 100
* `monitoring.known-services` - optional comma separated allow list; if set,
  heartbeats for any other name are rejected with HTTP 400

Service names are restricted to `[A-Za-z0-9_.-]`, at most 64 characters.

Authentication is expected to be handled by a reverse proxy in front of the
service (login + HTTPS). The service itself binds to 127.0.0.1 only and permits
all requests, see `SecurityConfig.java`.

### Building
```
mvn clean install
```

The OWASP dependency-check scan runs only when the environment variable
`NVD_API_KEY` is set, because the plugin requires an NVD API key and aborts the
build without one. Request a free key at
https://nvd.nist.gov/developers/request-an-api-key and store it as the
repository secret `NVD_API_KEY` for CI.

```
NVD_API_KEY=<key> mvn clean install
```

### Staring
./monitoring-service-X.X.X-SNAPSHOT.jar

### Deployment
`./deploy.sh` reads the version from the pom, copies the jar to
/srv/monitoring and updates the symlink `monitoring-service.jar`. Point the
systemd unit at that symlink so it does not need to know the version number.

### API

http://localhost/status
Return current status of all service

http://localhost/heartBeat?name=SERVICE1
Heartbear call für SERVICE1 (GET only)
