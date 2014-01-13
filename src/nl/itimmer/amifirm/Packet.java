/**
 * Copyright (C) 2013, 2014 Iwan Timmer
 *
 * This file is part of AmiFirm.
 *
 * AmiFirm is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * AmiFirm is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AmiFirm.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.itimmer.amifirm;

import java.nio.ByteBuffer;

/**
 * A packet in Amino Aminet Firmware
 * @author Iwan Timmer
 */
public class Packet implements Comparable<Packet> {
	
	public final static int HEADER_SIZE = 18;
	
	private final int id;
	private final short packetId;
	private final short totalPackets;
		
	private final ByteBuffer data;
	
	public Packet(ByteBuffer buffer) {
		this.id = buffer.getInt();
		this.packetId = buffer.getShort();
		this.totalPackets = buffer.getShort();
		
		this.data = buffer;
	}

	public int getId() {
		return id;
	}
	
	public ByteBuffer getData() {
		return data;
	}

	public short getPacketId() {
		return packetId;
	}

	public short getTotalPackets() {
		return totalPackets;
	}
	
	@Override
	public int compareTo(Packet t) {
		return packetId - t.packetId;
	}
		
}
