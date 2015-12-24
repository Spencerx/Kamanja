#!/bin/bash

set -e

installPath=$1
srcPath=$2
ivyPath=$3
KafkaRootDir=$4

if [ ! -d "$installPath" ]; then
        echo "Not valid install path supplied.  It should be a directory that can be written to and whose current content is of no value (will be overwritten) "
        echo "$0 <install path> <src tree trunk directory> <ivy directory path for dependencies> <kafka installation path>"
        exit 1
fi

if [ ! -d "$srcPath" ]; then
        echo "Not valid src path supplied.  It should be the trunk directory containing the jars, files, what not that need to be supplied."
        echo "$0 <install path> <src tree trunk directory> <ivy directory path for dependencies> <kafka installation path>"
        exit 1
fi

if [ ! -d "$ivyPath" ]; then
        echo "Not valid ivy path supplied.  It should be the ivy path for dependency the jars."
        echo "$0 <install path> <src tree trunk directory> <ivy directory path for dependencies> <kafka installation path>"
        exit 1
fi

if [ ! -d "$KafkaRootDir" ]; then
        echo "Not valid Kafka path supplied."
        echo "$0 <install path> <src tree trunk directory> <ivy directory path for dependencies> <kafka installation path>"
        exit 1
fi

installPath=$(echo $installPath | sed 's/[\/]*$//')
srcPath=$(echo $srcPath | sed 's/[\/]*$//')
ivyPath=$(echo $ivyPath | sed 's/[\/]*$//')

# *******************************
# Clean out prior installation
# *******************************
rm -Rf $installPath

# *******************************
# Make the directories as needed
# *******************************
mkdir -p $installPath/bin
mkdir -p $installPath/lib
mkdir -p $installPath/lib/system
mkdir -p $installPath/lib/application
mkdir -p $installPath/storage
mkdir -p $installPath/logs
mkdir -p $installPath/config
mkdir -p $installPath/documentation
mkdir -p $installPath/output
mkdir -p $installPath/workingdir
mkdir -p $installPath/template
mkdir -p $installPath/template/config
mkdir -p $installPath/template/script
mkdir -p $installPath/input
#new one
mkdir -p $installPath/input/SampleApplications
mkdir -p $installPath/input/SampleApplications/bin
mkdir -p $installPath/input/SampleApplications/data
mkdir -p $installPath/input/SampleApplications/metadata
mkdir -p $installPath/input/SampleApplications/metadata/config
mkdir -p $installPath/input/SampleApplications/metadata/container
mkdir -p $installPath/input/SampleApplications/metadata/function
mkdir -p $installPath/input/SampleApplications/metadata/message
mkdir -p $installPath/input/SampleApplications/metadata/model
mkdir -p $installPath/input/SampleApplications/metadata/script
mkdir -p $installPath/input/SampleApplications/metadata/type
mkdir -p $installPath/input/SampleApplications/template
#new one

bin=$installPath/bin
systemlib=$installPath/lib/system
applib=$installPath/lib/application

echo $installPath
echo $srcPath
echo $bin

# *******************************
# Build fat-jars
# *******************************

echo "clean, package and assemble $srcPath ..."

cd $srcPath
sbt clean package KamanjaManager/assembly MetadataAPI/assembly KVInit/assembly MethodExtractor/assembly SimpleKafkaProducer/assembly NodeInfoExtract/assembly ExtractData/assembly MetadataAPIService/assembly JdbcDataCollector/assembly FileDataConsumer/assembly SaveContainerDataComponent/assembly

# recreate eclipse projects
#echo "refresh the eclipse projects ..."
#cd $srcPath
#sbt eclipse

# Move them into place
echo "copy the fat jars to $installPath ..."

cd $srcPath
cp Utils/KVInit/target/scala-2.10/KVInit* $bin
cp MetadataAPI/target/scala-2.10/MetadataAPI* $bin
cp KamanjaManager/target/scala-2.10/KamanjaManager* $bin
cp Pmml/MethodExtractor/target/scala-2.10/MethodExtractor* $bin
cp Utils/SimpleKafkaProducer/target/scala-2.10/SimpleKafkaProducer* $bin
cp Utils/ExtractData/target/scala-2.10/ExtractData* $bin
cp Utils/NodeInfoExtract/target/scala-2.10/NodeInfoExtract* $bin
cp Utils/JdbcDataCollector/target/scala-2.10/JdbcDataCollector* $bin
cp MetadataAPIService/target/scala-2.10/MetadataAPIService* $bin
cp FileDataConsumer/target/scala-2.10/FileDataConsumer* $bin

# *******************************
# Copy jars required (more than required if the fat jars are used)
# *******************************

# Base Types and Functions, InputOutput adapters, and original versions of things
echo "copy all Kamanja jars and the jars upon which they depend to the $systemlib"


cp $srcPath/FactoriesOfModelInstanceFactory/JarFactoryOfModelInstanceFactory/target/scala-2.10/jarfactoryofmodelinstancefactory_2.10-1.0.jar $systemlib
cp $ivyPath/cache/org.apache.logging.log4j/log4j-core/jars/log4j-core-2.4.1.jar $systemlib
cp $ivyPath/cache/org.apache.logging.log4j/log4j-api/jars/log4j-api-2.4.1.jar $systemlib
cp $srcPath/Storage/SqlServer/src/test/resources/sqljdbc4-2.0.jar $systemlib
cp $ivyPath/cache/org.codehaus.jackson/jackson-xc/jars/jackson-xc-1.8.3.jar $systemlib
cp $ivyPath/cache/org.xerial.snappy/snappy-java/bundles/snappy-java-1.0.4.1.jar $systemlib
cp $ivyPath/cache/javax.xml.bind/jaxb-api/jars/jaxb-api-2.2.2.jar $systemlib
cp $ivyPath/cache/com.sun.jersey/jersey-core/bundles/jersey-core-1.9.jar $systemlib
cp $ivyPath/cache/com.google.guava/guava/bundles/guava-16.0.1.jar $systemlib
cp $srcPath/EnvContexts/SimpleEnvContextImpl/target/scala-2.10/simpleenvcontextimpl_2.10-1.0.jar $systemlib
cp $srcPath/MetadataAPI/target/scala-2.10/metadataapi_2.10-1.0.jar $systemlib
cp $ivyPath/cache/commons-digester/commons-digester/jars/commons-digester-1.8.jar $systemlib
cp $ivyPath/cache/org.mortbay.jetty/jetty-util/jars/jetty-util-6.1.26.jar $systemlib
cp $ivyPath/cache/org.apache.commons/commons-collections4/jars/commons-collections4-4.0.jar $systemlib
cp $srcPath/MessageDef/target/scala-2.10/messagedef_2.10-1.0.jar $systemlib
cp $ivyPath/cache/org.apache.hbase/hbase-protocol/jars/hbase-protocol-1.0.2.jar $systemlib
cp $srcPath/Pmml/PmmlRuntime/target/scala-2.10/pmmlruntime_2.10-1.0.jar $systemlib
cp $srcPath/SampleApplication/InterfacesSamples/target/scala-2.10/interfacessamples_2.10-1.0.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/gcm-server.jar $systemlib
cp $ivyPath/cache/org.parboiled/parboiled-core/bundles/parboiled-core-1.1.6.jar $systemlib
cp $ivyPath/cache/commons-net/commons-net/jars/commons-net-3.1.jar $systemlib
cp $ivyPath/cache/org.apache.hadoop/hadoop-auth/jars/hadoop-auth-2.7.1.jar $systemlib
cp $ivyPath/cache/com.101tec/zkclient/jars/zkclient-0.3.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.wmq.v6.jar $systemlib
cp $ivyPath/cache/com.typesafe.akka/akka-actor_2.10/jars/akka-actor_2.10-2.3.2.jar $systemlib
cp $ivyPath/cache/org.slf4j/slf4j-api/jars/slf4j-api-1.7.6.jar $systemlib
cp $ivyPath/cache/org.apache.camel/camel-core/bundles/camel-core-2.9.2.jar $systemlib
cp $ivyPath/cache/com.pyruby/java-stub-server/jars/java-stub-server-0.12-sources.jar $systemlib
cp $ivyPath/cache/com.esotericsoftware.reflectasm/reflectasm/jars/reflectasm-1.07-shaded.jar $systemlib
cp $ivyPath/cache/org.scalatest/scalatest_2.10/bundles/scalatest_2.10-2.2.0.jar $systemlib
cp $ivyPath/cache/io.spray/spray-can/bundles/spray-can-1.3.1.jar $systemlib
cp $ivyPath/cache/commons-httpclient/commons-httpclient/jars/commons-httpclient-3.1.jar $systemlib
cp $srcPath/Utils/Audit/target/scala-2.10/auditadapters_2.10-1.0.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.mq.jmqi.system.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.commonservices.j2se.jar $systemlib
cp $ivyPath/cache/com.esotericsoftware.kryo/kryo/bundles/kryo-2.21.jar $systemlib
cp $srcPath/InputOutputAdapters/KafkaSimpleInputOutputAdapters/target/scala-2.10/kafkasimpleinputoutputadapters_2.10-1.0.jar $systemlib
cp $srcPath/InputOutputAdapters/FileSimpleInputOutputAdapters/target/scala-2.10/filesimpleinputoutputadapters_2.10-1.0.jar $systemlib
cp $ivyPath/cache/com.ning/compress-lzf/bundles/compress-lzf-0.9.1.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.jms.jar $systemlib
# cp $ivyPath/cache/org.slf4j/slf4j-log4j12/jars/slf4j-log4j12-1.6.1.jar $systemlib
cp $ivyPath/cache/com.twitter/chill-java/jars/chill-java-0.3.6.jar $systemlib
cp $ivyPath/cache/org.apache.hadoop/hadoop-common/jars/hadoop-common-2.7.1.jar $systemlib
cp $ivyPath/cache/com.chuusai/shapeless_2.10/jars/shapeless_2.10-1.2.4.jar $systemlib
cp $ivyPath/cache/org.jdom/jdom/jars/jdom-1.1.jar $systemlib
cp $srcPath/Utils/ZooKeeper/CuratorLeaderLatch/target/scala-2.10/zookeeperleaderlatch_2.10-1.0.jar $systemlib
cp $ivyPath/cache/com.googlecode.json-simple/json-simple/jars/json-simple-1.1.jar $systemlib
cp $ivyPath/cache/com.twitter/chill_2.10/jars/chill_2.10-0.3.6.jar $systemlib
cp $ivyPath/cache/org.scala-lang/scalap/jars/scalap-2.10.0.jar $systemlib
cp $ivyPath/cache/ch.qos.logback/logback-classic/jars/logback-classic-1.0.13.jar $systemlib
cp $ivyPath/cache/asm/asm/jars/asm-3.1.jar $systemlib
cp $ivyPath/cache/com.sun.xml.bind/jaxb-impl/jars/jaxb-impl-2.2.3-1.jar $systemlib
cp $ivyPath/cache/com.fasterxml.jackson.core/jackson-databind/bundles/jackson-databind-2.3.1.jar $systemlib
cp $ivyPath/cache/com.fasterxml.jackson.core/jackson-annotations/bundles/jackson-annotations-2.3.0.jar $systemlib
cp $ivyPath/cache/com.sdicons.jsontools/jsontools-core/jars/jsontools-core-1.7.jar $systemlib
cp $srcPath/Utils/Controller/target/scala-2.10/controller_2.10-1.0.jar $systemlib
cp $ivyPath/cache/junit/junit/jars/junit-3.8.1.jar $systemlib
cp $ivyPath/cache/com.typesafe/config/bundles/config-1.2.0.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/dhbcore.jar $systemlib
cp $ivyPath/cache/org.mortbay.jetty/jetty-embedded/jars/jetty-embedded-6.1.26-sources.jar $systemlib
cp $ivyPath/cache/org.mortbay.jetty/jetty-embedded/jars/jetty-embedded-6.1.26.jar $systemlib
cp $ivyPath/cache/org.apache.httpcomponents/httpclient/jars/httpclient-4.2.5.jar $systemlib
cp $srcPath/MetadataAPIServiceClient/target/scala-2.10/metadataapiserviceclient_2.10-0.1.jar $systemlib
cp $ivyPath/cache/org.scala-lang/scala-compiler/jars/scala-compiler-2.10.0.jar $systemlib
cp $ivyPath/cache/commons-logging/commons-logging/jars/commons-logging-1.1.1.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.wmq.factories.jar $systemlib
cp $ivyPath/cache/org.slf4j/slf4j-api/jars/slf4j-api-1.7.2.jar $systemlib
cp $ivyPath/cache/net.java.dev.jets3t/jets3t/jars/jets3t-0.9.0.jar $systemlib
cp $srcPath/Utils/KVInit/target/scala-2.10/kvinit_2.10-1.0.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/jms-1.1.jar $systemlib
cp $srcPath/OutputMsgDef/target/scala-2.10/outputmsgdef_2.10-1.0.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.commonservices.jar $systemlib
cp $ivyPath/cache/commons-codec/commons-codec/jars/commons-codec-1.9.jar $systemlib
cp $srcPath/Utils/ZooKeeper/CuratorClient/target/scala-2.10/zookeeperclient_2.10-1.0.jar $systemlib
cp $ivyPath/cache/org.json4s/json4s-core_2.10/jars/json4s-core_2.10-3.2.9.jar $systemlib
cp $ivyPath/cache/org.joda/joda-convert/jars/joda-convert-1.7.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.jms.internal.jar $systemlib
cp $ivyPath/cache/org.json4s/json4s-native_2.10/jars/json4s-native_2.10-3.2.9.jar $systemlib
cp $ivyPath/cache/com.esotericsoftware.minlog/minlog/jars/minlog-1.2.jar $systemlib
cp $srcPath/MetadataAPIService/target/scala-2.10/metadataapiservice_2.10-1.0.jar $systemlib
cp $ivyPath/cache/org.mortbay.jetty/servlet-api/jars/servlet-api-2.5.20110712-sources.jar $systemlib
cp $ivyPath/cache/com.google.collections/google-collections/jars/google-collections-1.0.jar $systemlib
cp $ivyPath/cache/ch.qos.logback/logback-core/jars/logback-core-1.0.12.jar $systemlib
cp $ivyPath/cache/io.netty/netty/bundles/netty-3.7.0.Final.jar $systemlib
cp $ivyPath/cache/org.xerial.snappy/snappy-java/bundles/snappy-java-1.0.5.jar $systemlib
cp $ivyPath/cache/io.spray/spray-httpx/bundles/spray-httpx-1.3.1.jar $systemlib
cp $ivyPath/cache/io.spray/spray-client/bundles/spray-client-1.3.1.jar $systemlib
cp $ivyPath/cache/com.jamesmurty.utils/java-xmlbuilder/jars/java-xmlbuilder-0.4.jar $systemlib
cp $srcPath/BaseFunctions/target/scala-2.10/basefunctions_2.10-0.1.0.jar $systemlib
cp $ivyPath/cache/io.spray/spray-routing/bundles/spray-routing-1.3.1.jar $systemlib
cp $ivyPath/cache/org.scalatest/scalatest_2.10/bundles/scalatest_2.10-2.2.4.jar $systemlib
cp $ivyPath/cache/org.apache.commons/commons-compress/jars/commons-compress-1.4.1.jar $systemlib
cp $ivyPath/cache/commons-beanutils/commons-beanutils/jars/commons-beanutils-1.7.0.jar $systemlib
cp $ivyPath/cache/org.apache.avro/avro/jars/avro-1.7.4.jar $systemlib
cp $ivyPath/cache/ch.qos.logback/logback-core/jars/logback-core-1.0.13.jar $systemlib
cp $ivyPath/cache/commons-configuration/commons-configuration/jars/commons-configuration-1.7.jar $systemlib
cp $ivyPath/cache/commons-beanutils/commons-beanutils-core/jars/commons-beanutils-core-1.8.0.jar $systemlib
cp $ivyPath/cache/com.jcraft/jsch/jars/jsch-0.1.42.jar $systemlib
cp $ivyPath/cache/org.slf4j/slf4j-api/jars/slf4j-api-1.6.4.jar $systemlib
cp $ivyPath/cache/com.google.code.gson/gson/jars/gson-2.3.1.jar $systemlib
cp $ivyPath/cache/org.scalamacros/quasiquotes_2.10.4/jars/quasiquotes_2.10.4-2.0.0-M6.jar $systemlib
cp $ivyPath/cache/com.google.code.findbugs/jsr305/jars/jsr305-1.3.9.jar $systemlib
cp $ivyPath/cache/io.spray/spray-util/bundles/spray-util-1.3.1.jar $systemlib
cp $srcPath/Utils/Security/SimpleApacheShiroAdapter/target/scala-2.10/simpleapacheshiroadapter_2.10-1.0.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.wmq.common.jar $systemlib
cp $ivyPath/cache/org.hamcrest/hamcrest-core/jars/hamcrest-core-1.3.jar $systemlib
cp $ivyPath/cache/org.joda/joda-convert/jars/joda-convert-1.6.jar $systemlib
cp $ivyPath/cache/org.parboiled/parboiled-scala_2.10/bundles/parboiled-scala_2.10-1.1.6.jar $systemlib
cp $ivyPath/cache/commons-collections/commons-collections/jars/commons-collections-3.2.1.jar $systemlib
cp $ivyPath/cache/org.scala-lang/scala-reflect/jars/scala-reflect-2.10.0.jar $systemlib
cp $ivyPath/cache/org.scala-lang/scala-library/jars/scala-library-2.10.4.jar $systemlib
cp $ivyPath/cache/org.apache.zookeeper/zookeeper/jars/zookeeper-3.4.6.jar $systemlib
cp $ivyPath/cache/org.objenesis/objenesis/jars/objenesis-1.2.jar $systemlib
cp $ivyPath/cache/org.apache.hadoop/hadoop-annotations/jars/hadoop-annotations-2.7.1.jar $systemlib
cp $srcPath/SampleApplication/CustomUdfLib/target/scala-2.10/customudflib_2.10-1.0.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.wmq.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/json-simple-1.1.1.jar $systemlib
cp $srcPath/Utils/Serialize/target/scala-2.10/serialize_2.10-1.0.jar $systemlib
cp $ivyPath/cache/junit/junit/jars/junit-4.11.jar $systemlib
cp $ivyPath/cache/org.apache.httpcomponents/httpclient/jars/httpclient-4.1.2.jar $systemlib
cp $srcPath/BaseTypes/target/scala-2.10/basetypes_2.10-0.1.0.jar $systemlib
cp $ivyPath/cache/ch.qos.logback/logback-classic/jars/logback-classic-1.0.12.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.provider.jar $systemlib
cp $srcPath/MetadataBootstrap/Bootstrap/target/scala-2.10/bootstrap_2.10-1.0.jar $systemlib
cp $ivyPath/cache/org.jvnet.mimepull/mimepull/jars/mimepull-1.9.4.jar $systemlib
cp $ivyPath/cache/io.netty/netty/bundles/netty-3.9.0.Final.jar $systemlib
cp $ivyPath/cache/org.apache.httpcomponents/httpcore/jars/httpcore-4.2.4.jar $systemlib
cp $ivyPath/cache/commons-configuration/commons-configuration/jars/commons-configuration-1.6.jar $systemlib
# cp $ivyPath/cache/log4j/log4j/bundles/log4j-1.2.17.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.mqjms.jar $systemlib
cp $srcPath/Utils/JsonDataGen/target/scala-2.10/jsondatagen_2.10-0.1.0.jar $systemlib
cp $ivyPath/cache/org.apache.curator/curator-recipes/bundles/curator-recipes-2.6.0.jar $systemlib
cp $ivyPath/cache/org.ow2.asm/asm/jars/asm-4.0.jar $systemlib
cp $ivyPath/cache/com.google.protobuf/protobuf-java/bundles/protobuf-java-2.6.0.jar $systemlib
cp $ivyPath/cache/org.tukaani/xz/jars/xz-1.0.jar $systemlib
cp $ivyPath/cache/org.codehaus.jackson/jackson-jaxrs/jars/jackson-jaxrs-1.8.3.jar $systemlib
cp $ivyPath/cache/net.debasishg/redisclient_2.10/jars/redisclient_2.10-2.13.jar $systemlib
cp $ivyPath/cache/com.typesafe.akka/akka-testkit_2.10/jars/akka-testkit_2.10-2.3.0.jar $systemlib
cp $srcPath/Exceptions/target/scala-2.10/exceptions_2.10-1.0.jar $systemlib
cp $srcPath/DataDelimiters/target/scala-2.10/datadelimiters_2.10-1.0.jar $systemlib
cp $ivyPath/cache/org.apache.zookeeper/zookeeper/jars/zookeeper-3.3.4.jar $systemlib
# cp $ivyPath/cache/org.slf4j/slf4j-log4j12/jars/slf4j-log4j12-1.7.5.jar $systemlib
cp $ivyPath/cache/org.mortbay.jetty/servlet-api/jars/servlet-api-2.5.20110712.jar $systemlib
cp $ivyPath/cache/org.mortbay.jetty/jetty/jars/jetty-6.1.26.jar $systemlib
cp $ivyPath/cache/javax.activation/activation/jars/activation-1.1.jar $systemlib
cp $ivyPath/cache/com.sdicons.jsontools/jsontools-core/jars/jsontools-core-1.7-sources.jar $systemlib
cp $ivyPath/cache/antlr/antlr/jars/antlr-2.7.7.jar $systemlib
cp $ivyPath/cache/org.apache.curator/curator-framework/bundles/curator-framework-2.6.0.jar $systemlib
cp $ivyPath/cache/org.apache.curator/curator-client/bundles/curator-client-2.6.0.jar $systemlib
cp $srcPath/Utils/ZooKeeper/CuratorListener/target/scala-2.10/zookeeperlistener_2.10-1.0.jar $systemlib
cp $srcPath/KamanjaManager/target/scala-2.10/kamanjamanager_2.10-1.0.jar $systemlib
cp $ivyPath/cache/javax.servlet.jsp/jsp-api/jars/jsp-api-2.1.jar $systemlib
cp $ivyPath/cache/ch.qos.logback/logback-classic/jars/logback-classic-1.0.13.jar $systemlib
cp $ivyPath/cache/com.sun.jersey/jersey-json/bundles/jersey-json-1.9.jar $systemlib
cp $ivyPath/cache/org.ow2.asm/asm-tree/jars/asm-tree-4.0.jar $systemlib
cp $ivyPath/cache/io.spray/spray-io/bundles/spray-io-1.3.1.jar $systemlib
cp $ivyPath/cache/commons-io/commons-io/jars/commons-io-2.4.jar $systemlib
cp $srcPath/Pmml/MethodExtractor/target/scala-2.10/methodextractor_2.10-1.0.jar $systemlib
cp $ivyPath/cache/net.sf.jopt-simple/jopt-simple/jars/jopt-simple-3.2.jar $systemlib
cp $ivyPath/cache/com.github.stephenc.findbugs/findbugs-annotations/jars/findbugs-annotations-1.3.9-1.jar $systemlib
cp $srcPath/Utils/SimpleKafkaProducer/target/scala-2.10/simplekafkaproducer_2.10-0.1.0.jar $systemlib
cp $ivyPath/cache/commons-cli/commons-cli/jars/commons-cli-1.2.jar $systemlib
cp $ivyPath/cache/com.yammer.metrics/metrics-core/jars/metrics-core-2.2.0.jar $systemlib
cp $ivyPath/cache/org.mapdb/mapdb/bundles/mapdb-1.0.6.jar $systemlib
cp $ivyPath/cache/net.java.dev.jna/jna/jars/jna-3.2.7.jar $systemlib
cp $ivyPath/cache/voldemort/voldemort/jars/voldemort-0.96.jar $systemlib
cp $ivyPath/cache/io.spray/spray-http/bundles/spray-http-1.3.1.jar $systemlib
cp $srcPath/Utils/JdbcDataCollector/target/scala-2.10/jdbcdatacollector_2.10-1.0.jar $systemlib
cp $ivyPath/cache/uk.co.bigbeeconsultants/bee-client_2.10/jars/bee-client_2.10-0.28.0.jar $systemlib
cp $ivyPath/cache/com.fasterxml.jackson.core/jackson-core/bundles/jackson-core-2.3.1.jar $systemlib
cp $ivyPath/cache/commons-lang/commons-lang/jars/commons-lang-2.6.jar $systemlib
cp $ivyPath/cache/commons-digester/commons-digester/jars/commons-digester-1.8.1.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.ref.jar $systemlib
cp $srcPath/Pmml/PmmlCompiler/target/scala-2.10/pmmlcompiler_2.10-1.0.jar $systemlib
cp $ivyPath/cache/org.scala-lang/scala-reflect/jars/scala-reflect-2.10.4.jar $systemlib
cp $ivyPath/cache/org.json4s/json4s-jackson_2.10/jars/json4s-jackson_2.10-3.2.9.jar $systemlib
cp $ivyPath/cache/com.sun.jersey/jersey-server/bundles/jersey-server-1.9.jar $systemlib
cp $ivyPath/cache/javax.xml.stream/stax-api/jars/stax-api-1.0-2.jar $systemlib
cp $ivyPath/cache/commons-pool/commons-pool/jars/commons-pool-1.6.jar $systemlib
cp $ivyPath/cache/org.codehaus.jettison/jettison/bundles/jettison-1.1.jar $systemlib
cp $srcPath/Metadata/target/scala-2.10/metadata_2.10-1.0.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/target/scala-2.10/ibmmqsimpleinputoutputadapters_2.10-1.0.jar $systemlib
cp $srcPath/Utils/NodeInfoExtract/target/scala-2.10/nodeinfoextract_2.10-1.0.jar $systemlib
cp $ivyPath/cache/org.apache.hbase/hbase-common/jars/hbase-common-1.0.2.jar $systemlib
cp $ivyPath/cache/com.datastax.cassandra/cassandra-driver-core/bundles/cassandra-driver-core-2.1.2.jar $systemlib
cp $ivyPath/cache/org.apache.shiro/shiro-core/bundles/shiro-core-1.2.3.jar $systemlib
cp $ivyPath/cache/commons-beanutils/commons-beanutils/jars/commons-beanutils-1.8.3.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.msg.client.matchspace.jar $systemlib
cp $ivyPath/cache/org.mortbay.jetty/jetty-sslengine/jars/jetty-sslengine-6.1.26.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.mq.jmqi.remote.jar $systemlib
cp $srcPath/Utils/ExtractData/target/scala-2.10/extractdata_2.10-1.0.jar $systemlib
cp $ivyPath/cache/org.apache.hbase/hbase-client/jars/hbase-client-1.0.2.jar $systemlib
cp $srcPath/KamanjaBase/target/scala-2.10/kamanjabase_2.10-1.0.jar $systemlib
cp $srcPath/KvBase/target/scala-2.10/kvbase_2.10-0.1.0.jar $systemlib
cp $ivyPath/cache/com.sleepycat/je/jars/je-4.0.92.jar $systemlib
cp $ivyPath/cache/com.thoughtworks.paranamer/paranamer/jars/paranamer-2.6.jar $systemlib
cp $ivyPath/cache/javax.servlet/servlet-api/jars/servlet-api-2.5.jar $systemlib

cp $ivyPath/cache/org.ow2.asm/asm-commons/jars/asm-commons-4.0.jar $systemlib
cp $ivyPath/cache/org.scala-lang/scala-actors/jars/scala-actors-2.10.4.jar $systemlib
cp $srcPath/Utils/ZooKeeper/CuratorListener/target/scala-2.10/zookeeperlistener_2.10-1.0.jar $systemlib
cp $ivyPath/cache/org.apache.kafka/kafka_2.10/jars/kafka_2.10-0.8.1.1.jar $systemlib
cp $srcPath/Pmml/PmmlUdfs/target/scala-2.10/pmmludfs_2.10-1.0.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.mq.jmqi.jar $systemlib
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/com.ibm.mq.jmqi.local.jar $systemlib
cp $ivyPath/cache/joda-time/joda-time/jars/joda-time-2.8.2.jar $systemlib
cp $ivyPath/cache/jline/jline/jars/jline-0.9.94.jar $systemlib
cp $ivyPath/cache/org.apache.commons/commons-math3/jars/commons-math3-3.1.1.jar $systemlib
cp $ivyPath/cache/xmlenc/xmlenc/jars/xmlenc-0.52.jar $systemlib
cp $ivyPath/cache/org.apache.httpcomponents/httpcore/jars/httpcore-4.1.2.jar $systemlib
cp $ivyPath/cache/io.spray/spray-json_2.10/jars/spray-json_2.10-1.2.5.jar $systemlib
cp $ivyPath/cache/com.codahale.metrics/metrics-core/bundles/metrics-core-3.0.2.jar $systemlib
cp $ivyPath/cache/org.json4s/json4s-ast_2.10/jars/json4s-ast_2.10-3.2.9.jar $systemlib
cp $ivyPath/cache/io.spray/spray-testkit/jars/spray-testkit-1.3.1.jar $systemlib

cp $srcPath/Storage/Cassandra/target/scala-2.10/*.jar $systemlib
cp $srcPath/Storage/HashMap/target/scala-2.10/*.jar $systemlib
cp $srcPath/Storage/HBase/target/scala-2.10/*.jar $systemlib
#cp $srcPath/Storage/Redis/target/scala-2.10/*.jar $systemlib
cp $srcPath/Storage/StorageBase/target/scala-2.10/storagebase_2.10-1.0.jar $systemlib
cp $srcPath/Storage/StorageManager/target/scala-2.10/*.jar $systemlib
cp $srcPath/Storage/TreeMap/target/scala-2.10/*.jar $systemlib
#cp $srcPath/Storage/Voldemort/target/scala-2.10/*.jar $systemlib
cp $srcPath/InputOutputAdapters/InputOutputAdapterBase/target/scala-2.10/*.jar $systemlib
cp $srcPath/KamanjaUtils/target/scala-2.10/kamanjautils_2.10-1.0.jar $systemlib
cp $srcPath/SecurityAdapters/SecurityAdapterBase/target/scala-2.10/*.jar $systemlib

# an extra copy to make sure ?
cp $srcPath/InputOutputAdapters/IbmMqSimpleInputOutputAdapters/lib/*.jar $systemlib

cp $srcPath/Utils/SaveContainerDataComponent/target/scala-2.10/savecontainerdatacomponent*.jar $systemlib
cp $srcPath/Utils/SaveContainerDataComponent/target/scala-2.10/SaveContainerDataComponent* $systemlib
cp $srcPath/Utils/UtilsForModels/target/scala-2.10/utilsformodels*.jar $systemlib

# sample configs
#echo "copy sample configs..."
cp $srcPath/Utils/KVInit/src/main/resources/*cfg $systemlib

# Generate keystore file
#echo "generating keystore..."
#keytool -genkey -keyalg RSA -alias selfsigned -keystore $installPath/config/keystore.jks -storepass password -validity 360 -keysize 2048

#copy kamanja to bin directory
cp $srcPath/Utils/Script/kamanja $bin
#cp $srcPath/Utils/Script/MedicalApp.sh $bin
cp $srcPath/MetadataAPI/target/scala-2.10/classes/HelpMenu.txt $installPath/input
# *******************************
# COPD messages data prep
# *******************************

# Prepare test messages and copy them into place

echo "Prepare test messages and copy them into place..."
# *******************************
# Copy documentation files
# *******************************
cd $srcPath/Documentation
cp -rf * $installPath/documentation

# *******************************
# copy models, messages, containers, config, scripts, types  messages data prep
# *******************************

#HelloWorld
cd $srcPath/SampleApplication/HelloWorld/data
cp * $installPath/input/SampleApplications/data

cd $srcPath/SampleApplication/HelloWorld/message
cp * $installPath/input/SampleApplications/metadata/message

cd $srcPath/SampleApplication/HelloWorld/model
cp * $installPath/input/SampleApplications/metadata/model

cd $srcPath/SampleApplication/HelloWorld/template
cp -rf * $installPath/input/SampleApplications/template


cd $srcPath/SampleApplication/HelloWorld/config
cp -rf * $installPath/config
#HelloWorld

#Medical
cd $srcPath/SampleApplication/Medical/SampleData
cp *.csv $installPath/input/SampleApplications/data
cp *.csv.gz $installPath/input/SampleApplications/data

cd $srcPath/SampleApplication/Medical/MessagesAndContainers/Fixed/Containers
cp * $installPath/input/SampleApplications/metadata/container

cd $srcPath/SampleApplication/Medical/Functions
cp * $installPath/input/SampleApplications/metadata/function

cd $srcPath/SampleApplication/Medical/MessagesAndContainers/Fixed/Messages
cp * $installPath/input/SampleApplications/metadata/message

cd $srcPath/SampleApplication/Medical/Models
cp *.* $installPath/input/SampleApplications/metadata/model

cd $srcPath/SampleApplication/Medical/Types
cp * $installPath/input/SampleApplications/metadata/type

cd $srcPath/SampleApplication/Medical/template
cp -rf * $installPath/input/SampleApplications/template

cd $srcPath/SampleApplication/Medical/Configs
cp -rf * $installPath/config
#Medical

#Telecom
cd $srcPath/SampleApplication/Telecom/data
cp * $installPath/input/SampleApplications/data

cd $srcPath/SampleApplication/Telecom/metadata/container
cp * $installPath/input/SampleApplications/metadata/container

cd $srcPath/SampleApplication/Telecom/metadata/message
cp * $installPath/input/SampleApplications/metadata/message

cd $srcPath/SampleApplication/Telecom/metadata/model
cp *.* $installPath/input/SampleApplications/metadata/model

cd $srcPath/SampleApplication/Telecom/metadata/template
cp -rf * $installPath/input/SampleApplications/template

cd $srcPath/SampleApplication/Telecom/metadata/config
cp -rf * $installPath/config
#Telecom

#Finance
cd $srcPath/SampleApplication/InterfacesSamples/src/main/resources/sample-app/data
cp * $installPath/input/SampleApplications/data

cd $srcPath/SampleApplication/InterfacesSamples/src/main/resources/sample-app/metadata/container
cp * $installPath/input/SampleApplications/metadata/container

cd $srcPath/SampleApplication/InterfacesSamples/src/main/resources/sample-app/metadata/message
cp * $installPath/input/SampleApplications/metadata/message

cd $srcPath/SampleApplication/InterfacesSamples/src/main/resources/sample-app/metadata/model
cp *.* $installPath/input/SampleApplications/metadata/model

cd $srcPath/SampleApplication/InterfacesSamples/src/main/resources/sample-app/metadata/type
cp * $installPath/input/SampleApplications/metadata/type

cd $srcPath/SampleApplication/InterfacesSamples/src/main/resources/sample-app/metadata/template
cp -rf * $installPath/input/SampleApplications/template

cd $srcPath/SampleApplication/InterfacesSamples/src/main/resources/sample-app/metadata/config
cp -rf * $installPath/config
#Finance

cd $srcPath/SampleApplication/EasyInstall/template
cp -rf * $installPath/template

cd $srcPath/SampleApplication/EasyInstall
cp SetPaths.sh $installPath/bin/

bash $installPath/bin/SetPaths.sh $KafkaRootDir

chmod 0700 $installPath/input/SampleApplications/bin/*sh

echo "Kamanja install complete..."
