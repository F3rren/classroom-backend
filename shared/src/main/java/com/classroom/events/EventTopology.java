package com.classroom.events;

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
    public static final String EXCHANGE = "classroom.events";

    /** The routing key of the cancellation event. */
    public static final String ROUTING_KEY_CANCELLATION = "booking.cancelled";

    /**
     * The notification service's queue. It is durable: without that, a broker restart would
     * lose the messages not yet consumed, which is exactly what this queue exists to prevent.
     */
    public static final String CANCELLATION_QUEUE = "notifications.booking-cancelled";

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
    public static final String ERROR_EXCHANGE = "classroom.events.errors";

    /**
     * notification-service's one error queue, shared by every listener in that service, not
     * just the cancellation one. Spring Boot wires in a MessageRecoverer bean only when
     * there is exactly one unique candidate in the context (ObjectProvider.getIfUnique());
     * a second MessageRecoverer bean makes that lookup ambiguous and BOTH listeners silently
     * fall back to dropping exhausted messages with no trace. So: one recoverer bean per
     * service, one shared error queue, regardless of how many event types that service
     * consumes. Naming it after "notification" rather than "cancellation" is deliberate,
     * now that it also catches a failed UserDeletedEvent.
     */
    public static final String ROUTING_KEY_NOTIFICATION_FAILED = "notification.failed";

    public static final String NOTIFICATION_ERROR_QUEUE = "notifications.errors";

    /** The routing key of the user-deletion event. */
    public static final String ROUTING_KEY_USER_DELETED = "user.deleted";

    /**
     * The two consumers' queues. Unlike the cancellation event, this one has two independent
     * consumers - booking-service and notification-service - each cleaning up its own table,
     * so each gets its own durable queue bound to the same routing key on the same exchange.
     */
    public static final String USER_DELETED_BOOKINGS_QUEUE = "bookings.user-deleted";
    public static final String USER_DELETED_NOTIFICATIONS_QUEUE = "notifications.user-deleted";

    /**
     * booking-service's error queue, for the one listener it has today. If it ever gains a
     * second, the same reasoning as NOTIFICATION_ERROR_QUEUE applies: merge onto one shared
     * queue and one recoverer, do not add a second MessageRecoverer bean.
     */
    public static final String ROUTING_KEY_USER_DELETED_BOOKINGS_FAILED = "user.deleted.bookings.failed";
    public static final String USER_DELETED_BOOKINGS_ERROR_QUEUE = "bookings.user-deleted.errors";

    private EventTopology() {
    }
}
