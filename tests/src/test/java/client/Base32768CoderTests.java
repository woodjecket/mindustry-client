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
        String aaa = Base32768Coder.INSTANCE.encode(new byte[] { -1, -2, -3, 4, 5, 6, 10, 8, 9, 11 });
        System.out.println(Arrays.toString(Base32768Coder.INSTANCE.decode(aaa)));

        for (int i = 0; i < 100; i++) {
            byte[] bytes = new byte[1_000];
            new Random().nextBytes(bytes);

            String encoded = Base32768Coder.INSTANCE.encode(bytes);
            Assertions.assertArrayEquals(bytes, Base32768Coder.INSTANCE.decode(encoded));
        }

        byte[] bytes = new byte[1024 * 10];
        new Random().nextBytes(bytes);

        Instant start = Instant.now();
        String encoded = Base32768Coder.INSTANCE.encode(bytes);
        Assertions.assertArrayEquals(bytes, Base32768Coder.INSTANCE.decode(encoded));
        System.out.print("Time taken: ");
        System.out.println(UtilitiesKt.age(start, ChronoUnit.SECONDS));
    }
}
