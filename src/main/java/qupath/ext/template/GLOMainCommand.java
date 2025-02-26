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

        // Check if the file already exists
        if (destinationFile.exists()) {
            destinationFile.delete();
            // System.out.println("File already exists: " + destinationPath);
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

        ArrayList<File> iomDirs = new ArrayList<File>();

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

        File modelDir = new File(desktopDir + "/model/");
        if (modelDir.exists()) {
            deleteDirectory(modelDir);
        }
        modelDir.mkdirs();

        File configDir = new File(desktopDir + "/config/");
        if (configDir.exists()) {
            deleteDirectory(configDir);
        }
        configDir.mkdirs();

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


    // Method to download .pth files and Python scripts
    public void downloadResources(String pthLink, String configLink, String pyURL, String destinationDir, String configDir) throws IOException {
        // Download all .pth files
        String[] urlParts = pthLink.split("/");
        String pthFileName = urlParts[urlParts.length - 1];  // Extract the original file name from the URL
        String destinationPath = destinationDir + "/" + pthFileName;
        downloadFile(pthLink, destinationPath);

        configDir = configDir + "/" + "config.py";
        downloadFile(configLink, configDir);

        urlParts = pyURL.split("/");
        String pyFileName = urlParts[urlParts.length - 1];  // Extract the original file name from the URL
        destinationPath = destinationDir + "/" + pyFileName;
        downloadFile(pyURL, destinationPath);
    }

    // Method to determine the Desktop directory and create 'QuPath_extension_model' folder
    public String getDesktopDirectory() throws IOException {
        String desktopDir = null;
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // On Windows, the desktop is typically in the user's home directory
            desktopDir = System.getProperty("user.home") + "\\Desktop";
        } else if (os.contains("mac")) {
            // On macOS, the desktop is in the user's home directory
            desktopDir = System.getProperty("user.home") + "/Desktop";
        } else if (os.contains("nix") || os.contains("nux") || os.indexOf("aix") > 0) {
            // On Linux, the desktop is also in the user's home directory
            desktopDir = "/home/VANDERBILT/tungm1"+ "/Desktop";
        } else {
            throw new IOException("Unsupported operating system");
        }

        // Ensure the directory exists
        Files.createDirectories(Paths.get(desktopDir));
        return desktopDir;
    }

    // Submit detection task
    public void submitDetectionTask() {
        try {
            // QuPath directory setup based on OS
            String desktopDir = getDesktopDirectory();  // Get the QuPath directory based on the OS

            ArrayList<File> iomDirs = createInitialDirs();

            String outputDir = iomDirs.get(1).toPath().toString();
            String qupathModelDir = iomDirs.get(2).toPath().toString();
            String configDir = iomDirs.get(3).toPath().toString();

            // Set folder permissions after creating the directory
            setFolderPermissions(qupathModelDir);
    
            // direct download link for the .pth file
            String pthLink = "https://github.com/tungm1/QuPath_Glomeruli_Segmentation_Extension/raw/refs/heads/main/mask2former_swin_b_kpis_768_best_mDice.pth";

            // direct download link for the model's config file
            String configLink = "https://raw.githubusercontent.com/tungm1/QuPath_Glomeruli_Segmentation_Extension/refs/heads/main/mask2former_swin-b_kpis_isbi_768.py";

            // URL for the Python scripts
            String pyURL = "https://raw.githubusercontent.com/tungm1/glomeruli_segmentation_src/refs/heads/main/Code_to_Michael/Validation_slide_docker/src/inference_wsi_level_kpis.py";

            // Download resources (Python scripts and .pth files) to the QuPath models directory
            downloadResources(pthLink, configLink, pyURL, qupathModelDir, configDir);

            // Get WSI path
            String rawPath = qupath.getViewer().getImageData().getServer().getPath();
            String wholeSlideImagePath = rawPath.contains("file:") ? rawPath.split("file:")[1].trim() : rawPath;

            wholeSlideImagePath = wholeSlideImagePath.replaceAll("\\[--series, 0\\]$", "");
            System.out.println("Extracted Whole Slide Image Path: " + wholeSlideImagePath);

            File tempWSI = new File(wholeSlideImagePath);

            // Generate GeoJSON file path based on WSI name
            String wsiName = tempWSI.getName();
            
            Path sourceP = tempWSI.toPath();
            Path newdirP = iomDirs.get(0).toPath();

            Files.copy(sourceP, newdirP.resolve(sourceP.getFileName()));

            // Prepare Python command to run the downloaded script
            List<String> command = new ArrayList<>();
            command.add("/home/VANDERBILT/tungm1/glomeruli_segmentation_src/dist/inference_wsi_level_kpis/inference_wsi_level_kpis");

            command.add("--input");
            command.add(iomDirs.get(0).toPath().toString() + "/" + wsiName);
            command.add("--output");
            command.add(iomDirs.get(1).toPath().toString());
            command.add("--ckpt"); // model
            command.add(iomDirs.get(2).toPath().toString() + "/mask2former_swin_b_kpis_768_best_mDice.pth");
            command.add("--config");
            command.add(iomDirs.get(3).toPath().toString() + "/config.py");

            // Run the process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Capture the output
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

            // Output results
            loadGeoJsonToQuPath(outputDir, wsiName);
        } catch (IOException | InterruptedException e) {
            logger.error("Error during process execution", e);
        }
    }

    // The method is used to load the Geojson file to Qupath
    private void loadGeoJsonToQuPath(String geojsonDir, String wsiName) {
        File geojsonFile = new File(geojsonDir, wsiName); // Use the generated Geojson file name
        if (geojsonFile.exists()) {
            // Get the input stream of the file
            try (InputStream inputStream = new FileInputStream(geojsonFile)) {
                // Use the readObjectsFromGeoJSON method provided by Pathio to read the Geojson file
                List<PathObject> objects = PathIO.readObjectsFromGeoJSON(inputStream);
                // Add the object to the hierarchical structure of Qupath
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