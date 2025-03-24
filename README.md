# QuPath_Glomeruli_Segmentation_Extension
This is the QuPath extension for glomeruli segmentation created by the Biomedical Data Representation and Learning Lab (HRLB) at Vanderbilt

Model and Source Code Credit: https://github.com/huuquan1994/wsi_glomerulus_seg

Currently works for QuPath 0.5.1 on Ubuntu 20.04 and 22.04. Simply drag in the .jar file (with Java 17+ installed). If running another version, you will not be able to download the executable. You should run QuPath via the terminal to see the log output. For example, running "/home/USER/Desktop/QuPath-v0.5.1-Linux/QuPath/bin/QuPath"

As for the image to run the extension on, it needs to be a .tiff file. Other formats like .svs will not work and may need to be converted.

The first time running the program, it may take several minutes. The .jar will install a pyinstaller compiled executable to your system which will run the segmentation script. This means you do not need to have Python or the many dependencies installed yourself. Around 4.5GB of files need to be downloaded (this means you must also have 4.5GB of free space on your machine). This may also momentarily cause the QuPath window to say it is not responding, but just wait for the console output to finish.

Windows builds to come in the near future. MacOS is not planned for support.
