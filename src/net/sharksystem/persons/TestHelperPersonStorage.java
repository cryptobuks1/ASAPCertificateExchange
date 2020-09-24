package net.sharksystem.persons;

import net.sharksystem.asap.ASAPSecurityException;
import net.sharksystem.crypto.ASAPCertificate;
import net.sharksystem.crypto.ASAPCertificateStorage;
import net.sharksystem.crypto.BasicCryptoSettings;
import net.sharksystem.crypto.InMemoCertificateStorageImpl;

import java.io.IOException;
import java.util.Random;

public class TestHelperPersonStorage {
    public static final CharSequence FRANCIS_NAME = "Francis";
    public static final CharSequence GLORIA_NAME = "Gloria";
    public static final CharSequence HASSAN_NAME = "Hassan";
    public static final CharSequence IRIS_NAME = "Iris";

    public static void fillWithExampleData(FullAsapPKIStorage asapPKI)
            throws ASAPSecurityException, IOException {

        ASAPCertificateStorage certificateStorage;
        long now = System.currentTimeMillis();

        Random random = new Random(System.currentTimeMillis());
        int randomValue = random.nextInt();
        String randomString = random.toString();

        // very very unlikely, but better safe than sorry: example data must same id
        String idStart = randomString.substring(0, 3) + "_";

        ASAPPKI gloriaStorage = null, hassanStorage = null, irisStorage;

        ASAPBasicCryptoStorage asapBasicCryptoStorage = asapPKI.getASAPBasicCryptoStorage();

        // Owner signs Francis ia(F): 10
        String francisID = idStart + FRANCIS_NAME;
        certificateStorage = new InMemoCertificateStorageImpl(francisID, FRANCIS_NAME);
        ASAPPKI francisStorage = new ASAPPKIImpl(certificateStorage, asapBasicCryptoStorage);
        asapPKI.addAndSignPerson(francisID, FRANCIS_NAME, francisStorage.getPublicKey(), now);

        // Francis signs Gloria: cef(f) = 0.5 ia(g) = 5.0
        String gloriaID = idStart + GLORIA_NAME;
        certificateStorage = new InMemoCertificateStorageImpl(gloriaID, GLORIA_NAME);
        gloriaStorage = new ASAPPKIImpl(certificateStorage, asapBasicCryptoStorage);
        // francis signs gloria
        ASAPCertificate asapCertificate =
                francisStorage.addAndSignPerson(gloriaID, GLORIA_NAME, gloriaStorage.getPublicKey(), now);

        // store certificate(issuer: Francis, subject: Gloria)
        asapPKI.addCertificate(asapCertificate);

        // Gloria signs Hassan: cef(g) = 0.5 ia(h) = 2.5 == 3
        String hassanID = idStart + HASSAN_NAME;
        certificateStorage = new InMemoCertificateStorageImpl(hassanID, HASSAN_NAME);
        hassanStorage = new ASAPPKIImpl(certificateStorage, asapBasicCryptoStorage);
        // gloria signs hassan
        asapCertificate = gloriaStorage.addAndSignPerson(hassanID, HASSAN_NAME, hassanStorage.getPublicKey(), now);

        // store certificate(issuer: Gloria, subject: Hassan)
        asapPKI.addCertificate(asapCertificate);

        // Hassan signs Iris: cef(h) = 0.5: ia(i) = 1.25 == 1
        String irisID = idStart + IRIS_NAME;
        certificateStorage = new InMemoCertificateStorageImpl(irisID, IRIS_NAME);
        irisStorage = new ASAPPKIImpl(certificateStorage, asapBasicCryptoStorage);
        // hassan signs iris
        asapCertificate = hassanStorage.addAndSignPerson(irisID, IRIS_NAME, irisStorage.getPublicKey(), now);
        // store certificate(issuer: Hassan, subject: Iris)
        asapPKI.addCertificate(asapCertificate);
    }
}
