#!/bin/bash

# StartKamanjaCluster.sh
#

Usage()
{
    echo 
    echo "Usage:"
    echo "      StartKamanjaCluster.sh --ClusterId <cluster name identifer> "
    echo "                           --MetadataAPIConfig  <metadataAPICfgPath> "
    echo 
    echo "  NOTES: Start the cluster specified by the cluster identifier parameter.  Use the metadata api configuration to locate"
    echo "         the appropriate metadata store.  For "
    echo 
}


scalaversion="2.10"
name1=$1

if [ "$#" -eq 4 ]; then
	echo
else 
    echo "Problem: Incorrect number of arguments"
    echo 
    Usage
    exit 1
fi

if [[ "$name1" != "--MetadataAPIConfig" && "$name1" != "--ClusterId" ]]; then
	echo "Problem: Bad arguments"
	echo 
	Usage
	exit 1
fi

# Collect the named parameters 
metadataAPIConfig=""
clusterId=""

while [ "$1" != "" ]; do
    echo "parameter is $1"
    case $1 in
        --ClusterId )           shift
                                clusterId=$1
                                ;;
        --MetadataAPIConfig )   shift
                                metadataAPIConfig=$1
                                ;;
        * )                     echo "Problem: Argument $1 is invalid named parameter."
                                Usage
                                exit 1
                                ;;
    esac
    shift
done

# 1) Collect the relevant node information for this cluster.
workDir="/tmp" 
ipFile="ip.txt"
ipPathPairFile="ipPath.txt"
ipIdCfgTargPathQuartetFileName="ipIdCfgTarg.txt"
installDir=`cat $metadataAPIConfig | grep '[Rr][Oo][Oo][Tt]_[Dd][Ii][Rr]' | sed 's/.*=\(.*\)$/\1/g' | sed 's/[\x01-\x1F\x7F]//g'`

echo "...extract node information for the cluster to be started from the Metadata configuration information supplied"

# info is assumed to be present in the supplied metadata store... see trunk/utils/NodeInfoExtract for details 
echo "...Command = NodeInfoExtract-1.0 --MetadataAPIConfig \"$metadataAPIConfig\" --workDir \"$workDir\" --ipFileName \"$ipFile\" --ipPathPairFileName \"$ipPathPairFile\" --ipIdCfgTargPathQuartetFileName \"$ipIdCfgTargPathQuartetFileName\" --installDir \"$installDir\" --clusterId \"$clusterId\""
NodeInfoExtract-1.0 --MetadataAPIConfig $metadataAPIConfig --workDir "$workDir" --ipFileName "$ipFile" --ipPathPairFileName "$ipPathPairFile" --ipIdCfgTargPathQuartetFileName "$ipIdCfgTargPathQuartetFileName" --installDir "$installDir" --clusterId "$clusterId"
if [ "$?" -ne 0 ]; then
    echo
    echo "Problem: Invalid arguments supplied to the NodeInfoExtract-1.0 application... unable to obtain node configuration... exiting."
    Usage
    exit 1
fi

# Start the cluster nodes using the information extracted from the metadata and supplied config.  Remember the jvm's pid in the $installDir/run
# directory setup for that purpose.  The name of the pid file will always be 'node$id.pid'.  The targetPath points to the given cluster's 
# config directory where the Kamanja engine config file is located.
echo "...start the Kamanja cluster $clusterName"
exec 12<&0 # save current stdin
exec < "$workDir/$ipIdCfgTargPathQuartetFileName"
while read LINE; do
    machine=$LINE
    read LINE
    id=$LINE
    read LINE
    cfgFile=$LINE
    read LINE
    targetPath=$LINE
    read LINE
    roles=$LINE
    roles_lc=`echo $roles | tr '[:upper:]' '[:lower:]'`
    restapi_cnt=`echo $roles_lc | grep "restapi" | grep -v "grep" | wc -l`
    processingengine_cnt=`echo $roles_lc | grep "processingengine" | grep -v "grep" | wc -l`
    echo "NodeInfo = $machine, $id, $cfgFile, $targetPath, $roles"
    echo "...On machine $machine, starting Kamanja node with configuration $cfgFile for NodeId $id to $machine:$targetPath"
    nodeCfg=`echo $cfgFile | sed 's/.*\/\(.*\)/\1/g' | sed 's/[\x01-\x1F\x7F]//g'`
    pidfile=node$id.pid
     #scp -o StrictHostKeyChecking=no "$cfgFile" "$machine:$targetPath/"
	ssh -o StrictHostKeyChecking=no -T $machine  <<-EOF
                ulimit -u 8192
		cd $targetPath
		echo "nodeCfg=$nodeCfg"
        if [ "$processingengine_cnt" -gt 0 ]; then
		java -Xmx50g -Xms50g -Dlog4j.configurationFile=file:$targetPath/engine_log4j2.xml -Djavax.net.ssl.trustStore=/apps/projects/fusioncell2/ssl/keystoreFC16_RBB_Fusion_Cell.jks -Djavax.net.ssl.keyStore=/apps/projects/fusioncell2/ssl/keystoreFC16_RBB_Fusion_Cell.jks -Djavax.net.ssl.keyStorePassword=fc2appKey16app -Djavax.net.ssl.trustStorePassword=fc2appKey16app -jar "$installDir/bin/KamanjaManager-1.0" --config "$targetPath/$nodeCfg" < /dev/null > /dev/null 2>&1 & 
        fi
        if [ "$restapi_cnt" -gt 0 ]; then
		java -Dlog4j.configurationFile=file:$targetPath/restapi_log4j2.xml -jar "$installDir/bin/MetadataAPIService-1.0" --config "$targetPath/MetadataAPIConfig_${id}.properties" < /dev/null > /dev/null 2>&1 & 
        fi
		if [ ! -d "$installDir/run" ]; then
			mkdir "$installDir/run"
		fi
			ps aux | egrep "KamanjaManager|MetadataAPIService" | grep -v "grep" | tr -s " " | cut -d " " -f2 | tr "\\n" "," | sed "s/,$//"   > "$installDir/run/$pidfile"
#		sleep 5
EOF

# ssh -o StrictHostKeyChecking=no -T $machine 'ps aux | grep "KamanjaManager-1.0" | grep -v "grep"' > $workDir/temppid.pid
# scp  -o StrictHostKeyChecking=no   $workDir/temppid.pid "$machine:$installDir/run/node$id.pid"

done
exec 0<&12 12<&-

echo

