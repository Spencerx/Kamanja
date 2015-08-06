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

sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/COPD-RiskAssessment/template/script/InitKvStores_Template.sh > $install_dir/input/COPD-RiskAssessment/bin/InitKvStores.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/COPD-RiskAssessment/template/script/StartMetadataAPI_Template.sh > $install_dir/input/COPD-RiskAssessment/bin/ApplicationMetadata.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/COPD-RiskAssessment/template/script/StartMetadataAPI_Cassandra_Template.sh > $install_dir/input/COPD-RiskAssessment/bin/ApplicationMetadata_Cassandra.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/COPD-RiskAssessment/template/script/StartRestService_Template.sh > $install_dir/input/COPD-RiskAssessment/bin/RestService.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/COPD-RiskAssessment/template/script/PushSampleDataToKafka_Template.sh > $install_dir/input/COPD-RiskAssessment/bin/PushSampleDataToKafka.sh

#LowBalance
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/LowBalance/template/script/InitKvStores_Template.sh > $install_dir/input/LowBalance/bin/InitKvStores.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/LowBalance/template/script/StartMetadataAPI_Template.sh > $install_dir/input/LowBalance/bin/ApplicationMetadata.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/LowBalance/template/script/StartMetadataAPI_Cassandra_Template.sh > $install_dir/input/LowBalance/bin/ApplicationMetadata_Cassandra.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/LowBalance/template/script/StartRestService_Template.sh > $install_dir/input/LowBalance/bin/RestService.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/LowBalance/template/script/PushSampleDataToKafka_Template.sh > $install_dir/input/LowBalance/bin/PushSampleDataToKafka.sh
#LowBalance

# HelloWorld
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/HelloWorld/template/script/StartMetadataAPI_Template.sh > $install_dir/input/HelloWorld/bin/ApplicationMetadata.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/HelloWorld/template/script/PushSampleDataToKafka_Template.sh > $install_dir/input/HelloWorld/bin/PushSampleDataToKafka.sh
# HelloWorld


# changing path in config files
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/ClusterCfgMetadataAPIConfig_Template.properties > $install_dir/config/ClusterCfgMetadataAPIConfig.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/ClusterCfgMetadataAPIConfig_Cassandra_Template.properties > $install_dir/config/ClusterCfgMetadataAPIConfig_Cassandra.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/ClusterConfig_Template.json > $install_dir/config/ClusterConfig.json
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/EngineConfig_Template.properties > $install_dir/config/Engine1Config.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/EngineConfig_Cassandra_Template.properties > $install_dir/config/Engine1Config_Cassandra.properties

sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/COPD-RiskAssessment/template/config/MetadataAPIConfig_Template.properties > $install_dir/input/COPD-RiskAssessment/metadata/config/MetadataAPIConfig.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/COPD-RiskAssessment/template/config/MetadataAPIConfig_Cassandra_Template.properties > $install_dir/input/COPD-RiskAssessment/metadata/config/MetadataAPIConfig_Cassandra.properties

#LowBalance
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/LowBalance/template/config/MetadataAPIConfig_Template.properties > $install_dir/input/LowBalance/metadata/config/MetadataAPIConfig.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/LowBalance/template/config/MetadataAPIConfig_Cassandra_Template.properties > $install_dir/input/LowBalance/metadata/config/MetadataAPIConfig_Cassandra.properties
#LowBalance

# HelloWorld
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/HelloWorld/template/config/MetadataAPIConfig_Template.properties > $install_dir/input/HelloWorld/metadata/config/MetadataAPIConfig.properties

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
	fi
fi

chmod 777 $install_dir/bin/*.*

cd $pwdnm
