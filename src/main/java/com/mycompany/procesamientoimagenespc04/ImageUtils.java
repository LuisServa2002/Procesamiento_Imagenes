package com.mycompany.procesamientoimagenespc04;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
// import javax.swing.ImageIcon; // Descomenta si quieres usar el método displayImage
// import javax.swing.JFrame;   // Descomenta si quieres usar el método displayImage
// import javax.swing.JLabel;   // Descomenta si quieres usar el método displayImage


public class ImageUtils {

    /**
     * Crea una imagen principal de prueba (dummy) para propósitos de testeo.
     * @param path La ruta donde guardar la imagen.
     * @param width El ancho de la imagen.
     * @param height La altura de la imagen.
     */
    public static void createDummyMainImage(String path, int width, int height) {
        File imgFile = new File(path);
        if (!imgFile.exists()) {
            System.out.println(String.format("Creando una imagen principal de prueba en %s (%dx%d píxeles)...", path, width, height));
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = img.createGraphics();
            g2d.setColor(Color.RED);
            g2d.fillRect(0, 0, width, height);
            g2d.setColor(Color.BLUE);
            // Dibuja algo de texto para que sea distintivo
            g2d.setFont(new Font("Arial", Font.BOLD, 50));
            FontMetrics fm = g2d.getFontMetrics();
            String text = "Imagen de Prueba";
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            g2d.drawString(text, (width - textWidth) / 2, (height / 2) + (textHeight / 2));
            g2d.dispose(); // Libera los recursos gráficos
            try {
                ImageIO.write(img, "png", imgFile);
                System.out.println("Imagen de prueba creada.");
            } catch (IOException e) {
                System.err.println("Error al crear la imagen de prueba: " + e.getMessage());
            }
        } else {
            System.out.println(String.format("La imagen principal de prueba ya existe en %s.", path));
        }
    }

    /**
     * Calcula el número total de posibles sub-imágenes (frames).
     * @param M Altura de la imagen principal.
     * @param N Ancho de la imagen principal.
     * @param m Altura de la sub-imagen.
     * @param n Ancho de la sub-imagen.
     * @return Número total de frames.
     */
    public static long calculateTotalFrames(int M, int N, int m, int n) {
        if (M < m || N < n) {
            return 0; // No se pueden generar frames si la sub-imagen es más grande que la principal
        }
        return (long) (M - m + 1) * (N - n + 1);
    }

    /**
     * Calcula las coordenadas de la esquina superior izquierda (x, y) para un índice de frame dado.
     * Nota: x es la columna (horizontal), y es la fila (vertical).
     * @param frameIdx El índice del frame.
     * @param M Altura de la imagen principal.
     * @param N Ancho de la imagen principal.
     * @param m Altura de la sub-imagen.
     * @param n Ancho de la sub-imagen.
     * @return Un array {x, y} que representa la esquina superior izquierda.
     */
    public static int[] getFrameCoordinates(long frameIdx, int M, int N, int m, int n) {
        // Número de posibles sub-imágenes horizontalmente en la imagen principal
        int framesPerRow = (N - n + 1);
        long row = frameIdx / framesPerRow;
        long col = frameIdx % framesPerRow;
        int x = (int) col; // Coordenada x (horizontal)
        int y = (int) row; // Coordenada y (vertical)
        return new int[]{x, y};
    }

    /**
     * Crea un directorio si no existe.
     * @param path La ruta del directorio a crear.
     */
    public static void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
            // System.out.println(String.format("Directorio creado: %s", path)); // Descomenta para salida detallada
        } catch (IOException e) {
            System.err.println(String.format("Error al crear el directorio %s: %s", path, e.getMessage()));
        }
    }

    /**
     * Elimina un directorio y su contenido recursivamente.
     * @param path La ruta del directorio a eliminar.
     */
    public static void deleteDirectory(Path path) {
        if (Files.exists(path)) {
            try {
                // Recorre el árbol de archivos en orden inverso para eliminar los hijos antes que los padres
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
                System.out.println(String.format("Directorio eliminado: %s", path));
            } catch (IOException e) {
                System.err.println(String.format("Error al eliminar el directorio %s: %s", path, e.getMessage()));
            }
        }
    }

    /**
     * Elimina un archivo si existe.
     * @param path La ruta del archivo a eliminar.
     */
    public static void deleteFile(Path path) {
        if (Files.exists(path)) {
            try {
                Files.delete(path);
                System.out.println(String.format("Archivo eliminado: %s", path));
            } catch (IOException e) {
                System.err.println(String.format("Error al eliminar el archivo %s: %s", path, e.getMessage()));
            }
        }
    }

    // Opcional: Un método simple para mostrar un BufferedImage para pruebas
    // Descomenta los imports de Swing en la parte superior si deseas usarlo.
    /*
    public static void displayImage(BufferedImage image, String title) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(new JLabel(new ImageIcon(image)));
        frame.pack();
        frame.setLocationRelativeTo(null); // Centrar en pantalla
        frame.setVisible(true);
    }
    */
}
