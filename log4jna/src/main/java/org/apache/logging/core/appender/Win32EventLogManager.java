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

import java.io.File;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.status.StatusLogger;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinReg;

/**
 * @author <a href="mailto:henrik.karlsson@pulsen.se">Henrik Karlsson</a>
 *
 */
public class Win32EventLogManager extends AbstractManager {

	private static final Logger LOGGER = StatusLogger.getLogger();

	
	private HANDLE handle = null;
	
	private Win32EventLogManager(final String name, final String source, final String server,
			final String application, final String eventMessageFile, final String categoryMessageFile) {
		super(name);
	
		LOGGER.debug(String.format("Server: %s; source:%s; application:%s; eventMessageFile:%s;categoryFile:%s",
				server, source, application,
				eventMessageFile, categoryMessageFile));

		handle = registerEventSource(server, source, application,
				eventMessageFile, categoryMessageFile);
	}

    /**
     * Gets a Win32EventManager using the specified configuration parameters.
     *
     * @param name                  The name to use for this Win32EventManager.
     * @param source           		The source to use for this Win32EventManager.
     * @param server				The server to use for this Win32EventManager.
     * @param application       	The application to use for this Win32EventManager.
     * @param eventMessageFile      The eventMessageFile to use for this Win32EventManager.
     * @param categoryMessageFile	The categoryMessageFile to use for this Win32EventManager.
     * @return The Win32EventManager as configured.
     */
    public static Win32EventLogManager getWin32EventLogManager(final String name, final String source,
                                           final String server, final String application,
                                           final String eventMessageFile, final String categoryMessageFile) {
        return new Win32EventLogManager(name, source, server, application, eventMessageFile, categoryMessageFile);
    }

	/* (non-Javadoc)
	 * @see org.apache.logging.log4j.core.appender.AbstractManager#releaseSub()
	 */
	@Override
	protected void releaseSub() {
		if (handle != null) {
			if (!Advapi32.INSTANCE.DeregisterEventSource(handle)) {
				throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
			}
			handle = null;
		}
	}

	private static HANDLE registerEventSource(String server, String source,
			String application, String eventMessageFile,
			String categoryMessageFile) {
		String eventSourceKeyPath = "SYSTEM\\CurrentControlSet\\Services\\EventLog\\"
				+ application + "\\" + source;
		if (Advapi32Util.registryCreateKey(WinReg.HKEY_LOCAL_MACHINE,
				eventSourceKeyPath)) {
			Advapi32Util.registrySetIntValue(WinReg.HKEY_LOCAL_MACHINE,
					eventSourceKeyPath, "TypesSupported", 7);
			Advapi32Util.registrySetIntValue(WinReg.HKEY_LOCAL_MACHINE,
					eventSourceKeyPath, "CategoryCount", 6);
			File emf = new File(eventMessageFile);
			Advapi32Util.registrySetStringValue(WinReg.HKEY_LOCAL_MACHINE,
					eventSourceKeyPath, "EventMessageFile", 
					emf.getAbsolutePath());
			File cmf = new File(categoryMessageFile);
			Advapi32Util.registrySetStringValue(WinReg.HKEY_LOCAL_MACHINE,
					eventSourceKeyPath, "CategoryMessageFile",
					cmf.getAbsolutePath());
		}

		HANDLE h = Advapi32.INSTANCE.RegisterEventSource(server, source);
		if (h == null) {
			throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
		}

		return h;
	}

	/**
	 * @return the handle
	 */
	protected HANDLE getHandle() {
		return handle;
	}
    
}
