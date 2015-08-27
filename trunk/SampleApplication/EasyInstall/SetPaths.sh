KafkaRootDir=$1
if [ -d "$KafkaRootDir" ]; then
	KafkaRootDir=$(echo $KafkaRootDir | sed 's/[\/]*$//')
fi

jar_full_path=$(which jar)

if [ "$?" != "0" ]; then
	echo "Not found java home directory."
	exit 1
fi

scala_full_path=$(which scala)

if [ "$?" != "0" ]; then
	echo "Not found scala home directory."
	exit 1
fi

pwdnm=$(pwd -P)

java_home=$(dirname $(dirname $jar_full_path))
scala_home=$(dirname $(dirname $scala_full_path))

dirnm=$(dirname "$0")
cd $dirnm

install_dir=$(dirname $(pwd -P))

java_home_repl=$(echo $java_home | sed 's/\//\\\//g')
scala_home_repl=$(echo $scala_home | sed 's/\//\\\//g')
install_dir_repl=$(echo $install_dir | sed 's/\//\\\//g')

# changing path in script files
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/script/ClusterMetadata_Template.sh > $install_dir/bin/ClusterMetadata.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/script/ClusterMetadata_Cassandra_Template.sh > $install_dir/bin/ClusterMetadata_Cassandra.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/script/StartEngine_Template.sh > $install_dir/bin/StartEngine.sh

#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Medical/template/script/InitKvStores_Template.sh > $install_dir/input/Medical/bin/InitKvStores.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Medical/template/script/StartMetadataAPI_Template.sh > $install_dir/input/Medical/bin/ApplicationMetadata.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Medical/template/script/StartMetadataAPI_Cassandra_Template.sh > $install_dir/input/Medical/bin/ApplicationMetadata_Cassandra.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Medical/template/script/StartRestService_Template.sh > $install_dir/input/Medical/bin/RestService.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Medical/template/script/PushSampleDataToKafka_Template.sh > $install_dir/input/Medical/bin/PushSampleDataToKafka.sh

#Finance
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Finance/template/script/InitKvStores_Template.sh > $install_dir/input/Finance/bin/InitKvStores.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Finance/template/script/StartMetadataAPI_Template.sh > $install_dir/input/Finance/bin/ApplicationMetadata.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Finance/template/script/StartMetadataAPI_Cassandra_Template.sh > $install_dir/input/Finance/bin/ApplicationMetadata_Cassandra.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Finance/template/script/StartRestService_Template.sh > $install_dir/input/Finance/bin/RestService.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Finance/template/script/PushSampleDataToKafka_Template.sh > $install_dir/input/Finance/bin/PushSampleDataToKafka.sh
#Finance


# HelloWorld
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartMetadataAPI_Template_HelloWorld.sh > $install_dir/SampleApplications/HelloWorld/bin/HelloWorld_Metadata.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/PushSampleDataToKafka_Template_HelloWorld.sh > $install_dir/SampleApplications/HelloWorld/bin/Push_HelloWorld_DataToKafka.sh
# HelloWorld

#new one
#HelloWorld
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartMetadataAPI_Template_HelloWorld.sh > $install_dir/input/SampleApplications/bin/ApplicationMetadata_HelloWorld.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/PushSampleDataToKafka_Template_HelloWorld.sh > $install_dir/input/SampleApplications/bin/PushSampleDataToKafka_HelloWorld.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/config/MetadataAPIConfig_Template_HelloWorld.properties > $install_dir/input/SampleApplications/metadata/config/MetadataAPIConfig_HelloWorld.properties
#HelloWorld

#Medical
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/InitKvStores_Template_Medical.sh > $install_dir/input/SampleApplications/bin/InitKvStores_Medical.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/MedicalApp_Template.sh > $install_dir/input/SampleApplications/bin/MedicalApp.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartMetadataAPI_Template_Medical.sh > $install_dir/input/SampleApplications/bin/ApplicationMetadata_Medical.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartMetadataAPI_Cassandra_Template_Medical.sh > $install_dir/input/SampleApplications/bin/ApplicationMetadata_Cassandra_Medical.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartRestService_Template_Medical.sh > $install_dir/input/SampleApplications/bin/RestService_Medical.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/PushSampleDataToKafka_Template_Medical.sh > $install_dir/input/SampleApplications/bin/PushSampleDataToKafka_Medical.sh

sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/config/MetadataAPIConfig_Template_Medical.properties > $install_dir/input/SampleApplications/metadata/config/MetadataAPIConfig_Medical.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/config/MetadataAPIConfig_Cassandra_Template_Medical.properties > $install_dir/input/SampleApplications/metadata/config/MetadataAPIConfig_Cassandra_Medical.properties
#Medical

#Telecom
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/InitKvStores_Template_Telecom.sh > $install_dir/input/SampleApplications/bin/InitKvStores_Telecom.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartMetadataAPI_Template_Telecom.sh > $install_dir/input/SampleApplications/bin/ApplicationMetadata_Telecom.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartMetadataAPI_Cassandra_Template_Telecom.sh > $install_dir/input/SampleApplications/bin/ApplicationMetadata_Cassandra_Telecom.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartRestService_Template_Telecom.sh > $install_dir/input/SampleApplications/bin/RestService_Telecom.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/PushSampleDataToKafka_Template_Telecom.sh > $install_dir/input/SampleApplications/bin/PushSampleDataToKafka_Telecom.sh

sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/config/MetadataAPIConfig_Template_Telecom.properties > $install_dir/input/SampleApplications/metadata/config/MetadataAPIConfig_Telecom.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/config/MetadataAPIConfig_Cassandra_Template_Telecom.properties > $install_dir/input/SampleApplications/metadata/config/MetadataAPIConfig_Cassandra_Telecom.properties
#Telecom

#Finance
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/InitKvStores_Template_Finance.sh > $install_dir/input/SampleApplications/bin/InitKvStores_Finance.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartMetadataAPI_Template_Finance.sh > $install_dir/input/SampleApplications/bin/ApplicationMetadata_Finance.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartMetadataAPI_Cassandra_Template_Finance.sh > $install_dir/input/SampleApplications/bin/ApplicationMetadata_Cassandra_Finance.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/StartRestService_Template_Finance.sh > $install_dir/input/SampleApplications/bin/RestService_Finance.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/PushSampleDataToKafka_Template_Finance.sh > $install_dir/input/SampleApplications/bin/PushSampleDataToKafka_Finance.sh

sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/config/MetadataAPIConfig_Template_Finance.properties > $install_dir/input/SampleApplications/metadata/config/MetadataAPIConfig_Finance.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/config/MetadataAPIConfig_Cassandra_Template_Finance.properties > $install_dir/input/SampleApplications/metadata/config/MetadataAPIConfig_Cassandra_Finance.properties
#Finance

#new one

#Telecom
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Telecom/template/script/InitKvStores_Template.sh > $install_dir/input/Telecom/bin/InitKvStores.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Telecom/template/script/StartMetadataAPI_Template.sh > $install_dir/input/Telecom/bin/ApplicationMetadata.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Telecom/template/script/StartMetadataAPI_Cassandra_Template.sh > $install_dir/input/Telecom/bin/ApplicationMetadata_Cassandra.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Telecom/template/script/StartRestService_Template.sh > $install_dir/input/Telecom/bin/RestService.sh
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Telecom/template/script/PushSampleDataToKafka_Template.sh > $install_dir/input/Telecom/bin/PushSampleDataToKafka.sh
#Telecom

# changing path in config files
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/ClusterCfgMetadataAPIConfig_Template.properties > $install_dir/config/ClusterCfgMetadataAPIConfig.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/ClusterCfgMetadataAPIConfig_Cassandra_Template.properties > $install_dir/config/ClusterCfgMetadataAPIConfig_Cassandra.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/ClusterConfig_Template.json > $install_dir/config/ClusterConfig.json
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/ClusterConfig_Template.json > $install_dir/input/SampleApplications/metadata/config/ClusterConfig.json
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/EngineConfig_Template.properties > $install_dir/config/Engine1Config.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/EngineConfig_Cassandra_Template.properties > $install_dir/config/Engine1Config_Cassandra.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/MetadataAPIConfig_Template.properties > $install_dir/config/MetadataAPIConfig.properties

#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Medical/template/config/MetadataAPIConfig_Template.properties > $install_dir/input/Medical/metadata/config/MetadataAPIConfig.properties
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Medical/template/config/MetadataAPIConfig_Cassandra_Template.properties > $install_dir/input/Medical/metadata/config/MetadataAPIConfig_Cassandra.properties

#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Telecom/template/config/MetadataAPIConfig_Template.properties > $install_dir/input/Telecom/metadata/config/MetadataAPIConfig.properties
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Telecom/template/config/MetadataAPIConfig_Cassandra_Template.properties > $install_dir/input/Telecom/metadata/config/MetadataAPIConfig_Cassandra.properties

#Finance
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Finance/template/config/MetadataAPIConfig_Template.properties > $install_dir/input/Finance/metadata/config/MetadataAPIConfig.properties
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/Finance/template/config/MetadataAPIConfig_Cassandra_Template.properties > $install_dir/input/Finance/metadata/config/MetadataAPIConfig_Cassandra.properties
#Finance

# HelloWorld
#sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/config/MetadataAPIConfig_Template_HelloWorld.properties > $install_dir/input/SampleApplications/metadata/config/MetadataAPIConfig_HelloWorld.properties
# HelloWorld

# Expecting 1st Parameter as Kafka Install directory
if [ "$#" -ne 1 ] || ! [ -d "$KafkaRootDir" ]; then
	echo "WARN: Not given/found Kafka install directory. Not going to create CreateQueues.sh, WatchOutputQueue.sh, WatchStatusQueue.sh and WatchInputQueue.sh"
else
	kafkatopics="$KafkaRootDir/bin/kafka-topics.sh"
	if [ ! -f "$kafkatopics" ]; then
		echo "WARN: Not found bin/kafka-topics.sh in given Kafka install directory $KafkaRootDir. Not going to create CreateQueues.sh, WatchOutputQueue.sh, WatchStatusQueue.sh and WatchInputQueue.sh"
	else
		KafkaRootDir_repl=$(echo $KafkaRootDir | sed 's/\//\\\//g')
		sed "s/{KafkaInstallDir}/$KafkaRootDir_repl/g" $install_dir/template/script/CreateQueues_Template.sh > $install_dir/bin/CreateQueues.sh
		sed "s/{KafkaInstallDir}/$KafkaRootDir_repl/g" $install_dir/template/script/WatchOutputQueue_Template.sh > $install_dir/bin/WatchOutputQueue.sh
		sed "s/{KafkaInstallDir}/$KafkaRootDir_repl/g" $install_dir/template/script/WatchStatusQueue_Template.sh > $install_dir/bin/WatchStatusQueue.sh
		sed "s/{KafkaInstallDir}/$KafkaRootDir_repl/g" $install_dir/template/script/WatchInputQueue_Template.sh > $install_dir/bin/WatchInputQueue.sh
		sed "s/{KafkaInstallDir}/$KafkaRootDir_repl/g" $install_dir/template/script/PushSampleDataToKafka_Template.sh > $install_dir/bin/PushSampleDataToKafka.sh
		sed "s/{KafkaInstallDir}/$KafkaRootDir_repl/g" $install_dir/template/script/WatchTopic_Template.sh > $install_dir/bin/WatchTopic.sh
		sed "s/{KafkaInstallDir}/$KafkaRootDir_repl/g" $install_dir/template/script/CreateTopic_Template.sh > $install_dir/bin/CreateTopic.sh
	fi
fi

chmod 777 $install_dir/bin/*.*

cd $pwdnm
