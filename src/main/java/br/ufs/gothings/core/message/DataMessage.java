package br.ufs.gothings.core.message;

import br.ufs.gothings.core.message.headers.GwHeaders;

/**
 * @author Wagner Macedo
 */
public abstract class DataMessage extends GwMessage {

    private final Payload payload;

    protected DataMessage() {
        this(null, null);
    }

    protected DataMessage(GwHeaders headers, Payload payload) {
        super(headers);
        this.payload = (payload != null) ? payload : new Payload();
    }

    protected DataMessage(final GwHeaders headers, final Payload payload, final long sequence) {
        this(headers, payload);
        setSequence(sequence);
    }

    public final Payload payload() {
        return payload;
    }

    @Override
    protected DataMessage readOnly() {
        super.readOnly();
        payload.readOnly();
        return this;
    }
}
