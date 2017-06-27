import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

/**
 * Created by goldratio on 25/06/2017.
 */
public class Test {

    public static String data = "hello world";
    public static String modulusString = "95701876885335270857822974167577168764621211406341574477817778908798408856077334510496515211568839843884498881589280440763139683446418982307428928523091367233376499779842840789220784202847513854967218444344438545354682865713417516385450114501727182277555013890267914809715178404671863643421619292274848317157";
    public static String publicExponentString = "65537";
    public static String privateExponentString = "15118200884902819158506511612629910252530988627643229329521452996670429328272100404155979400725883072214721713247384231857130859555987849975263007110480563992945828011871526769689381461965107692102011772019212674436519765580328720044447875477151172925640047963361834004267745612848169871802590337012858580097";

    public static void main(String[] args) throws Exception {

        Security.addProvider(new BouncyCastleProvider());
//        PublicKey publicKey = getPublicKey(modulusString, publicExponentString);
        PrivateKey privateKey = getPrivateKey(modulusString, privateExponentString);
//
//        byte[] encryptedBytes = encrypt(data.getBytes(), publicKey);
//        char[] test = Hex.encodeHex(encryptedBytes);
//        //System.out.println("加密后：" + new String(encryptedBytes));
//
//        System.out.println("加密后：" + new String(test));

        //byte[] testEnc = doTest(data, modulusString, publicExponentString);

        Hex hex = new Hex();
//        //byte[] encrypted = hex.decode(testEnc.getBytes());
        byte[] encrypted = hex.decode("0ee89d75eecf7c5836395e537ed9358b".getBytes());
        System.out.println(new String(encrypted));
//        byte[] testc = doTest2(encrypted, modulusString, privateExponentString);
//        byte[] encrypted = testEnc;
//        byte[] decryptedBytes = decrypt(encrypted, privateKey);
//        System.out.println("解密后：" + new String(decryptedBytes));
    }


    public static PublicKey getPublicKey(String modulusStr, String exponentStr) throws Exception{
        BigInteger modulus = new BigInteger(modulusStr);
        BigInteger exponent = new BigInteger(exponentStr);

        System.out.println(modulus.toString(16));
        System.out.println(exponent.toString(16));

        System.out.println(String.format("%X", modulus));
        System.out.println(String.format("%X", exponent));

        RSAPublicKeySpec publicKeySpec=new RSAPublicKeySpec(modulus, exponent);
        KeyFactory keyFactory= KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(publicKeySpec);
    }

    public static byte[] encrypt(byte[] content, PublicKey publicKey) throws Exception{
        Cipher cipher= Cipher.getInstance("RSA");//java默认"RSA"="RSA/ECB/PKCS1Padding"
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(content);
    }

    public static byte[] decrypt(byte[] content, PrivateKey privateKey) throws Exception{
        Cipher cipher = Cipher.getInstance("RSA", "BC");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(content);
    }

    public static PrivateKey getPrivateKey(String modulusStr, String exponentStr) throws Exception{
        BigInteger modulus=new BigInteger(modulusStr);
        BigInteger exponent=new BigInteger(exponentStr);
        RSAPrivateKeySpec privateKeySpec=new RSAPrivateKeySpec(modulus, exponent);
        KeyFactory keyFactory=KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(privateKeySpec);
    }

    public static byte[] doTest(String plainText, String modulus, String publicExponent) throws Exception {
        BigInteger bigIntModulus = new BigInteger(modulus);

        BigInteger bigIntPrivateExponent = new BigInteger(publicExponent);

        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(bigIntModulus, bigIntPrivateExponent);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        Cipher cipher = Cipher.getInstance("RSA/NONE/NoPadding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] cipherText = cipher.doFinal(plainText.getBytes());

        Hex hex = new Hex();
        byte[] ss =  hex.encode(Arrays.copyOfRange(cipherText, cipherText.length - 128, cipherText.length));
        //加密后的东西
        System.out.println("加密后：" + new String(ss));      //256
        return cipherText;
    }

    public static byte[] doTest2(byte[] plainText, String modulus, String publicExponent) throws Exception {
        BigInteger bigIntModulus = new BigInteger(modulus);

        BigInteger bigIntPrivateExponent = new BigInteger(publicExponent);

        RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(bigIntModulus, bigIntPrivateExponent);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey publicKey = keyFactory.generatePrivate(keySpec);

        Cipher cipher = Cipher.getInstance("RSA/None/PKCS1PADDING", "BC");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        byte[] cipherText = cipher.doFinal(plainText);

        //加密后的东西
        System.out.println("解密后：" + new String(cipherText));      //256
        return cipherText;
    }

}


