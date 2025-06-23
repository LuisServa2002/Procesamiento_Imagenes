package com.mycompany.procesamientoimagenespc04;

// Esta clase será serializada/deserializada por Jackson para JSON
public class FrameMetadata {
    public long id;
    public int x; // Coordenada X (desplazamiento horizontal) de la esquina superior izquierda del frame
    public int y; // Coordenada Y (desplazamiento vertical) de la esquina superior izquierda del frame
    public int width; // Ancho del frame (n_SUB)
    public int height; // Altura del frame (m_SUB)
    public String mainImagePath; // Ruta al archivo de la imagen principal

    // Constructor por defecto para Jackson (requerido para la deserialización)
    public FrameMetadata() {}

    // Constructor parametrizado para facilitar la creación de objetos
    public FrameMetadata(long id, int x, int y, int width, int height, String mainImagePath) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.mainImagePath = mainImagePath;
    }

    // Getters y Setters (Jackson a menudo puede funcionar sin ellos si los campos son públicos,
    // pero es una buena práctica incluirlos para la encapsulación y las convenciones de JavaBeans)
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public String getMainImagePath() { return mainImagePath; }
    public void setMainImagePath(String mainImagePath) { this.mainImagePath = mainImagePath; }
}
