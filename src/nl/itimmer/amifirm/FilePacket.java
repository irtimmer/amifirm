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
public class FilePacket extends Packet {
	
	public final static byte TYPE = 0x03;
	
	private final short fileId;
	
	private final int offset;
	private final int size;

	public FilePacket(ByteBuffer buffer) {
		super(buffer);
		
		this.fileId = buffer.getShort();
		this.size = buffer.getInt();
		this.offset = buffer.getInt();
	}
	
	public short getFileId() {
		return fileId;
	}

	public int getOffset() {
		return offset;
	}

	public int getSize() {
		return size;
	}	
		
}
