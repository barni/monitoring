# monitoring
Monitoring service which receives rest calls. If no rest call is received for 2 minutes an email is send.

## Getting Started

### Prerequisites
* Java 8

### Configuration
Adopt application.properties.

### Staring
./monitoring-service-X.X.X-SNAPSHOT.jar

### API

http://localhost/status
Return current status of all service

http://localhost/heartBeat?name=SERVICE1
Heartbear call für SERVICE1
