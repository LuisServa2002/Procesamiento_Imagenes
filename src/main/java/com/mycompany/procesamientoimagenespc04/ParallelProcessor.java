package com.mycompany.procesamientoimagenespc04;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*; // For ExecutorService, Callable, Future, BlockingQueue
import java.util.concurrent.atomic.AtomicLong; // For thread-safe counter

public class ParallelProcessor extends SerialProcessor { // Inherit for common properties and helper methods

    private final int numProcesses; // Number of parallel processes/threads to use

    public ParallelProcessor(String mainImagePath, int M_MAIN, int N_MAIN, int m_SUB, int n_SUB,
                             String physicalFramesDir, String virtualMetadataFile, int numProcesses) {
        super(mainImagePath, M_MAIN, N_MAIN, m_SUB, n_SUB, physicalFramesDir, virtualMetadataFile);
        this.numProcesses = numProcesses;
        System.out.println(String.format("Using %d processes/threads for parallel operations.", this.numProcesses));
    }

    // Callable for parallel frame generation (Case 1)
    // Each instance of this task will generate a single frame.
    private static class GenerateFrameTask implements Callable<Boolean> {
        private final long frameIdx;
        private final String mainImagePath;
        private final int M_MAIN; // Main image height
        private final int N_MAIN; // Main image width
        private final int m_SUB;  // Sub-image height
        private final int n_SUB;  // Sub-image width
        private final String physicalFramesDir;

        public GenerateFrameTask(long frameIdx, String mainImagePath, int M_MAIN, int N_MAIN, int m_SUB, int n_SUB, String physicalFramesDir) {
            this.frameIdx = frameIdx;
            this.mainImagePath = mainImagePath;
            this.M_MAIN = M_MAIN;
            this.N_MAIN = N_MAIN;
            this.m_SUB = m_SUB;
            this.n_SUB = n_SUB;
            this.physicalFramesDir = physicalFramesDir;
        }

        @Override
        public Boolean call() throws Exception {
            // Declare coords outside the try block so it's accessible in catch blocks
            int[] coords = null;
            try {
                // Each thread/process reads the main image from disk
                BufferedImage mainImg = ImageIO.read(new File(mainImagePath));
                if (mainImg == null) {
                    System.err.println(String.format("Task Error (Thread %d): Main image '%s' could not be loaded for frame %d.", Thread.currentThread().getId(), mainImagePath, frameIdx));
                    return false;
                }

                coords = ImageUtils.getFrameCoordinates(frameIdx, M_MAIN, N_MAIN, m_SUB, n_SUB); // Assign value here
                int x_start = coords[0];
                int y_start = coords[1];

                BufferedImage croppedImg = mainImg.getSubimage(x_start, y_start, n_SUB, m_SUB);
                String framePath = String.format("%s/frame_%05d.png", physicalFramesDir, frameIdx);
                ImageIO.write(croppedImg, "png", new File(framePath));
                return true; // Indicate success
            } catch (IOException e) {
                System.err.println(String.format("Error generating frame %d (Thread %d): %s", frameIdx, Thread.currentThread().getId(), e.getMessage()));
                return false; // Indicate failure
            } catch (java.awt.image.RasterFormatException e) {
                 // Enhanced error message to handle null coords gracefully
                 String coordStr = (coords != null && coords.length == 2) ? String.format("x=%d, y=%d", coords[0], coords[1]) : "unknown";
                 System.err.println(String.format("Error cropping image for frame %d (Thread %d - invalid region): %s. Coords: %s, w=%d, h=%d",
                    frameIdx, Thread.currentThread().getId(), e.getMessage(), coordStr, n_SUB, m_SUB));
                return false;
            }
        }
    }

    // --- Case 1: Parallel Physical Frame Generation ---
    public void generatePhysicalFramesParallel() {
        long startTime = System.currentTimeMillis();
        System.out.println(String.format("\n--- Parallel: Generating %d physical frames using %d processes/threads ---", totalFrames, numProcesses));
        ImageUtils.createDirectory(Paths.get(physicalFramesDir));

        // Create a fixed-size thread pool
        ExecutorService executor = Executors.newFixedThreadPool(numProcesses);
        List<Future<Boolean>> futures = new ArrayList<>();
        AtomicLong generatedCount = new AtomicLong(0); // Thread-safe counter for progress updates

        // Submit tasks for all frames
        for (long i = 0; i < totalFrames; i++) {
            futures.add(executor.submit(new GenerateFrameTask(i, mainImagePath, M_MAIN, N_MAIN, m_SUB, n_SUB, physicalFramesDir)));
        }

        // Collect results and update progress
        for (int i = 0; i < futures.size(); i++) {
            try {
                if (futures.get(i).get()) { // .get() blocks until the task corresponding to this Future completes
                    long count = generatedCount.incrementAndGet();
                    if (count % 10000 == 0) {
                        System.out.println(String.format("Generated %d/%d frames...", count, totalFrames));
                    }
                } else {
                    System.err.println(String.format("Failed to generate frame %d (task index %d).", i, i));
                }
            } catch (InterruptedException | ExecutionException e) {
                System.err.println(String.format("Error fetching result for frame task %d: %s", i, e.getMessage()));
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
        }

        executor.shutdown(); // Initiate orderly shutdown of the executor
        try {
            // Wait for all tasks to complete or timeout after 60 minutes
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                System.err.println("Executor did not terminate in the specified time. Forcing shutdown.");
                executor.shutdownNow(); // Force shutdown if it doesn't terminate cleanly
            }
        } catch (InterruptedException e) {
            System.err.println("Executor termination interrupted: " + e.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt(); // Restore interrupt status
        }

        long endTime = System.currentTimeMillis();
        System.out.println(String.format("Parallel frame generation completed in %.2f seconds.", (endTime - startTime) / 1000.0));
        compressPhysicalFrames(); // Compression is still a serial process here
    }


    // Runnable for concurrent virtual frame reproduction (Case 2)
    // Each instance of this task runs in a thread and processes frames from a shared queue.
    private static class ReproduceFrameTask implements Runnable {
        private final BlockingQueue<Long> frameQueue;
        private final BlockingQueue<Boolean> resultsQueue;
        private final double reproductionDelaySeconds;
        private final List<FrameMetadata> metadata;

        // Constructor does NOT throw IOException directly, but catches it and re-throws as RuntimeException
        public ReproduceFrameTask(BlockingQueue<Long> frameQueue, BlockingQueue<Boolean> resultsQueue,
                                  String virtualMetadataFile, double reproductionDelaySeconds) {
            this.frameQueue = frameQueue;
            this.resultsQueue = resultsQueue;
            this.reproductionDelaySeconds = reproductionDelaySeconds;

            ObjectMapper mapper = new ObjectMapper();
            try {
                // Handle IOException during metadata loading directly in the constructor
                this.metadata = mapper.readValue(new File(virtualMetadataFile),
                        mapper.getTypeFactory().constructCollectionType(List.class, FrameMetadata.class));
            } catch (IOException e) {
                System.err.println(String.format("Error loading metadata for ReproduceFrameTask: %s", e.getMessage()));
                // Re-throw as RuntimeException because the task cannot be initialized without metadata
                throw new RuntimeException("Failed to initialize ReproduceFrameTask due to metadata loading error.", e);
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Long frameId = frameQueue.take(); // Blocks until a frame ID is available in the queue
                    if (frameId == null) { // Null is used as a sentinel value to signal worker to stop
                        break;
                    }

                    // The cast (int) frameId is correct here, as List.get() expects an int index.
                    // The range check ensures frameId is within valid bounds.
                    if (!(frameId >= 0 && frameId < metadata.size())) {
                        System.err.println(String.format("Worker Error (Thread %d): Frame ID %d out of range. Metadata size: %d", Thread.currentThread().getId(), frameId, metadata.size()));
                        resultsQueue.put(false);
                        continue;
                    }

                    // *** ESTA ES LA LÍNEA MODIFICADA (antes era metadata.get((int) frameId); ) ***
                    FrameMetadata frameInfo = metadata.get(frameId.intValue()); 

                    BufferedImage mainImg = null; // Declare here so it's accessible outside try-block if needed for error
                    try {
                        // Each thread reads the main image (or a portion) from disk
                        mainImg = ImageIO.read(new File(frameInfo.getMainImagePath()));
                        if (mainImg == null) {
                            System.err.println(String.format("Worker Error (Thread %d): Main image '%s' not found or could not be read for frame %d.", Thread.currentThread().getId(), frameInfo.getMainImagePath(), frameId));
                            resultsQueue.put(false);
                            continue;
                        }
                        // Crop the sub-image
                        mainImg.getSubimage(frameInfo.getX(), frameInfo.getY(), frameInfo.getWidth(), frameInfo.getHeight());
                        // Simulate processing/displaying the frame by pausing the thread
                        Thread.sleep((long) (reproductionDelaySeconds * 1000));
                        // System.out.println(String.format("Process %d reproduced frame %d", Thread.currentThread().getId(), frameId)); // Uncomment for verbose per-frame output
                        resultsQueue.put(true); // Indicate successful reproduction
                    } catch (IOException e) {
                        System.err.println(String.format("Worker Error (Thread %d) reproducing frame %d: %s", Thread.currentThread().getId(), frameId, e.getMessage()));
                        resultsQueue.put(false);
                    } catch (InterruptedException e) {
                        System.err.println(String.format("Worker thread %d interrupted while reproducing frame %d: %s", Thread.currentThread().getId(), frameId, e.getMessage()));
                        Thread.currentThread().interrupt(); // Restore interrupt status
                        resultsQueue.put(false);
                        break; // Exit loop if interrupted
                    } catch (java.awt.image.RasterFormatException e) {
                         System.err.println(String.format("Worker Error cropping image for frame %d (Thread %d - invalid region): %s. Coords: x=%d, y=%d, w=%d, h=%d. Main image dims: %dx%d",
                            frameId, Thread.currentThread().getId(), e.getMessage(), frameInfo.getX(), frameInfo.getY(), frameInfo.getWidth(), frameInfo.getHeight(),
                            (mainImg != null ? mainImg.getWidth() : 0), (mainImg != null ? mainImg.getHeight() : 0))); // Add main image dims for context
                        resultsQueue.put(false);
                    }
                }
            } catch (InterruptedException e) {
                System.err.println(String.format("Worker thread %d interrupted while waiting for frames: %s", Thread.currentThread().getId(), e.getMessage()));
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
            // Note: The IOException catch in the run() method for metadata loading is removed
            // because that specific exception is now handled in the constructor.
            // Other IOExceptions (like ImageIO.read) are still handled within the inner try-catch.
            // If the RuntimeException from the constructor occurred, this run() method would not even start.
        }
    }

    /**
     * Reproduces a set of K frames concurrently using multiple threads.
     * @param K_frames_to_reproduce The number of frames to reproduce.
     * @param reproductionDelay A simulated delay for each frame reproduction in seconds.
     */
    public void reproduceVirtualFrameConcurrently(int K_frames_to_reproduce, double reproductionDelay) {
        if (!Paths.get(virtualMetadataFile).toFile().exists()) {
            System.err.println(String.format("Metadata file '%s' not found. Please generate it first.", virtualMetadataFile));
            return;
        }

        System.out.println(String.format("\n--- Concurrency: Reproducing %d virtual frames concurrently using %d threads ---", K_frames_to_reproduce, numProcesses));
        long startTime = System.currentTimeMillis();

        // Queues for communication between the main thread and worker threads
        BlockingQueue<Long> frameQueue = new LinkedBlockingQueue<>(); // Tasks to be processed
        BlockingQueue<Boolean> resultsQueue = new LinkedBlockingQueue<>(); // Results from processed tasks

        // Determine the actual number of frames to process (min of requested K and total available frames)
        List<Long> framesToProcess = new ArrayList<>();
        long actualK = Math.min(K_frames_to_reproduce, totalFrames);
        for (long i = 0; i < actualK; i++) {
            framesToProcess.add(i); // For simplicity, take the first K frames
        }
        if (K_frames_to_reproduce > totalFrames) {
            System.out.println(String.format("Warning: Requested %d frames, but only %d available. Processing %d frames.", K_frames_to_reproduce, totalFrames, actualK));
        }

        // Create and start worker threads
        ExecutorService executor = Executors.newFixedThreadPool(numProcesses);
        List<Future<?>> workerFutures = new ArrayList<>(); // To hold Futures for worker threads

        for (int i = 0; i < numProcesses; i++) {
            try {
                // Submit a ReproduceFrameTask to the executor
                workerFutures.add(executor.submit(new ReproduceFrameTask(frameQueue, resultsQueue, virtualMetadataFile, reproductionDelay)));
            } catch (RuntimeException e) { // Catch RuntimeException from constructor
                System.err.println("Failed to create ReproduceFrameTask due to metadata loading error: " + e.getMessage());
                executor.shutdownNow(); // Stop the executor if workers cannot be initialized
                return; // Exit the method
            }
        }

        // Add frame IDs to the task queue
        for (Long frameId : framesToProcess) {
            try {
                frameQueue.put(frameId); // Add frame ID to the queue for workers to pick up
            } catch (InterruptedException e) {
                System.err.println("Main thread interrupted while adding frames to queue: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Wait for all frames to be processed by monitoring the results queue
        long processedCount = 0;
        while (processedCount < framesToProcess.size()) {
            try {
                resultsQueue.take(); // Blocks until a result is available
                processedCount++;
                if (processedCount % 100 == 0) {
                    System.out.println(String.format("Reproduced %d/%d frames...", processedCount, framesToProcess.size()));
                }
            } catch (InterruptedException e) {
                System.err.println("Main thread interrupted while waiting for results: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Signal workers to exit by putting 'null' (sentinel) into the queue for each worker
        for (int i = 0; i < numProcesses; i++) {
            try {
                frameQueue.put(null);
            } catch (InterruptedException e) {
                System.err.println("Main thread interrupted while sending stop signals to workers: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        executor.shutdown(); // Initiate graceful shutdown of the executor
        try {
            // Wait for all worker threads to finish their tasks and terminate
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) { // Max wait time 5 minutes
                System.err.println("Concurrent reproduction executor did not terminate in time. Forcing shutdown.");
                executor.shutdownNow(); // Force shutdown if workers don't respond
            }
        } catch (InterruptedException e) {
            System.err.println("Concurrent reproduction executor termination interrupted: " + e.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt(); // Restore interrupt status
        }

        long endTime = System.currentTimeMillis();
        System.out.println(String.format("Concurrent reproduction completed in %.2f seconds.", (endTime - startTime) / 1000.0));
    }
}