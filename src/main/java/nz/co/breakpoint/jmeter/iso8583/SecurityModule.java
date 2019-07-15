package nz.co.breakpoint.jmeter.iso8583;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Key;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.core.SimpleConfiguration;
import org.jpos.iso.ISOUtil;
import org.jpos.security.*;
import org.jpos.security.jceadapter.JCEHandler;
import org.jpos.security.jceadapter.JCEHandlerException;
import org.jpos.security.jceadapter.JCESecurityModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Extension of jPOS class to use non-public methods (like calculateARQC).
 */
public class SecurityModule extends JCESecurityModule {

    private static final Logger log = LoggerFactory.getLogger(SecurityModule.class);

    public SecurityModule() {
        super();
        Configuration cfg = new SimpleConfiguration();
        cfg.put("provider", BouncyCastleProvider.class.getName());
        cfg.put("rebuildlmk", "true"); // generate some keys rather than from "lmk" file
        try {
            setConfiguration(cfg);
        } catch (ConfigurationException shouldNotHappen) { // config is correct (unless BC is missing)
            shouldNotHappen.printStackTrace();
        }
    }

    protected JCEHandler getJceHandler() {
        try {
            Field f = getClass().getSuperclass().getDeclaredField("jceHandler");
            f.setAccessible(true);
            return (JCEHandler) f.get(this);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("Failed to access jceHandler", e);
        }
        return null;
    }

    public String encryptPINBlock(byte[] clearPinBlock, Key clearPinKey) throws JCEHandlerException {
        return ISOUtil.byte2hex(getJceHandler().encryptData(clearPinBlock, clearPinKey));
    }

    public String generateMAC(byte[] packedMsg, Key clearMacKey, String macAlgorithm) throws JCEHandlerException {
        return ISOUtil.byte2hex(getJceHandler().generateMAC(packedMsg, clearMacKey, macAlgorithm));
    }

    protected byte[] calculateDerivedKey(KeySerialNumber ksn, SecureDESKey bdk, boolean tdes, boolean dataEncryption) {
        try {
            Method m = getClass().getSuperclass().getDeclaredMethod("calculateDerivedKey",
                KeySerialNumber.class, SecureDESKey.class, boolean.class, boolean.class);
            m.setAccessible(true);
            return (byte[]) m.invoke(this, ksn, bdk, tdes, dataEncryption);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error("Failed to invoke calculateDerivedKey", e);
        }
        return new byte[0];
    }

    protected byte[] specialEncrypt(byte[] data, byte[] key) {
        try {
            Method m = getClass().getSuperclass().getDeclaredMethod("specialEncrypt",
                byte[].class, byte[].class);
            m.setAccessible(true);
            return (byte[]) m.invoke(this, data, key);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error("Failed to invoke specialEncrypt", e);
        }
        return new byte[0];
    }

    protected String calculateARQC(MKDMethod mkdm, SKDMethod skdm,
           SecureDESKey imkac, String accountNo, String accntSeqNo, String atc,
           String upn, String transData) {
        try {
            Method m = getClass().getSuperclass().getDeclaredMethod("calculateARQC",
                MKDMethod.class, SKDMethod.class, SecureDESKey.class, String.class, String.class,
                byte[].class, byte[].class, byte[].class);
            m.setAccessible(true);
            return ISOUtil.byte2hex((byte[]) m.invoke(this, mkdm, skdm, imkac, accountNo, accntSeqNo,
                ISOUtil.hex2byte(atc), ISOUtil.hex2byte(upn), ISOUtil.hex2byte(transData)));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error("Failed to invoke calculateARQC", e);
        }
        return "";
    }

    public String calculateARQC(MKDMethod mkdm, SKDMethod skdm, String clearMKAC,
            String accountNo, String accntSeqNo, String atc, String upn, String transData) {
        log.debug("ARQC input '{}'", transData);
        try {
            SecureDESKey mkac = formKEYfromClearComponents(LENGTH_DES3_2KEY, TYPE_MK_AC, clearMKAC);
            return calculateARQC(mkdm, skdm, mkac, accountNo, accntSeqNo, atc, upn, transData);
        } catch (SMException e) {
            log.error("ARQC calculation failed {}", e.toString(), e);
        }
        return "";
    }

    // TODO WIP, untested
    // PIN encryption with DUKPT
    // https://github.com/jpos/jPOS/blob/v2_1_3/jpos/src/main/java/org/jpos/security/jceadapter/JCESecurityModule.java#L2452
    public String encryptPINBlock(String baseKeyID, String deviceID, String transactionCounter,
            String clearBDK, String clearPINBlock, boolean tdes) {
        KeySerialNumber ksn = new KeySerialNumber(baseKeyID, deviceID, transactionCounter);
        try {
            SecureDESKey bdk = formKEYfromClearComponents(LENGTH_DES3_2KEY, TYPE_BDK, clearBDK); // TODO what keyLength?
            byte[] derivedKey = calculateDerivedKey(ksn, bdk, tdes, false);
            byte[] translatedPINBlock = specialEncrypt(ISOUtil.hex2byte(clearPINBlock), derivedKey);
            return ISOUtil.byte2hex(translatedPINBlock);
        } catch (SMException e) {
            log.error("DUKPT PIN-Block encryption failed {}", e.toString(), e);
        }
        return "";
    }

    // TODO WIP, untested
    public String calculateCVV(String accountNo, Key cvk, String expDate, String serviceCode) {
        try {
            Method m = getClass().getSuperclass().getDeclaredMethod("calculateCVD",
                String.class, Key.class, String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(this, accountNo, cvk, expDate, serviceCode);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error("Failed to invoke calculateCVD", e);
        }
        return "";
    }
}