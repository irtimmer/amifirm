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
 * A file packet in Amino Aminet Firmware
 * @author Iwan Timmer
 */
public class HeaderPacket extends Packet {
	
	public final static byte TYPE = 0x01;
	
	private final short totalFiles;
	private final short offset;
	
	public HeaderPacket(ByteBuffer buffer) {
		super(buffer);
		
		this.totalFiles = buffer.getShort();
		buffer.getInt(); //Unknown;
		this.offset = buffer.getShort();
	}

	public short getTotalFiles() {
		return totalFiles;
	}

	public short getOffset() {
		return offset;
	}	
	
}
