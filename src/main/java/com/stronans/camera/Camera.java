package com.stronans.camera;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Composites an image drawn from an external program which dumps the image to the output stream
 * of the running process. Converts that image into a Base64 string.
 * Created by S.King on 11/01/2015.
 */
public class Camera {
    private static final Logger log = LogManager.getLogger(com.stronans.camera.Camera.class);

    public static final String STILL_DEFAULTS = " -t 100 -rot 180 "; // Take image after 100 milliseconds and rotate 180 degrees.
    public static final String VIDEO_DEFAULTS = "";         //  -t 10000
    /**
     * The <code>Logger</code> to be used.
     */
    private static final String EXTERNAL_STILL_TOOL = "raspistill";
    // --nopreview,     -n      Do not display a preview window
    // --output,        -o      Output filename <filename>.
    // Specify the output filename. If not specified, no file is saved. If the filename is '-', then all output is sent to stdout.
    private static final String FINAL_STILL_SETTINGS = "-n -o -";

    private static final String EXTERNAL_VIDEO_TOOL = "raspivid";

    // --nopreview,     -n      Do not display a preview window
    // --output,        -o      Output filename <filename>.
    // Specify the output filename. If not specified, no file is saved. If the filename is '-', then all output is sent to stdout.
    private static final String FINAL_VIDEO_SETTINGS = "-n -o ";

    public static byte[] getImage(String settings) throws IOException {
        byte[] image = new byte[0];

        log.debug("Start getting Camera Image");
        long start = System.currentTimeMillis();

        StringJoiner joiner = new StringJoiner(" ");
        String finalSettings = joiner.add(EXTERNAL_STILL_TOOL).add(settings).add(FINAL_STILL_SETTINGS).toString();

        try {
            log.info("Capture image with settings [" + finalSettings + "]");
            Process p = Runtime.getRuntime().exec(finalSettings);
            image = new byte[p.getInputStream().available()];

        } catch (Exception e) {
            log.error("During reading input stream, Camera settings [" + finalSettings + "]", e);
        }

        log.debug("Stop getting Camera Image");
        log.debug("Gathered " + image.length + " bytes");
        log.debug("Duration in ms: " + (System.currentTimeMillis() - start));

        return image;
    }

    public static Optional<String> getEncodedStillImage(String settings) {
        String result = null;

        try {
            byte[] image = getImage(settings);
            result= Base64.getEncoder().encodeToString(image);
        } catch (Exception exp) {
            log.error("During encoding of image", exp);
            result = null;
        }

        return Optional.ofNullable(result);
    }

    public static boolean getNamedVideo(String name, String settings) {
        boolean result = true;

        log.debug("Start getting video Image");
        StringJoiner joiner = new StringJoiner(" ");
        String finalSettings = joiner.add(EXTERNAL_VIDEO_TOOL).add(settings).add(FINAL_VIDEO_SETTINGS).add(name).toString();

        try {
            log.info("Capture video image with settings [" + finalSettings + "]");

            // Note that this process will wait for the process created to complete before continuing.
            Process p = Runtime.getRuntime().exec(finalSettings);
            p.waitFor();

        } catch (Exception e) {
            log.error("During video capture, Camera settings [" + finalSettings + "]", e);
            result = false;
        }

        log.debug("Stop getting video Image");
        log.info("Completed capture");

        return result;
    }

    public static CameraProcess getPausedNamedVideo(String name, String settings) {
        Process process = null;
        log.debug("Start video paused");
        StringJoiner joiner = new StringJoiner(" ");

        // --signal,   -s      Toggle between record and pause according to SIGUSR1
        // Sending a USR1 signal to the raspivid process will toggle between recording and paused.
        // --initial,  -i      Define initial state on startup.
        // Define whether the camera will start paused or will immediately start recording. Options are 'record' or 'pause'.
        // Note that if you are using a simple timeout, and initial is set to 'pause', no output will be recorded.
        String finalSettings = joiner.add(EXTERNAL_VIDEO_TOOL).add("-s -i pause").add(settings).add(FINAL_VIDEO_SETTINGS).add(name).toString();

        try {
            log.info("Capture video image with settings [" + finalSettings + "]");

            // Note that this process will wait for the process created to complete before continuing.
            process = Runtime.getRuntime().exec(finalSettings);

        } catch (Exception e) {
            log.error("During video capture, Camera settings [" + finalSettings + "]", e);
            process = null;
        }

        return new CameraProcess(process);
    }
}
