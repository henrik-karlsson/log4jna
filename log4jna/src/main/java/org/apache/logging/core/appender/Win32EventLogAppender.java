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

import java.io.Serializable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.SerializedLayout;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinNT;

/**
 * Append to the NT event log system.
 * 
 * <p>
 * <b>WARNING</b> This appender can only be installed and used on a Windows
 * system.
 * 
 * <p>
 * Do not forget to place jna.jar and platform.jar in the CLASSPATH.
 * </p>
 * 
 * @author <a href="mailto:cstaylor@pacbell.net">Chris Taylor</a>
 * @author <a href="mailto:jim_cakalic@na.biomerieux.com">Jim Cakalic</a>
 * @author <a href="mailto:dblock@dblock.org">Daniel Doubrovkine</a>
 * @author <a href="mailto:tony@niemira.com">Tony Niemira</a>
 * @author <a href="mailto:henrik.karlsson@pulsen.se">Henrik Karlsson</a>
 */
@Plugin(name = "Win32EventLog", category = "Core", elementType = "appender", printObject=true)
public class Win32EventLogAppender extends AbstractAppender {

	private static final long serialVersionUID = 1L;
    private final Win32EventLogManager manager;

    protected Win32EventLogAppender(final String name, final Filter filter, final Layout<? extends Serializable> layout,
                        final boolean ignoreExceptions, final Win32EventLogManager manager) {
        super(name, filter, layout, ignoreExceptions);
        this.manager = manager;
    }	
    
    public void append(final LogEvent event) {
		String s = new String(getLayout().toByteArray(event));
		// Normalize the log message level into the supported categories
		// Anything above FATAL or below DEBUG is labeled as INFO.
		// if (nt_category > FATAL || nt_category < DEBUG) {
		// 	nt_category = INFO;
		// }
		// This is the only message supported by the package. It is backed by
		// a message resource which consists of just '%1' which is replaced
		// by the string we just created.
		final int messageID = 0x1000;
		
		String[] buffer = { s };
		
		if (Advapi32.INSTANCE.ReportEvent(manager.getHandle(), getEventLogType(event.getLevel()),
				getEventLogCategory(event.getLevel()), messageID, null, buffer.length, 0, buffer,
				null) == false) {
			Exception e = new Win32Exception(Kernel32.INSTANCE.GetLastError());
			getHandler().error(
					"Failed to report event [" + s + "].", event, e);
		}
    }
    
    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<Win32EventLogAppender> {

        @PluginBuilderAttribute
        @Required(message = "A name for the Win32EventLogAppender must be specified")
        private String name;

        @PluginBuilderAttribute
        @Required(message = "Source must be specified")
        private String source;

        @PluginBuilderAttribute
        //@Required(message = "Server must be specified")
        private String server;

        @PluginBuilderAttribute
        private String application = "Application";

        @PluginBuilderAttribute
        private String eventMessageFile = "";

        @PluginBuilderAttribute
        private String categoryMessageFile = "";

        @PluginElement("Layout")
        private Layout<? extends Serializable> layout = SerializedLayout.createLayout();

        @PluginElement("Filter")
        private Filter filter;

        @PluginBuilderAttribute
        private boolean ignoreExceptions = true;

        private Builder() {
        }

        public Builder setName(final String name) {
            this.name = name;
            return this;
        }

        public Builder setSource(final String source) {
            this.source = source;
            return this;
        }

        public Builder setServer(final String server) {
            this.server = server;
            return this;
        }

        public Builder setApplication(final String application) {
            this.application = application;
            return this;
        }

        public Builder setEventMessageFile(final String eventMessageFile) {
            this.eventMessageFile = eventMessageFile;
            return this;
        }

        public Builder setCategoryMessageFile(final String categoryMessageFile) {
            this.categoryMessageFile = categoryMessageFile;
            return this;
        }
        
        public Builder setLayout(final Layout<? extends Serializable> layout) {
            this.layout = layout;
            return this;
        }

        public Builder setFilter(final Filter filter) {
            this.filter = filter;
            return this;
        }

        public Builder setIgnoreExceptions(final boolean ignoreExceptions) {
            this.ignoreExceptions = ignoreExceptions;
            return this;
        }

        public Win32EventLogAppender build() {
            final Win32EventLogManager win32EventLogManager = Win32EventLogManager.getWin32EventLogManager(name, source, server, 
            		application, eventMessageFile, categoryMessageFile);
            try {
                return new Win32EventLogAppender(name, filter, layout, ignoreExceptions, win32EventLogManager);
            } catch (final Win32Exception e) {
                LOGGER.error("Error creating Win32EventLogAppender [{}].", name, e);
                return null;
            }
        }
    }


	/**
	 * Convert log4j Priority to an EventLog type. The log4j package supports 8
	 * defined priorities, but the NT EventLog only knows 3 event types of
	 * interest to us: ERROR, WARNING, and INFO.
	 * 
	 * @param level
	 *            Log4j priority.
	 * @return EventLog type.
	 */
	private static int getEventLogType(Level level) {
		int type = WinNT.EVENTLOG_SUCCESS;
		
		if (level.intLevel() <= Level.INFO.intLevel()) {
			type = WinNT.EVENTLOG_INFORMATION_TYPE;
			if (level.intLevel() <= Level.WARN.intLevel()) {
				type = WinNT.EVENTLOG_WARNING_TYPE;
				if (level.intLevel() <=  Level.ERROR.intLevel()) {
					type = WinNT.EVENTLOG_ERROR_TYPE;
				}
			}
		}

		return type;
	}

	/**
	 * Convert log4j Priority to an EventLog category. Each category is backed
	 * by a message resource so that proper category names will be displayed in
	 * the NT Event Viewer.
	 * 
	 * @param level.intLevel()
	 *            Log4J priority.
	 * @return EventLog category.
	 */
	private static int getEventLogCategory(Level level) {
		int category = 1;
		if (level.intLevel() >= Level.DEBUG.intLevel()) {
			category = 2;
			if (level.intLevel() >= Level.INFO.intLevel()) {
				category = 3;
				if (level.intLevel() >= Level.WARN.intLevel()) {
					category = 4;
					if (level.intLevel() >= Level.ERROR.intLevel()) {
						category = 5;
						if (level.intLevel() >= Level.FATAL.intLevel()) {
							category = 6;
						}
					}
				}
			}
		}
		return category;
	}

}
