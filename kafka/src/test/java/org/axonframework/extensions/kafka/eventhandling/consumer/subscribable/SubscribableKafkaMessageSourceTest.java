/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.kafka.eventhandling.consumer.subscribable;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.errors.BrokerNotAvailableException;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.extensions.kafka.eventhandling.consumer.AsyncFetcher;
import org.axonframework.extensions.kafka.eventhandling.consumer.ConsumerFactory;
import org.axonframework.extensions.kafka.eventhandling.consumer.Fetcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.awaitility.Awaitility.await;
import static org.axonframework.extensions.kafka.eventhandling.util.ConsumerConfigUtil.DEFAULT_GROUP_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SubscribableKafkaMessageSource}, asserting construction and utilization of the class.
 *
 * @author Steven van Beelen
 */
class SubscribableKafkaMessageSourceTest {

    private static final String TEST_TOPIC = "someTopic";
    private static final Registration NO_OP_FETCHER_REGISTRATION = () -> {
        // No-op
        return true;
    };
    private static final java.util.function.Consumer<List<? extends EventMessage<?>>> NO_OP_EVENT_PROCESSOR = eventMessages -> {
        // No-op
    };

    private static final Pattern TEST_PATTERN = Pattern.compile(TEST_TOPIC);

    private ConsumerFactory<String, String> consumerFactory;
    private Fetcher<String, String, EventMessage<?>> fetcher;

    private SubscribableKafkaMessageSource<String, String> testSubject;

    private Consumer<String, String> mockConsumer;


    private static Stream<Arguments> getSourceBuilders() {
        return Stream.of(
                Arguments.of(SubscribableKafkaMessageSource.<String, String>builder().topics(Collections.singletonList(TEST_TOPIC)),
                        (BiFunction<Integer, Consumer<String, String>, Void>) (consumerCount, mockConsumer) -> {
                            verify(mockConsumer, times(consumerCount)).subscribe(Collections.singletonList(TEST_TOPIC));
                            return null;
                        }
                ),
                Arguments.of(SubscribableKafkaMessageSource.<String, String>builder().topicPattern(TEST_PATTERN),
                        (BiFunction<Integer, Consumer<String, String>, Void>) (consumerCount, mockConsumer) -> {
                            verify(mockConsumer, times(consumerCount)).subscribe(TEST_PATTERN);
                            return null;
                        }
                )
        );
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        consumerFactory = mock(ConsumerFactory.class);
        mockConsumer = mock(Consumer.class);
        when(consumerFactory.createConsumer(DEFAULT_GROUP_ID)).thenReturn(mockConsumer);
        fetcher = mock(Fetcher.class);

        testSubject = SubscribableKafkaMessageSource.<String, String>builder()
                .topics(Collections.singletonList(TEST_TOPIC))
                .groupId(DEFAULT_GROUP_ID)
                .consumerFactory(consumerFactory)
                .fetcher(fetcher)
                .build();
    }

    @AfterEach
    void tearDown() {
        testSubject.close();
    }

    @Test
    void testBuildWithInvalidTopicsThrowsAxonConfigurationException() {
        SubscribableKafkaMessageSource.Builder<Object, Object> builder = SubscribableKafkaMessageSource.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.topics(null));
    }

    @Test
    void testBuildWithInvalidTopicThrowsAxonConfigurationException() {
        SubscribableKafkaMessageSource.Builder<Object, Object> builder = SubscribableKafkaMessageSource.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.addTopic(null));
    }

    @Test
    void testBuildWithInvalidGroupIdThrowsAxonConfigurationException() {
        SubscribableKafkaMessageSource.Builder<Object, Object> builder = SubscribableKafkaMessageSource.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.groupId(null));
    }

    @Test
    void testBuildWithInvalidConsumerFactoryThrowsAxonConfigurationException() {
        //noinspection unchecked,rawtypes
        SubscribableKafkaMessageSource.Builder<Object, Object> builder = SubscribableKafkaMessageSource.builder();
        assertThrows(
                AxonConfigurationException.class,
                () -> builder.consumerFactory((ConsumerFactory) null)
        );
    }

    @Test
    void testBuildWithInvalidFetcherThrowsAxonConfigurationException() {
        SubscribableKafkaMessageSource.Builder<Object, Object> builder = SubscribableKafkaMessageSource.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.fetcher(null));
    }

    @Test
    void testBuildWithInvalidMessageConverterThrowsAxonConfigurationException() {
        SubscribableKafkaMessageSource.Builder<Object, Object> builder = SubscribableKafkaMessageSource.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.messageConverter(null));
    }

    @Test
    void testBuildWithInvalidConsumerCountThrowsAxonConfigurationException() {
        SubscribableKafkaMessageSource.Builder<Object, Object> builder = SubscribableKafkaMessageSource.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.consumerCount(0));
    }

    @Test
    void testBuildingWhilstMissingRequiredFieldsShouldThrowAxonConfigurationException() {
        SubscribableKafkaMessageSource.Builder<Object, Object> builder = SubscribableKafkaMessageSource.builder();
        assertThrows(AxonConfigurationException.class, builder::build);
    }

    @Test
    void testAutoStartInitiatesProcessingOnFirstEventProcessor() {
        when(fetcher.poll(eq(mockConsumer), any(), any(), any())).thenReturn(NO_OP_FETCHER_REGISTRATION);

        SubscribableKafkaMessageSource<String, String> testSubject =
                SubscribableKafkaMessageSource.<String, String>builder()
                        .topics(Collections.singletonList(TEST_TOPIC))
                        .groupId(DEFAULT_GROUP_ID)
                        .consumerFactory(consumerFactory)
                        .fetcher(fetcher)
                        .autoStart()
                        .build();

        testSubject.subscribe(NO_OP_EVENT_PROCESSOR);

        verify(consumerFactory, times(1)).createConsumer(DEFAULT_GROUP_ID);
        verify(mockConsumer, times(1)).subscribe(Collections.singletonList(TEST_TOPIC));
        verify(fetcher).poll(eq(mockConsumer), any(), any(), any());
    }

    @Test
    void testCancelingSubscribedEventProcessorRunsConnectedCloseHandlerWhenAutoStartIsOn() {
        AtomicBoolean closedFetcherRegistration = new AtomicBoolean(false);
        when(fetcher.poll(eq(mockConsumer), any(), any(), any())).thenReturn(() -> {
            closedFetcherRegistration.set(true);
            return true;
        });

        SubscribableKafkaMessageSource<String, String> testSubject =
                SubscribableKafkaMessageSource.<String, String>builder()
                        .topics(Collections.singletonList(TEST_TOPIC))
                        .groupId(DEFAULT_GROUP_ID)
                        .consumerFactory(consumerFactory)
                        .fetcher(fetcher)
                        .autoStart() // This enables auto close
                        .build();

        Registration registration = testSubject.subscribe(NO_OP_EVENT_PROCESSOR);
        testSubject.start();

        verify(consumerFactory).createConsumer(DEFAULT_GROUP_ID);
        verify(mockConsumer).subscribe(Collections.singletonList(TEST_TOPIC));

        assertTrue(registration.cancel());
        assertTrue(closedFetcherRegistration.get());
    }

    @Test
    void testSubscribingTheSameInstanceTwiceDisregardsSecondInstanceOnStart() {
        when(fetcher.poll(eq(mockConsumer), any(), any(), any())).thenReturn(NO_OP_FETCHER_REGISTRATION);

        testSubject.subscribe(NO_OP_EVENT_PROCESSOR);
        testSubject.subscribe(NO_OP_EVENT_PROCESSOR);

        testSubject.start();

        verify(consumerFactory, times(1)).createConsumer(DEFAULT_GROUP_ID);
        verify(mockConsumer, times(1)).subscribe(Collections.singletonList(TEST_TOPIC));
        verify(fetcher, times(1)).poll(eq(mockConsumer), any(), any(), any());
    }

    @Test
    void testStartSubscribesConsumerToAllProvidedTopics() {
        when(fetcher.poll(eq(mockConsumer), any(), any(), any())).thenReturn(NO_OP_FETCHER_REGISTRATION);

        List<String> testTopics = new ArrayList<>();
        testTopics.add("topicOne");
        testTopics.add("topicTwo");

        SubscribableKafkaMessageSource<String, String> testSubject =
                SubscribableKafkaMessageSource.<String, String>builder()
                        .topics(testTopics)
                        .groupId(DEFAULT_GROUP_ID)
                        .consumerFactory(consumerFactory)
                        .fetcher(fetcher)
                        .build();

        testSubject.subscribe(NO_OP_EVENT_PROCESSOR);
        testSubject.start();

        verify(consumerFactory).createConsumer(DEFAULT_GROUP_ID);
        verify(mockConsumer).subscribe(testTopics);
        verify(fetcher).poll(eq(mockConsumer), any(), any(), any());
    }

    @ParameterizedTest()
    @MethodSource("getSourceBuilders")
    void testStartBuildsConsumersUpToConsumerCount(SubscribableKafkaMessageSource.Builder<String, String> builder, BiFunction<Integer, Consumer<String, String>, Void> verifyMock) {
        int expectedNumberOfConsumers = 2;

        when(fetcher.poll(eq(mockConsumer), any(), any(), any())).thenReturn(NO_OP_FETCHER_REGISTRATION);

        SubscribableKafkaMessageSource<String, String> testSubject =
                builder
                        .groupId(DEFAULT_GROUP_ID)
                        .consumerFactory(consumerFactory)
                        .fetcher(fetcher)
                        .consumerCount(expectedNumberOfConsumers)
                        .build();

        testSubject.subscribe(NO_OP_EVENT_PROCESSOR);
        testSubject.start();

        verify(consumerFactory, times(expectedNumberOfConsumers)).createConsumer(DEFAULT_GROUP_ID);
        verifyMock.apply(expectedNumberOfConsumers, mockConsumer);
        verify(fetcher, times(expectedNumberOfConsumers)).poll(eq(mockConsumer), any(), any(), any());
    }

    @ParameterizedTest()
    @MethodSource("getSourceBuilders")
    void testCloseRunsCloseHandlerPerConsumerCount(SubscribableKafkaMessageSource.Builder<String, String> builder, BiFunction<Integer, Consumer<String, String>, Void> verifyMock) {
        int expectedNumberOfConsumers = 2;

        AtomicBoolean closedEventProcessorOne = new AtomicBoolean(false);
        AtomicBoolean closedEventProcessorTwo = new AtomicBoolean(false);
        when(fetcher.poll(eq(mockConsumer), any(), any(), any()))
                .thenReturn(() -> {
                    closedEventProcessorOne.set(true);
                    return true;
                })
                .thenReturn(() -> {
                    closedEventProcessorTwo.set(true);
                    return true;
                });

        SubscribableKafkaMessageSource<String, String> testSubject =
                builder
                        .groupId(DEFAULT_GROUP_ID)
                        .consumerFactory(consumerFactory)
                        .fetcher(fetcher)
                        .autoStart()
                        .consumerCount(expectedNumberOfConsumers)
                        .build();

        testSubject.subscribe(NO_OP_EVENT_PROCESSOR);
        testSubject.close();

        verify(consumerFactory, times(expectedNumberOfConsumers)).createConsumer(DEFAULT_GROUP_ID);
        verifyMock.apply(expectedNumberOfConsumers, mockConsumer);
        verify(fetcher, times(expectedNumberOfConsumers)).poll(eq(mockConsumer), any(), any(), any());

        assertTrue(closedEventProcessorOne.get());
        assertTrue(closedEventProcessorTwo.get());
    }


    @ParameterizedTest()
    @MethodSource("getSourceBuilders")
    void restartingConsumerShouldNotCauseAMemoryLeakAndOnCloseNoRegistrationsShouldBeleft(SubscribableKafkaMessageSource.Builder<String, String> builder, BiFunction<Integer, Consumer<String, String>, Void> verifyMock) throws NoSuchFieldException, IllegalAccessException {
        fetcher = AsyncFetcher.<String, String, EventMessage<?>>builder()
                .executorService(newSingleThreadExecutor()).build();
        when(mockConsumer.poll(any(Duration.class))).thenThrow(new BrokerNotAvailableException("none available"));

        SubscribableKafkaMessageSource<String, String> testSubject =
                builder
                        .groupId(DEFAULT_GROUP_ID)
                        .consumerFactory(consumerFactory)
                        .fetcher(fetcher)
                        .autoStart()
                        .build();

        testSubject.subscribe(NO_OP_EVENT_PROCESSOR);

        await().atMost(Duration.ofSeconds(4)).untilAsserted(() -> verify(consumerFactory, atLeast(4)).createConsumer(DEFAULT_GROUP_ID));
        Field fetcherRegistrations = SubscribableKafkaMessageSource.class.getDeclaredField("fetcherRegistrations");

        fetcherRegistrations.setAccessible(true);

        Map<Integer, Registration> registrations = (Map<Integer, Registration>) fetcherRegistrations.get(testSubject);
        assertEquals(1, registrations.values().size());

        testSubject.close();

        registrations = (Map<Integer, Registration>) fetcherRegistrations.get(testSubject);
        assertTrue(registrations.isEmpty());
    }
}