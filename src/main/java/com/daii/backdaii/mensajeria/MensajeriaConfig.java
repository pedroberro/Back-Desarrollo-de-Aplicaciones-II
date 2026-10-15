package com.daii.backdaii.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker topology, declared by the app on startup (RabbitAdmin creates what is missing).
 * Only active with app.mensajeria.habilitada=true: without a broker, bookings stay PENDIENTE
 * and an administrator confirms them, as before.
 */
@Configuration
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
public class MensajeriaConfig {

    @Bean
    DirectExchange exchangeReservas() {
        return new DirectExchange(Colas.EXCHANGE_RESERVAS);
    }

    @Bean
    DirectExchange exchangeDlx() {
        return new DirectExchange(Colas.EXCHANGE_DLX);
    }

    /** Durable, and rejected messages go to the dead-letter exchange instead of being lost. */
    @Bean
    Queue colaCobros() {
        return QueueBuilder.durable(Colas.COBROS)
                .deadLetterExchange(Colas.EXCHANGE_DLX)
                .deadLetterRoutingKey(Colas.COBROS_DLQ)
                .build();
    }

    @Bean
    Queue colaCobrosDlq() {
        return QueueBuilder.durable(Colas.COBROS_DLQ).build();
    }

    @Bean
    Binding cobrosAlExchange() {
        return BindingBuilder.bind(colaCobros()).to(exchangeReservas()).with(Colas.COBRO_SOLICITADO);
    }

    @Bean
    Binding dlqAlExchange() {
        return BindingBuilder.bind(colaCobrosDlq()).to(exchangeDlx()).with(Colas.COBROS_DLQ);
    }

    /** JSON on the wire (readable in the RabbitMQ console); Boot wires it into the template and the listeners. */
    @Bean
    MessageConverter conversorJson(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
