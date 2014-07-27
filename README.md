AmiFirm
=======

With this application you can download or extract Amino Aminet MCastFSv2 
Firmware from the network or locally stored file using their proprietary 
multicast update protocol (MCastFSv2). This will enable you to further 
investigate the firmware contents.

## Usage

Start the application using:

```
java -jar amifirm.jar [options]
	-m [multicast address:port]	address to download firmware from
	-f [file]					name of local MCastFSv2 file
	-d [path]					path to extract firmware files to
	-s [filename]				file to cache firmware packets (multicast only)
```

## Known issues
* The file saved by the network mode is incompatible with the file mode
* File are overwritten or added to an existing directory without prior notice

## Copyright and license

Copyright 2013, 2014 Iwan Timmer
Copyright 2014 mielleman
Distributed under the GNU GPL v3. For full terms see the LICENSE file