package com.prenotazioni.events;

/**
 * The names by which the services find each other on the broker.
 *
 * They live here rather than duplicated across the two application.properties for the same
 * reason the event is a shared record: a queue name written twice is a name that can drift
 * apart, and the way that would show up is the worst possible one - no error at all, the
 * message simply never arrives.
 *
 * Constants only: shared does not depend on Spring AMQP. Each service declares its own beans
 * (the exchange for the publisher, the queue and the binding for the consumer) using these
 * names.
 *
 * The VALUES are still Italian. Renaming them is not a code change but a broker change: the
 * old exchange and queue would stay behind, and any message still sitting in the old queue
 * would be stranded with nobody listening. It is worth doing on an empty broker, and it is
 * deliberately not bundled with a translation pass.
 */
public final class EventTopology {

    /** The topic exchange the booking events travel on. */
    public static final String EXCHANGE = "prenotazioni.eventi";

    /** The routing key of the cancellation event. */
    public static final String ROUTING_KEY_CANCELLATION = "prenotazione.cancellata";

    /**
     * The notification service's queue. It is durable: without that, a broker restart would
     * lose the messages not yet consumed, which is exactly what this queue exists to prevent.
     */
    public static final String CANCELLATION_QUEUE = "notifiche.prenotazione-cancellata";

    /**
     * Where a message that could not be handled ends up.
     *
     * Without it, Spring AMQP's default behaviour requeues a message whose listener throws:
     * if the save fails - unreachable database, violated constraint - that message comes back
     * and starts over forever, burning resources and burying everything else in the logs.
     *
     * These are NEW names on purpose. The canonical approach would be to declare the existing
     * queue with x-dead-letter-exchange, but RabbitMQ refuses to redeclare a durable queue
     * with arguments different from the ones it was born with: on a broker that already has
     * the queue - that is, on any environment ever started once - the declaration would fail
     * with a 406 and the service would not start. Recovering on the application side avoids
     * that problem entirely, and does not ask anybody to delete queues by hand before an
     * upgrade.
     */
    public static final String ERROR_EXCHANGE = "prenotazioni.eventi.errori";

    public static final String ROUTING_KEY_CANCELLATION_FAILED = "prenotazione.cancellata.fallita";

    public static final String CANCELLATION_ERROR_QUEUE = "notifiche.prenotazione-cancellata.errori";

    private EventTopology() {
    }
}
