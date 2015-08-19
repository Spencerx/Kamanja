#!/bin/bash

ipport="8998"

if [ "$1" != "debug" ]; then
	java -jar {InstallDirectory}/bin/MetadataAPI-1.0 --config {InstallDirectory}/input/SampleApplications/metadata/config/MetadataAPIConfig_Cassandra_Finance.properties
else	
	java -Xdebug -Xrunjdwp:transport=dt_socket,address="$ipport",server=y -jar {InstallDirectory}/bin/MetadataAPI-1.0 --config {InstallDirectory}/input/SampleApplications/metadata/config/MetadataAPIConfig_Cassandra_Finance.properties
fi

