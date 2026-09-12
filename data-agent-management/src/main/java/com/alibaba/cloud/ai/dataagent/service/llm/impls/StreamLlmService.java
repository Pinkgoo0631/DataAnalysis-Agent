/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.llm.impls;

import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.ModelUserContext;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@AllArgsConstructor
public class StreamLlmService implements LlmService {

	private final AiModelRegistry registry;

	@Override
	public Flux<ChatResponse> call(String system, String user) {
		return Flux.deferContextual(context -> chatClient(context).prompt().system(system).user(user).stream().chatResponse());
	}

	@Override
	public Flux<ChatResponse> call(String system, String user, Class<?> outputType) {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(outputType)
			.maxRepeatAttempts(2)
			.build();
		return Flux.deferContextual(context -> Mono
			.fromCallable(() -> chatClient(context)
				.prompt()
				.system(system)
				.user(user)
				.advisors(advisor)
				.call()
				.chatResponse())
			.subscribeOn(Schedulers.boundedElastic())
			.flux());
	}

	@Override
	public Flux<ChatResponse> callSystem(String system) {
		return Flux.deferContextual(context -> chatClient(context).prompt().system(system).stream().chatResponse());
	}

	@Override
	public Flux<ChatResponse> callUser(String user) {
		return Flux.deferContextual(context -> chatClient(context).prompt().user(user).stream().chatResponse());
	}

	@Override
	public Flux<ChatResponse> callUser(String user, Class<?> outputType) {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(outputType)
			.maxRepeatAttempts(2)
			.build();
		return Flux.deferContextual(context -> Mono
			.fromCallable(() -> chatClient(context).prompt().user(user).advisors(advisor).call().chatResponse())
			.subscribeOn(Schedulers.boundedElastic())
			.flux());
	}

	private org.springframework.ai.chat.client.ChatClient chatClient(reactor.util.context.ContextView context) {
		return context.hasKey(ModelUserContext.USER_ID)
				? registry.getChatClient(context.get(ModelUserContext.USER_ID)) : registry.getChatClient();
	}

}
