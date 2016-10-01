package com.stronans.camera;

import org.apache.log4j.Logger;

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
    private static final Logger log = Logger.getLogger(CameraProcess.class);

    private enum Status {Active, Inactive}

    private final Long pid;
    private volatile Status status;

    public CameraProcess(final Process process) {
        this.pid = unixLikeProcessId(process);
        this.status = Status.Active;

        if (pid == null) {
            String err = "Unable to get pid for process: " + process.toString();
            log.error(err);
            throw new IllegalArgumentException(err);
        }
    }

    public void setActive(boolean state) {
        boolean execute = false;

        if (state && (status == Status.Inactive)) {
            log.info("Camera Activated");
            status = Status.Active;
            execute = true;
        } else if (status == Status.Active) {
            log.info("Camera Deactivated");
            status = Status.Inactive;
            execute = true;
        }

        if (execute) {
            try {
                Runtime.getRuntime().exec("kill -SIGUSR1 " + pid.toString());

            } catch (IOException e) {
                log.error("IOException: ", e);
            }
        }
    }

    public void shutdown() {
        try {
            Runtime.getRuntime().exec("kill " + pid.toString());

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
