# monitoring
Monitoring service which receives rest calls. If no rest call is received for 2 minutes an email is send.

## Getting Started

### Prerequisites
* Java 17 or newer (Spring Boot 4 baseline)

### Configuration
Adopt application.properties.

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

### API

http://localhost/status
Return current status of all service

http://localhost/heartBeat?name=SERVICE1
Heartbear call für SERVICE1
