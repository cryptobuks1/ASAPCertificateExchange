package net.sharksystem.persons;

import net.sharksystem.asap.*;
import net.sharksystem.asap.ASAPMessages;
import net.sharksystem.asap.util.ASAPPeerHandleConnectionThread;
import net.sharksystem.asap.util.Helper;
import net.sharksystem.asap.util.Log;
import net.sharksystem.cmdline.TCPStream;
import net.sharksystem.crypto.ASAPCertificate;
import net.sharksystem.crypto.ASAPCertificateStorage;
import net.sharksystem.crypto.ASAPCertificateStorageImpl;
import net.sharksystem.crypto.InMemoASAPKeyStorage;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ExchangeTest {
    public static final String ALICE_ROOT_FOLDER = "tests/Alice";
    public static final String ALICE_APP_FOLDER = ALICE_ROOT_FOLDER + "/certificateStorage";
    public static final String BOB_ROOT_FOLDER = "tests/Bob";
    public static final String BOB_APP_FOLDER = BOB_ROOT_FOLDER + "/certificateStorage";
    public static final String ALICE_ID = "AliceID";
    public static final String BOB_ID = "BobID";
    public static final String ALICE_NAME = "Alice";
    public static final String BOB_NAME = "Bob";
    private static final int PORTNUMBER = 7777;

    @Test
    public void credentialCertificateExchangeOneWay() throws IOException, ASAPException, ASAPSecurityException, InterruptedException {
        ///////////////////////////////////////////////////////////////////////////////////////////////////
        //                                        prepare storages                                       //
        ///////////////////////////////////////////////////////////////////////////////////////////////////

        ASAPEngineFS.removeFolder(ALICE_ROOT_FOLDER); // clean previous version before
        ASAPEngineFS.removeFolder(BOB_ROOT_FOLDER); // clean previous version before

        // setup alice
        ASAPStorage aliceStorage = ASAPEngineFS.getASAPStorage(
                ALICE_ID, ALICE_APP_FOLDER, ASAPCertificateStorage.CERTIFICATE_APP_NAME);

        ASAPCertificateStorage asapAliceCertificateStorage =
                new ASAPCertificateStorageImpl(aliceStorage, ALICE_ID, ALICE_NAME);
        ASAPBasicCryptoStorage aliceCryptoStorage = new InMemoASAPKeyStorage();
        ASAPPKI aliceASAPPKI = new ASAPPKIImpl(asapAliceCertificateStorage, aliceCryptoStorage);

        // setup bob
        ASAPStorage bobStorage = ASAPEngineFS.getASAPStorage(
                BOB_ID, BOB_APP_FOLDER, ASAPCertificateStorage.CERTIFICATE_APP_NAME);

        ASAPCertificateStorage asapBobCertificateStorage =
                new ASAPCertificateStorageImpl(bobStorage, BOB_ID, BOB_NAME);
        ASAPBasicCryptoStorage bobCryptoStorage = new InMemoASAPKeyStorage();
        ASAPPKI bobASAPPKI = new ASAPPKIImpl(asapBobCertificateStorage, bobCryptoStorage);

        ///////////////////////////////////////////////////////////////////////////////////////////////////
        //                                        prepare multi engines                                  //
        ///////////////////////////////////////////////////////////////////////////////////////////////////

        Set<CharSequence> supportedFormats = new HashSet<>();
        supportedFormats.add(ASAPCertificateStorage.CERTIFICATE_APP_NAME);
        supportedFormats.add(ASAPPKI.CREDENTIAL_APP_NAME);

        CredentialReceiver aliceListener = new CredentialReceiver(aliceASAPPKI);
        ASAPPeer alicePeer = ASAPPeerFS.createASAPPeer(
                ALICE_ID, ALICE_ROOT_FOLDER, ASAPPeer.DEFAULT_MAX_PROCESSING_TIME, supportedFormats, aliceListener);

        //aliceEngine.activateOnlineMessages();

        SignCredentialAndReply bobListener = new SignCredentialAndReply(BOB_ROOT_FOLDER, bobASAPPKI);
        ASAPPeer bobPeer = ASAPPeerFS.createASAPPeer(
                BOB_ID, BOB_ROOT_FOLDER, ASAPPeer.DEFAULT_MAX_PROCESSING_TIME, supportedFormats, bobListener);
        bobListener.setAsapPeer(bobPeer);

        //bobEngine.activateOnlineMessages();

        ///////////////////////////////////////////////////////////////////////////////////////////////////
        //                                        setup connection                                       //
        ///////////////////////////////////////////////////////////////////////////////////////////////////

        int portNumber = PORTNUMBER;
        // create connections for both sides
        TCPStream aliceChannel = new TCPStream(portNumber, true, "a2b");
        TCPStream bobChannel = new TCPStream(portNumber, false, "b2a");

        aliceChannel.start();
        bobChannel.start();

        // wait to connect
        aliceChannel.waitForConnection();
        bobChannel.waitForConnection();

        ///////////////////////////////////////////////////////////////////////////////////////////////////
        //                                        run asap connection                                    //
        ///////////////////////////////////////////////////////////////////////////////////////////////////

        // start alice engine as thread
        ASAPPeerHandleConnectionThread aliceEngineThread = new ASAPPeerHandleConnectionThread(alicePeer,
                aliceChannel.getInputStream(), aliceChannel.getOutputStream());
        aliceEngineThread.start();

        // start bob engine as thread
        ASAPPeerHandleConnectionThread bobEngineThread = new ASAPPeerHandleConnectionThread(bobPeer,
                bobChannel.getInputStream(), bobChannel.getOutputStream());
        bobEngineThread.start();

        // wait to connect
        Thread.sleep(2000);

        // run scenario
        System.out.println("//////////////////////////////////////////////////////////////////////////////////");
        System.out.println("//                                  scenario starts                             //");
        System.out.println("//////////////////////////////////////////////////////////////////////////////////");

        // Alice send credentials to Bob
        CredentialMessage credentialMessage = aliceASAPPKI.createCredentialMessage();

        // send it to bob - without traces in asap storages
        alicePeer.sendOnlineASAPAssimilateMessage(ASAPPKI.CREDENTIAL_APP_NAME,
                ASAPPKI.CREDENTIAL_URI, credentialMessage.getMessageAsBytes());

        // wait until communication probably ends
        System.out.flush();
        System.err.flush();
        Thread.sleep(2000);
        System.out.flush();
        System.err.flush();

        // close connections: note ASAPEngine does NOT close any connection
        aliceChannel.close();
        bobChannel.close();
        System.out.flush();
        System.err.flush();
        Thread.sleep(1000);
        System.out.flush();
        System.err.flush();

        // check results

        // alice should have got a certificate of Bob
        Assert.assertTrue(aliceListener.received);
        bobASAPPKI.getIdentityAssurance(ALICE_ID);

        Thread.sleep(1000);
    }

    private class SignCredentialAndReply implements ASAPChunkReceivedListener {
        private final String folderName;
        private final ASAPPKI ASAPPKI;
        private ASAPPeer asapPeer;

        public SignCredentialAndReply(String folderName, ASAPPKI ASAPPKI) {
            this.folderName = folderName;
            this.ASAPPKI = ASAPPKI;
        }

        @Override
        public void chunkReceived(String format, String sender, String uri, int era) {
            ASAPMessages asapMessages =
                    Helper.getMessagesByChunkReceivedInfos(format, sender, uri, this.folderName, era);

            Iterator<byte[]> messages = null;
            try {
                messages = asapMessages.getMessages();
                Log.writeLog(this, "#asap messages: " + asapMessages.size());
                if(messages.hasNext()) {
                    Log.writeLog(this, "create credential message object..");

                    CredentialMessage credential = new CredentialMessage(messages.next());

                    Log.writeLog(this, "..created: " + credential);

                    ASAPCertificate newCert = ASAPPKI.addAndSignPerson(
                            credential.getOwnerID(),
                            credential.getOwnerName(),
                            credential.getPublicKey(),
                            credential.getValidSince());

                    // return newly created certificate
                    Log.writeLog(this, "try to get asap engine for "
                            + ASAPCertificateStorage.CERTIFICATE_APP_NAME);

                    ASAPEngine asapCertEngine = asapPeer.getASAPEngine(ASAPCertificateStorage.CERTIFICATE_APP_NAME);

                    Log.writeLog(this,
                            "right before sending certificate as ASAP Message");
                    asapCertEngine.activateOnlineMessages(asapPeer);
                    asapCertEngine.add(ASAPCertificate.ASAP_CERTIFICATE_URI, newCert.asBytes());
                }
            } catch (Exception e) {
                Log.writeLog(this, "problems when handling incoming credential: "
                        + e.getLocalizedMessage());
            }
        }

        public void setAsapPeer(ASAPPeer asapPeer) {
            this.asapPeer = asapPeer;
        }
    }

    private class CredentialReceiver implements ASAPChunkReceivedListener {
        private final ASAPPKI ASAPPKI;
        boolean received = false;

        public CredentialReceiver(ASAPPKI ASAPPKI) {
            this.ASAPPKI = ASAPPKI;
        }

        @Override
        public void chunkReceived(String format, String sender, String uri, int era) {
            this.received = true;
            this.ASAPPKI.syncNewReceivedCertificates();
        }
    }
}
