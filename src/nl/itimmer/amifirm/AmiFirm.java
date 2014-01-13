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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Application to download and extract Amino Aminet firmware
 * @author Iwan Timmer
 */
public class AmiFirm {
	
	private final InetAddress address;
	private final int port;
	private final File dir;
	private final File export;
	
	private final Map<Short, List<Packet>> files;
	private	final Map<Short, String> names;
	
	/**
	 * Create a new download and extract session
	 * @param address multicast address on which firmware is broadcasted
	 * @param port portnumber of the udp packets containing firmware
	 * @param dir directory to extract firmware to
	 * @param export file to export firmware to or null
	 */
	public AmiFirm(InetAddress address, int port, File dir, File export) {
		this.address = address;
		this.port = port;
		this.dir = dir;
		this.export = export;
		
		this.files = new TreeMap<>();
		this.names = new TreeMap<>();
	}
	
	/**
	 * Download and extract firmware
	 * @throws IOException 
	 */
	public void run() throws IOException {
		download();
		extract();
	}
	
	/**
	 * Download firmware
	 * @throws IOException 
	 */
	private void download() throws IOException, SocketTimeoutException {
		System.out.println("Press key to stop downloading");
		System.out.print("Downloading firmware...");
		try (MulticastSocket socket = new MulticastSocket(port)) {
			socket.setSoTimeout(5000);
			socket.joinGroup(address);
			
			Set<String> keys = new HashSet<>();
			boolean running = true;
			byte[] buffer = new byte[1500];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			
			FileOutputStream out = null;
			if (export!=null)
				out = new FileOutputStream(export);
			
			int n = 0;
			long last = 0;
			while (running) {
				String key = new String(buffer, 0, 10);
				socket.receive(packet);
				if (!keys.contains(key)) {
					if (out!=null) {
						ByteBuffer size = ByteBuffer.allocate(4).putInt(packet.getLength());
						out.write(size.array(), 0, 4);
						out.write(buffer, 0, packet.getLength());
					}
					
					ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, packet.getLength());
					Packet firmwarePacket = null;
					short id = 0;
					switch (buffer[0]) {
						case FilePacket.TYPE:
							firmwarePacket = new FilePacket(byteBuffer);
							id = ((FilePacket) firmwarePacket).getFileId();
							break;
						case HeaderPacket.TYPE:
							firmwarePacket = new HeaderPacket(byteBuffer);
							break;
						default:
							throw new IOException("Not supported firmware");
					}
					keys.add(key);
					
					if (!files.containsKey(id)) {
						System.out.print('+');
						files.put(id, new ArrayList<Packet>());
					}

					files.get(id).add(firmwarePacket);
					
					if (firmwarePacket instanceof HeaderPacket && names.isEmpty()) {
						if (firmwarePacket.getTotalPackets() == files.get((short) 0).size()) {
							System.out.println();
							parseHeaders();
							System.out.print("Downloading firmware...");
						}
					}
					
					buffer = new byte[buffer.length];
					if ((n++)%50 == 0 || last+1000<System.currentTimeMillis()) {
						System.out.print('.');
						last = System.currentTimeMillis();
					}
					
					if (files.size() == (names.size()+1)) {
						running = false;
						for (Short fileId:files.keySet()) {
							List<Packet> packets = files.get(fileId);
							if (packets.get(0).getTotalPackets() != packets.size()) {
								running = true;
								break;
							}
						}
					}
				}
				packet.setData(buffer);
				
				if (System.in.available()>0)
					running = false;
			}
		}
		System.out.println();
		files.remove((short) 0);
	}
	
	/**
	 * Download firmware
	 * @throws IOException 
	 */
	private void parseHeaders() {
		System.out.println("Parse headers...");
		List<Packet> packets = files.get((short) 0);
		Collections.sort(packets);
		for (Packet packet:packets) {
			ByteBuffer buffer = packet.getData();
			buffer.position(Packet.HEADER_SIZE + (packet.getPacketId()==0?20:-2));
			while (buffer.remaining()>0) {
				short fileId = buffer.getShort();
				buffer.position(buffer.position()+6); //Skip 00 00 00 00 81 B4
				buffer.getInt(); //Read unknown
				buffer.getInt(); //Read filesize
				buffer.position(buffer.position()+4); //Skip 12 CE 97 F0 00 0E
				short fileNameLength = buffer.getShort(); //Filename length
				String name = new String(buffer.array(), buffer.position(), fileNameLength);
				if (name.charAt(fileNameLength-1)==0)
					name = name.substring(0, fileNameLength-1);
				
				buffer.position(buffer.position()+fileNameLength);
				names.put(fileId, name);
			}
		}
	}
	
	/**
	 * Extract firmware
	 * @throws IOException 
	 */
	private void extract() throws FileNotFoundException, IOException {
		System.out.println("Extracting files...");
		
		for (Short fileId : names.keySet()) {
			String fileName = names.get(fileId);
			if (files.containsKey(fileId) && files.get(fileId).size() == files.get(fileId).get(0).getTotalPackets()) {
				System.out.println("Extracting " + fileName);
				List<Packet> filePackets = files.get(fileId);
				Collections.sort(filePackets);

				try (FileOutputStream out = new FileOutputStream(new File(dir, names.get(fileId)))) {
					for (Packet packet:filePackets) {
						ByteBuffer buffer = packet.getData();
						out.write(buffer.array(), Packet.HEADER_SIZE, buffer.limit()-Packet.HEADER_SIZE);
					}
				}
			} else {
				if (files.containsKey(fileId))
					System.err.println(fileName + " not found");
				else
					System.err.println(fileName + " incomplete");
			}
		}
	}
	
	public static void main(String args[]) {
		System.out.println("AmiFirm 0.1 Copyright (c) 2013 Iwan Timmer");
		System.out.println("Distributed under the GNU GPL v3. For full terms see the file LICENSE.");
		System.out.println();

		if (args.length!=3&&args.length!=4) {
			System.out.println("Usage: java -jar amifirm.jar <multicast_address> <port> <path_to_extract> [firmware_backup]");
			System.exit(-1);
		}

		try {
			InetAddress source = InetAddress.getByName(args[0]);
			if (!source.isMulticastAddress()) {
				System.err.println("Address is not a multicast address");
				System.exit(-1);
			}
			
			int port = Integer.parseInt(args[1]);
			if (port<0||port>65535) {
				System.err.println("Port is not a valid portnumber");
				System.exit(-1);
			}
			
			File dir = new File(args[2]);
			dir.mkdir();
			if (!dir.isDirectory()) {
				System.err.println("Directory is not valid");
				System.exit(-1);
			}
			
			File export = null;
			if (args.length==4)
				export = new File(args[3]);
		
			AmiFirm firm = new AmiFirm(source, port, dir, export);
			
			try {
				firm.run();
			} catch (SocketTimeoutException e) {
				System.err.println("Couldn't receive firmware data");
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		} catch (NumberFormatException e) {
			System.err.println("Port is not a valid portnumber");
			System.exit(-1);
		} catch (UnknownHostException e) {
			System.err.println("Address is not a multicast address");
			System.exit(-1);
		}
	}
	
}
