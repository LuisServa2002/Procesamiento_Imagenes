package com.mycompany.procesamientoimagenespc04;

import com.fasterxml.jackson.databind.ObjectMapper; // For JSON serialization/deserialization

import javax.imageio.ImageIO; // For image reading/writing
import java.awt.image.BufferedImage; // Represents an image in memory
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry; // For ZIP compression
import java.util.zip.ZipOutputStream; // For ZIP compression

public class SerialProcessor {

    protected final String mainImagePath;
    protected final int M_MAIN; // Height of the main image
    protected final int N_MAIN; // Width of the main image
    protected final int m_SUB;  // Sub-image height
    protected final int n_SUB;  // Sub-image width
    protected final String physicalFramesDir; // Directory for physical frames
    protected final String virtualMetadataFile; // File for virtual frame metadata
    protected final long totalFrames; // Total number of possible frames
    protected final int[] mainImageDims; // {width, height} as read from the main image file

    public SerialProcessor(String mainImagePath, int M_MAIN, int N_MAIN, int m_SUB, int n_SUB,
                           String physicalFramesDir, String virtualMetadataFile) {
        this.mainImagePath = mainImagePath;
        this.M_MAIN = M_MAIN;
        this.N_MAIN = N_MAIN;
        this.m_SUB = m_SUB;
        this.n_SUB = n_SUB;
        this.physicalFramesDir = physicalFramesDir;
        this.virtualMetadataFile = virtualMetadataFile;
        // Calculate total frames based on provided dimensions (M_MAIN, N_MAIN, etc.)
        this.totalFrames = ImageUtils.calculateTotalFrames(M_MAIN, N_MAIN, m_SUB, n_SUB);
        // Attempt to get actual dimensions from the file, which might override the provided ones if mismatch
        this.mainImageDims = getMainImageDimsFromFile();
    }

    /**
     * Reads the actual dimensions of the main image file.
     * @return An array {width, height} or null if the file cannot be read.
     */
    protected int[] getMainImageDimsFromFile() {
        try {
            File file = new File(mainImagePath);
            if (!file.exists()) {
                System.err.println(String.format("Error: Main image file not found at %s.", mainImagePath));
                return null;
            }
            BufferedImage mainImg = ImageIO.read(file);
            if (mainImg != null) {
                return new int[]{mainImg.getWidth(), mainImg.getHeight()};
            }
        } catch (IOException e) {
            System.err.println(String.format("Error reading main image dimensions from %s: %s", mainImagePath, e.getMessage()));
        }
        return null;
    }

    /**
     * Returns the actual dimensions of the main image as read from the file.
     * @return An array {width, height} or null.
     */
    public int[] getMainImageDims() {
        return mainImageDims;
    }

    // --- Case 1: Serial Physical Frame Generation ---
    public void generatePhysicalFramesSerial() {
        long startTime = System.currentTimeMillis();
        System.out.println(String.format("\n--- Serial: Generating %d physical frames ---", totalFrames));
        ImageUtils.createDirectory(Paths.get(physicalFramesDir));

        try {
            // Load the main image once for serial processing
            BufferedImage mainImg = ImageIO.read(new File(mainImagePath));
            if (mainImg == null) {
                System.err.println(String.format("Error: Main image '%s' could not be loaded. Cannot generate frames.", mainImagePath));
                return;
            }

            for (long i = 0; i < totalFrames; i++) {
                int[] coords = ImageUtils.getFrameCoordinates(i, M_MAIN, N_MAIN, m_SUB, n_SUB);
                int x_start = coords[0]; // horizontal start
                int y_start = coords[1]; // vertical start

                // getSubimage uses (x, y, width, height)
                // Note: n_SUB is width, m_SUB is height
                BufferedImage croppedImg = mainImg.getSubimage(x_start, y_start, n_SUB, m_SUB);
                String framePath = String.format("%s/frame_%05d.png", physicalFramesDir, i); // e.g., frame_00000.png
                ImageIO.write(croppedImg, "png", new File(framePath));

                if ((i + 1) % 10000 == 0) { // Print progress every 10,000 frames
                    System.out.println(String.format("Generated %d/%d frames...", i + 1, totalFrames));
                }
            }
        } catch (IOException e) {
            System.err.println(String.format("Error during serial frame generation: %s", e.getMessage()));
            e.printStackTrace(); // Print stack trace for debugging
            return;
        }

        long endTime = System.currentTimeMillis();
        System.out.println(String.format("Serial frame generation completed in %.2f seconds.", (endTime - startTime) / 1000.0));
        compressPhysicalFrames(); // Perform compaction
    }

    /**
     * Compresses the directory of physical frames into a single ZIP archive.
     */
    protected void compressPhysicalFrames() {
        String compressedArchivePath = MainImageProcessor.COMPRESSED_ARCHIVE_PATH; // Get path from main config
        System.out.println(String.format("Compressing physical frames directory '%s' to '%s'...", physicalFramesDir, compressedArchivePath));
        long startTime = System.currentTimeMillis();

        try (FileOutputStream fos = new FileOutputStream(compressedArchivePath);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {

            File sourceDir = new File(physicalFramesDir);
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                System.err.println(String.format("Source directory '%s' not found for compression.", physicalFramesDir));
                return;
            }

            // Iterate over all files in the source directory
            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) { // Only process files, not subdirectories
                        zipOut.putNextEntry(new ZipEntry(file.getName())); // Create a new entry in the zip
                        Files.copy(file.toPath(), zipOut); // Copy file content to the zip stream
                        zipOut.closeEntry(); // Close the current entry
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(String.format("Error during compression: %s", e.getMessage()));
            e.printStackTrace();
            return;
        }
        long endTime = System.currentTimeMillis();
        System.out.println(String.format("Compression completed in %.2f seconds.", (endTime - startTime) / 1000.0));
    }

    // --- Case 2: Serial Virtual Frame Indexing and Reproduction ---
    public void generateVirtualFrameMetadataSerial() {
        long startTime = System.currentTimeMillis();
        System.out.println("\n--- Serial: Generating virtual frame metadata ---");
        List<FrameMetadata> metadata = new ArrayList<>();

        for (long i = 0; i < totalFrames; i++) {
            int[] coords = ImageUtils.getFrameCoordinates(i, M_MAIN, N_MAIN, m_SUB, n_SUB);
            // Store x, y, width, height (n_SUB is width, m_SUB is height)
            metadata.add(new FrameMetadata(i, coords[0], coords[1], n_SUB, m_SUB, mainImagePath));
        }

        ObjectMapper mapper = new ObjectMapper(); // Jackson object for JSON operations
        try {
            // Write the list of metadata objects to a JSON file, pretty-printed for readability
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(virtualMetadataFile), metadata);
        } catch (IOException e) {
            System.err.println(String.format("Error writing virtual frame metadata: %s", e.getMessage()));
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        System.out.println(String.format("Virtual frame metadata generation completed in %.2f seconds.", (endTime - startTime) / 1000.0));
    }

    /**
     * Reproduces a virtual frame by loading its metadata and cropping the main image.
     * @param frameId The ID of the frame to reproduce.
     * @return The cropped BufferedImage representing the frame, or null if an error occurs.
     * @throws IOException If there's an error reading files (metadata or main image).
     */
    public BufferedImage reproduceVirtualFrameSerial(long frameId) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<FrameMetadata> metadata;
        try {
            // Read all metadata from the JSON file
            metadata = mapper.readValue(new File(virtualMetadataFile),
                    mapper.getTypeFactory().constructCollectionType(List.class, FrameMetadata.class));
        } catch (IOException e) {
            System.err.println(String.format("Error: Metadata file not found at %s or could not be read: %s", virtualMetadataFile, e.getMessage()));
            throw e; // Re-throw to indicate a critical error
        }

        if (!(frameId >= 0 && frameId < metadata.size())) {
            System.err.println(String.format("Error: Frame ID %d out of range (0 to %d).", frameId, metadata.size() - 1));
            return null;
        }

        FrameMetadata frameInfo = metadata.get((int) frameId); // Cast to int is safe because frameId is within list bounds

        BufferedImage mainImg = null;
        try {
            // Load the main image (this will be done for each reproduction in serial mode)
            mainImg = ImageIO.read(new File(frameInfo.getMainImagePath()));
            if (mainImg == null) {
                System.err.println(String.format("Error: Main image '%s' not found or could not be read for frame %d.", frameInfo.getMainImagePath(), frameId));
                return null;
            }
            // Crop the sub-image based on the metadata
            return mainImg.getSubimage(frameInfo.getX(), frameInfo.getY(), frameInfo.getWidth(), frameInfo.getHeight());
        } catch (IOException e) {
            System.err.println(String.format("Error reproducing frame %d: %s", frameId, e.getMessage()));
            throw e; // Re-throw to indicate a critical error
        } catch (java.awt.image.RasterFormatException e) {
            System.err.println(String.format("Error cropping image for frame %d (invalid region): %s. Coords: x=%d, y=%d, w=%d, h=%d. Main image dims: %dx%d",
                frameId, e.getMessage(), frameInfo.getX(), frameInfo.getY(), frameInfo.getWidth(), frameInfo.getHeight(), mainImg.getWidth(), mainImg.getHeight()));
            return null;
        }
    }
}