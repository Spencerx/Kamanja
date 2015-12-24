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

package com.ligadata.kamanja.consumer.message;

import kafka.consumer.KafkaStream;

import org.apache.logging.log4j.{ Logger, LogManager };
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class AlertsConsumer extends AbstractConsumer {

	private Logger logger = LogManager.getLogger(AlertsConsumer.class);

	public AlertsConsumer(KafkaStream<byte[], byte[]> stream, int threadNumbe) {
		super(stream, threadNumbe);
	}

	@Override
	public void handleMessage(String message, int threadNumber) {

		Aggregations agg = Aggregations.getInstance();
		try {
			String[] messageInfo = getAlertType(message);
			agg.add(messageInfo[0]);
			agg.addMessage("Alerts of type " + messageInfo[0] + " at "
					+ messageInfo[1]);

		} catch (ParseException e) {
			logger.error("Error while parsing message", e);
			return;
		}

	}

	private String[] getAlertType(String message) throws ParseException {

		JSONParser jsonParser = new JSONParser();
		JSONObject jsonObject = (JSONObject) jsonParser.parse(message);

		JSONArray lang = (JSONArray) jsonObject.get("ModelsResult");
		JSONObject modelValue = (JSONObject) lang.get(0);
		String dateString = (String) modelValue.get("ExecutionTime");
		JSONArray outputArray = (JSONArray) modelValue.get("output");
		JSONObject alertObject = (JSONObject) outputArray.get(2);

		return new String[] { alertObject.get("Value").toString(), dateString };

	}

}
