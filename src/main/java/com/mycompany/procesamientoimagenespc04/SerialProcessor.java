package com.mycompany.procesamientoimagenespc04;

import com.fasterxml.jackson.databind.ObjectMapper; // Para serialización/deserialización JSON.

import javax.imageio.ImageIO; // Para lectura y escritura de imágenes.
import java.awt.image.BufferedImage; // Representa una imagen en memoria.
import java.io.File; // Para operaciones con archivos.
import java.io.FileOutputStream; // Para escribir datos en un archivo.
import java.io.IOException; // Para manejar excepciones de entrada/salida.
import java.nio.file.Files; // Para operaciones con archivos y directorios.
import java.nio.file.Path; // Para representar rutas de archivos.
import java.nio.file.Paths; // Para obtener objetos Path.
import java.util.ArrayList; // Para listas dinámicas.
import java.util.List; // Interfaz para colecciones de elementos.
import java.util.zip.ZipEntry; // Para la compresión ZIP.
import java.util.zip.ZipOutputStream; // Para la compresión ZIP.

public class SerialProcessor {

    protected final String mainImagePath; // Ruta de la imagen principal.
    protected final int M_MAIN; // Altura de la imagen principal (definida por configuración).
    protected final int N_MAIN; // Ancho de la imagen principal (definida por configuración).
    protected final int m_SUB;  // Altura de la sub-imagen (frame).
    protected final int n_SUB;  // Ancho de la sub-imagen (frame).
    protected final String physicalFramesDir; // Directorio para los frames físicos generados.
    protected final String virtualMetadataFile; // Archivo para los metadatos de los frames virtuales.
    protected final long totalFrames; // Número total de frames posibles.
    protected final int[] mainImageDims; // {ancho, alto} de la imagen principal, leídos del archivo.

    // Constructor que inicializa las propiedades del procesador serial.
    public SerialProcessor(String mainImagePath, int M_MAIN, int N_MAIN, int m_SUB, int n_SUB,
                           String physicalFramesDir, String virtualMetadataFile) {
        this.mainImagePath = mainImagePath;
        this.M_MAIN = M_MAIN;
        this.N_MAIN = N_MAIN;
        this.m_SUB = m_SUB;
        this.n_SUB = n_SUB;
        this.physicalFramesDir = physicalFramesDir;
        this.virtualMetadataFile = virtualMetadataFile;
        // Calcula el número total de frames basándose en las dimensiones proporcionadas.
        this.totalFrames = ImageUtils.calculateTotalFrames(M_MAIN, N_MAIN, m_SUB, n_SUB);
        // Intenta obtener las dimensiones reales del archivo, que podrían sobrescribir las proporcionadas si hay una discrepancia.
        this.mainImageDims = getMainImageDimsFromFile();
    }

    /**
     * Lee las dimensiones reales del archivo de la imagen principal.
     * @return Un array {ancho, alto} o null si el archivo no puede ser leído.
     */
    protected int[] getMainImageDimsFromFile() {
        try {
            File file = new File(mainImagePath);
            if (!file.exists()) {
                System.err.println(String.format("Error: Archivo de imagen principal no encontrado en %s.", mainImagePath));
                return null;
            }
            BufferedImage mainImg = ImageIO.read(file);
            if (mainImg != null) {
                return new int[]{mainImg.getWidth(), mainImg.getHeight()};
            }
        } catch (IOException e) {
            System.err.println(String.format("Error al leer las dimensiones de la imagen principal desde %s: %s", mainImagePath, e.getMessage()));
        }
        return null;
    }

    /**
     * Devuelve las dimensiones reales de la imagen principal tal como se leyeron del archivo.
     * @return Un array {ancho, alto} o null.
     */
    public int[] getMainImageDims() {
        return mainImageDims;
    }

    // --- Caso 1: Generación Serial de Frames Físicos ---
    public void generatePhysicalFramesSerial() {
        long startTime = System.currentTimeMillis(); // Marca de tiempo de inicio.
        System.out.println(String.format("\n--- Serial: Generando %d frames físicos ---", totalFrames));
        ImageUtils.createDirectory(Paths.get(physicalFramesDir)); // Asegura que el directorio de salida exista.

        try {
            // Carga la imagen principal una sola vez para el procesamiento serial.
            BufferedImage mainImg = ImageIO.read(new File(mainImagePath));
            if (mainImg == null) {
                System.err.println(String.format("Error: La imagen principal '%s' no pudo ser cargada. No se pueden generar frames.", mainImagePath));
                return;
            }

            for (long i = 0; i < totalFrames; i++) {
                // Obtiene las coordenadas de inicio del frame.
                int[] coords = ImageUtils.getFrameCoordinates(i, M_MAIN, N_MAIN, m_SUB, n_SUB);
                int x_start = coords[0]; // Inicio horizontal.
                int y_start = coords[1]; // Inicio vertical.

                // getSubimage usa (x, y, ancho, alto).
                // Nota: n_SUB es el ancho, m_SUB es la altura.
                BufferedImage croppedImg = mainImg.getSubimage(x_start, y_start, n_SUB, m_SUB);
                String framePath = String.format("%s/frame_%05d.png", physicalFramesDir, i); // Ej: frame_00000.png
                ImageIO.write(croppedImg, "png", new File(framePath)); // Escribe el frame recortado.

                if ((i + 1) % 10000 == 0) { // Imprime el progreso cada 10,000 frames.
                    System.out.println(String.format("Generados %d/%d frames...", i + 1, totalFrames));
                }
            }
        } catch (IOException e) {
            System.err.println(String.format("Error durante la generación serial de frames: %s", e.getMessage()));
            e.printStackTrace(); // Imprime la traza de la pila para depuración.
            return;
        } catch (java.awt.image.RasterFormatException e) {
            System.err.println(String.format("Error al recortar la imagen durante la generación serial de frames (región inválida): %s. Asegúrate de que las dimensiones de los sub-frames (%dx%d) sean válidas para la imagen principal.", e.getMessage(), n_SUB, m_SUB));
            e.printStackTrace();
            return;
        }

        long endTime = System.currentTimeMillis(); // Marca de tiempo de finalización.
        System.out.println(String.format("Generación serial de frames completada en %.2f segundos.", (endTime - startTime) / 1000.0));
        compressPhysicalFrames(); // Realiza la compactación (compresión).
    }

    /**
     * Comprime el directorio de frames físicos en un único archivo ZIP.
     */
    protected void compressPhysicalFrames() {
        String compressedArchivePath = MainImageProcessor.COMPRESSED_ARCHIVE_PATH; // Obtiene la ruta de la configuración principal.
        System.out.println(String.format("Comprimiendo el directorio de frames físicos '%s' a '%s'...", physicalFramesDir, compressedArchivePath));
        long startTime = System.currentTimeMillis();

        try (FileOutputStream fos = new FileOutputStream(compressedArchivePath);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {

            File sourceDir = new File(physicalFramesDir);
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                System.err.println(String.format("Directorio de origen '%s' no encontrado para la compresión.", physicalFramesDir));
                return;
            }

            // Itera sobre todos los archivos en el directorio de origen.
            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) { // Solo procesa archivos, no subdirectorios.
                        zipOut.putNextEntry(new ZipEntry(file.getName())); // Crea una nueva entrada en el zip.
                        Files.copy(file.toPath(), zipOut); // Copia el contenido del archivo al flujo zip.
                        zipOut.closeEntry(); // Cierra la entrada actual.
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(String.format("Error durante la compresión: %s", e.getMessage()));
            e.printStackTrace();
            return;
        }
        long endTime = System.currentTimeMillis(); // Marca de tiempo de finalización.
        System.out.println(String.format("Compresión completada en %.2f segundos.", (endTime - startTime) / 1000.0));
    }

    // --- Caso 2: Indexación y Reproducción Serial de Frames Virtuales ---
    public void generateVirtualFrameMetadataSerial() {
        long startTime = System.currentTimeMillis(); // Marca de tiempo de inicio.
        System.out.println("\n--- Serial: Generando metadatos de frames virtuales ---");
        List<FrameMetadata> metadata = new ArrayList<>(); // Lista para almacenar los metadatos de los frames.

        for (long i = 0; i < totalFrames; i++) {
            // Obtiene las coordenadas del frame.
            int[] coords = ImageUtils.getFrameCoordinates(i, M_MAIN, N_MAIN, m_SUB, n_SUB);
            // Almacena id, x, y, ancho, alto (n_SUB es ancho, m_SUB es altura) y la ruta de la imagen principal.
            metadata.add(new FrameMetadata(i, coords[0], coords[1], n_SUB, m_SUB, mainImagePath));
        }

        ObjectMapper mapper = new ObjectMapper(); // Objeto Jackson para operaciones JSON.
        try {
            // Escribe la lista de objetos de metadatos en un archivo JSON, con formato legible.
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(virtualMetadataFile), metadata);
        } catch (IOException e) {
            System.err.println(String.format("Error al escribir los metadatos del frame virtual: %s", e.getMessage()));
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis(); // Marca de tiempo de finalización.
        System.out.println(String.format("Generación de metadatos de frames virtuales completada en %.2f segundos.", (endTime - startTime) / 1000.0));
    }

    /**
     * Reproduce un frame virtual cargando sus metadatos y recortando la imagen principal.
     * @param frameId El ID del frame a reproducir.
     * @return El BufferedImage recortado que representa el frame, o null si ocurre un error.
     * @throws IOException Si hay un error al leer archivos (metadatos o imagen principal).
     */
    public BufferedImage reproduceVirtualFrameSerial(long frameId) throws IOException {
        ObjectMapper mapper = new ObjectMapper(); // Objeto Jackson para operaciones JSON.
        List<FrameMetadata> metadata;
        try {
            // Lee todos los metadatos del archivo JSON.
            metadata = mapper.readValue(new File(virtualMetadataFile),
                    mapper.getTypeFactory().constructCollectionType(List.class, FrameMetadata.class));
        } catch (IOException e) {
            System.err.println(String.format("Error: Archivo de metadatos no encontrado en %s o no pudo ser leído: %s", virtualMetadataFile, e.getMessage()));
            throw e; // Relanza para indicar un error crítico.
        }

        if (!(frameId >= 0 && frameId < metadata.size())) {
            System.err.println(String.format("Error: ID de frame %d fuera de rango (0 a %d).", frameId, metadata.size() - 1));
            return null;
        }

        // Obtiene la información del frame basándose en el ID. La conversión a int es segura aquí.
        FrameMetadata frameInfo = metadata.get((int) frameId);

        BufferedImage mainImg = null;
        try {
            // Carga la imagen principal (esto se hará para cada reproducción en modo serial).
            mainImg = ImageIO.read(new File(frameInfo.getMainImagePath()));
            if (mainImg == null) {
                System.err.println(String.format("Error: Imagen principal '%s' no encontrada o no pudo ser leída para el frame %d.", frameInfo.getMainImagePath(), frameId));
                return null;
            }
            // Recorta la sub-imagen basándose en los metadatos.
            return mainImg.getSubimage(frameInfo.getX(), frameInfo.getY(), frameInfo.getWidth(), frameInfo.getHeight());
        } catch (IOException e) {
            System.err.println(String.format("Error al reproducir el frame %d: %s", frameId, e.getMessage()));
            throw e; // Relanza para indicar un error crítico.
        } catch (java.awt.image.RasterFormatException e) {
            // Captura errores de formato de raster (por ejemplo, coordenadas de recorte fuera de límites).
            System.err.println(String.format("Error al recortar la imagen para el frame %d (región inválida): %s. Coordenadas: x=%d, y=%d, ancho=%d, alto=%d. Dimensiones de la imagen principal: %dx%d",
                frameId, e.getMessage(), frameInfo.getX(), frameInfo.getY(), frameInfo.getWidth(), frameInfo.getHeight(), mainImg.getWidth(), mainImg.getHeight()));
            return null;
        }
    }
}
