package com.prenotazioni.eventi;

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
public final class TopologiaEventi {

    /** Exchange di tipo topic su cui viaggiano gli eventi delle prenotazioni. */
    public static final String EXCHANGE = "prenotazioni.eventi";

    /** Chiave di routing dell'evento di cancellazione. */
    public static final String ROUTING_KEY_CANCELLAZIONE = "prenotazione.cancellata";

    /**
     * Coda del servizio notifiche. E' durevole: senza, un riavvio del broker perderebbe i
     * messaggi non ancora consumati, che e' esattamente cio' che questa coda esiste per
     * evitare.
     */
    public static final String CODA_NOTIFICHE_CANCELLAZIONE = "notifiche.prenotazione-cancellata";

    private TopologiaEventi() {
    }
}
