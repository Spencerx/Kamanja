java -jar {InstallDirectory}/bin/KVInit-1.0 --kvname System.GlobalPreferences  --config {InstallDirectory}/config/Engine1Config.properties --csvpath {InstallDirectory}/input/SampleApplications/data/GlobalPreferences_Finance.dat  --keyfieldname PrefType
java -jar {InstallDirectory}/bin/KVInit-1.0 --kvname System.CustPreferences    --config {InstallDirectory}/config/Engine1Config.properties --csvpath {InstallDirectory}/input/SampleApplications/data/CustPreferences_Finance.dat    --keyfieldname custId
java -jar {InstallDirectory}/bin/KVInit-1.0 --kvname System.CustomerInfo       --config {InstallDirectory}/config/Engine1Config.properties --csvpath {InstallDirectory}/input/SampleApplications/data/CustomerInfo_Finance.dat       --keyfieldname custId
