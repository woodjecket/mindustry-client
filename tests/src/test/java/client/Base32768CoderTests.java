package client;

import mindustry.client.crypto.Base32768Coder;
import mindustry.client.utils.UtilitiesKt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Random;

public class Base32768CoderTests {
    private final Random r = new Random();
    private final byte[] b = new byte[1000];
    private int i = 0;
    private int failCount = 0;

    @RepeatedTest(500)
    void testCoder() throws IOException {
        r.nextBytes(b);

        String encoded = Base32768Coder.INSTANCE.encode(b);

        boolean shouldFail = false;
        if (i++ % 2 == 0) {
            char[] array = encoded.toCharArray();
            array[r.nextInt(encoded.length() - 1)] += 1;
            encoded = new String(array);
            shouldFail = true;
        }

        if (!shouldFail) {
            Assertions.assertArrayEquals(b, Base32768Coder.INSTANCE.decode(encoded));
        } else {
            try {
                Base32768Coder.INSTANCE.decode(encoded);
                if (failCount++ > 10) {  // small chance it'll happen anyways, not much to do about it
                    throw new AssertionError("Missed changed char!");
                }
            } catch (IOException ignored) {}
        }
    }
}
