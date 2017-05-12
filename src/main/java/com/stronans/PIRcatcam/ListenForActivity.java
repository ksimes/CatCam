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

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.stronans.ProgramProperties;
import com.stronans.camera.Camera;
import com.stronans.camera.CameraProcess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 *
 */
public class ListenForActivity {
    /**
     * The <code>Logger</code> to be used.
     */
    private static final Logger log = LogManager.getLogger(ListenForActivity.class);
    //
    private static final String PROGRAM_ROOT = "com.stronans.catcam.";

    private static final String VIDEO_SETTINGS = PROGRAM_ROOT + "video.settings";
    private static final String NAME_STUB = PROGRAM_ROOT + "filename.stub";
    private static final String STORE_PATH = PROGRAM_ROOT + "store.path";
    private static final String FILE_EXTENSION = PROGRAM_ROOT + "file.extension";
    private static final String TIME_TO_RECORD = PROGRAM_ROOT + "time.record";      // Milliseconds
    private static final String DROP_OFF_RATE = PROGRAM_ROOT + "drop.off";          // Number of seconds before camera switch off

    private static final String TIME_SET = "-t ";

    private static final Pin GPIO_MOVEMENT_PIN = RaspiPin.GPIO_06;

    private static final int DEFAULT_CUT_OFF_SECS = 15;

    private static int cutOffSeconds = DEFAULT_CUT_OFF_SECS;
    private static int cutOffCounter;
    private static final Object syncVar = new Object();

    private static CameraProcess videoCamera;

    private static String getName(String root, String extension, LocalDateTime dateTimeStamp) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        String result = "";

        try {
            result = dateTimeStamp.format(format);
        }
        catch (DateTimeException exc) {
            log.error("%s can't be formatted!%n", dateTimeStamp);
            throw exc;
        }

        return root + result + extension;
    }

    public static void main(String args[]) throws InterruptedException {
        final ProgramProperties properties;

        log.info("PIR Detector started");

        properties = ProgramProperties.getInstance("catcam.properties");

        StringJoiner joiner = new StringJoiner(" ");
        String videoProperties = joiner.add(properties.getString(VIDEO_SETTINGS))
                .add(TIME_SET + properties.getString(TIME_TO_RECORD))
                .add(Camera.VIDEO_DEFAULTS).toString();

        joiner = new StringJoiner("/");
        String fileName = joiner.add(properties.getString(STORE_PATH))
                .add(properties.getString(NAME_STUB)).toString();

        String fileExtension = properties.getString(FILE_EXTENSION);

        cutOffSeconds = properties.getInt(DROP_OFF_RATE, DEFAULT_CUT_OFF_SECS);

        // create gpio controller
        final GpioController gpio = GpioFactory.getInstance();

        // provision GPIO_MOVEMENT_PIN as an input pin with its internal pull down resistor enabled
        final GpioPinDigitalInput movementDetected = gpio.provisionDigitalInputPin(GPIO_MOVEMENT_PIN, PinPullResistance.PULL_DOWN);

        LocalDateTime timeStamp = LocalDateTime.now();

        videoCamera = Camera.getPausedNamedVideo(getName(fileName, fileExtension, timeStamp), videoProperties);

        // create and register gpio pin listener
        movementDetected.addListener(new GpioPinListenerDigital() {
                                         @Override
                                         public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                                             log.debug("Event fired [" + (event.getState().isHigh() ? "High" : "Low") + "]");

                                             if (event.getState().isHigh()) {
                                                 // Start capture
                                                 videoCamera.setActive(true);
                                                 synchronized (syncVar) {
                                                     cutOffCounter = cutOffSeconds;      // Reset to the cut off seconds.
                                                 }
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
                                                     videoCamera.shutdown();
                                                     log.info("shutdown camera.");
                                                     log.info("Exiting program.");
                                                 }
                                             }
        );

        log.info("Ready for capture");

        // keep program running until user aborts (CTRL-C or SIGINT)
        for (; ; ) {
            Thread.sleep(1000);         // Sleep for a 1 second

            if (cutOffCounter > 0) {
                synchronized (syncVar) {
                    cutOffCounter--;
                }
                log.debug("cutOffCounter = " + cutOffCounter);
            } else {
                if (videoCamera.status() != CameraProcess.Status.Paused) {
                    // Stop capture
                    videoCamera.setActive(false);
                    // If we have crossed over midnight of the day the video was started then create a new named video.
                    if(LocalDateTime.now().truncatedTo(DAYS).isAfter(timeStamp.truncatedTo(DAYS))) {
                        videoCamera.shutdown();
                        log.info("shutdown camera.");
                        timeStamp = LocalDateTime.now();

                        videoCamera = Camera.getPausedNamedVideo(getName(fileName, fileExtension, timeStamp), videoProperties);
                    }
                }
            }
        }
//        log.info("Exiting program.");
    }
}
