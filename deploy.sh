#!/bin/bash

sudo systemctl stop monitoring
sudo cp target/monitoring-service-0.1.0-SNAPSHOT.jar /srv/monitoring/
sudo chmod ugo+x /srv/monitoring/monitoring-service-0.1.0-SNAPSHOT.jar
sudo systemctl start monitoring
