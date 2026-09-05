package com.prenotazioni.events;

/**
 * I nomi con cui i servizi si trovano sul broker.
 *
 * Sono qui e non duplicati nei due application.properties per la stessa ragione per cui
 * l'evento e' un record condiviso: un nome di coda scritto due volte e' un nome che puo'
 * divergere, e il modo in cui si manifesterebbe e' il peggiore possibile - nessun errore,
 * il messaggio semplicemente non arriva mai a destinazione.
 *
 * Solo costanti: shared non dipende da Spring AMQP. Ogni servizio dichiara i propri bean
 * (l'exchange chi pubblica, la coda e il binding chi consuma) usando questi nomi.
 */
public final class EventTopology {

    /** Exchange di tipo topic su cui viaggiano gli eventi delle prenotazioni. */
    public static final String EXCHANGE = "prenotazioni.eventi";

    /** Chiave di routing dell'evento di cancellazione. */
    public static final String ROUTING_KEY_CANCELLATION = "prenotazione.cancellata";

    /**
     * Coda del servizio notifiche. E' durevole: senza, un riavvio del broker perderebbe i
     * messaggi non ancora consumati, che e' esattamente cio' che questa coda esiste per
     * evitare.
     */
    public static final String CANCELLATION_QUEUE = "notifiche.prenotazione-cancellata";

    /**
     * Dove finisce un messaggio che non si e' riusciti a trattare.
     *
     * Senza, il comportamento predefinito di Spring AMQP rimette in coda un messaggio il
     * cui ascoltatore solleva un'eccezione: se il salvataggio fallisce - database
     * irraggiungibile, vincolo violato - quel messaggio rientra e riparte all'infinito,
     * bruciando risorse e coprendo tutto il resto nei log.
     *
     * Sono nomi NUOVI di proposito. Il modo canonico sarebbe dichiarare la coda esistente
     * con x-dead-letter-exchange, ma RabbitMQ rifiuta di ridichiarare una coda durabile
     * con argomenti diversi da quelli con cui e' nata: su un broker che ha gia' la coda -
     * cioe' su qualunque ambiente gia' avviato una volta - la dichiarazione fallirebbe con
     * un 406 e il servizio non partirebbe. Recuperare dal lato applicativo evita del tutto
     * quel problema, e non chiede di cancellare code a mano prima di un aggiornamento.
     */
    public static final String EXCHANGE_ERRORI = "prenotazioni.eventi.errori";

    public static final String ROUTING_KEY_ERRORI = "prenotazione.cancellata.fallita";

    public static final String CANCELLATION_ERROR_QUEUE = "notifiche.prenotazione-cancellata.errori";

    private EventTopology() {
    }
}
