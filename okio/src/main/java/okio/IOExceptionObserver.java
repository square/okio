package okio;

import java.io.IOException;

public interface IOExceptionObserver {
    void onIOException(IOException e);
}
