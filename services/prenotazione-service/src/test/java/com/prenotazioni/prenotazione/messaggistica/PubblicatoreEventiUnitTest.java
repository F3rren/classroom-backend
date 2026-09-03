package com.prenotazioni.prenotazione.messaggistica;

import com.prenotazioni.config.CorrelazioneRichiesta;
import com.prenotazioni.eventi.PrenotazioneCancellataEvento;
import com.prenotazioni.eventi.TopologiaEventi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * La pubblicazione dell'evento di cancellazione.
 *
 * Il test centrale e' quello sull'intestazione: e' cio' che permette di leggere in fila la
 * richiesta HTTP che ha annullato la prenotazione e la notifica creata dopo, su un altro
 * servizio e un altro thread. Senza, la correlazione si ferma al confine del servizio -
 * che e' esattamente il punto in cui inizia a servire.
 */
class PubblicatoreEventiUnitTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final PubblicatoreEventi pubblicatore = new PubblicatoreEventi(rabbitTemplate);

    private final PrenotazioneCancellataEvento evento = new PrenotazioneCancellataEvento(
            7L, 42L, "Aula 1", "Admin", "2026-09-03", "09:00", "11:00", "manutenzione");

    @AfterEach
    void pulisci() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** Applica al messaggio il post-processore che il pubblicatore ha passato. */
    private MessageProperties intestazioniProdotte() {
        ArgumentCaptor<MessagePostProcessor> processore = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq(TopologiaEventi.EXCHANGE),
                eq(TopologiaEventi.ROUTING_KEY_CANCELLAZIONE),
                eq(evento),
                processore.capture());

        Message messaggio = new Message(new byte[0], new MessageProperties());
        return processore.getValue().postProcessMessage(messaggio).getMessageProperties();
    }

    @Test
    void portaLIdentificativoDellaRichiestaCheHaCausatoLEvento() {
        MockHttpServletRequest richiesta = new MockHttpServletRequest();
        richiesta.setAttribute(CorrelazioneRichiesta.ATTRIBUTO, "REQ_DALGATEWAY");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(richiesta));

        pubblicatore.pubblicaCancellazione(evento);

        assertThat((String) intestazioniProdotte().getHeader(CorrelazioneRichiesta.INTESTAZIONE))
                .isEqualTo("REQ_DALGATEWAY");
    }

    @Test
    void portaComunqueUnIdentificativoFuoriDaUnaRichiesta() {
        // Un evento puo' nascere anche fuori da una richiesta HTTP. Meglio un
        // identificativo scollegato che nessuno: senza, la riga di log del consumatore
        // resterebbe senza chiave e non si potrebbe nemmeno raggrupparla con se stessa.
        pubblicatore.pubblicaCancellazione(evento);

        assertThat((String) intestazioniProdotte().getHeader(CorrelazioneRichiesta.INTESTAZIONE))
                .isNotBlank();
    }

    @Test
    void unBrokerIrraggiungibileNonFaFallireLaCancellazione() {
        // La cancellazione della prenotazione e' gia' avvenuta quando si arriva qui: far
        // fallire tutto perche' la notifica non parte sarebbe peggio del danno.
        doThrow(new AmqpException("broker giu'"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class),
                        any(Object.class), any(MessagePostProcessor.class));

        assertThat(pubblicatore.pubblicaCancellazione(evento)).isFalse();
    }
}
