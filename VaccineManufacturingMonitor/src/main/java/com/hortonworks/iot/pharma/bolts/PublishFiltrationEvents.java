package com.hortonworks.iot.pharma.bolts;

import java.util.HashMap;
import java.util.Map;

import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.HttpClient;

import com.hortonworks.iot.pharma.events.FiltrationStatus;
import com.hortonworks.iot.pharma.util.Constants;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

/*
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
*/

public class PublishFiltrationEvents extends BaseRichBolt {
	private static final long serialVersionUID = 1L;
	private Constants constants;
	private String pubSubUrl;
	private String filtrationStatusChannel;
	private BayeuxClient bayuexClient;
	private OutputCollector collector;
	
	public void execute(Tuple tuple) {
		FiltrationStatus filtrationStatus = (FiltrationStatus) tuple.getValueByField("FiltrationStatus");
		
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("serialNumber", filtrationStatus.getSerialNumber());
		data.put("flowTemp", filtrationStatus.getFlowTemp());
		data.put("flowRate", filtrationStatus.getFlowRate());
		data.put("internalPressure", filtrationStatus.getInternalPressure());
		
		bayuexClient.getChannel(filtrationStatusChannel).publish(data);
		collector.emit(tuple, new Values((FiltrationStatus)filtrationStatus));
		collector.ack(tuple);
	}

	public void prepare(Map arg0, TopologyContext arg1, OutputCollector collector) {
		this.collector = collector;
		this.constants = new Constants();
		this.pubSubUrl = constants.getPubSubUrl();
		this.filtrationStatusChannel = constants.getFiltrationStatusChannel();
		HttpClient httpClient = new HttpClient();
		try {
			httpClient.start();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Prepare the transport
		Map<String, Object> options = new HashMap<String, Object>();
		ClientTransport transport = new LongPollingTransport(options, httpClient);

		// Create the BayeuxClient
		bayuexClient = new BayeuxClient(pubSubUrl, transport);
		
		bayuexClient.handshake();
		boolean handshaken = bayuexClient.waitFor(3000, BayeuxClient.State.CONNECTED);
		if (handshaken)
		{
			System.out.println("Connected to Cometd Http PubSub Platform");
		}
		else{
			System.out.println("Could not connect to Cometd Http PubSub Platform");
		}
	}

	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("FiltrationStatus"));
	}
}
