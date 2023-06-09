package optimus.prime.rsa.server.crypto;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.SecureRandom;

public class KeyGenerator {

    private static final long MAX = 5000;

    /**
     * this method we use to generate our test keys
     */
    public static void main(String[] args) throws IOException {
        RSAKeyPairGenerator ownGenerator
                = new RSAKeyPairGenerator();
        ownGenerator.init(new RSAKeyGenerationParameters(
                RSAStatics.e,
                new SecureRandom(),
                RSAStatics.KEY_SIZE,
                RSAStatics.CERTAINTY));

        String path = "src/main/resources/primes10000.txt";
        File f = new File(path);
        if (!f.exists()) {
            // noinspection ResultOfMethodCallIgnored
            f.createNewFile();
        }
        BufferedWriter bw = new BufferedWriter(new FileWriter(f));

        for (long i = 0; i < MAX; i++) {
            AsymmetricCipherKeyPair keyPair = ownGenerator.generateKeyPair();

            RSAPrivateCrtKeyParameters privateKey = (RSAPrivateCrtKeyParameters) keyPair.getPrivate();

            System.out.println("p => '" + privateKey.getP() + "'");
            System.out.println("q => '" + privateKey.getQ() + "'");

            bw.write(String.valueOf(privateKey.getP()));
            bw.newLine();
            bw.write(String.valueOf(privateKey.getQ()));
            bw.newLine();

        }
        bw.close();
    }
}
