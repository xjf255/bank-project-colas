package org.example.shared;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesInfo {
    public Properties getProperties() {
        Properties properties = new Properties();
        try (InputStream input = PropertiesInfo.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new RuntimeException("Cannot find application.properties");
            }
            properties.load(input);
            return properties;
        } catch (IOException e) {
            System.err.println("Error loading properties: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}