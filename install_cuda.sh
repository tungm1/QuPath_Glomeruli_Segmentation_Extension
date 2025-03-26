#!/bin/bash

# Check the Ubuntu version
os_version=$(lsb_release -rs)

echo "Detected Ubuntu version: $os_version"

if [ "$os_version" == "20.04" ]; then
    echo "Running installation for Ubuntu 20.04..."
    wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2004/x86_64/cuda-keyring_1.1-1_all.deb
elif [ "$os_version" == "22.04" ]; then
    echo "Running installation for Ubuntu 22.04..."
    wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2204/x86_64/cuda-keyring_1.1-1_all.deb
else
    echo "Unsupported Ubuntu version. Please run the installation commands manually."
    exit 1
fi

sudo dpkg -i cuda-keyring_1.1-1_all.deb
sudo apt-get update
sudo apt-get -y install cuda-toolkit-12-4
sudo apt-get install -y cuda-drivers

echo "Installation complete. The system will now reboot."
sudo reboot

