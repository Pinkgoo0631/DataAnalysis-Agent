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
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.IS_RERANK;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.QUERY_ENHANCE_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.RERANK_NODE;

/**
 * 证据召回之后的条件分派器：根据全局状态中的 IS_RERANK 决定是否进入精排节点。
 *
 * <p>
 * IS_RERANK 为 true 时进入 {@code RERANK_NODE} 节点精排；否则直接进入
 * {@code QUERY_ENHANCE_NODE}。
 * </p>
 */
@Slf4j
public class RerankDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) throws Exception {
		Boolean isRerank = StateUtil.getObjectValue(state, IS_RERANK, Boolean.class, Boolean.FALSE);
		if (Boolean.TRUE.equals(isRerank)) {
			log.info("Rerank enabled, proceeding to RerankNode");
			return RERANK_NODE;
		}

		log.info("Rerank disabled, proceeding to QueryEnhanceNode directly");
		return QUERY_ENHANCE_NODE;
	}

}
