#!/usr/bin/env bash

KAMANJA_HOME={InstallDirectory}

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties upload cluster config $KAMANJA_HOME/config/ClusterConfig.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/AccountAggregatedUsage_Telecom.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/AccountInfo_Telecom.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/SubscriberAggregatedUsage_Telecom.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/SubscriberGlobalPreferences_Telecom.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/SubscriberInfo_Telecom.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/SubscriberPlans_Telecom.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add message $KAMANJA_HOME/input/SampleApplications/metadata/message/SubscriberUsage_Telecom.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties upload compile config $KAMANJA_HOME/config/SubscriberUsageAlertCompileCfg_Telecom.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add model java $KAMANJA_HOME/input/SampleApplications/metadata/model/SubscriberUsageAlert_Telecom.java DEPENDSON subscriberusagealert
