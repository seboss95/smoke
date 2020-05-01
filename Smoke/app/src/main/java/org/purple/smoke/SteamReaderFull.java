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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SteamReaderFull extends SteamReader
{
    private AtomicBoolean m_read = null;
    private AtomicLong m_acknowledgedOffset = null;
    private AtomicLong m_lastResponse = null;
    private static byte m_keyStream[] = null;
    private static int PACKET_SIZE = 16384;
    private static long READ_INTERVAL = 250; // 250 Milliseconds
    private static long RESPONSE_WINDOW = 15000; // 15 Seconds

    private void prepareReader()
    {
	if(m_reader == null)
	{
	    m_reader = Executors.newSingleThreadScheduledExecutor();
	    m_reader.scheduleAtFixedRate(new Runnable()
	    {
		@Override
		public void run()
		{
		    try
		    {
			if(m_completed.get() || !m_read.get())
			{
			    if(m_completed.get())
				return;
			    else if(System.currentTimeMillis() -
				    m_lastResponse.get() <= RESPONSE_WINDOW)
				return;
			    else
				m_readOffset.set(m_acknowledgedOffset.get());
			}

			if(m_canceled.get() || m_fileInputStream == null)
			    return;
			else
			    m_fileInputStream.getChannel().position
				(m_readOffset.get());

			byte bytes[] = new byte[PACKET_SIZE];
			int offset = m_fileInputStream.read(bytes);

			if(offset == -1)
			    m_completed.set(true);
			else
			    m_readOffset.addAndGet((long) offset);

			m_read.set(false);

			/*
			** Send bytes, filename, m_acknowledgedOffset.
			*/
		    }
		    catch(Exception exception)
		    {
		    }
		}
	    }, 1500, READ_INTERVAL, TimeUnit.MILLISECONDS);
	}
    }

    public SteamReaderFull(String fileName,
			   byte keyStream[],
			   int oid,
			   long readOffset)
    {
	super(fileName, oid, readOffset);
	m_acknowledgedOffset = new AtomicLong(0);
	m_keyStream = keyStream;
	m_lastResponse = new AtomicLong(System.currentTimeMillis());
	m_read = new AtomicBoolean(true);
	prepareReader();
    }

    public void delete()
    {
	super.delete();
	m_acknowledgedOffset.set(0);
	m_lastResponse.set(0);
	m_read.set(false);
    }

    public void setAcknowledgedOffset(long readOffset)
    {
	if(m_acknowledgedOffset.get() == readOffset)
	{
	    m_acknowledgedOffset.set(m_readOffset.get());
	    m_lastResponse.set(System.currentTimeMillis());
	    m_read.set(true);
	}
    }

    public void setReadInterval(int readInterval)
    {
    }
}