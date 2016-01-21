/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.logging.core.appender;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.core.appender.Win32EventLogAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.junit.InitialLoggerContext;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Advapi32Util.EventLogIterator;
import com.sun.jna.platform.win32.Advapi32Util.EventLogRecord;
import com.sun.jna.platform.win32.Advapi32Util.EventLogType;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;

/**
 * 
 * Win32EventLogAppender tests.
 * 
 * @author Curt Arnold
 * @author <a href="mailto:dblock@dblock.org">Daniel Doubrovkine</a>
 * @author <a href="mailto:tony@niemira.com">Tony Niemira</a>
 * @author <a href="mailto:henrik.karlsson@pulsen.se">Henrik Karlsson</a>
 * 
 */
public class Win32EventLogAppenderTest {

	@Rule
    public InitialLoggerContext ctx = new InitialLoggerContext("Win32EventLogAppenderTest.xml");

	private static final String TEST_LOGGER_NAME = "testLogger";

	// If the events from this test need to be observed in the Windows Event
	// Logger then ensure the Win32EventlogAppender.dll can be found at this
	// location, or change as appropriate
	private static String _eventLogAppenderDLL = "c:\\windows\\temp\\Win32EventlogAppender.dll";

	@Test
	public void testDebugEvent() {
        final Win32EventLogAppender appender = (Win32EventLogAppender) ctx.getRequiredAppender("Win32EventLogAppender");		
		String message = "log4jna DEBUG message @ "
				+ Kernel32.INSTANCE.GetTickCount();
        appender.append(asLogEvent(message, Level.DEBUG));
		expectEvent(message, Level.DEBUG, EventLogType.Informational);
	}

	@Test
	public void testInfoEvent() {
        final Win32EventLogAppender appender = (Win32EventLogAppender) ctx.getRequiredAppender("Win32EventLogAppender");		
		String message = "log4jna INFO message @ "
				+ Kernel32.INSTANCE.GetTickCount();
		appender.append(asLogEvent(message, Level.INFO));
		expectEvent(message, Level.INFO, EventLogType.Informational);
	}

	@Test
	public void testWarnEvent() {
        final Win32EventLogAppender appender = (Win32EventLogAppender) ctx.getRequiredAppender("Win32EventLogAppender");		
		String message = "log4jna WARN message @ "
				+ Kernel32.INSTANCE.GetTickCount();
		appender.append(asLogEvent(message, Level.WARN));
		expectEvent(message, Level.WARN, EventLogType.Warning);
	}

	@Test
	public void testFatalEvent() {
        final Win32EventLogAppender appender = (Win32EventLogAppender) ctx.getRequiredAppender("Win32EventLogAppender");		
		String message = "log4jna FATAL message @ "
				+ Kernel32.INSTANCE.GetTickCount();
		appender.append(asLogEvent(message, Level.FATAL));
		expectEvent(message, Level.FATAL, EventLogType.Error);
	}

	public void donttestRegistryValues() {
		String eventSourceKeyPath = "SYSTEM\\CurrentControlSet\\Services\\EventLog\\Log4jnaApplicationTest\\Log4jnaTest";

		System.err.println("Key path: "+ eventSourceKeyPath);
		String eventMessageFileInRegistry = Advapi32Util
				.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
						eventSourceKeyPath, "EventMessageFile");

		Path eventMessageFileGiven = Paths.get(_eventLogAppenderDLL);
		assertEquals(eventMessageFileInRegistry,
				eventMessageFileGiven.toString());

		String categoryMessageFileInRegistry = Advapi32Util
				.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
						eventSourceKeyPath, "CategoryMessageFile");

		Path categoryMessageFileGiven = Paths.get(_eventLogAppenderDLL);
		assertEquals(categoryMessageFileInRegistry,
				categoryMessageFileGiven.toString());
	}

	private LogEvent asLogEvent(String message, Level level) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName(TEST_LOGGER_NAME)
                .setLoggerFqcn(Win32EventLogAppenderTest.class.getName())
                .setLevel(level)
                .setMessage(new SimpleMessage(message))
                .build();
	}

	/*
	 * public void testException() { String message =
	 * "log4jna exception message @ " + Kernel32.INSTANCE.GetTickCount();
	 * _logger.debug(message, new Exception("testing exception"));
	 * expectEvent(message, Level.DEBUG, EventLogType.Informational); }
	 */

	private void expectEvent(String message, Level level,
			EventLogType eventLogType) {
		EventLogIterator iter = new EventLogIterator(null, "Log4jnaTest",
				WinNT.EVENTLOG_BACKWARDS_READ);
		try {
			assertTrue(iter.hasNext());
			EventLogRecord record = iter.next();
			assertEquals("Log4jnaTest", record.getSource());

			assertEquals(eventLogType, record.getType());
			assertEquals(1, record.getRecord().NumStrings.intValue());
			assertNull(record.getData());

			// The full message includes a level and the full class name
			String fullMessage = level + " "
					+ TEST_LOGGER_NAME + " " + "[]"
					+ " - " + message;

			// The event message has the location tacked on the front
			StringBuilder eventMessage = new StringBuilder();	        
	        for (int i = 0; i < record.getStrings().length; i++) {
	        	eventMessage.append(record.getStrings()[i].trim());
	        }

			System.err.println("Got: " + eventMessage.toString());

			int levelMarker = eventMessage.indexOf(level.toString());
			assertTrue("missing level marker in '" + eventMessage + "'",
					levelMarker >= 0);
			String eventMessageWithoutLocation = eventMessage
					.substring(levelMarker);
			
			System.err.println("Expecting: " + fullMessage);
			assertEquals(fullMessage, eventMessageWithoutLocation);
		} finally {
			iter.close();
		}
	}
		
}
