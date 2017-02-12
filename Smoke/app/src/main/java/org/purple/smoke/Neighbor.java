/*
** Copyright (c) Alexis Megas.
** All rights reserved.
**
** Redistribution and use in source and binary forms, with or without
** modification, are permitted provided that the following conditions
** are met:
** 1. Redistributions of source code must retain the above copyright
**    notice, this list of conditions and the following disclaimer.
** 2. Redistributions in binary form must reproduce the above copyright
**    notice, this list of conditions and the following disclaimer in the
**    documentation and/or other materials provided with the distribution.
** 3. The name of the author may not be used to endorse or promote products
**    derived from Smoke without specific prior written permission.
**
** SMOKE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
** IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
** OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
** IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
** INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
** NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
** DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
** THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
** (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
** SMOKE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.purple.smoke;

import android.util.Base64;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public abstract class Neighbor
{
    private static final int s_silence = 90000; // 90 Seconds
    private static final int s_timerInterval = 15000; // 15 Seconds
    private Timer m_timer = null;
    private UUID m_uuid = null;
    protected Date m_lastTimeReadWrite = null;
    protected Object m_socketMutex = null;
    protected String m_echoMode = "full";
    protected String m_ipAddress = "";
    protected String m_ipPort = "";
    protected String m_scopeId = "";
    protected String m_version = "";
    protected int m_laneWidth = 100000;
    protected int m_oid = -1;
    protected static final String s_eom = "\r\n\r\n\r\n";
    protected static final int s_maximumBytes = 32 * 1024 * 1024; // 32 MiB

    private class NeighborTask extends TimerTask
    {
	@Override
	public void run()
	{
	    sendCapabilities();
	    terminate();
	}
    }

    private void terminate()
    {
	Date now = new Date();
	boolean disconnect = false;

	synchronized(m_lastTimeReadWrite)
	{
	    disconnect = now.getTime() - m_lastTimeReadWrite.getTime() >
		s_silence;
	}

	if(disconnect)
	    disconnect();
    }

    protected Neighbor(String ipAddress,
		       String ipPort,
		       String scopeId,
		       String transport,
		       String version,
		       int oid)
    {
	m_ipAddress = ipAddress;
	m_ipPort = ipPort;
	m_lastTimeReadWrite = new Date();
	m_oid = oid;
	m_scopeId = scopeId;
	m_socketMutex = new Object();
	m_timer = new Timer(true);
	m_uuid = UUID.randomUUID();
	m_version = version;

	/*
	** Start timers.
	*/

	m_timer.scheduleAtFixedRate(new NeighborTask(), 0, s_timerInterval);
    }

    protected String getCapabilities()
    {
	try
	{
	    StringBuffer message = new StringBuffer();

	    message.append(m_uuid.toString());
	    message.append("\n");
	    message.append(String.valueOf(m_laneWidth));
	    message.append("\n");
	    message.append(m_echoMode);

	    StringBuffer results = new StringBuffer();

	    results.append("POST HTTP/1.1\r\n");
	    results.append
		("Content-Type: application/x-www-form-urlencoded\r\n");
	    results.append("Content-Length: %1\r\n");
	    results.append("\r\n");
	    results.append("type=0014&content=%2\r\n");
	    results.append("\r\n\r\n");

	    String base64 = Base64.encodeToString
		(message.toString().getBytes(), Base64.DEFAULT);
	    int indexOf = results.indexOf("%1");
	    int length = base64.length() +
		"type=0014&content=\r\n\r\n\r\n".length();

	    results = results.replace
		(indexOf, indexOf + 2, String.valueOf(length));
	    indexOf = results.indexOf("%2");
	    results = results.replace(indexOf, indexOf + 2, base64);
	    return results.toString();
	}
	catch(Exception exception)
	{
	    return "";
	}
	finally
	{
	}
    }

    protected abstract void sendCapabilities();

    public String getLocalIp()
    {
	if(m_version.equals("IPv4"))
	    return "0.0.0.0";
	else
	    return "::";
    }

    public abstract boolean connected();
    public abstract void connect();
    public abstract void disconnect();

    public int getLocalPort()
    {
	return 0;
    }

    public int oid()
    {
	return m_oid;
    }
}
