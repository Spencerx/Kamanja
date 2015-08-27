#!/bin/sh

KAMANJA_HOME={InstallDirectory}

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties upload engine config $KAMANJA_HOME/config/ClusterConfig.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/CoughCodes_Medical.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/DyspnoeaCodes_Medical.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/EnvCodes_Medical.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/SmokeCodes_Medical.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties add container $KAMANJA_HOME/input/SampleApplications/metadata/container/SputumCodes_Medical.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties add message $KAMANJA_HOME/input/SampleApplications/metadata/message/beneficiary_Medical.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties add message $KAMANJA_HOME/input/SampleApplications/metadata/message/hl7_Medical.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties add message $KAMANJA_HOME/input/SampleApplications/metadata/message/inpatientclaim_Medical.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties add message $KAMANJA_HOME/input/SampleApplications/metadata/message/outpatientclaim_Medical.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties upload compile config $KAMANJA_HOME/input/SampleApplications/metadata/config/Java_ModelConfig_Medical.json

$KAMANJA_HOME/bin/kamanja $KAMANJA_HOME/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties add model java $KAMANJA_HOME/input/SampleApplications/metadata/model/COPDRiskAssessment.java DEPENDSON copdriskassessment