/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.DisabledOnOs;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Flaky on Github CI")
@DisabledOnOs(architectures = { "s390x" },
              disabledReason = "This test does not run reliably on s390x (see CAMEL-21438)")
public class MulticastParallelStreamingTimeoutTest extends ContextTestSupport {

    @Test
    public void testMulticastParallelStreamingTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // A will timeout so we only get B and C (C is faster than B)
        mock.expectedBodiesReceived("CB");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").multicast(new AggregationStrategy() {
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        if (oldExchange == null) {
                            return newExchange;
                        }

                        String body = oldExchange.getIn().getBody(String.class);
                        oldExchange.getIn().setBody(body + newExchange.getIn().getBody(String.class));
                        return oldExchange;
                    }
                }).parallelProcessing().streaming().timeout(2000).to("direct:a", "direct:b", "direct:c")
                        // use end to indicate end of multicast route
                        .end().to("mock:result");

                from("direct:a").delay(3000).setBody(constant("A"));

                from("direct:b").delay(500).setBody(constant("B"));

                from("direct:c").setBody(constant("C"));
            }
        };
    }
}
