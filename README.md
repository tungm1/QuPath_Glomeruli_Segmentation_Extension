# QuPath_Glomeruli_Segmentation_Extension
This is the QuPath extension for glomeruli segmentation created by the Biomedical Data Representation and Learning Lab (HRLB) at Vanderbilt

Model and Source Code Credit: https://github.com/huuquan1994/wsi_glomerulus_seg

Currently works for QuPath on Ubuntu (tested for 20.04). Simply drag in the .jar file (with Java 17+ installed). The .jar will install a pyinstaller compiled executable to your system which will run the segmentation script. This means you do not need to have Python or the many dependencies installed yourself. The first time this will take a while, as around 4.5GB of files need to be downloaded (this means you must also have 4.5GB of free space on your machine).

MacOS and Windows builds to come in the near future.