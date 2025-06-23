package com.mycompany.procesamientoimagenespc04;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
// import javax.swing.ImageIcon; // Uncomment if you want to use displayImage method
// import javax.swing.JFrame;   // Uncomment if you want to use displayImage method
// import javax.swing.JLabel;   // Uncomment if you want to use displayImage method


public class ImageUtils {

    /**
     * Creates a dummy main image for testing purposes.
     * @param path The path to save the image.
     * @param width The width of the image.
     * @param height The height of the image.
     */
    public static void createDummyMainImage(String path, int width, int height) {
        File imgFile = new File(path);
        if (!imgFile.exists()) {
            System.out.println(String.format("Creating a dummy main image at %s (%dx%d pixels)...", path, width, height));
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = img.createGraphics();
            g2d.setColor(Color.RED);
            g2d.fillRect(0, 0, width, height);
            g2d.setColor(Color.BLUE);
            // Draw some text to make it distinct
            g2d.setFont(new Font("Arial", Font.BOLD, 50));
            FontMetrics fm = g2d.getFontMetrics();
            String text = "Dummy Image";
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            g2d.drawString(text, (width - textWidth) / 2, (height / 2) + (textHeight / 2));
            g2d.dispose(); // Release graphics resources
            try {
                ImageIO.write(img, "png", imgFile);
                System.out.println("Dummy image created.");
            } catch (IOException e) {
                System.err.println("Error creating dummy image: " + e.getMessage());
            }
        } else {
            System.out.println(String.format("Dummy main image already exists at %s.", path));
        }
    }

    /**
     * Calculates the total number of possible sub-images (frames).
     * @param M Main image height.
     * @param N Main image width.
     * @param m Sub-image height.
     * @param n Sub-image width.
     * @return Total number of frames.
     */
    public static long calculateTotalFrames(int M, int N, int m, int n) {
        if (M < m || N < n) {
            return 0; // No frames can be generated if sub-image is larger than main
        }
        return (long) (M - m + 1) * (N - n + 1);
    }

    /**
     * Calculates the top-left coordinates (x, y) for a given frame index.
     * Note: x is column (horizontal), y is row (vertical).
     * @param frameIdx The index of the frame.
     * @param M Main image height.
     * @param N Main image width.
     * @param m Sub-image height.
     * @param n Sub-image width.
     * @return An array {x, y} representing the top-left corner.
     */
    public static int[] getFrameCoordinates(long frameIdx, int M, int N, int m, int n) {
        // Number of possible sub-images horizontally in the main image
        int framesPerRow = (N - n + 1);
        long row = frameIdx / framesPerRow;
        long col = frameIdx % framesPerRow;
        int x = (int) col; // x-coordinate (horizontal)
        int y = (int) row; // y-coordinate (vertical)
        return new int[]{x, y};
    }

    /**
     * Creates a directory if it doesn't exist.
     * @param path The path of the directory to create.
     */
    public static void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
            // System.out.println(String.format("Directory created: %s", path)); // Uncomment for verbose output
        } catch (IOException e) {
            System.err.println(String.format("Error creating directory %s: %s", path, e.getMessage()));
        }
    }

    /**
     * Deletes a directory and its contents recursively.
     * @param path The path of the directory to delete.
     */
    public static void deleteDirectory(Path path) {
        if (Files.exists(path)) {
            try {
                // Walk the file tree in reverse order to delete children before parents
                Files.walk(path)
                     .sorted(Comparator.reverseOrder())
                     .map(Path::toFile)
                     .forEach(File::delete);
                System.out.println(String.format("Deleted directory: %s", path));
            } catch (IOException e) {
                System.err.println(String.format("Error deleting directory %s: %s", path, e.getMessage()));
            }
        }
    }

    /**
     * Deletes a file if it exists.
     * @param path The path of the file to delete.
     */
    public static void deleteFile(Path path) {
        if (Files.exists(path)) {
            try {
                Files.delete(path);
                System.out.println(String.format("Deleted file: %s", path));
            } catch (IOException e) {
                System.err.println(String.format("Error deleting file %s: %s", path, e.getMessage()));
            }
        }
    }

    // Optional: A simple method to display a BufferedImage for testing
    // Uncomment the swing imports at the top if you wish to use this.
    /*
    public static void displayImage(BufferedImage image, String title) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(new JLabel(new ImageIcon(image)));
        frame.pack();
        frame.setLocationRelativeTo(null); // Center on screen
        frame.setVisible(true);
    }
    */
}