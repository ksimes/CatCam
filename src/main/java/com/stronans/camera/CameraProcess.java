package com.stronans.camera;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Controlling the RPi video Camera using *nix signals.
 * Created by S.King on 08/01/2016.
 */
public class CameraProcess {
    /**
     * The <code>Logger</code> to be used.
     */
    private static final Logger log = LogManager.getLogger(CameraProcess.class);

    public static enum Status {Recording, Paused}

    private final Long pid;
    private volatile Status status;

    public CameraProcess(final Process process) {
        this.pid = unixLikeProcessId(process);
        this.status = Status.Paused;          // Initial state is paused.

        if (pid == null) {
            String err = "Unable to get pid for process: " + process.toString();
            log.error(err);
            throw new IllegalArgumentException(err);
        }
    }

    public Status status() {
        return status;
    }

    private void sendSignal(Status status)
    {
        try {
            Runtime.getRuntime().exec("kill -SIGUSR1 " + pid.toString());
        } catch (IOException e) {
            log.error("IOException: ", e);
        }
    }

    public void setActive(boolean changeState) {
        if (changeState && (status == Status.Paused)) {
            log.info("Camera Recording");
            status = Status.Recording;
            sendSignal(status);
        } else if (!changeState && (status == Status.Recording)) {
            log.info("Camera Paused");
            status = Status.Paused;
            sendSignal(status);
        }
    }

    public void shutdown() {
        try {
            Runtime.getRuntime().exec("kill " + pid.toString());
            log.info("Sent TERM kill");

        } catch (IOException e) {
            log.error("IOException: ", e);
        }
    }

    // From a german website
    private Long unixLikeProcessId(Process process) {
        Class<?> clazz = process.getClass();
        try {
            if (clazz.getName().equals("java.lang.UNIXProcess")) {
                Field pidField = clazz.getDeclaredField("pid");
                pidField.setAccessible(true);
                Object value = pidField.get(process);
                if (value instanceof Integer) {
                    log.debug("Detected pid: " + value);
                    return ((Integer) value).longValue();
                }
            }
        } catch (SecurityException sx) {
            log.error("SecurityException: ", sx);
        } catch (NoSuchFieldException e) {
            log.error("NoSuchFieldException: ", e);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException: ", e);
        } catch (IllegalAccessException e) {
            log.error("IllegalAccessException: ", e);
        }
        return null;
    }
}
