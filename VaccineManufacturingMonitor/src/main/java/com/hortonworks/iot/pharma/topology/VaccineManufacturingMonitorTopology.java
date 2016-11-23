package com.hortonworks.iot.pharma.topology;

import java.util.UUID;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.AlreadyAliveException;
import org.apache.storm.generated.AuthorizationException;
import org.apache.storm.generated.InvalidTopologyException;
import org.apache.storm.hdfs.bolt.HdfsBolt;
import org.apache.storm.hdfs.bolt.format.DefaultFileNameFormat;
import org.apache.storm.hdfs.bolt.format.DelimitedRecordFormat;
import org.apache.storm.hdfs.bolt.format.FileNameFormat;
import org.apache.storm.hdfs.bolt.format.RecordFormat;
import org.apache.storm.hdfs.bolt.rotation.FileRotationPolicy;
import org.apache.storm.hdfs.bolt.rotation.FileSizeRotationPolicy;
import org.apache.storm.hdfs.bolt.rotation.FileSizeRotationPolicy.Units;
import org.apache.storm.hdfs.bolt.sync.CountSyncPolicy;
import org.apache.storm.hdfs.bolt.sync.SyncPolicy;
import org.apache.storm.kafka.BrokerHosts;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.kafka.SpoutConfig;
import org.apache.storm.kafka.ZkHosts;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.topology.TopologyBuilder;

import com.hortonworks.iot.pharma.bolts.DetectFiltrationSubOptimalConditions;
import com.hortonworks.iot.pharma.bolts.DetectSubOptimalConditions;
import com.hortonworks.iot.pharma.bolts.PublishDeviceEvents;
import com.hortonworks.iot.pharma.bolts.PublishFiltrationEvents;
import com.hortonworks.iot.pharma.util.BioReactorEventJSONScheme;
import com.hortonworks.iot.pharma.util.Constants;
import com.hortonworks.iot.pharma.util.FiltrationEventJSONScheme;
/*
import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.AuthorizationException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.topology.TopologyBuilder;
import storm.kafka.BrokerHosts;
import storm.kafka.KafkaSpout;
import storm.kafka.SpoutConfig;
import storm.kafka.ZkHosts;
*/

public class VaccineManufacturingMonitorTopology {
	
	public static void main(String[] args) {
		TopologyBuilder builder = new TopologyBuilder();
		Constants constants = new Constants();
		
		// Use pipe as record boundary
		RecordFormat format = new DelimitedRecordFormat().withFieldDelimiter(",");

		//Synchronize data buffer with the filesystem every 1000 tuples
		SyncPolicy syncPolicy = new CountSyncPolicy(1000);

		// Rotate data files when they reach five MB
		FileRotationPolicy rotationPolicy = new FileSizeRotationPolicy(5.0f, Units.MB);

		// Use default, Storm-generated file names
		FileNameFormat transactionLogFileNameFormat = new DefaultFileNameFormat().withPath(constants.getHivePath());
		HdfsBolt LogTransactionHdfsBolt = new HdfsBolt()
 		     .withFsUrl(constants.getNameNodeUrl())
 		     .withFileNameFormat(transactionLogFileNameFormat)
 		     .withRecordFormat(format)
 		     .withRotationPolicy(rotationPolicy)
 		     .withSyncPolicy(syncPolicy);
      
		Config conf = new Config(); 
		BrokerHosts hosts = new ZkHosts(constants.getZkConnString(), constants.getZkKafkaPath());
		SpoutConfig incomingBioReactorEventsKafkaSpoutConfig = new SpoutConfig(hosts, constants.getIncomingBioReactorTopicName(), constants.getZkKafkaPath(), UUID.randomUUID().toString());
		incomingBioReactorEventsKafkaSpoutConfig.scheme = new SchemeAsMultiScheme(new BioReactorEventJSONScheme());
		incomingBioReactorEventsKafkaSpoutConfig.useStartOffsetTimeIfOffsetOutOfRange = true;
		incomingBioReactorEventsKafkaSpoutConfig.startOffsetTime = kafka.api.OffsetRequest.LatestTime();
		KafkaSpout incomingBioReactorEventsKafkaSpout = new KafkaSpout(incomingBioReactorEventsKafkaSpoutConfig); 
     
		SpoutConfig incomingFiltrationEventsKafkaSpoutConfig = new SpoutConfig(hosts, constants.getIncomingFiltrationTopicName(), constants.getZkKafkaPath(), UUID.randomUUID().toString());
		incomingFiltrationEventsKafkaSpoutConfig.scheme = new SchemeAsMultiScheme(new FiltrationEventJSONScheme());
		incomingFiltrationEventsKafkaSpoutConfig.useStartOffsetTimeIfOffsetOutOfRange = true;
		incomingFiltrationEventsKafkaSpoutConfig.startOffsetTime = kafka.api.OffsetRequest.LatestTime();
		KafkaSpout incomingFiltrationEventsKafkaSpout = new KafkaSpout(incomingFiltrationEventsKafkaSpoutConfig);
		
		builder.setSpout("IncomingBioReactorKafkaSpout", incomingBioReactorEventsKafkaSpout);
		builder.setBolt("PublishDeviceEvents", new PublishDeviceEvents(), 1).shuffleGrouping("IncomingBioReactorKafkaSpout");
		builder.setBolt("DetectSubOptimalConditions", new DetectSubOptimalConditions(), 1).shuffleGrouping("PublishDeviceEvents");
    
		builder.setSpout("IncomingFiltrationKafkaSpout", incomingFiltrationEventsKafkaSpout);
		builder.setBolt("PublishFiltrationEvents", new PublishFiltrationEvents(), 1).shuffleGrouping("IncomingFiltrationKafkaSpout");
		builder.setBolt("DetectFiltrationSubOptimalConditions", new DetectFiltrationSubOptimalConditions(), 1).shuffleGrouping("PublishFiltrationEvents");
		
		conf.setNumWorkers(1);
	    conf.setMaxSpoutPending(5000);
	    conf.setMaxTaskParallelism(1);
	      
	    //submitToLocal(builder, conf);
	    submitToCluster(builder, conf);
	 }
       
	public static void submitToLocal(TopologyBuilder builder, Config conf){
		LocalCluster cluster = new LocalCluster();
		cluster.submitTopology("VaccineManufacturingMonitor", conf, builder.createTopology()); 
	}
		
	public static void submitToCluster(TopologyBuilder builder, Config conf){
		try {
			StormSubmitter.submitTopology("VaccineManufacturingMonitor", conf, builder.createTopology());
		} catch (AlreadyAliveException e) {
			e.printStackTrace();
		} catch (InvalidTopologyException e) {
			e.printStackTrace();
		} catch (AuthorizationException e) {
			e.printStackTrace();
		}
	}
}