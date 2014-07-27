/**
 * Copyright (C) 2013, 2014 Iwan Timmer
 * Copyright (C) 2014 mielemann
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Application to download and extract Amino Aminet firmware (MCastFSv2)
 * @author Iwan Timmer
 */
public class AmiFirm {	
	private final Map<Short, ByteBuffer> fileBuffer;
	private	final Map<Short, String> fileNames;
	private final Map<Short, String> directoryNames;
	
	/**
	 * Create a new instance of AmiFirm
	 */
	public AmiFirm() {
		this.fileBuffer = new TreeMap<>();
		this.fileNames = new TreeMap<>();
		this.directoryNames = new TreeMap<>();
	}
        
	/**
	 * Parse local firmware file
	 * @throws IOException 
	 */
	private void parseFile(File file) throws IOException, SocketTimeoutException {
		System.out.println("Parsing firmware...");

		// try to read the file from 0..length with no overhead
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			// Skip the header
			in.skip(28);
			in.skip(2); // number of file headers
			in.skip(4);

			while (in.available() > 0) {
				int type = in.read();
				short fileId;
				int fileType;
				short parentId;
				int fileSize;

				switch(type) {
					case 0x00:
						// read the file information
						fileId = (short) in.read();				// fileid 25					
						parentId = in.readShort();				// parentid 004A
						in.skip(2);								// Skip 0000
						fileType = in.read();					// fileType 81
						in.skip(1);								// Skip A4
						in.skip(2);								// Skip 0000
						in.skip(4);								// Skip 0000 000D // size?
						in.skip(2);								// Skip F5CA // checksum?
						in.skip(4);								// Skip 12CE 97F0
						short fileNameLength = in.readShort();	// fileNameLength 000E

						// Stop if we don't have a filename to read
						if(fileNameLength <= 0)						
							break;

						// Read the name, move the read poition
						byte[] nameBuffer = new byte[fileNameLength];
						in.read(nameBuffer);
						String name = new String(nameBuffer);

						// Stop if we already have saved this entry
						if (directoryNames.containsKey(fileId) || fileNames.containsKey(fileId))
							break;

						// Remove the trailing 0x00 of a filename
						if (name.charAt(fileNameLength-1)==0)
							name = name.substring(0, fileNameLength-1);

						// When we have a parentId we want to include this name
						if(parentId > 0 && directoryNames.containsKey(parentId))
							name = String.format("%s/%s", directoryNames.get(parentId), name);					

						switch(fileType) {
							case 0x41:															
								directoryNames.put(fileId, name);
								break;
							case 0x81:
								fileNames.put(fileId, name);
								break;	
						}
						break;
					case 0x04:
						// file contents (sometimes) 19 
						in.skip(1);								// Skip 00
						short dataLength = in.readShort();		// length 0536
						short dataType = in.readShort();		// type 0312

						switch(dataType) {
							case 0x0110:
								// Unknown, no idea what this is ... so we skip
								in.skip(14);
								break;
							case 0x0312:
								// File data! split into multiple parts, normally parts are presented in sequence
								in.skip(2);							// Skip 0035
								in.skip(2);							// Part 0001
								in.skip(2);							// Skip 0006
								fileId = in.readShort();			// fileId 0001
								fileSize = in.readInt();			// fileSize 0000 1CA7
								in.skip(4);							// offset 0000 0524

								if(!fileBuffer.containsKey(fileId)) {
									ByteBuffer bb = ByteBuffer.allocate(fileSize);
									fileBuffer.put(fileId, bb);
								}
								
								ByteBuffer buffer = fileBuffer.get(fileId);
								in.read(buffer.array(), buffer.position(), dataLength - 18);
								buffer.position(buffer.position() + dataLength - 18);
								break;
							default:
								throw new IOException("Unsupported data header type: "+ dataType);		  		
						}
						break;

					default:
						// unknown data, maybe we misunderstood the information and did not parse it correctly
						System.out.println("Unknown header of type: " + type);
				}
			}
		}
	}
	
	/**
	 * Download firmware through a multicast subscription
	 * @param address multicast address to download from
	 * @param port portnumber to download from
	 * @param export file to read and save downloaded packets to and from (can be null)
	 * @throws IOException 
	 */
	private void download(InetAddress address, int port, File export) throws IOException, SocketTimeoutException {
		System.out.println("Press key to stop downloading");
		System.out.print("Downloading firmware...");
		try (MulticastSocket socket = new MulticastSocket(port)) {
			socket.setSoTimeout(5000);
			socket.joinGroup(address);
			
			Map<Short, Short> filePackets = new TreeMap<>();
			Map<Short, Short> completeFilePackets = new TreeMap<>();
			
			Set<String> keys = new HashSet<>();
			boolean running = true;
			byte[] data = new byte[1500];
			DatagramPacket packet = new DatagramPacket(data, data.length);
			
			FileOutputStream out = null;
			if (export!=null)
				out = new FileOutputStream(export);
			
			int n = 0;
			long last = 0;
			while (running) {
				String key = new String(data, 0, 10);
				socket.receive(packet);
				if (!keys.contains(key)) {
					if (out!=null) {
						ByteBuffer size = ByteBuffer.allocate(4).putInt(packet.getLength());
						out.write(size.array(), 0, 4);
						out.write(data, 0, packet.getLength());
					}
					
					ByteBuffer buffer = ByteBuffer.wrap(data, 0, packet.getLength());
					switch (data[0]) {
						case FilePacket.TYPE:
							FilePacket filePacket = new FilePacket(buffer);
							short id = filePacket.getFileId();
							
							ByteBuffer fileData = fileBuffer.get(id);
							if(fileData == null) {
								fileData = ByteBuffer.allocate(filePacket.getSize());
								filePackets.put(id, filePacket.getTotalPackets());
								fileBuffer.put(id, fileData);
								completeFilePackets.put(id, (short) 0);
							} else
								completeFilePackets.put(id, (short) (1 + completeFilePackets.get(id)));
							
							fileData.position(filePacket.getOffset());
							fileData.put(buffer.array(), Packet.HEADER_SIZE, buffer.limit());
							
							break;
						case HeaderPacket.TYPE:
							HeaderPacket headerPacket = new HeaderPacket(buffer);
							buffer.position(Packet.HEADER_SIZE + (headerPacket.getPacketId()==0?20:-2));
							while (buffer.remaining()>0) {
								short fileId = buffer.getShort();                 // fileid  0000
								buffer.position(buffer.position()+6);             // Skip    0001 0000 81A4
								buffer.getInt();                            // Read unknown  0000 0000
								buffer.getInt();                            // Read filesize 0000 1CA7
								buffer.position(buffer.position()+4);             // Skip    12CE 97F0
								short fileNameLength = buffer.getShort(); // Filename length 0000
								String name = new String(buffer.array(), buffer.position(), fileNameLength);
								if (name.charAt(fileNameLength-1)==0)
									name = name.substring(0, fileNameLength-1);

								buffer.position(buffer.position()+fileNameLength);
								fileNames.put(fileId, name);
							}						
							break;
						default:
							throw new IOException("Not supported firmware");
					}
					keys.add(key);
					
					data = new byte[data.length];
					if ((n++)%50 == 0 || last+1000<System.currentTimeMillis()) {
						System.out.print('.');
						last = System.currentTimeMillis();
					}
					
					if (filePackets.size() == (fileNames.size()+1)) {
						running = false;
						for (Short fileId:filePackets.keySet()) {
							if (!filePackets.get(fileId).equals(completeFilePackets.get(fileId))) {
								running = true;
								break;
							}
						}
					}
				}
				packet.setData(data);
				
				if (System.in.available()>0)
					running = false;
			}
		}
		System.out.println();
	}
	
	/**
	 * Extract firmware
	 * @param dir Directory to extract files to
	 * @param files name of files to extract (empty to extract all)
	 * @throws IOException 
	 */
	private void extract(File dir, List<String> files) throws FileNotFoundException, IOException {
		System.out.println("Creating directories...");
		
		for (String name : directoryNames.values()) {
			File d = new File(dir, name);
			
			if(!d.exists())
				d.mkdirs();
		}
		
		System.out.println("Saving files...");
		
		for (Short fileId : fileNames.keySet()) {
			String fileName = fileNames.get(fileId);
			boolean inflate = fileName.endsWith(".gz");
			if (inflate)
				fileName = fileName.substring(0, fileName.length()-3);
			
			if (files.isEmpty() || files.contains(fileName)) {
				if (fileBuffer.containsKey(fileId)) {
					System.out.println("Extracting " + fileName);
					ByteBuffer buffer = fileBuffer.get(fileId);
					buffer.rewind();
					
					// open the output (append is false), write and close
					try (FileOutputStream out = new FileOutputStream(new File(dir, fileName))) {
						if (inflate)
							writeInflate(out, buffer.array(), 0, buffer.remaining());
						else
							out.write(buffer.array(), 0, buffer.remaining());
					}
				} else {
					System.err.println(fileName + " not found in firmware");
				}
			}
		}
	}
	
	private void writeInflate(OutputStream out, byte[] data, int offset, int length) throws IOException {
		InputStream in = new GZIPInputStream(new ByteArrayInputStream(data, offset, length));
		byte[] buffer = new byte[4096];
		int len;
		while ((len = in.read(buffer)) != -1) {
			out.write(buffer, 0, len);
		}
	}
	
	public static void main(String args[]) {
		System.out.println("AmiFirm 0.2.0 - Amino Aminet Firmware downloader and extractor");

		int port = 0;
		InetAddress address = null;
		File file = null;
		File dir = null;
		File save = null;
		boolean usage = false;
		List<String> files = new ArrayList<>();
		
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-m":
					i++;
					if (i == args.length) {
						System.err.println("Multicast address is missing");
						usage = true;
					}
					
					String multicast[] = args[i].split(":");
					if (multicast.length != 2) {
						System.err.println("Address is not a multicast address");
						usage = true;
					}
					
					try {
						address = InetAddress.getByName(multicast[0]);
					} catch (UnknownHostException e) {
						address = null;
					}
					
					if (address != null && !address.isMulticastAddress()) {
						System.err.println("Address is not a multicast address");
						usage = true;
					}
					
					try {
						port = Integer.parseInt(multicast[1]);
					} catch (NumberFormatException e) {
						port = -1;
					}
					
					if (port<0||port>65535) {
						System.err.println("Port is not a valid portnumber");
						usage = true;
					}
					break;
				case "-s":
					i++;
					if (i == args.length) {
						System.err.println("File name is missing");
						usage = true;
					}
					save = new File(args[i]);
					break;
				case "-f":
					i++;
					if (i == args.length) {
						System.err.println("File name is missing");
						usage = true;
					}
					file = new File(args[i]);
					if (!file.exists()) {
						System.err.println("File doesn't exist");
						usage = true;
					}
					break;
				case "-d":
					i++;
					if (i == args.length) {
						System.err.println("Directory name is missing");
						usage = true;
					}
					dir = new File(args[i]);
					dir.mkdir();
					if (!dir.isDirectory()) {
						System.err.println("Directory is not valid");
						usage = true;
					}
					break;
				case "-e":
					i++;
					if (i == args.length) {
						System.err.println("File name is missing");
						usage = true;
					}
					files.add(args[i]);
					break;
			}
		}
		
		if (file != null && dir == null) {
			System.err.println("Need directory to extract to");
			usage = true;
		}
		
		if (address != null) {
			if (file != null) {
				System.err.println("You need to specify either a firmware file or a multicast address");
				usage = true;
			}
		}
		
		if (address == null && file == null)
			usage = true;
		
		if (usage) {
			System.out.println("Usage: java -jar amifirm.jar [options]");
			System.out.println("\t-m [multicast address:port]\taddress to download firmware from");
			System.out.println("\t-f [file]\t\t\tname of local MCastFSv2 file");
			System.out.println("\t-d [path]\t\t\tpath to extract firmware files to");
			System.out.println("\t-s [filename]\t\t\tfile to cache firmware packets (multicast only)");
			System.exit(-1);
		}

		try {
			AmiFirm firm = new AmiFirm();
			
			if (address != null)
				firm.download(address, port, save);
			if (file != null)
				firm.parseFile(file);
			
			if (dir != null)
				firm.extract(dir, files);
		} catch (SocketTimeoutException e) {
			System.err.println("Couldn't receive firmware data");
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}
	
}
