package optimus.prime.rsa.server.crypto;

import org.bouncycastle.crypto.AsymmetricBlockCipher;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * this class will check if a private key is valid or not
 */
public class RSAHelper {

    /**
     * this method returns a if a RSA key generated out of p and q fits to the public key
     *
     * @param p         the first prime
     * @param q         the second prime
     * @param publicKey the key to check against
     * @return true, if the private key fits to the public key, else false
     */
    public boolean isValid(String p, String q, String publicKey) {
        BigInteger P = new BigInteger(p);
        BigInteger Q = new BigInteger(q);
        BigInteger MODULUS = new BigInteger(publicKey);

        RSAHelper checker = new RSAHelper();
        return checker.isValid(P, Q, MODULUS);
    }

    /**
     * this method is used to generate a keypair to be used in BC for crypto stuff
     *
     * @param p the first prime
     * @param q the second prime
     * @return AsymmetricCipherKeyPair the key pair
     */
    private AsymmetricCipherKeyPair getKeyPair(String p, String q) {
        // first we need the generator
        RSAKeyPairGenerator ownGenerator
                = new RSAKeyPairGenerator();
        ownGenerator.init(new RSAKeyGenerationParameters(
                RSAStatics.e,
                new SecureRandom(),
                RSAStatics.KEY_SIZE,
                RSAStatics.CERTAINTY)
        );

        BigInteger P = new BigInteger(p);
        BigInteger Q = new BigInteger(q);

        return ownGenerator.generateKeyPair(P, Q);
    }

    /**
     * this method decodes a cipher text and returns the decrypted text
     *
     * @param p      P prime factor of the private key
     * @param q      Q prime factor of the private key
     * @param cipher String HEX encoded and encrypted
     * @return the decoded {@link String} in UTF-8
     */
    public String decrypt(String p, String q, String cipher) {
        AsymmetricCipherKeyPair keyPair = getKeyPair(p, q);
        return decrypt(keyPair, cipher);
    }

    /**
     * this method checks if the 2 primes will lead to a bitlength as exspected
     *
     * @param p         the first prime
     * @param q         the second prime
     * @param bitLength b
     * @return true, if the strength is valid
     */
    public boolean isStrengthValid(BigInteger p, BigInteger q, int bitLength) {
        BigInteger n = p.multiply(q);
        int bitL = n.bitLength();
        //System.out.println("p: '"+p+"', q: '"+q+"' => strength is: "+bitL+"bits");
        return bitL == bitLength;
    }

    /**
     * this method returns a if a RSA key generated out of p and q fits to the public key
     *
     * @param p                the first prime
     * @param q                the second prime
     * @param modulusPublicKey the key to check against
     * @return true, if the private key fits to the public key, else false
     */
    public boolean isValid(BigInteger p, BigInteger q, BigInteger modulusPublicKey) {
        RSAKeyPairGenerator generator =
                new RSAKeyPairGenerator();
        generator.init(new RSAKeyGenerationParameters(
                RSAStatics.e,
                new SecureRandom(),
                RSAStatics.KEY_SIZE,
                RSAStatics.CERTAINTY)
        );

        try {
            AsymmetricCipherKeyPair keyPair = generator.generateKeyPair(p, q);

            RSAKeyParameters params = (RSAKeyParameters) keyPair.getPublic();

            BigInteger modulus = params.getModulus();

            return modulus.equals(modulusPublicKey);
        } catch (Exception e) {
            return false;
        }

    }

    /**
     * this method decodes a cipher text and returns the decrypted text
     *
     * @param keypair       RSA keypair to use
     * @param encodedString String HEX encoded and encrypted
     * @return the decoded {@link String} in UTF-8
     */
    public String decrypt(AsymmetricCipherKeyPair keypair, String encodedString) {
        StringBuilder value = new StringBuilder();
        AsymmetricKeyParameter key = keypair.getPrivate();
        AsymmetricBlockCipher e = new RSAEngine();
        e = new org.bouncycastle.crypto.encodings.PKCS1Encoding(e);
        e.init(false, key);
        try {
            byte[] messageBytes = Hex.decode(encodedString);
            int i = 0;
            int len = e.getInputBlockSize();
            while (i < messageBytes.length) {
                if (i + len > messageBytes.length)
                    len = messageBytes.length - i;

                byte[] hexEncodedCipher;

                hexEncodedCipher = e.processBlock(messageBytes, i, len);

                value.append(new String(hexEncodedCipher));
                i += e.getInputBlockSize();
            }
            return value.toString();
        } catch (InvalidCipherTextException invalidCipherTextException) {
            invalidCipherTextException.printStackTrace();
        }

        return "";
    }

    /**
     * this method encodes a text and returns the cipher text base64 encoded
     *
     * @param keypair  RSA keypair to use
     * @param clearTxt String HEX encoded and encrypted
     * @return the encoded and encrypted {@link String} in base64
     */
    public String encrypt(AsymmetricCipherKeyPair keypair, String clearTxt) {
        StringBuilder value = new StringBuilder();
        AsymmetricKeyParameter publicKey = keypair.getPublic();
        AsymmetricBlockCipher e = new RSAEngine();
        e = new org.bouncycastle.crypto.encodings.PKCS1Encoding(e);
        e.init(true, publicKey);
        try {
            byte[] messageBytes = clearTxt.getBytes(StandardCharsets.UTF_8);
            int i = 0;
            int len = e.getInputBlockSize();
            while (i < messageBytes.length) {
                if (i + len > messageBytes.length)
                    len = messageBytes.length - i;

                byte[] hexEncodedCipher;

                hexEncodedCipher = e.processBlock(messageBytes, i, len);

                value.append(new String(Hex.encode(hexEncodedCipher)));
                i += e.getInputBlockSize();
            }
            return value.toString();
        } catch (InvalidCipherTextException invalidCipherTextException) {
            invalidCipherTextException.printStackTrace();
        }

        return "";
    }
}
