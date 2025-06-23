package com.mycompany.procesamientoimagenespc04;

// This class will be serialized/deserialized by Jackson for JSON
public class FrameMetadata {
    public long id;
    public int x; // X-coordinate (horizontal offset) of the top-left corner of the frame
    public int y; // Y-coordinate (vertical offset) of the top-left corner of the frame
    public int width; // Width of the frame (n_SUB)
    public int height; // Height of the frame (m_SUB)
    public String mainImagePath; // Path to the main image file

    // Default constructor for Jackson (required for deserialization)
    public FrameMetadata() {}

    // Parameterized constructor for easy object creation
    public FrameMetadata(long id, int x, int y, int width, int height, String mainImagePath) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.mainImagePath = mainImagePath;
    }

    // Getters and Setters (Jackson can often work without them if fields are public,
    // but it's good practice to include them for encapsulation and JavaBeans conventions)
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