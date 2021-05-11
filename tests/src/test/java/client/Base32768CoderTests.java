package client;

import mindustry.client.crypto.Base32768Coder;
import mindustry.client.utils.UtilitiesKt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Random;

public class Base32768CoderTests {

    @Test
    void testCoder() throws IOException {
        Random r = new Random(0);
        for (int i = 0; i < 10000; i++) {
            byte[] bytes = new byte[i];
            r.nextBytes(bytes);
            String encoded = Base32768Coder.INSTANCE.encode(bytes);
            byte[] decoded = Base32768Coder.INSTANCE.decode(encoded);
            try {
                Assertions.assertArrayEquals(bytes, decoded);
            } catch (AssertionError e) {
                System.out.println(Arrays.toString(bytes));
                System.out.println(Arrays.toString(decoded));
                throw e;
            }
        }

        byte[] bytes = new byte[1024 * 1024];
        r.nextBytes(bytes);

        Instant start = Instant.now();
        String encoded = Base32768Coder.INSTANCE.encode(bytes);
        Assertions.assertArrayEquals(bytes, Base32768Coder.INSTANCE.decode(encoded));
        System.out.print("Time taken: ");
        System.out.println(UtilitiesKt.age(start, ChronoUnit.MILLIS));
    }
}
