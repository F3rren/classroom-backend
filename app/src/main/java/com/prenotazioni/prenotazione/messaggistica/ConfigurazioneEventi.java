package com.prenotazioni.prenotazione.messaggistica;

import com.prenotazioni.eventi.TopologiaEventi;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Il lato "chi pubblica" della topologia.
 *
 * Dichiara solo l'exchange: la coda appartiene a chi consuma, ed e' notifica-service a
 * crearla. E' voluto - se il servizio prenotazioni dichiarasse anche la coda, saprebbe chi
 * sta ascoltando, e l'accoppiamento che la coda serve a togliere rientrerebbe dalla finestra.
 * Un exchange senza code collegate scarta i messaggi, il che e' il comportamento giusto:
 * pubblicare non richiede che qualcuno sia interessato.
 *
 * Il converter e' JSON e non la serializzazione Java di default: i messaggi devono restare
 * leggibili nella console del broker e non devono legare le due parti alla stessa classe
 * compilata. Entrambi i servizi conoscono comunque il record condiviso in shared.
 */
@Configuration
public class ConfigurazioneEventi {

    @Bean
    TopicExchange exchangeEventi() {
        // durable: sopravvive al riavvio del broker, come la coda dall'altra parte.
        return new TopicExchange(TopologiaEventi.EXCHANGE, true, false);
    }

    @Bean
    MessageConverter convertitoreJson() {
        return new Jackson2JsonMessageConverter();
    }
}
