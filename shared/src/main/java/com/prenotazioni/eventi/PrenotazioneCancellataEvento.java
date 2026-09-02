package com.prenotazioni.eventi;

/**
 * Una prenotazione e' stata cancellata da un amministratore.
 *
 * E' il contratto fra chi pubblica (il servizio prenotazioni) e chi consuma (il servizio
 * notifiche). Sta in shared di proposito: cosi' il compilatore garantisce che le due parti
 * parlino della stessa cosa, invece di affidarsi a due mappe di stringhe che possono
 * divergere senza che nulla lo segnali.
 *
 * I campi sono gia' formattati per essere mostrati (date e ore come stringhe) perche' la
 * notifica e' un messaggio da leggere, non un dato su cui calcolare: chi la riceve non deve
 * riformattare nulla, e il formato resta deciso da chi conosce il fuso della richiesta.
 *
 * E' un record, quindi immutabile: un messaggio che viaggia fra servizi non ha ragione di
 * poter essere modificato dopo essere stato costruito.
 *
 * COMPATIBILITA': una volta che dei messaggi sono in coda, questo tipo non puo' cambiare
 * liberamente. Aggiungere un campo va bene (chi consuma la versione vecchia lo ignora),
 * rimuoverne o rinominarne uno no: rompe i messaggi gia' pubblicati e non ancora letti.
 */
public record PrenotazioneCancellataEvento(
        Long utenteId,
        Long prenotazioneId,
        String nomeStanza,
        String adminNome,
        String dataPrenotazione,
        String oraInizio,
        String oraFine,
        String motivo) {
}
