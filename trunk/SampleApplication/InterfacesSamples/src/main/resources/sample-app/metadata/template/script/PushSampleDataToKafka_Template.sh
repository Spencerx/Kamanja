# java -jar {InstallDirectory}/bin/SimpleKafkaProducer-0.1.0 --topics "testin_1" --threads 1 --topicpartitions 8 --brokerlist "localhost:9092" --files "{InstallDirectory}/input/LowBalance/data/TxnDataOneCustomer.dat" --partitionkeyidxs "1" --format CSV

java -jar {InstallDirectory}/bin/SimpleKafkaProducer-0.1.0 --topics "testin_1" --threads 1 --topicpartitions 8 --brokerlist "localhost:9092" --files "{InstallDirectory}/input/LowBalance/data/TxnData.dat" --partitionkeyidxs "1" --format CSV
