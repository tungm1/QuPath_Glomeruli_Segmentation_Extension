# QuPath_Glomeruli_Segmentation_Extension
This is the QuPath extension for glomeruli segmentation created by the Biomedical Data Representation and Learning Lab (HRLB) at Vanderbilt.

# FIRST-TIME RUN INSTRUCTIONS:
### This first time install may take a while, and requires a solid internet connection. There may be compatibility issues if you have multiple versions of QuPath or CUDA installed.

Check Your Ubuntu Version:
Open a Terminal window and run the following command to display your Ubuntu version:
```
lsb_release -a
```
Make a note of whether your system is Ubuntu 20.04 or Ubuntu 22.04.

## For Ubuntu 20.04:
In the Terminal, copy and paste these commands line by line:
```
wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2004/x86_64/cuda-keyring_1.1-1_all.deb
sudo dpkg -i cuda-keyring_1.1-1_all.deb
sudo apt-get update
sudo apt-get -y install cuda-toolkit-12-4
sudo apt-get install -y cuda-drivers
sudo reboot
```
These commands will download and install the NVIDIA keyring, update your package list, install the CUDA toolkit (version 12-4), install the necessary NVIDIA drivers, and then reboot your system.

## For Ubuntu 22.04:
In the Terminal, copy and paste these commands line by line:
```
wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2204/x86_64/cuda-keyring_1.1-1_all.deb
sudo dpkg -i cuda-keyring_1.1-1_all.deb
sudo apt-get update
sudo apt-get -y install cuda-toolkit-12-4
sudo apt-get install -y cuda-drivers
sudo reboot
```
These commands perform the same functions as above, using the correct download URL for Ubuntu 22.04.

### Note: The final command (sudo reboot) will restart your computer, which is required to complete the installation.

## Alternative Option
You can also download the install_cuda.sh file from this repository and run it. This will automatically check what Ubuntu version you have and run it. it will still reboot the computer.

# WORKFLOW INSTRUCTIONS:

* Currently works for QuPath 0.5.1 on Ubuntu 20.04 and 22.04. Simply drag in the .jar file (with Java 17+ installed). If running another version, you will not be able to download the executable. You should run QuPath via the terminal to see the log output. For example, running "/home/USER/Desktop/QuPath-v0.5.1-Linux/QuPath/bin/QuPath".

* As for the image to run the extension on, it needs to be a .tiff file. Other formats like .svs will not work and may need to be converted.

* The first time running the program, it may take several minutes. The .jar will install a PyInstaller compiled executable to your system which will run the segmentation script. This means you do not need to have Python or the many dependencies installed yourself. Around 4.5GB of files need to be downloaded (this means you must also have 4.5GB of free space on your machine). This may also momentarily cause the QuPath window to say it is not responding, but just wait for the console output to finish.

Windows builds to come in the near future. MacOS is not planned for support.

### Model and original source code credit: https://github.com/huuquan1994/wsi_glomerulus_seg
