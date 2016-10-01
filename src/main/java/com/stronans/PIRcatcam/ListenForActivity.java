package com.stronans.PIRcatcam;
/*
 * **********************************************************************
 * ORGANIZATION  :  Cathcart Software
 * PROJECT       :  PIR Catcam client
 * FILENAME      :  ListenForActivity.java
 *
 * This file is part of the CatCam project. More information about
 * this project can be found here:  TBC
 * **********************************************************************
 */

import com.google.common.base.Joiner;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.wiringpi.Gpio;
import com.stronans.ProgramProperties;
import com.stronans.camera.Camera;
import com.stronans.camera.CameraProcess;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 *
 */
public class ListenForActivity {
    /**
     * The <code>Logger</code> to be used.
     */
    private static final Logger log = Logger.getLogger(ListenForActivity.class);
    //
    private static final String PROGRAM_ROOT = "com.stronans.catcam.";

    private static final String VIDEO_SETTINGS = PROGRAM_ROOT + "video.settings";
    private static final String NAME_STUB = PROGRAM_ROOT + "filename.stub";
    private static final String STORE_PATH = PROGRAM_ROOT + "store.path";
    private static final String FILE_EXTENSION = PROGRAM_ROOT + "file.extension";
    private static final String TIME_TO_RECORD = PROGRAM_ROOT + "time.record";      // Milliseconds
    private static final String DROP_OFF_RATE = PROGRAM_ROOT + "drop.off";          // Number of seconds before camera switch off

    private static final String TIME_SET = "-t ";

    public static final Pin GPIO_MOVEMENT_PIN = RaspiPin.GPIO_06;

    private static final int DEFAULT_CUT_OFF_SECS = 15;

    private static int cutOffSeconds = DEFAULT_CUT_OFF_SECS;
    private static int cutOffCounter;
    private static final Object syncVar = new Object();

    /**
     * Handles the loading of the log4j configuration. properties file must be
     * on the classpath.
     *
     * @throws RuntimeException
     */
    private static void initLogging() throws RuntimeException {
        try {
            Properties properties = new Properties();
            properties.load(ListenForActivity.class.getClassLoader().getResourceAsStream("log4j.properties"));
            PropertyConfigurator.configure(properties);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to load logging properties for System");
        }
    }

    private static String getName(String root, String extension) {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date now = new Date();

        return root + sdfDate.format(now) + extension;
    }

    public static void main(String args[]) throws InterruptedException {
        final ProgramProperties properties;

        try {
            initLogging();
        } catch (RuntimeException ex) {
            System.out.println("Error setting up log4j logging");
            System.out.println("Application will continue but without any logging.");
        }

        log.info("PIR Detector started");

        properties = ProgramProperties.getInstance("catcam.properties");

        if (Gpio.wiringPiSetup() == -1) {
            log.error(" ==>> GPIO SETUP FAILED");
        } else {
            Joiner joiner = Joiner.on(" ");
            String videoProperties = joiner.join(properties.getString(VIDEO_SETTINGS),
                    TIME_SET + properties.getString(TIME_TO_RECORD),
                    Camera.VIDEO_DEFAULTS);

            joiner = Joiner.on("/");
            String fileName = joiner.join(properties.getString(STORE_PATH), properties.getString(NAME_STUB));

            String fileExtension = properties.getString(FILE_EXTENSION);

            cutOffSeconds = properties.getInt(DROP_OFF_RATE, DEFAULT_CUT_OFF_SECS);

            // create gpio controller
            final GpioController gpio = GpioFactory.getInstance();

            // provision GPIO_MOVEMENT_PIN as an input pin with its internal pull down resistor enabled
            final GpioPinDigitalInput movementDetected = gpio.provisionDigitalInputPin(GPIO_MOVEMENT_PIN, PinPullResistance.PULL_DOWN);

            final CameraProcess videoCamera = Camera.getPausedNamedVideo(getName(fileName, fileExtension), videoProperties);

            // create and register gpio pin listener
            movementDetected.addListener(new GpioPinListenerDigital() {
                                             @Override
                                             public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                                                 log.debug("Event fired [" + event.getState().isHigh() + "]");

                                                 if (event.getState().isHigh()) {
                                                     // Start capture
                                                     videoCamera.setActive(true);
                                                 }

                                                 synchronized (syncVar) {
                                                     cutOffCounter = cutOffSeconds;      // Reset to the cut off seconds.
                                                 }
                                             }
                                         }
            );

            Runtime.getRuntime().addShutdownHook(new Thread() {
                                                     @Override
                                                     public void run() {
                                                         // stop all GPIO activity/threads by shutting down the GPIO controller
                                                         // (this method will forcefully shutdown all GPIO monitoring threads and scheduled tasks)
                                                         gpio.shutdown();
                                                         log.info("shutdown GPIO.");
                                                         videoCamera.setActive(false);
                                                         log.info("disabled camera.");
                                                         videoCamera.shutdown();
                                                         log.info("shutdown camera.");

                                                         log.info("Exiting program.");
                                                     }
                                                 }

            );

            log.info("Ready for capture");

            // keep program running until user aborts (CTRL-C or SIGINT)
            for (; ; )

            {
                Thread.sleep(1000);         // Sleep for a 1 second

                if (cutOffCounter > 0) {
                    synchronized (syncVar) {
                        cutOffCounter--;
                    }
                    log.debug("cutOffCounter = " + cutOffCounter);
                } else {
                    // Stop capture
                    videoCamera.setActive(false);
                }
            }
        }

        log.info("Exiting program.");
    }
}
