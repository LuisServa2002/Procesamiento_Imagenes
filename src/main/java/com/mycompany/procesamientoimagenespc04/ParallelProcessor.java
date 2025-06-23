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

public class ParallelProcessor extends SerialProcessor { // Hereda propiedades comunes y métodos de ayuda

    private final int numProcesses; // Número de procesos/hilos paralelos a usar

    public ParallelProcessor(String mainImagePath, int M_MAIN, int N_MAIN, int m_SUB, int n_SUB,
                             String physicalFramesDir, String virtualMetadataFile, int numProcesses) {
        super(mainImagePath, M_MAIN, N_MAIN, m_SUB, n_SUB, physicalFramesDir, virtualMetadataFile);
        this.numProcesses = numProcesses;
        System.out.println(String.format("Usando %d procesos/hilos para operaciones paralelas.", this.numProcesses));
    }

    // Callable para la generación de frames paralelos (Caso 1)
    // Cada instancia de esta tarea generará un solo frame.
    private static class GenerateFrameTask implements Callable<Boolean> {
        private final long frameIdx;
        private final String mainImagePath;
        private final int M_MAIN; // Altura de la imagen principal
        private final int N_MAIN; // Ancho de la imagen principal
        private final int m_SUB;  // Altura de la sub-imagen
        private final int n_SUB;  // Ancho de la sub-imagen
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
            // Declara coords fuera del bloque try para que sea accesible en los bloques catch
            int[] coords = null;
            try {
                // Cada hilo/proceso lee la imagen principal desde el disco
                BufferedImage mainImg = ImageIO.read(new File(mainImagePath));
                if (mainImg == null) {
                    System.err.println(String.format("Error de Tarea (Hilo %d): No se pudo cargar la imagen principal '%s' para el frame %d.", Thread.currentThread().getId(), mainImagePath, frameIdx));
                    return false;
                }

                coords = ImageUtils.getFrameCoordinates(frameIdx, M_MAIN, N_MAIN, m_SUB, n_SUB); // Asigna valor aquí
                int x_start = coords[0];
                int y_start = coords[1];

                BufferedImage croppedImg = mainImg.getSubimage(x_start, y_start, n_SUB, m_SUB);
                String framePath = String.format("%s/frame_%05d.png", physicalFramesDir, frameIdx);
                ImageIO.write(croppedImg, "png", new File(framePath));
                return true; // Indica éxito
            } catch (IOException e) {
                System.err.println(String.format("Error generando frame %d (Hilo %d): %s", frameIdx, Thread.currentThread().getId(), e.getMessage()));
                return false; // Indica fallo
            } catch (java.awt.image.RasterFormatException e) {
                // Mensaje de error mejorado para manejar coords nulas de forma elegante
                String coordStr = (coords != null && coords.length == 2) ? String.format("x=%d, y=%d", coords[0], coords[1]) : "desconocido";
                System.err.println(String.format("Error recortando imagen para el frame %d (Hilo %d - región inválida): %s. Coordenadas: %s, ancho=%d, alto=%d",
                    frameIdx, Thread.currentThread().getId(), e.getMessage(), coordStr, n_SUB, m_SUB));
                return false;
            }
        }
    }

    // --- Caso 1: Generación de Frames Físicos Paralela ---
    public void generatePhysicalFramesParallel() {
        long startTime = System.currentTimeMillis();
        System.out.println(String.format("\n--- Paralelo: Generando %d frames físicos usando %d procesos/hilos ---", totalFrames, numProcesses));
        ImageUtils.createDirectory(Paths.get(physicalFramesDir));

        // Crea un pool de hilos de tamaño fijo
        ExecutorService executor = Executors.newFixedThreadPool(numProcesses);
        List<Future<Boolean>> futures = new ArrayList<>();
        AtomicLong generatedCount = new AtomicLong(0); // Contador seguro para hilos para actualizaciones de progreso

        // Envía tareas para todos los frames
        for (long i = 0; i < totalFrames; i++) {
            futures.add(executor.submit(new GenerateFrameTask(i, mainImagePath, M_MAIN, N_MAIN, m_SUB, n_SUB, physicalFramesDir)));
        }

        // Recopila resultados y actualiza el progreso
        for (int i = 0; i < futures.size(); i++) {
            try {
                if (futures.get(i).get()) { // .get() bloquea hasta que la tarea correspondiente a este Future se completa
                    long count = generatedCount.incrementAndGet();
                    if (count % 10000 == 0) {
                        System.out.println(String.format("Generados %d/%d frames...", count, totalFrames));
                    }
                } else {
                    System.err.println(String.format("Fallo al generar el frame %d (índice de tarea %d).", i, i));
                }
            } catch (InterruptedException | ExecutionException e) {
                System.err.println(String.format("Error al obtener el resultado para la tarea del frame %d: %s", i, e.getMessage()));
                Thread.currentThread().interrupt(); // Restaura el estado de interrupción
            }
        }

        executor.shutdown(); // Inicia el apagado ordenado del ejecutor
        try {
            // Espera a que todas las tareas se completen o que se agote el tiempo de espera (60 minutos)
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                System.err.println("El ejecutor no terminó en el tiempo especificado. Forzando el apagado.");
                executor.shutdownNow(); // Fuerza el apagado si no termina limpiamente
            }
        } catch (InterruptedException e) {
            System.err.println("La terminación del ejecutor fue interrumpida: " + e.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt(); // Restaura el estado de interrupción
        }

        long endTime = System.currentTimeMillis();
        System.out.println(String.format("Generación de frames paralela completada en %.2f segundos.", (endTime - startTime) / 1000.0));
        compressPhysicalFrames(); // La compresión sigue siendo un proceso serial aquí
    }


    // Runnable para la reproducción concurrente de frames virtuales (Caso 2)
    // Cada instancia de esta tarea se ejecuta en un hilo y procesa frames de una cola compartida.
    private static class ReproduceFrameTask implements Runnable {
        private final BlockingQueue<Long> frameQueue;
        private final BlockingQueue<Boolean> resultsQueue;
        private final double reproductionDelaySeconds;
        private final List<FrameMetadata> metadata;

        // El constructor NO lanza IOException directamente, pero la captura y relanza como RuntimeException
        public ReproduceFrameTask(BlockingQueue<Long> frameQueue, BlockingQueue<Boolean> resultsQueue,
                                  String virtualMetadataFile, double reproductionDelaySeconds) {
            this.frameQueue = frameQueue;
            this.resultsQueue = resultsQueue;
            this.reproductionDelaySeconds = reproductionDelaySeconds;

            ObjectMapper mapper = new ObjectMapper();
            try {
                // Maneja IOException durante la carga de metadatos directamente en el constructor
                this.metadata = mapper.readValue(new File(virtualMetadataFile),
                        mapper.getTypeFactory().constructCollectionType(List.class, FrameMetadata.class));
            } catch (IOException e) {
                System.err.println(String.format("Error cargando metadatos para ReproduceFrameTask: %s", e.getMessage()));
                // Relanza como RuntimeException porque la tarea no puede inicializarse sin metadatos
                throw new RuntimeException("Fallo al inicializar ReproduceFrameTask debido a un error de carga de metadatos.", e);
            }
        }

        @Override
        public void run() {
            BufferedImage mainImg = null; // Declarado aquí para acceso en bloques catch de RasterFormatException
            try {
                while (true) {
                    // Intenta obtener un frame de la cola con un tiempo de espera.
                    // Esto permite que el hilo salga si la cola está vacía y el ejecutor se está apagando.
                    Long frameId = frameQueue.poll(500, TimeUnit.MILLISECONDS); // Espera 500 ms

                    if (frameId == null) {
                        // Si no hay más frames disponibles después del tiempo de espera,
                        // y el hilo actual ha sido interrumpido, significa que debemos terminar.
                        // Cuando el ExecutorService se apaga, no se enviarán más tareas,
                        // y los hilos en poll() se despertarán y eventualmente se interrumpirán
                        // o verán la cola vacía.
                        if (Thread.currentThread().isInterrupted()) {
                            break; // Salir del bucle si el hilo ha sido interrumpido
                        }
                        // Si la cola está vacía y no hay interrupción, podemos intentar
                        // otra vez (continue) o salir si hay una señal más explícita
                        // de que el trabajo ha terminado (que será el shutdown del Executor).
                        // Por ahora, solo confiar en la interrupción es suficiente con el patrón ExecutorService.
                        continue;
                    }

                    // La conversión frameId.intValue() es correcta.
                    // La comprobación de rango asegura que frameId esté dentro de los límites válidos.
                    if (!(frameId >= 0 && frameId < metadata.size())) {
                        System.err.println(String.format("Error de Trabajador (Hilo %d): ID de Frame %d fuera de rango. Tamaño de metadatos: %d", Thread.currentThread().getId(), frameId, metadata.size()));
                        resultsQueue.put(false);
                        continue;
                    }

                    FrameMetadata frameInfo = metadata.get(frameId.intValue()); // Usa intValue() para mayor claridad

                    try {
                        // Cada hilo lee la imagen principal (o una porción) desde el disco
                        mainImg = ImageIO.read(new File(frameInfo.getMainImagePath()));
                        if (mainImg == null) {
                            System.err.println(String.format("Error de Trabajador (Hilo %d): Imagen principal '%s' no encontrada o no pudo ser leída para el frame %d.", Thread.currentThread().getId(), frameInfo.getMainImagePath(), frameId));
                            resultsQueue.put(false);
                            continue;
                        }
                        // Recorta la sub-imagen
                        mainImg.getSubimage(frameInfo.getX(), frameInfo.getY(), frameInfo.getWidth(), frameInfo.getHeight());
                        // Simula el procesamiento/visualización del frame pausando el hilo
                        Thread.sleep((long) (reproductionDelaySeconds * 1000));
                        // System.out.println(String.format("Proceso %d reprodujo el frame %d", Thread.currentThread().getId(), frameId)); // Descomentar para salida detallada por frame
                        resultsQueue.put(true); // Indica reproducción exitosa
                    } catch (IOException e) {
                        System.err.println(String.format("Error de Trabajador (Hilo %d) reproduciendo el frame %d: %s", Thread.currentThread().getId(), frameId, e.getMessage()));
                        resultsQueue.put(false);
                    } catch (InterruptedException e) {
                        System.err.println(String.format("Hilo trabajador %d interrumpido mientras reproducía el frame %d: %s", Thread.currentThread().getId(), frameId, e.getMessage()));
                        Thread.currentThread().interrupt(); // Restaura el estado de interrupción
                        resultsQueue.put(false);
                        break; // Sale del bucle si es interrumpido
                    } catch (java.awt.image.RasterFormatException e) {
                        System.err.println(String.format("Error de Trabajador al recortar la imagen para el frame %d (Hilo %d - región inválida): %s. Coordenadas: x=%d, y=%d, ancho=%d, alto=%d. Dimensiones de la imagen principal: %dx%d",
                            frameId, Thread.currentThread().getId(), e.getMessage(), frameInfo.getX(), frameInfo.getY(), frameInfo.getWidth(), frameInfo.getHeight(),
                            (mainImg != null ? mainImg.getWidth() : 0), (mainImg != null ? mainImg.getHeight() : 0))); // Agrega dimensiones de la imagen principal para contexto
                        resultsQueue.put(false);
                    }
                }
            } catch (InterruptedException e) {
                System.err.println(String.format("Hilo trabajador %d interrumpido mientras esperaba frames: %s", Thread.currentThread().getId(), e.getMessage()));
                Thread.currentThread().interrupt(); // Restaura el estado de interrupción
            }
            // Nota: La captura de IOException en el método run() para la carga de metadatos se elimina
            // porque esa excepción específica ahora se maneja en el constructor.
            // Otras IOExceptions (como ImageIO.read) todavía se manejan dentro del try-catch interno.
            // Si la RuntimeException del constructor ocurrió, este método run() ni siquiera comenzaría.
        }
    }

    /**
     * Reproduce un conjunto de K frames concurrentemente usando múltiples hilos.
     * @param K_frames_to_reproduce El número de frames a reproducir.
     * @param reproductionDelay Un retardo simulado para cada reproducción de frame en segundos.
     */
    public void reproduceVirtualFrameConcurrently(int K_frames_to_reproduce, double reproductionDelay) {
        if (!Paths.get(virtualMetadataFile).toFile().exists()) {
            System.err.println(String.format("El archivo de metadatos '%s' no se encontró. Por favor, genéralo primero.", virtualMetadataFile));
            return;
        }

        System.out.println(String.format("\n--- Concurrencia: Reproduciendo %d frames virtuales concurrentemente usando %d hilos ---", K_frames_to_reproduce, numProcesses));
        long startTime = System.currentTimeMillis();

        // Colas para la comunicación entre el hilo principal y los hilos trabajadores
        BlockingQueue<Long> frameQueue = new LinkedBlockingQueue<>(); // Tareas a procesar
        BlockingQueue<Boolean> resultsQueue = new LinkedBlockingQueue<>(); // Resultados de las tareas procesadas

        // Determina el número real de frames a procesar (mínimo de K solicitado y frames totales disponibles)
        List<Long> framesToProcess = new ArrayList<>();
        long actualK = Math.min(K_frames_to_reproduce, totalFrames);
        for (long i = 0; i < actualK; i++) {
            framesToProcess.add(i); // Para simplificar, toma los primeros K frames
        }
        if (K_frames_to_reproduce > totalFrames) {
            System.out.println(String.format("Advertencia: Se solicitaron %d frames, pero solo hay %d disponibles. Procesando %d frames.", K_frames_to_reproduce, totalFrames, actualK));
        }

        // Crea e inicia los hilos trabajadores
        ExecutorService executor = Executors.newFixedThreadPool(numProcesses);
        // No necesitamos guardar los Futures de los trabajadores si no vamos a cancelarlos o esperar resultados individuales aquí.

        for (int i = 0; i < numProcesses; i++) {
            try {
                // Envía una ReproduceFrameTask al ejecutor
                // Nota: El constructor lanzará RuntimeException si los metadatos no pueden cargarse.
                executor.submit(new ReproduceFrameTask(frameQueue, resultsQueue, virtualMetadataFile, reproductionDelay));
            } catch (RuntimeException e) { // Captura RuntimeException del constructor
                System.err.println("Fallo al crear ReproduceFrameTask debido a un error de carga de metadatos: " + e.getMessage());
                executor.shutdownNow(); // Detiene el ejecutor si los trabajadores no pueden inicializarse
                return; // Sale del método
            }
        }

        // Añade IDs de frames a la cola de tareas
        for (Long frameId : framesToProcess) {
            try {
                frameQueue.put(frameId); // Añade el ID del frame a la cola para que los trabajadores lo recojan
            } catch (InterruptedException e) {
                System.err.println("Hilo principal interrumpido mientras añadía frames a la cola: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Espera a que todos los frames sean procesados monitoreando la cola de resultados
        long processedCount = 0;
        while (processedCount < framesToProcess.size()) {
            try {
                resultsQueue.take(); // Bloquea hasta que un resultado esté disponible
                processedCount++;
                if (processedCount % 100 == 0) {
                    System.out.println(String.format("Reproducidos %d/%d frames...", processedCount, framesToProcess.size()));
                }
            } catch (InterruptedException e) {
                System.err.println("Hilo principal interrumpido mientras esperaba resultados: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }

        // IMPORTANTE: Señaliza a los trabajadores para que salgan APAGANDO EL EJECUTOR.
        // Los trabajadores, al ver que no hay más tareas en la cola y que el ejecutor
        // está apagándose, terminarán sus bucles 'while(true)' de forma natural.
        // Eliminamos el envío de 'null' porque LinkedBlockingQueue no lo soporta.
        executor.shutdown(); // Inicia el apagado elegante del ejecutor
        try {
            // Espera a que todos los hilos trabajadores terminen sus tareas y finalicen
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) { // Tiempo máximo de espera: 5 minutos
                System.err.println("El ejecutor de reproducción concurrente no terminó a tiempo. Forzando el apagado.");
                executor.shutdownNow(); // Fuerza el apagado si los trabajadores no responden
            }
        } catch (InterruptedException e) {
            System.err.println("La terminación del ejecutor de reproducción concurrente fue interrumpida: " + e.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt(); // Restaura el estado de interrupción
        }

        long endTime = System.currentTimeMillis();
        System.out.println(String.format("Reproducción concurrente completada en %.2f segundos.", (endTime - startTime) / 1000.0));
    }
}
