#!/bin/sh
if [ "$KAFKA_HOME" = "" ]; then
    echo "Please set KAFKA_HOME"
    exit 1
fi
TOPIC_NAME=$@
$KAFKA_HOME/bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic $TOPIC_NAME --from-beginning
