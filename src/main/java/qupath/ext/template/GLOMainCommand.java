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
            System.out.println("File already exists: " + destinationPath);
            return;
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


    

    // Method to unzip a .zip file
    public void unzipFile(String zipFilePath, String destDir) throws IOException {
        File dir = new File(destDir);
        if (!dir.exists()) dir.mkdirs();

        try (FileInputStream fis = new FileInputStream(zipFilePath);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(dir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    public ArrayList<File> createInitialDirs() throws IOException {
        String desktopDir = getDesktopDirectory();

        ArrayList<File> iomDirs = new ArrayList<File>();

        File inputDir = new File(desktopDir + "/input_slide/");
        if (!inputDir.exists()) inputDir.mkdirs();

        File outputDir = new File(desktopDir + "/output_slide/");
        if (!outputDir.exists()) outputDir.mkdirs();

        File modelDir = new File(desktopDir + "/model/");
        if (!modelDir.exists()) modelDir.mkdirs();

        iomDirs.add(inputDir);
        iomDirs.add(outputDir);
        iomDirs.add(modelDir);

        return iomDirs;
    }

    

    // Method to download .pth files and Python scripts
    public void downloadResources(String[] pthLinks, String zipFileUrl, String destinationDir) throws IOException {
        // Download all .pth files
        for (String modelUrl : pthLinks) {
            String[] urlParts = modelUrl.split("/");
            String pthFileName = urlParts[urlParts.length - 1];  // Extract the original file name from the URL
            String destinationPath = destinationDir + "/" + pthFileName;
            downloadFile(modelUrl, destinationPath);
        }

        // Ensure the 'zip' directory exists
        File zipDirectory = new File(destinationDir + "/zip");
        if (!zipDirectory.exists()) {
            zipDirectory.mkdirs();  // Create the directory if it doesn't exist
        }

        // Download the Python scripts as a ZIP file from GitHub
        String zipFileDestination = destinationDir + "/zip/python_scripts.zip";
        downloadFile(zipFileUrl, zipFileDestination);

        // Unzip the file after download
        unzipFile(zipFileDestination, destinationDir + "/python_scripts");
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
            String qupathModelDir = desktopDir + "/models_and_pythonfiles_v2";  // Create a models folder in the QuPath directory
            Files.createDirectories(Paths.get(qupathModelDir));

            // Set folder permissions after creating the directory
            setFolderPermissions(qupathModelDir);
    

            // Google Drive direct download links for the .pth files
            String[] pthLinks = {
                "https://drive.google.com/drive/folders/1ORYOhx-dqZ5CTeDLvwTSgLsueW4phBNJ?usp=drive_link",
            };

            // URL for the Python scripts ZIP file 
            String zipFileUrl = "https://raw.githubusercontent.com/tungm1/glomeruli_segmentation_src/refs/heads/main/Code_to_Michael.zip";

            
            // Make initial directories for py script
            ArrayList<File> iomDirs = createInitialDirs();

            // Download resources (Python scripts and .pth files) to the QuPath models directory
            downloadResources(pthLinks, zipFileUrl, qupathModelDir);

            // Set the directory where the models and Python scripts are located
            String loadModelDir = qupathModelDir;

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


            if (wsiName.endsWith(".svs")) {
                wsiName = wsiName.replace(".svs", ".geojson");
            } else if (wsiName.endsWith(".scn")) {
                wsiName = wsiName.replace(".scn", ".geojson");
            } else {
                logger.warn("Unsupported WSI format for file: " + wholeSlideImagePath);
                return;
            }

            // Prepare Python command to run the downloaded script
            List<String> command = new ArrayList<>();
            command.add("/home/VANDERBILT/tungm1/miniconda3/envs/CircleNet/bin/python3.7");  // Python interpreter
            command.add(qupathModelDir + "/python_scripts/Code_to_Michael/Validation_slide_docker/src/unet_validation_slide.py");  // Use the downloaded Python script
            // command.add("circledet");
            // command.add("--circle_fusion");
            // command.add("--generate_geojson");
            // command.add("--arch");
            // command.add("dla_34");
            // command.add("--demo");
            // command.add(wholeSlideImagePath);
            // command.add("--load_model_dir");
            // command.add(loadModelDir);  // Use the model directory with downloaded .pth files
            // command.add("--filter_boarder");
            // command.add("--demo_dir");
            // command.add(qupathModelDir + "/test_result");  // Set demo_dir as "test_result"
            // command.add("--target_dir");
            // command.add(qupathModelDir + "/test_only_result");  // Set target_dir as "test_only_result"

            // Docker command 
            // command.add("docker");
            // command.add("run");
            // command.add("--rm");

            // // Mount the input directory
            // command.add("-v");
            // command.add(inputDirPath + ":/input/:ro");  // Replace inputDirPath with your actual input directory variable

            // // Mount the output directory
            // command.add("-v");
            // command.add(outputDirPath + ":/output");  // Replace outputDirPath with your actual output directory variable

            // // Enable GPU support
            // command.add("--gpus");
            // command.add("all");

            // // Run interactively
            // command.add("-it");

            // Specify the Docker image
            command.add("hrlblab333/kpis:1.0");

            // Run the process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Map<String, String> env = processBuilder.environment();
            env.put("PATH", "/home/VANDERBILT/tungm1/miniconda3/envs/CircleNet/bin:" + env.get("PATH"));
            env.put("PYTHONPATH", "/home/VANDERBILT/tungm1/.local/lib/python3.7/site-packages");
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
                logger.error("Python script output: " + output.toString());
            } else {
                logger.info("Python script executed successfully.");
                logger.info("Python script output: " + output.toString());
            }

            // Call the method of generating Geojson path
            String targetDir= qupathModelDir + "/test_only_result";
            String geojsonDir = generateGeoJsonPath(targetDir);  // targetDir replaced by geojsonDir
            // Output results
            parsePythonOutput(output.toString(), geojsonDir, wsiName);
            
        } catch (IOException | InterruptedException e) {
            logger.error("Error during process execution", e);
        }
    }

    // Methods to generate Geojson to save directory paths
    private String generateGeoJsonPath(String targetDir) {
        Path targetDirPath = Paths.get(targetDir);
        String baseName = targetDirPath.getFileName().toString();
        String geoJsonDir = targetDirPath.getParent().resolve(baseName + "_geojson").toString();
        return geoJsonDir;
    }

    // The method is used to analyze Python output and load the Geojson file to Qupath
    private void parsePythonOutput(String output, String geojsonDir, String wsiName) {
        // Here you can add analytical logic of python output

        // Load the generated Geojson file
        loadGeoJsonToQuPath(geojsonDir, wsiName);
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