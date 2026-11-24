package dev.oskar_lab.backend.storage;

/** Safe storage error; never includes credentials, filenames or upstream response bodies. */
public class StorageFailure extends RuntimeException {
    public final int status;
    public StorageFailure(int status, String code) { super(code); this.status = status; }
}
