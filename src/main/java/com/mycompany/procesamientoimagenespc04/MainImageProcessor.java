package com.mycompany.procesamientoimagenespc04;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.image.BufferedImage; // Necesario para el test de reproducción

public class MainImageProcessor {

    // --- Configuration ---
    public static final String MAIN_IMAGE_PATH = "main_image.jpg";
    public static final String PHYSICAL_FRAMES_DIR = "physical_frames";
    public static final String COMPRESSED_ARCHIVE_PATH = "physical_frames.zip";
    public static final String VIRTUAL_METADATA_FILE = "virtual_frames_metadata.json";

    // Image dimensions (example values, replace with your actual image size)
    // M_MAIN (Height) and N_MAIN (Width) define the size of the *main* image.
    // m_SUB (sub-image height) and n_SUB (sub-image width) define the size of each *frame*.
    public static final int M_MAIN = 64; // Height of the main image
    public static final int N_MAIN = 64; // Width of the main image
    public static final int m_SUB = 32;  // Sub-image height
    public static final int n_SUB = 32;  // Sub-image width

    public static void main(String[] args) {
        System.out.println("Starting Image Processing Application...");

        // Ensure the main image exists for testing
        // Note: ImageUtils.createDummyMainImage expects width, then height for BufferedImage
        ImageUtils.createDummyMainImage(MAIN_IMAGE_PATH, N_MAIN, M_MAIN); // N_MAIN (width), M_MAIN (height)

        long totalFramesPossible = ImageUtils.calculateTotalFrames(M_MAIN, N_MAIN, m_SUB, n_SUB);
        System.out.println(String.format("\nTotal frames possible: %d", totalFramesPossible));

        // Clean up previous runs
        ImageUtils.deleteDirectory(Paths.get(PHYSICAL_FRAMES_DIR));
        ImageUtils.deleteFile(Paths.get(COMPRESSED_ARCHIVE_PATH));
        ImageUtils.deleteFile(Paths.get(VIRTUAL_METADATA_FILE));

        // --- Serial Execution ---
        System.out.println("\n===== SERIAL EXECUTION =====");
        SerialProcessor serialProcessor = new SerialProcessor(MAIN_IMAGE_PATH, M_MAIN, N_MAIN, m_SUB, n_SUB,
                                                              PHYSICAL_FRAMES_DIR, VIRTUAL_METADATA_FILE);

        // Check if the main image dimensions were successfully loaded
        if (serialProcessor.getMainImageDims() != null) {
            serialProcessor.generatePhysicalFramesSerial();
            serialProcessor.generateVirtualFrameMetadataSerial();

            // Test serial reproduction
            System.out.println("\n--- Testing Serial Reproduction (Frame 10 and 100) ---");
            try {
                long reproductionStartTime = System.currentTimeMillis();
                BufferedImage reproducedFrame10 = serialProcessor.reproduceVirtualFrameSerial(10);
                if (reproducedFrame10 != null) {
                    System.out.println("Successfully reproduced virtual frame 10.");
                    // In a GUI app, you'd display it. For this example, we just confirm.
                    // ImageUtils.displayImage(reproducedFrame10, "Frame 10"); // Uncomment to display visually
                }
                BufferedImage reproducedFrame100 = serialProcessor.reproduceVirtualFrameSerial(100);
                if (reproducedFrame100 != null) {
                    System.out.println("Successfully reproduced virtual frame 100.");
                    // ImageUtils.displayImage(reproducedFrame100, "Frame 100"); // Uncomment to display visually
                }
                long reproductionEndTime = System.currentTimeMillis();
                System.out.println(String.format("Serial reproduction test completed in %.2f seconds.", (reproductionEndTime - reproductionStartTime) / 1000.0));

            } catch (IOException e) {
                System.err.println("Error during serial reproduction test: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("Skipping serial execution as main image dimensions could not be determined.");
        }


        // Clean up physical frames for parallel run comparison
        ImageUtils.deleteDirectory(Paths.get(PHYSICAL_FRAMES_DIR));
        ImageUtils.deleteFile(Paths.get(COMPRESSED_ARCHIVE_PATH));

        // --- Parallel Execution ---
        System.out.println("\n===== PARALLEL EXECUTION =====");
        // Get the number of available CPU cores to determine thread pool size
        int numProcesses = Runtime.getRuntime().availableProcessors();
        ParallelProcessor parallelProcessor = new ParallelProcessor(MAIN_IMAGE_PATH, M_MAIN, N_MAIN, m_SUB, n_SUB,
                                                                  PHYSICAL_FRAMES_DIR, VIRTUAL_METADATA_FILE, numProcesses);

        // Check if the main image dimensions were successfully loaded
        if (parallelProcessor.getMainImageDims() != null) {
            parallelProcessor.generatePhysicalFramesParallel();

            // Ensure virtual metadata exists for concurrent reproduction
            // It should have been generated by the serial run, but check again.
            if (!Files.exists(Paths.get(VIRTUAL_METADATA_FILE))) {
                 System.out.println(String.format("Virtual metadata file '%s' not found. Generating it for concurrent test.", VIRTUAL_METADATA_FILE));
                 // Use serial processor to generate it quickly if it somehow got deleted
                 serialProcessor.generateVirtualFrameMetadataSerial();
            }

            // --- Concurrency Test ---
            int K_FRAMES = 10; // Number of frames to reproduce concurrently (K < T)
            double reproductionDelay = 0.001; // Small delay in seconds to simulate work per frame
            parallelProcessor.reproduceVirtualFrameConcurrently(K_FRAMES, reproductionDelay);
        } else {
            System.err.println("Skipping parallel execution as main image dimensions could not be determined.");
        }

        System.out.println("\n--- All operations completed ---");
    }
}