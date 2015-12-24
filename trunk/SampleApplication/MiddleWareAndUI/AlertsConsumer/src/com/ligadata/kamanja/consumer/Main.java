/*
 * Copyright 2015 ligaDATA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ligadata.kamanja.consumer;

import java.util.Calendar;
import java.util.Timer;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.shared.SharedValue;
import org.apache.curator.framework.recipes.shared.SharedValueListener;
import org.apache.logging.log4j.{ Logger, LogManager };

import com.ligadata.kamanja.consumer.listener.StartListener;
import com.ligadata.kamanja.consumer.message.AbstractConsumerController;
import com.ligadata.kamanja.consumer.message.AlertsConsumerController;
import com.ligadata.kamanja.consumer.message.StatusConsumerController;
import com.ligadata.kamanja.consumer.scheduled.AlertsAggTimerTask;
import com.ligadata.kamanja.consumer.util.ConfigFields;
import com.ligadata.kamanja.consumer.util.ConfigManager;
import com.ligadata.kamanja.consumer.util.ZKFields;
import com.ligadata.kamanja.consumer.util.ZKUtil;

public class Main {

	private static CuratorFramework zkClient;
	private static Logger logger = LogManager.getLogger(Main.class);
	public static XMLConfiguration config;
	private static boolean isRun = true;
	private static AbstractConsumerController alertsController,
			statusController;

	public static final int START_SCHEDULED_TASK_AFTER = 5000;

	public static void main(String[] args) {

		if (args.length < 1) {
			System.err
					.println("Please try again and provide config file path.");
			System.exit(1);
		}

		// start up
		logger.info("Starting ...");
		config = ConfigManager.getConfig(args[0]);
		zkClient = ZKUtil.getSimpleClient(config
				.getString(ConfigFields.ZK_CONNECTION));


		// reset all values
		resetValuesOnZK();


		// register listeners
		logger.info("Registering listeners ...");
		registerListeners();
		logger.info("Listeners registered");

		// start message consumers
		logger.info("Starting messages consumers ...");
		startConsumers();
		logger.info("Consumers started");

		// start timers
		logger.info("Starting scheduled tasks ...");
		startScheduledTasks();
		logger.info("Scheduled tasks started");

		// keep running
		logger.info("Running :)");
		while (isRun) {
		}

		// shutting down
		logger.info("Shutting down ...");
		logger.info("Closing consumers ...");
		closeConsumers();
		logger.info("Consumers closed");

		logger.info("Closing ZooKeeper client ...");
		zkClient.close();
		logger.info("ZooKeeper client hs been closed");

		logger.info("Good bye");
	}

	public static void resetValuesOnZK() {

		CuratorFramework client = ZKUtil.getSimpleClient(null);
		ZKUtil.setValue(client, ZKFields.EB1_ALERTS, "0");
		ZKUtil.setValue(client, ZKFields.EB2_ALERTS, "0");
		ZKUtil.setValue(client, ZKFields.EVENTS_PROCESSED, "0");
		ZKUtil.setValue(client, ZKFields.LATEST_MSGS, "-");
		ZKUtil.setValue(client, ZKFields.LB_ALERTS, "0");
		ZKUtil.setValue(client, ZKFields.NOD_ALERTS, "0");
		ZKUtil.setValue(client, ZKFields.OD1_ALERTS, "0");
		ZKUtil.setValue(client, ZKFields.OD2_ALERTS, "0");
		ZKUtil.setValue(client, ZKFields.OD3_ALERTS, "0");
		ZKUtil.setValue(client, ZKFields.TOTAL_ALERTS, "0");
		ZKUtil.setValue(client, ZKFields.UTF_ALERTS, "0");
	}

	private static void startConsumers() {

		alertsController = new AlertsConsumerController(
				config.getString(ConfigFields.ZK_CONNECTION),
				config.getString(ConfigFields.QUEUE_CONSUMER_GROUP_ID),
				config.getString(ConfigFields.ALERTS_QUEUE_TOPIC),
				Integer.parseInt(config
						.getString(ConfigFields.ALERTS_QUEUE_THREADS_NUMBER)));
		alertsController.start();

		statusController = new StatusConsumerController(
				config.getString(ConfigFields.ZK_CONNECTION),
				config.getString(ConfigFields.QUEUE_CONSUMER_GROUP_ID),
				config.getString(ConfigFields.STATUS_QUEUE_TOPIC),
				Integer.parseInt(config
						.getString(ConfigFields.STATUS_QUEUE_THREADS_NUMBER)));
		statusController.start();

	}

	private static void closeConsumers() {

		try {
			Thread.sleep(10000);
		} catch (InterruptedException ie) {
		}

		alertsController.shutdown();
		statusController.shutdown();
	}

	private static void registerListeners() {

		registerListener(ZKFields.START, new StartListener());
	}

	private static void registerListener(String zkFile,
			SharedValueListener listener) {

		SharedValue value = new SharedValue(zkClient, ZKFields.START,
				"".getBytes());
		try {
			value.start();
			value.getListenable().addListener(listener);
		} catch (Exception e) {
			logger.error("Error while adding listener!", e);
		} finally {
			try {
				// value.close();
			} catch (Exception e) {
				logger.error("Error while closing shared value!", e);
			}
		}
	}

	private static void startScheduledTasks() {

		Calendar alertsAggCal = Calendar.getInstance();
		alertsAggCal.add(Calendar.MILLISECOND, START_SCHEDULED_TASK_AFTER);

		Timer timer = new Timer();
		timer.schedule(new AlertsAggTimerTask(), alertsAggCal.getTime(),
				config.getLong(ConfigFields.ALERTS_AGG_TASK_PERIOD_MILLIES));

	}
}
