package com.prenotazioni.notifica.eventi;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import com.prenotazioni.eventi.TopologiaEventi;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Il lato "chi consuma" della topologia.
 *
 * La coda la dichiara questo servizio, perche' e' sua: chi pubblica non deve sapere chi
 * ascolta. L'exchange e' dichiarato da entrambi, ed e' corretto - dichiararlo e'
 * idempotente, e serve a poter avviare i due servizi in qualunque ordine senza che il primo
 * fallisca perche' l'altro non e' ancora passato.
 *
 * La coda e' DURABLE e i messaggi sono persistenti per default con questo converter: e'
 * l'unica ragione per cui questa coda esiste. Una coda non durevole perderebbe i messaggi
 * al riavvio del broker, cioe' proprio nel momento in cui servirebbe.
 */
@Configuration
public class ConfigurazioneCodaNotifiche {

    @Bean
    Queue codaCancellazioni() {
        return new Queue(TopologiaEventi.CODA_NOTIFICHE_CANCELLAZIONE, true);
    }

    @Bean
    TopicExchange exchangeEventi() {
        return new TopicExchange(TopologiaEventi.EXCHANGE, true, false);
    }

    @Bean
    Binding bindingCancellazioni(Queue codaCancellazioni, TopicExchange exchangeEventi) {
        return BindingBuilder.bind(codaCancellazioni)
                .to(exchangeEventi)
                .with(TopologiaEventi.ROUTING_KEY_CANCELLAZIONE);
    }

    @Bean
    MessageConverter convertitoreJson() {
        return new Jackson2JsonMessageConverter();
    }

    // ==================== recupero dei messaggi non trattabili ====================

    @Bean
    Queue codaErrori() {
        return new Queue(TopologiaEventi.CODA_ERRORI_CANCELLAZIONE, true);
    }

    @Bean
    DirectExchange exchangeErrori() {
        return new DirectExchange(TopologiaEventi.EXCHANGE_ERRORI, true, false);
    }

    @Bean
    Binding bindingErrori(Queue codaErrori, DirectExchange exchangeErrori) {
        return BindingBuilder.bind(codaErrori)
                .to(exchangeErrori)
                .with(TopologiaEventi.ROUTING_KEY_ERRORI);
    }

    /**
     * Dove va a finire un messaggio dopo che i tentativi si sono esauriti.
     *
     * Senza questo bean il comportamento predefinito dopo i tentativi sarebbe scartarlo e
     * basta: nessun ciclo infinito, ma anche nessuna traccia di cosa non e' riuscito.
     * RepublishMessageRecoverer lo ripubblica sulla coda degli errori insieme allo stack
     * trace del guasto, quindi resta li' da guardare e, se serve, da rimettere in circolo.
     *
     * Il messaggio viene poi confermato: e' questo che chiude il ciclo di riconsegna
     * infinito descritto nel reperto 04.
     */
    @Bean
    MessageRecoverer recuperoMessaggiFalliti(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate,
                TopologiaEventi.EXCHANGE_ERRORI,
                TopologiaEventi.ROUTING_KEY_ERRORI);
    }
}
