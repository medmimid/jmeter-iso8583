package nz.co.breakpoint.jmeter.iso8583;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.jpos.iso.*;
import org.jpos.tlv.ISOTaggedField;

/* Builds an ISOMsg from elements configured in the JMeter elements.
 */
public class MessageBuilder {

    protected ISOMsg msg;
    private byte[] packedMsg; // cache a packed version of the message to avoid repeated packing
    protected ISOPackager packager;

    public MessageBuilder() {
        this(null);
    }

    public MessageBuilder(ISOPackager packager) {
        msg = new ISOMsg();
        withPackager(packager);
    }

    public ISOMsg build() throws ISOException {
        pack();
        return msg;
    }

    public MessageBuilder withPackager(ISOPackager packager) {
        this.packager = packager;
        msg.setPackager(packager);
        return this;
    }

    public MessageBuilder define(Iterable<MessageField> fields) throws ISOException {
        msg = new ISOMsg();
        msg.setPackager(packager); // may be undefined at this stage
        return extend(fields);
    }

    public MessageBuilder extend(Iterable<MessageField> fields) throws ISOException {
        if (fields != null) {
            for (MessageField f : fields) {
                if (f.getTag().isEmpty()) {
                    // no tag => normal ISOField can be set directly:
                    msg.set(f.getName(), f.getContent());
                } else {
                    // tag => ISOTaggedField has to be created explicitly, and needs to know the subfield Id:
                    int lastDot = f.getName().lastIndexOf('.');
                    int subfieldId = Integer.parseInt(f.getName().substring(lastDot+1));
                    msg.set(f.getName(), new ISOTaggedField(f.getTag(), new ISOField(subfieldId, f.getContent())));
                }
            }
            packedMsg = null; // needs to be packed again
        }
        return this;
    }

    public MessageBuilder pack() throws ISOException {
        if (packager != null) {
            msg.setPackager(packager);
            packedMsg = msg.pack();
        }
        return this;
    }

    public ISOMsg getMessage() {
        return msg;
    }

    public byte[] getPackedMessage() throws ISOException {
        pack();
        return packedMsg;
    }

    // For SampleResult
    int getMessageSize() {
        return packedMsg == null ? 0 : packedMsg.length;
    }

    // For unit tests
    String getMessageBytes() {
        return ISOUtil.byte2hex(packedMsg);
    }

    public static String getMessageAsString(ISOMsg msg) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        msg.dump(new PrintStream(baos, true), "");
        return baos.toString();
    }

    public String getMessageAsString(boolean hexdump) {
        StringBuilder sb = new StringBuilder(getMessageAsString(msg));
        if (hexdump && packedMsg != null) {
            sb.append("\n<!--\n").append(ISOUtil.hexdump(packedMsg)).append("-->");
        }
        return sb.toString();
    }
}