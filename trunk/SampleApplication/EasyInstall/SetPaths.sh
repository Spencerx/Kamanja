
echo "Setting up paths"
KafkaRootDir=$1
if [ -d "$KafkaRootDir" ]; then
	KafkaRootDir=$(echo $KafkaRootDir | sed 's/[\/]*$//')
fi
jar_full_path=$(which jar)
if [ "$?" != "0" ]; then
	jar_full_path=$JAVA_HOME/bin/jar
	if [ $JAVA_HOME == "" ]; then
		echo "The command 'which jar' failed and the environment variable $JAVA_HOME is not set. Please set the $JAVA_HOME environment variable."
		exit 1
	fi
fi

scala_full_path=$(which scala)
if [ "$?" != "0" ]; then
	scala_full_path=$SCALA_HOME/bin/scala
	if [$SCALA_HOME == ""]; then
		echo "The command 'which scala' failed and the environment variable $SCALA_HOME is not set. Please set the $SCALA_HOME environment variable."
		exit 1
	fi
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
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/script/StartEngine_Template.sh > $install_dir/bin/StartEngine.sh

#new one
#HelloWorld
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/HelloWorldApp_Template.sh > $install_dir/input/SampleApplications/bin/HelloWorldApp.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/PushSampleDataToKafka_Template_HelloWorld.sh > $install_dir/input/SampleApplications/bin/PushSampleDataToKafka_HelloWorld.sh
#HelloWorld

#Medical
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/PushSampleDataToKafka_Template_Medical.sh > $install_dir/input/SampleApplications/bin/PushSampleDataToKafka_Medical.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/InitKvStores_Template_Medical.sh > $install_dir/input/SampleApplications/bin/InitKvStores_Medical.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/MedicalApp_Template.sh > $install_dir/input/SampleApplications/bin/MedicalApp.sh
#Medical

#Telecom
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/SubscriberUsageApp_Template.sh > $install_dir/input/SampleApplications/bin/SubscriberUsageApp.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/InitKvStores_Template_Telecom.sh > $install_dir/input/SampleApplications/bin/InitKvStores_Telecom.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/PushSampleDataToKafka_Template_Telecom.sh > $install_dir/input/SampleApplications/bin/PushSampleDataToKafka_Telecom.sh
#Telecom

#Finance
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/LowBalanceAlertApp_Template.sh > $install_dir/input/SampleApplications/bin/LowBalanceAlertApp.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/InitKvStores_Template_Finance.sh > $install_dir/input/SampleApplications/bin/InitKvStores_Finance.sh
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/input/SampleApplications/template/script/PushSampleDataToKafka_Template_Finance.sh > $install_dir/input/SampleApplications/bin/PushSampleDataToKafka_Finance.sh
#Finance

#new one

# logfile
sed "s/{InstallDirectory}/$install_dir_repl/g" $install_dir/template/config/log4j2_Template.xml > $install_dir/config/log4j2.xml

# changing path in config files
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/ClusterConfig_Template.json > $install_dir/config/ClusterConfig.json
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/EngineConfig_Template.properties > $install_dir/config/Engine1Config.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/EngineConfig_Cassandra_Template.properties > $install_dir/config/Engine1Config_Cassandra.properties
sed "s/{InstallDirectory}/$install_dir_repl/g;s/{ScalaInstallDirectory}/$scala_home_repl/g;s/{JavaInstallDirectory}/$java_home_repl/g" $install_dir/template/config/MetadataAPIConfig_Template.properties > $install_dir/config/MetadataAPIConfig.properties
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
		sed "s/{InstallDirectory}/$install_dir_repl/g;s/{KafkaInstallDir}/$KafkaRootDir_repl/g" $install_dir/template/script/PushSampleDataToKafka_Template.sh > $install_dir/bin/PushSampleDataToKafka.sh
	fi
fi

chmod 777 $install_dir/bin/*.*

cd $pwdnm
