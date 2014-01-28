AmiFirm
=======

With this application you can download or extract Amino Aminet MCastFSv2 
Firmware from the network or locally stored file using their proprietary 
multicast update protocol (MCastFSv2). This will enable you to further 
investigate the firmware contents.

## Usage

### Network multicast download
Start the application using:

```
java -jar amifirm.jar -m <multicast_address> <port> [path_to_extract]
```

Press a key when you think the download is ready (when there aren't appearing
new dots) to extract the firmware.

### Network multicast download
Start the application using:

```
java -jar amifirm.jar -f <path to firmware> <path to extract firmware to>
```

## Known issues
* The file saved by the network mode is incompatible with the file mode
* File are overwritten or added to an existing directory without prior notice

## Copyright and license

Copyright 2013, 2014 Iwan Timmer under [the GPLv3 license](LICENSE).

