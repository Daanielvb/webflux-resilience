//package com.reactive.config;
//
//import io.github.resilience4j.core.registry.EntryAddedEvent;
//import io.github.resilience4j.core.registry.EntryRemovedEvent;
//import io.github.resilience4j.core.registry.EntryReplacedEvent;
//import io.github.resilience4j.core.registry.RegistryEventConsumer;
//import io.github.resilience4j.retry.Retry;
//import lombok.extern.slf4j.Slf4j;
//import org.slf4j.LoggerFactory;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.logging.Logger;
//
//@Configuration
//@Slf4j
//public class ResilienceEventConfig {
//
//    @Bean
//    public RegistryEventConsumer<Retry> retryEventConsumer() {
//        return new RegistryEventConsumer<>() {
//            @Override
//            public void onEntryAddedEvent(EntryAddedEvent<Retry> entryAddedEvent) {
//                entryAddedEvent.getAddedEntry().getEventPublisher()
//                        .onRetry(e -> log.warn(
//                                "Retry attempt #{} for '{}' due to: {}",
//                                e.getNumberOfRetryAttempts(),
//                                e.getName(),
//                                e.getLastThrowable().getMessage()
//                        ))
//                        .onError(e -> log.error(
//                                "All {} attempts exhausted for '{}'",
//                                e.getNumberOfRetryAttempts(),
//                                e.getName()
//                        ))
//                        .onSuccess(e -> log.info(
//                                "Succeeded after {} attempt(s) for '{}'",
//                                e.getNumberOfRetryAttempts(),
//                                e.getName()
//                        ));
//            }
//
//            @Override
//            public void onEntryRemovedEvent(EntryRemovedEvent<Retry> entryRemoveEvent) {}
//
//            @Override
//            public void onEntryReplacedEvent(EntryReplacedEvent<Retry> entryReplacedEvent) {}
//        };
//    }
//}
