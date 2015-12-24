#!/usr/bin/env bash

KAMANJA_HOME={InstallDirectory}

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties upload cluster config $KAMANJA_HOME/config/ClusterConfig.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/CustAlertHistory_Finance.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/CustPreferences_Finance.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/CustomerInfo_Finance.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/GlobalPreferences_Finance.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add message $KAMANJA_HOME/input/SampleApplications/metadata/message/TransactionMsg_Finance.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties upload compile config $KAMANJA_HOME/config/LBCompileCfg_Finance.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/config/MetadataAPIConfig.properties add model scala $KAMANJA_HOME/input/SampleApplications/metadata/model/LowBalanceAlert_Finance.scala DEPENDSON lowbalancealert
