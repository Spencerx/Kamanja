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

package main;


import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.ConsoleAppender;
import org.apache.logging.log4j.DailyRollingFileAppender;
import org.apache.logging.log4j.FileAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.{ Logger, LogManager };
import org.apache.logging.log4j.PatternLayout;
import org.apache.logging.log4j.SimpleLayout;
import org.joda.time.DateTime;

//@SuppressWarnings("unused")
public class Log {

	private static Log _log = null;

	private Map<String, StringBuilder> _logBuffers = new HashMap<String, StringBuilder>();
	private static Logger logger;
	

	private Log(String methodName, HttpServletRequest request) {
		UserState userState = request.getSession().getAttribute("npariostate") == null ? new UserState() : (UserState) request.getSession()
				.getAttribute("npariostate");

		StringBuilder logBuffer = CurrentLog(methodName, request);
		userState.LogBuffer = logBuffer;

		logger = LogManager.getLogger(Log.class);
		
		//metaDBQueriesLogger = LogManager.getLogger("meta.sql.Log");
		//mainDBQueriesLogger = LogManager.getLogger("main.sql.Log");
		try 
		{
			System.out.println("Application log dir: " + Global.CONFIG_LOG_FILE_PATH);
			System.out.println("Application log pattern: " + Global.CONFIG_LOG_FILE_PATTERN);

			DailyRollingFileAppender fa = new DailyRollingFileAppender(new SimpleLayout(), Global.CONFIG_LOG_FILE_PATH,
					Global.CONFIG_LOG_FILE_PATTERN);
			fa.activateOptions();
			fa.setName("main.Log");
			logger.addAppender(fa);

			setupDBLoggers();
			
			setupSecurityLogger();
		} catch (IOException e) 
		{
			e.printStackTrace();
		}

	}

	private void setupDBLoggers() throws IOException
	{
		org.apache.logging.log4j.Layout layout = new PatternLayout("%m%n");
		
		/*DailyRollingFileAppender metaDBfa = new DailyRollingFileAppender(new SimpleLayout(), Global.CONFIG_LOG_FILE_PATH + ".meta.sql", Global.CONFIG_LOG_FILE_PATTERN);
		metaDBfa.activateOptions();
		metaDBfa.setName("meta.sql.Log");
		metaDBfa.setThreshold(Level.ALL); 
		metaDBfa.setLayout(layout);*/
		//metaDBQueriesLogger.addAppender(metaDBfa);
		
		/*DailyRollingFileAppender mainDBfa = new DailyRollingFileAppender(new SimpleLayout(), Global.CONFIG_LOG_FILE_PATH + ".main.sql", Global.CONFIG_LOG_FILE_PATTERN);
		mainDBfa.activateOptions();
		mainDBfa.setName("main.sql.Log");
		mainDBfa.setThreshold(Level.ALL);
		mainDBfa.setLayout(layout);*/
		//mainDBQueriesLogger.addAppender(mainDBfa);
	}
	/**
	 * Set up the security logger.
	 * 
	 * @param logger2
	 * @throws IOException
	 */
	private void setupSecurityLogger() throws IOException {

		//TODO Console appender should be turned off except for debugging
		//TODO level should be turned to warn or no more than info in steady state.
		final String fileName = Global.CONFIG_LOG_FILE_PATH + "_user_security";
		final PatternLayout layout = new PatternLayout("%d{ISO8601} [%t] %-5p %c %x - %m%n");
		final List<Logger> loggers = new ArrayList<Logger>();
		loggers.add(LogManager.getLogger("com.npario.user"));
		loggers.add(LogManager.getLogger("com.npario.user.security"));
		loggers.add(LogManager.getLogger("com.npario.user.security.custom"));
		loggers.add(LogManager.getLogger("com.npario.datalayer"));

		ConsoleAppender console = new ConsoleAppender(); // create appender
		// configure the appender
		console.setLayout(layout);
		console.setThreshold(Level.ALL);
		console.activateOptions();

		/*DailyRollingFileAppender fa = new DailyRollingFileAppender(layout, fileName, Global.CONFIG_LOG_FILE_PATTERN);

		fa.setName("user_security_log");
		fa.setThreshold(Level.ALL);
		fa.setAppend(true);
		fa.activateOptions();

		for(final Logger l : loggers){
			l.addAppender(console);
			l.addAppender(fa);
		}*/
	}

	public static void WriteMethodCall(String methodName, String parameters, HttpServletRequest request) {
		if (_log == null)
			_log = new Log(methodName, request);

		_log.WriteMethodCall(methodName, parameters, request, 0);
	}

	private void WriteMethodCall(String methodName, String parameters, HttpServletRequest request, int z) {
		StringBuilder logBuffer = CurrentLog(methodName, request);
		if (parameters.length() > 256)
			parameters = parameters.substring(0, 255);
		
		logBuffer.append(new DateTime().toString("yyyy/MM/dd HH:mm:ss") + " | " + methodName + " | Call with parameters: " + parameters
				+ System.lineSeparator());
	}

	public static void WriteInnerMessage(String methodName, String message, HttpServletRequest request) {
		if (_log == null)
			_log = new Log(methodName, request);

		StringBuilder logBuffer = _log.CurrentLog(methodName, request);
		logBuffer.append(new DateTime().toString("HH:mm:ss") + " | " + methodName + " | innerMethod: " + methodName + " | " + message
				+ System.lineSeparator());
	}

	protected StringBuilder CurrentLog(String methodName, HttpServletRequest request) {

		String ipAddress = "";
		String key = "";
		String WebService = "";
		try {
			ipAddress = request.getRemoteAddr();
			key = request.getSession(true).getId();
			WebService = request.getRequestURI().toString() + " ? " + request.getQueryString();
		} catch (Exception e) {
		}

		UserState userState = request.getSession().getAttribute("npariostate") == null ? new UserState() : (UserState) request.getSession()
				.getAttribute("npariostate");

		StringBuilder buffer = new StringBuilder();
		buffer.append("******* Starting request for user Unknown *******" + " MethodName: "
				+ methodName + " *******" + System.lineSeparator());
		if(!Utils.isStringNullOrEmpty(methodName) && (methodName.equalsIgnoreCase("login") || methodName.equalsIgnoreCase("getloginuserinfo")))
		{//Write Client IP only in the Login method
			buffer.append("ClientIp: " + ipAddress + System.lineSeparator());
		}
		
		buffer.append("WebService: " + WebService + System.lineSeparator());
		//buffer.append("ClientAgent: " + request.getHeader("User-Agent") + System.lineSeparator());
		buffer.append("SessionID: " + key + System.lineSeparator());

		if (!_logBuffers.containsKey(key))
			_logBuffers.put(key, buffer);

		return _logBuffers.get(key);
	}

	/*protected static StringBuilder CurrentMetaDBLog(HttpServletRequest request) {

		String ipAddress = "";
		String key = "";
		String sessionId = "";
		String WebService = "";
		try {
			ipAddress = request.getRemoteAddr();
			sessionId = request.getSession(true).getId();
			key = sessionId;
			WebService = request.getRequestURI().toString() + " ? " + request.getQueryString();
		} catch (Exception e) {
		}

		key += "_" + request.getQueryString();//to lower chance of overlapping logs
		
		UserState userState = request.getSession().getAttribute("npariostate") == null ? 
				new UserState() : (UserState) request.getSession().getAttribute("npariostate");

		String methodName = Utils.GetCurrentWebMethodName(request);
		
		StringBuilder buffer = new StringBuilder();
		buffer.append("--******* Starting request for user Unknown *******" + " MethodName: "
				+ methodName + " *******" + System.lineSeparator());
		
		//buffer.append("ClientIp: " + ipAddress + System.lineSeparator());
		buffer.append("--WebService: " + WebService + System.lineSeparator());
		//buffer.append("ClientAgent: " + request.getHeader("User-Agent") + System.lineSeparator());
		buffer.append("--SessionID: " + sessionId + System.lineSeparator());

		if (!_metaDBLogBuffers.containsKey(key))
			_metaDBLogBuffers.put(key, buffer);

		return _metaDBLogBuffers.get(key);
	}*/
	
	/*protected static StringBuilder CurrentMainDBLog(HttpServletRequest request) {

		String ipAddress = "";
		String key = "";
		String sessionId = "";
		String WebService = "";
		try {
			ipAddress = request.getRemoteAddr();
			sessionId = request.getSession(true).getId();
			key = sessionId;
			WebService = request.getRequestURI().toString() + " ? " + request.getQueryString();
		} catch (Exception e) {
		}

		key += "_" + request.getQueryString();//to decrease chance of overlapping logs
		
		UserState userState = request.getSession().getAttribute("npariostate") == null ? 
				new UserState() : (UserState) request.getSession().getAttribute("npariostate");

		String methodName = Utils.GetCurrentWebMethodName(request);
		StringBuilder buffer = new StringBuilder();
		buffer.append("--******* Starting request for user  Unknown *******" + " MethodName: "
				+ methodName + " *******" + System.lineSeparator());
		
		//buffer.append("ClientIp: " + ipAddress + System.lineSeparator());
		buffer.append("--WebService: " + WebService + System.lineSeparator());
		//buffer.append("ClientAgent: " + request.getHeader("User-Agent") + System.lineSeparator());
		buffer.append("--SessionID: " + sessionId + System.lineSeparator());

		if (!_mainDBLogBuffers.containsKey(key))
			_mainDBLogBuffers.put(key, buffer);

		return _mainDBLogBuffers.get(key);
	}*/
	
	public static void WriteMethodException(String methodName, Exception exp, HttpServletRequest request) {
		exp.printStackTrace();// TODO : remove later

		if (_log == null)
			_log = new Log(methodName, request);

		_log.WriteMethodException(methodName, exp, request, 0);
	}

	private void WriteMethodException(String methodName, Exception exp, HttpServletRequest request, int z) {
		StringBuilder stackTraceBuffer = new StringBuilder();
		StringWriter stwriter = new StringWriter();
		exp.printStackTrace(new PrintWriter(stwriter));

		for (StackTraceElement e : exp.getStackTrace()) {
			stackTraceBuffer.append(e.toString());
		}
		StringBuilder logBuffer = CurrentLog(methodName, request);
		logBuffer.append(now() + " | " + methodName + "| Error: " + exp.getMessage() + " | CallStack: " + stwriter.toString()
				+ System.lineSeparator());
		FlushLogToFile(methodName, request);
	}

	private void FlushLogToFile(String methodName, HttpServletRequest request) {

		try {

			UserState userState = request.getSession().getAttribute("npariostate") == null ? new UserState() : (UserState) request.getSession()
					.getAttribute("npariostate");
			StringBuilder logBuffer = CurrentLog(methodName, request);
			logBuffer.append("******* Flushing request log on error *******" + " methodName: " + methodName + " *******");
			logBuffer.append(System.lineSeparator());

			logger.info(logBuffer.toString());

			//for debugging
			System.out.println(logBuffer.toString());
			
			/*StringBuilder metaLogBuffer = CurrentMetaDBLog(request);
			StringBuilder mainLogBuffer = CurrentMainDBLog(request);
			if(metaLogBuffer != null && metaLogBuffer.length() > 0)
			{
				metaLogBuffer.append("--******* Completed request for user *******" + " methodName: " + methodName + " *******");
				metaLogBuffer.append(System.lineSeparator());
				metaDBQueriesLogger.info(metaLogBuffer);
			}
			if(mainLogBuffer != null && mainLogBuffer.length() > 0)
			{
				mainLogBuffer.append("--******* Completed request for user *******" + " methodName: " + methodName + " *******");
				mainLogBuffer.append(System.lineSeparator());
				mainDBQueriesLogger.info(mainLogBuffer);
			}*/
			
			EndLog(methodName, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String now() {
		return new DateTime().toString("yyyy/MM/dd HH:mm:ss");
	}

	public static void WriteMethodResult(String methodName, HttpServletRequest request) {

		
		if (_log == null)
			_log = new Log(methodName, request);

		_log.WriteMethodResult(methodName, request, 0);
	}

	private void WriteMethodResult(String methodName, HttpServletRequest request, int z) {
		StringBuilder logBuffer = CurrentLog(methodName, request);
		logBuffer.append(now() + " | " + methodName + " | Success" + System.lineSeparator());
		WriteLogToFile(methodName, request);
	}

	private void WriteLogToFile(String methodName, HttpServletRequest request) {
		StringBuilder logBuffer = CurrentLog(methodName, request);

		UserState userState = request.getSession().getAttribute("npariostate") == null ? new UserState() : (UserState) request.getSession()
				.getAttribute("npariostate");

		logBuffer.append("******* Completed request  *******" + " methodName: " + methodName + " *******");
		logBuffer.append(System.lineSeparator());

		//for debugging
		System.out.println(logBuffer.toString());
				
		logger.info(logBuffer.toString());
		
		/*StringBuilder metaLogBuffer = CurrentMetaDBLog(request);
		StringBuilder mainLogBuffer = CurrentMainDBLog(request);
		if(metaLogBuffer != null && metaLogBuffer.length() > 0)
		{
			metaLogBuffer.append("--******* Completed request  *******" + " methodName: " + methodName + " *******");
			metaLogBuffer.append(System.lineSeparator());
			metaDBQueriesLogger.info(metaLogBuffer);
		}
		
		if(mainLogBuffer != null && mainLogBuffer.length() > 0)
		{
			mainLogBuffer.append("--******* Completed request *******" + " methodName: " + methodName + " *******");
			mainLogBuffer.append(System.lineSeparator());
			mainDBQueriesLogger.info(mainLogBuffer);
		}*/

		
		EndLog(methodName, request);
	}

	public static void WriteCustomMessage(String methodName, String message, HttpServletRequest request) {
		if (_log == null)
			_log = new Log(methodName, request);

		_log.WriteCustomMessage(methodName, message, request, 0);
	}

	private void WriteCustomMessage(String methodName, String message, HttpServletRequest request, int z) {
		StringBuilder logBuffer = CurrentLog(methodName, request);
		//if (message.length() > 256)
        	//message = message.substring(0, 255);
		logBuffer.append(now() + " | " + methodName + "| CustomMessage: " + message + System.lineSeparator());
	}

	protected void EndLog(String methodName, HttpServletRequest request) {
		String ipAddress = request.getRemoteAddr();
		String key = request.getSession().getId();

		if (_logBuffers.containsKey(key))
			_logBuffers.remove(key);

		String sqlLogsKey = key+ "_" + request.getQueryString();
		
		/*if(_mainDBLogBuffers.containsKey(sqlLogsKey))
			_mainDBLogBuffers.remove(sqlLogsKey);
		
		if(_metaDBLogBuffers.containsKey(sqlLogsKey))
			_metaDBLogBuffers.remove(sqlLogsKey);*/
	}
	
	/*public static void WriteMetaDBLogMessage(String message, HttpServletRequest request)
	{
		if (_log == null)
			_log = new Log("", request);
		
		String extension = "";
		if(!Utils.isStringNullOrEmpty(message))
		{
			if(!message.endsWith(";"))
				extension = ";";
		}
		
		StringBuilder logBuffer = CurrentMetaDBLog(request);
		logBuffer.append("--" + now() + "| Message:\n" + message + extension + System.lineSeparator());
	}*/
	
	/*public static void WriteMainDBLogMessage(String message, HttpServletRequest request)
	{
		System.out.println(message);
		if (_log == null)
			_log = new Log("", request);
		
		String extension = "";
		if(!Utils.isStringNullOrEmpty(message))
		{
			if(!message.endsWith(";"))
				extension = ";";
		}
		
		StringBuilder logBuffer = CurrentMainDBLog(request);
		logBuffer.append("--" + now() + "| Message:\n" + message + extension + System.lineSeparator());
	}*/

}
