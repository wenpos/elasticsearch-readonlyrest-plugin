#!/bin/bash
mvn clean package
zip -j -g target/elasticsearch-readonlyrest-v*es-v*.zip src/main/resources/plugin-descriptor.properties
zip -j -g target/elasticsearch-readonlyrest-v*es-v*.zip src/main/resources/plugin-security.policy
