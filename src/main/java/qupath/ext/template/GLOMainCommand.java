package qupath.ext.template;

import javafx.application.Platform;
import javafx.concurrent.Task;
import qupath.lib.io.PathIO;
import java.io.InputStream;
import java.io.FileInputStream; 
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.net.HttpURLConnection;
import java.net.URL;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Comparator;

public class GLOMainCommand {

    private static final Logger logger = LoggerFactory.getLogger(GLOMainCommand.class);
    private final QuPathGUI qupath;
    private String serverURL;
    private String targetDir;

    public GLOMainCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    public void setFolderPermissions(String folderPath) throws IOException {
        Path path = Paths.get(folderPath);
        
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_WRITE);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_WRITE);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);
        
        Files.setPosixFilePermissions(path, perms);
    }

    public void downloadFile(String fileUrl, String destinationPath) throws IOException {
        File destinationFile = new File(destinationPath);

        // Always delete the file if it exists so we download a fresh copy
        if (destinationFile.exists()) {
            destinationFile.delete();
        }

        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);  // Automatically follow redirects
        connection.setRequestMethod("GET");
        connection.connect();

        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            // Handle redirects (e.g., 303, 302, etc.)
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER) {
                String newUrl = connection.getHeaderField("Location");
                connection = (HttpURLConnection) new URL(newUrl).openConnection();
                connection.connect();
            } else {
                throw new IOException("Failed to download file: " + connection.getResponseCode());
            }
        }
        
        try (InputStream inputStream = connection.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        System.out.println("Downloaded: " + destinationPath);
        setFolderPermissions(destinationPath);
    }

    public ArrayList<File> createInitialDirs() throws IOException {
        String desktopDir = getDesktopDirectory();
        ArrayList<File> iomDirs = new ArrayList<>();

        // Delete and recreate input and output directories every time
        File inputDir = new File(desktopDir + "/input_slide/");
        if (inputDir.exists()) {
            deleteDirectory(inputDir);
        }
        inputDir.mkdirs();

        File outputDir = new File(desktopDir + "/output_slide/");
        if (outputDir.exists()) {
            deleteDirectory(outputDir);
        }
        outputDir.mkdirs();

        // Preserve model and config directories (create if they don't exist)
        File modelDir = new File(desktopDir + "/model/");
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }

        File configDir = new File(desktopDir + "/config/");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        iomDirs.add(inputDir);
        iomDirs.add(outputDir);
        iomDirs.add(modelDir);
        iomDirs.add(configDir);

        return iomDirs;
    }

    public static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) { // Check if directory is non-empty
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file); // Recursively delete subdirectories
                }
                file.delete(); // Delete files
            }
        }
        dir.delete(); // Finally delete the main directory
    }

    public void downloadResources(String pthLink, String configLink, String destinationDir, String configDir, 
                                  String linuxExeLink) throws IOException {
        // Download .pth file: skip if already exists
        String pthFileName = "mask2former_swin_b_kpis_768_best_mDice.pth";
        String destinationPath = destinationDir + "/" + pthFileName;
        File pthFile = new File(destinationPath);
        if (pthFile.exists()) {
            System.out.println("Model file already exists, skipping download: " + destinationPath);
        } else {
            downloadFile(pthLink, destinationPath);
        }

        // Download Linux executable file from Google Drive: skip if already exists
        String finalExeName = "inference_wsi_level_kpis";
        String finalExePath = destinationDir + "/" + finalExeName;
        File linuxExe = new File(finalExePath);
        if (linuxExe.exists()) {
            System.out.println("Linux executable already exists, skipping download: " + finalExePath);
        } else {
            downloadFile(linuxExeLink, finalExePath);
        }
        
        // Download config file: skip if already exists
        String configFilePath = configDir + "/config.py";
        File configFile = new File(configFilePath);
        if (configFile.exists()) {
            System.out.println("Config file already exists, skipping download: " + configFilePath);
        } else {
            downloadFile(configLink, configFilePath);
        }
    }

    // Method to determine the Desktop directory and create required folders
    public String getDesktopDirectory() throws IOException {
        String desktopDir = null;
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            desktopDir = System.getProperty("user.home") + "\\Desktop";
        } else if (os.contains("mac")) {
            desktopDir = System.getProperty("user.home") + "/Desktop";
        } else if (os.contains("nix") || os.contains("nux") || os.indexOf("aix") > 0) {
            desktopDir = "/home/VANDERBILT/tungm1" + "/Desktop";
        } else {
            throw new IOException("Unsupported operating system");
        }

        Files.createDirectories(Paths.get(desktopDir));
        return desktopDir;
    }

    // Submit detection task
    public void submitDetectionTask() {
        try {
            // Setup directories based on the OS
            String desktopDir = getDesktopDirectory();
            ArrayList<File> iomDirs = createInitialDirs();

            String outputDir = iomDirs.get(1).toPath().toString();
            String qupathModelDir = iomDirs.get(2).toPath().toString();
            String configDir = iomDirs.get(3).toPath().toString();

            // Set folder permissions after creating the directory
            setFolderPermissions(qupathModelDir);
    
            // Direct download link for the .pth file
            String pthLink = "https://drive.usercontent.google.com/download?id=1C8N1Lw0Mb3Yr8SRmdUjw5kAQ_2U3qxjw&export=download&confirm=t";

            // Direct download link for the model's config file
            String configLink = "https://raw.githubusercontent.com/tungm1/QuPath_Glomeruli_Segmentation_Extension/refs/heads/main/mask2former_swin-b_kpis_isbi_768.py";

            // Download link for the Linux executable
            String linuxExeLink = "https://drive.usercontent.google.com/download?id=1neqpv14KgtQ2MNEypMPgEa_JCnKRthUy&export=download&confirm=t";

            // Download resources to the model and config directories
            downloadResources(pthLink, configLink, qupathModelDir, configDir, linuxExeLink);

            // The Linux executable is now downloaded as a single file named "inference_wsi_level_kpis"
            String executablePath = qupathModelDir + "/inference_wsi_level_kpis";
            File executableFile = new File(executablePath);
            if (!executableFile.exists()) {
                throw new IOException("Linux executable was not downloaded correctly: " + executablePath);
            }

            // Get the Whole Slide Image (WSI) path from QuPath
            String rawPath = qupath.getViewer().getImageData().getServer().getPath();
            String wholeSlideImagePath = rawPath.contains("file:") ? rawPath.split("file:")[1].trim() : rawPath;
            wholeSlideImagePath = wholeSlideImagePath.replaceAll("\\[--series, 0\\]$", "");
            System.out.println("Extracted Whole Slide Image Path: " + wholeSlideImagePath);

            File tempWSI = new File(wholeSlideImagePath);
            String wsiName = tempWSI.getName();
            
            Path sourceP = tempWSI.toPath();
            Path newdirP = iomDirs.get(0).toPath();

            Files.copy(sourceP, newdirP.resolve(sourceP.getFileName()));

            // Prepare the command to run the executable
            List<String> command = new ArrayList<>();
            command.add(executablePath);
            command.add("--input");
            command.add(iomDirs.get(0).toPath().toString() + "/" + wsiName);
            command.add("--output");
            command.add(iomDirs.get(1).toPath().toString());
            command.add("--ckpt"); // model checkpoint
            command.add(iomDirs.get(2).toPath().toString() + "/mask2former_swin_b_kpis_768_best_mDice.pth");
            command.add("--config");
            command.add(iomDirs.get(3).toPath().toString() + "/config.py");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Capture and print the output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("Python script exited with error code: " + exitCode);
            } else {
                logger.info("Python script executed successfully.");
            }

            if (wsiName.endsWith(".tiff")) {
                wsiName = wsiName.replace(".tiff", ".geojson");
            } else {
                logger.warn("Unsupported WSI format for file: " + wholeSlideImagePath);
                return;
            }

            // Load the generated GeoJSON file into QuPath
            loadGeoJsonToQuPath(outputDir, wsiName);
        } catch (IOException | InterruptedException e) {
            logger.error("Error during process execution", e);
        }
    }

    // Load GeoJSON file to QuPath
    private void loadGeoJsonToQuPath(String geojsonDir, String wsiName) {
        File geojsonFile = new File(geojsonDir, wsiName);
        if (geojsonFile.exists()) {
            try (InputStream inputStream = new FileInputStream(geojsonFile)) {
                List<PathObject> objects = PathIO.readObjectsFromGeoJSON(inputStream);
                qupath.getViewer().getImageData().getHierarchy().addPathObjects(objects);
                logger.info("GeoJSON file loaded successfully: " + geojsonFile.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to read GeoJSON file: " + geojsonFile.getAbsolutePath(), e);
            }
        } else {
            logger.error("GeoJSON file does not exist: " + geojsonFile.getAbsolutePath());
        }
    }
}