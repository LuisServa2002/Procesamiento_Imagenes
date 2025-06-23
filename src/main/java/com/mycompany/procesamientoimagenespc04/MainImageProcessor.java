package com.mycompany.procesamientoimagenespc04;

// Importa las clases necesarias para manejar excepciones de E/S y rutas de archivos.
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.image.BufferedImage; // Necesario para el test de reproducción visual de frames.

public class MainImageProcessor {

    // --- Configuración Global de Rutas y Dimensiones ---
    // Ruta del archivo de la imagen principal a ser procesada.
    public static final String MAIN_IMAGE_PATH = "main_image.jpg"; // ¡IMPORTANTE: CAMBIA ESTO a la ruta real de tu imagen!
    // Directorio donde se guardarán los frames físicos generados.
    public static final String PHYSICAL_FRAMES_DIR = "physical_frames"; // Directorio de salida para frames.
    // Ruta del archivo ZIP donde se comprimirán los frames físicos.
    public static final String COMPRESSED_ARCHIVE_PATH = "physical_frames.zip"; // Archivo comprimido.
    // Ruta del archivo JSON que almacenará los metadatos de los frames virtuales.
    public static final String VIRTUAL_METADATA_FILE = "virtual_frames_metadata.json"; // Archivo de metadatos.

    // Dimensiones de la imagen principal (valores de ejemplo, reemplaza con el tamaño real de tu imagen).
    // M_MAIN (Altura) y N_MAIN (Ancho) definen las dimensiones de la *imagen principal*.
    // m_SUB (altura de sub-imagen) y n_SUB (ancho de sub-imagen) definen el tamaño de cada *frame*.
    public static final int M_MAIN = 64; // Altura de la imagen principal en píxeles.
    public static final int N_MAIN = 64; // Ancho de la imagen principal en píxeles.
    public static final int m_SUB = 32;  // Altura de cada sub-imagen (frame) en píxeles.
    public static final int n_SUB = 32;  // Ancho de cada sub-imagen (frame) en píxeles.

    public static void main(String[] args) {
        System.out.println("Iniciando la Aplicación de Procesamiento de Imágenes...");

        // Asegura que la imagen principal exista para propósitos de prueba.
        // Nota: ImageUtils.createDummyMainImage espera el ancho y luego la altura para BufferedImage.
        ImageUtils.createDummyMainImage(MAIN_IMAGE_PATH, N_MAIN, M_MAIN); // N_MAIN (ancho), M_MAIN (altura)

        // Calcula el número total de frames que teóricamente se pueden extraer de la imagen principal.
        long totalFramesPossible = ImageUtils.calculateTotalFrames(M_MAIN, N_MAIN, m_SUB, n_SUB);
        System.out.println(String.format("\nNúmero total de frames posibles: %d", totalFramesPossible));

        // --- Limpieza de Ejecuciones Anteriores ---
        // Elimina el directorio de frames físicos, el archivo ZIP comprimido y el archivo de metadatos JSON
        // de ejecuciones previas para asegurar un inicio limpio en cada ejecución.
        ImageUtils.deleteDirectory(Paths.get(PHYSICAL_FRAMES_DIR));
        ImageUtils.deleteFile(Paths.get(COMPRESSED_ARCHIVE_PATH));
        ImageUtils.deleteFile(Paths.get(VIRTUAL_METADATA_FILE));

        // --- Ejecución Secuencial ---
        System.out.println("\n===== EJECUCIÓN SECUENCIAL =====");
        // Crea una instancia del procesador serial. Este manejará las operaciones una a una.
        SerialProcessor serialProcessor = new SerialProcessor(MAIN_IMAGE_PATH, M_MAIN, N_MAIN, m_SUB, n_SUB,
                                                              PHYSICAL_FRAMES_DIR, VIRTUAL_METADATA_FILE);

        // Verifica si las dimensiones de la imagen principal se cargaron correctamente.
        // Si no se pueden determinar, se omite la ejecución serial.
        if (serialProcessor.getMainImageDims() != null) {
            // Genera frames físicos de forma secuencial. Estos se guardan en disco.
            serialProcessor.generatePhysicalFramesSerial();
            // Genera los metadatos de los frames virtuales de forma secuencial.
            // Esto crea un archivo JSON que describe los frames sin guardarlos como imágenes.
            serialProcessor.generateVirtualFrameMetadataSerial();

            // --- Prueba de Reproducción Serial ---
            System.out.println("\n--- Probando la Reproducción Serial (Frames 10 y 100) ---");
            try {
                long reproductionStartTime = System.currentTimeMillis();
                // Reproduce un frame virtual específico (frame con ID 10).
                BufferedImage reproducedFrame10 = serialProcessor.reproduceVirtualFrameSerial(10);
                if (reproducedFrame10 != null) {
                    System.out.println("Frame virtual 10 reproducido exitosamente.");
                    // En una aplicación con interfaz gráfica, aquí se mostraría la imagen.
                    // ImageUtils.displayImage(reproducedFrame10, "Frame 10"); // Descomenta para mostrar visualmente
                }
                // Reproduce otro frame virtual específico (frame con ID 100).
                BufferedImage reproducedFrame100 = serialProcessor.reproduceVirtualFrameSerial(100);
                if (reproducedFrame100 != null) {
                    System.out.println("Frame virtual 100 reproducido exitosamente.");
                    // ImageUtils.displayImage(reproducedFrame100, "Frame 100"); // Descomenta para mostrar visualmente
                }
                long reproductionEndTime = System.currentTimeMillis();
                System.out.println(String.format("Prueba de reproducción serial completada en %.2f segundos.", (reproductionEndTime - reproductionStartTime) / 1000.0));

            } catch (IOException e) {
                System.err.println("Error durante la prueba de reproducción serial: " + e.getMessage());
                e.printStackTrace(); // Imprime la traza de la pila para depuración.
            }
        } else {
            System.err.println("Saltando la ejecución serial ya que no se pudieron determinar las dimensiones de la imagen principal.");
        }

        // --- Limpieza para la Ejecución Paralela ---
        // Limpia los frames físicos y el archivo ZIP de la ejecución serial
        // para evitar conflictos y asegurar que la prueba paralela inicie limpia.
        ImageUtils.deleteDirectory(Paths.get(PHYSICAL_FRAMES_DIR));
        ImageUtils.deleteFile(Paths.get(COMPRESSED_ARCHIVE_PATH));

        // --- Ejecución Paralela ---
        System.out.println("\n===== EJECUCIÓN PARALELA =====");
        // Obtiene el número de núcleos de CPU disponibles para determinar el tamaño del pool de hilos.
        int numProcesses = Runtime.getRuntime().availableProcessors(); // Ya definida arriba, pero se recalcula para claridad.
        // Crea una instancia del procesador paralelo. Este usará múltiples hilos.
        ParallelProcessor parallelProcessor = new ParallelProcessor(MAIN_IMAGE_PATH, M_MAIN, N_MAIN, m_SUB, n_SUB,
                                                                  PHYSICAL_FRAMES_DIR, VIRTUAL_METADATA_FILE, numProcesses);

        // Verifica si las dimensiones de la imagen principal se cargaron correctamente.
        // Si no se pueden determinar, se omite la ejecución paralela.
        if (parallelProcessor.getMainImageDims() != null) {
            // Genera frames físicos de forma paralela. Debería ser más rápido que la versión serial.
            parallelProcessor.generatePhysicalFramesParallel();

            // Asegura que el archivo de metadatos virtuales exista para la reproducción concurrente.
            // Si por alguna razón no se generó o se borró, el procesador serial lo genera de nuevo.
            if (!Files.exists(Paths.get(VIRTUAL_METADATA_FILE))) {
                 System.out.println(String.format("Archivo de metadatos virtuales '%s' no encontrado. Generándolo para la prueba concurrente.", VIRTUAL_METADATA_FILE));
                 serialProcessor.generateVirtualFrameMetadataSerial(); // Usa el procesador serial para generarlo.
            }

            // --- Prueba de Concurrencia ---
            int K_FRAMES = 10; // Número de frames a reproducir concurrentemente (K < T). Ajusta para pruebas.
            double reproductionDelay = 0.001; // Pequeño retraso en segundos para simular el trabajo por frame.
            // Simula la reproducción de frames virtuales de forma concurrente, usando múltiples hilos.
            parallelProcessor.reproduceVirtualFrameConcurrently(K_FRAMES, reproductionDelay);
        } else {
            System.err.println("Saltando la ejecución paralela ya que no se pudieron determinar las dimensiones de la imagen principal.");
        }

        System.out.println("\n--- Todas las operaciones completadas ---");
    }
}
