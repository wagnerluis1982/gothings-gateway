package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwReply extends DataMessage {
    /**
     * Construct a reply to a specific request.
     *
     * @param req    request to be replied
     */
    public GwReply(GwRequest req) {
        super(req.headers(), req.payload(), req.getSequence());
    }

    /**
     * Construct an unsequenced reply, i.e. an independent reply, used to send
     * data without match a previous request.
     */
    public GwReply() {
        super();
        lockSequence();
    }

    public GwReply(final GwHeaders headers, final Payload payload, final Long sequence) {
        super(headers, payload, sequence);
    }

    /**
     * Construct a read-only view of this reply with a new sequence
     *
     * @param sequence  new sequence
     * @return a read-only reply
     */
    public GwReply asReadOnly(final Long sequence) {
        final GwReply reply = new GwReply(this.headers().asReadOnly(), this.payload().asReadOnly(), sequence);
        reply.lockSequence();
        return reply;
    }

    @Override
    public MessageType getType() {
        return MessageType.REPLY;
    }

    private static final Payload emptyPayload = new Payload().asReadOnly();

    public static GwReply emptyReply(GwRequest req) {
        return new GwReply(req.headers().asReadOnly(), emptyPayload, req.getSequence());
    }
}
