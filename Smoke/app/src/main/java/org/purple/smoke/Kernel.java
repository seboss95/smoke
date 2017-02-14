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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;

public class Kernel
{
    private Cryptography m_cryptography = null;
    private Hashtable<Integer, Object> m_neighbors = null;
    private Timer m_congestionPurgeTimer = null;
    private Timer m_neighborsTimer = null;
    private final static int s_congestionPurgeInterval = 15000; // 15 Seconds
    private final static int s_neighborsInterval = 10000; // 10 Seconds
    private static Kernel s_instance = null;

    private Kernel()
    {
	m_cryptography = Cryptography.getInstance();
	m_neighbors = new Hashtable<> ();
	prepareTimers();
    }

    private class NeighborsTask extends TimerTask
    {
	@Override
	public void run()
	{
	    prepareNeighbors();
	}
    }

    private void prepareNeighbors()
    {
	ArrayList<NeighborElement> neighbors =
	    Database.getInstance().readNeighbors(m_cryptography);
	int count = Database.getInstance().count("neighbors");

	if(count == 0 || neighbors == null)
	{
	    /*
	    ** Disconnect all existing sockets.
	    ** Remove null neighbors.
	    */

	    if(count == 0)
		m_neighbors.clear();

	    return;
	}
	else
	    for(Hashtable.Entry<Integer, Object> entry:m_neighbors.entrySet())
	    {
		/*
		** Remove neighbor objects which do not exist in the
		** database.
		*/

		boolean found = false;
		int oid = entry.getKey();

		for(int i = 0; i < neighbors.size(); i++)
		    if(neighbors.get(i) != null &&
		       neighbors.get(i).m_oid == oid)
		    {
			found = true;
			break;
		    }

		if(!found)
		    m_neighbors.remove(entry.getKey());
	    }

	for(int i = 0; i < neighbors.size(); i++)
	{
	    NeighborElement neighborElement = neighbors.get(i);

	    if(neighborElement == null)
		continue;
	    else if(m_neighbors.containsKey(neighborElement.m_oid))
		continue;
	    else if(neighborElement.m_statusControl.toLowerCase().
		    equals("delete") ||
		    neighborElement.m_statusControl.toLowerCase().
		    equals("disconnect"))
	    {
		if(neighborElement.m_statusControl.toLowerCase().
		   equals("disconnect"))
		{
		    Database database = Database.getInstance();

		    database.saveNeighborLocalIpInformation
			(m_cryptography,
			 "",
			 "",
			 String.valueOf(neighborElement.m_oid));
		    database.saveNeighborStatus
			(m_cryptography,
			 "disconnected",
			 String.valueOf(neighborElement.m_oid));
		}

		continue;
	    }

	    Neighbor neighbor = null;

	    if(neighborElement.m_transport.equals("TCP"))
		neighbor = new TcpNeighbor
		    (neighborElement.m_remoteIpAddress,
		     neighborElement.m_remotePort,
		     neighborElement.m_remoteScopeId,
		     neighborElement.m_ipVersion,
		     neighborElement.m_oid);
	    else if(neighborElement.m_transport.equals("UDP"))
		neighbor = new UdpNeighbor
		    (neighborElement.m_remoteIpAddress,
		     neighborElement.m_remotePort,
		     neighborElement.m_remoteScopeId,
		     neighborElement.m_ipVersion,
		     neighborElement.m_oid);

	    if(neighbor == null)
		continue;

	    m_neighbors.put(neighborElement.m_oid, new Object());
	}
    }

    private void prepareTimers()
    {
	if(m_congestionPurgeTimer == null)
	{
	    m_congestionPurgeTimer = new Timer(true);
	    m_congestionPurgeTimer.scheduleAtFixedRate
		(new CongestionPurgeTask(), 0, s_congestionPurgeInterval);
	}

	if(m_neighborsTimer == null)
	{
	    m_neighborsTimer = new Timer(true);
	    m_neighborsTimer.scheduleAtFixedRate
		(new NeighborsTask(), 0, s_neighborsInterval);
	}
    }

    public static synchronized Kernel getInstance()
    {
	if(s_instance == null)
	    s_instance = new Kernel();

	return s_instance;
    }
}
