/*
 * Developed by Simon King 2010
 */
package com.stronans;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Iterator;
import java.util.Properties;

/**
 * Program Properties class.
 */
public final class ProgramProperties {
    /**
     * Property file name.
     */
    private static final String DEFAULT_PROPERTIES_FILE_NAME = "default.properties";
    // Singleton
    private static ProgramProperties _instance = null;
    /**
     * The <code>Logger</code> to be used.
     */
    private static Logger log = LogManager.getLogger(ProgramProperties.class);
    private final String fileName;
    private Properties properties = null;

    /**
     * Default constructor.
     */
    private ProgramProperties(String fileName) {
        this.fileName = fileName;
        init();
    }

    /**
     * Return instance of singleton.
     *
     * @return ServerProperties instance of {@link ProgramProperties}
     */
    public synchronized static ProgramProperties getInstance(String fileName) {
        if (_instance == null) {
            _instance = new ProgramProperties(fileName);
        }

        return (_instance);
    }

    /**
     * Initialise structures.
     */
    public void init() {
        try {
            log.info("Load properties...");

            loadProperties(fileName);
        } catch (Exception e) {
            log.error("An exception occured trying to create properties instance: " + e.getMessage());
        }
    }

    /**
     * loadProperties
     * <p/>
     * Load localised properties.
     */
    public void loadProperties(String fileName) {
        // ------------------------------------------------------------
        // Load application properties
        // ------------------------------------------------------------
        log.info("Loading program properties");
        try {
            if (fileName == null) {
                fileName = DEFAULT_PROPERTIES_FILE_NAME;
            }

            properties = load(fileName);
        } catch (Exception ie) {
            log.warn("Warning: Could not load properties file " + ie);
        }
    }

    /**
     * loadProperties.
     *
     * @param configFileName {@link String}
     * @return {@link Properties}
     */
    public Properties load(String configFileName) {
        Properties appProperties = new Properties();

        try {
            // **NOTE** Has to be 'this.getClass' or you will get all sorts of problems finding file.
            InputStream inputStream = this.getClass().getResourceAsStream("/" + configFileName);

            if (inputStream == null) {
                File file = new File("./" + configFileName);
                inputStream = new FileInputStream(file);
            }

            appProperties.load(inputStream);

            log.debug("Loaded application properties as follows:\n");
            logProperties(appProperties);

        } catch (IOException ioException) {
            log.error("Error initialising the application properties", ioException);
        }

        return appProperties;
    }

    /**
     * Get the Named property from the properties list.
     *
     * @param propertyName {@link String}
     * @return {@link String}
     */
    public String getString(String propertyName) {
        String result = null;

        try {
            result = properties.getProperty(propertyName);
        } catch (Exception e) {
            log.error("Could not load property, " + propertyName);
        }

        return result;
    }

    /**
     * Get the Named property from the properties list.
     *
     * @param propertyName {@link String{
     * @return {@link int}
     */
    public int getInt(String propertyName, int defaultVal ) {
        int result = defaultVal;
        String temp;

        try {
            temp = properties.getProperty(propertyName);

            result = Integer.parseInt(temp);
        } catch (Exception e) {
            log.error("Could not load property, " + propertyName);
            result = defaultVal;
        }

        return result;
    }

    /**
     * Logs the System properties to the log file.
     *
     * @param properties {@link Properties}
     */
    private void logProperties(Properties properties) {
        // get all the property keys
        Iterator<Object> iter = properties.keySet().iterator();

        // log the property key/value pairs
        while (iter.hasNext()) {
            String key = (String) iter.next();
            log.debug("       >" + key + " = [" + properties.getProperty(key) + "]");
        }
    }

}
