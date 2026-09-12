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
package com.alibaba.cloud.ai.dataagent.util;

import java.util.Set;

import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import org.springframework.util.StringUtils;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.PYTHON_GENERATE_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.REPORT_GENERATOR_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_NODE;

/**
 * 计划校验器，被 Planner 生成端与 PlanExecutor 执行端共用，避免两处规则漂移。
 *
 * <p>
 * 校验通过返回 {@code null}，否则返回人类可读的错误信息。
 */
public final class PlanValidator {

	/**
	 * 受支持的执行节点集合。
	 */
	public static final Set<String> SUPPORTED_NODES = Set.of(SQL_GENERATE_NODE, PYTHON_GENERATE_NODE,
			REPORT_GENERATOR_NODE);

	private PlanValidator() {
	}

	/**
	 * 校验整个执行计划。
	 * @param plan 待校验的计划
	 * @return 校验通过返回 {@code null}，否则返回错误描述
	 */
	public static String validate(Plan plan) {
		if (plan == null || plan.getExecutionPlan() == null || plan.getExecutionPlan().isEmpty()) {
			return "Validation failed: The generated plan is empty or has no execution steps.";
		}

		for (ExecutionStep step : plan.getExecutionPlan()) {
			String stepError = validateStep(step);
			if (stepError != null) {
				return stepError;
			}
		}
		return null;
	}

	/**
	 * 校验单个执行步骤。
	 * @return 校验通过返回 {@code null}，否则返回错误描述
	 */
	private static String validateStep(ExecutionStep step) {
		if (step.getToolToUse() == null || !SUPPORTED_NODES.contains(step.getToolToUse())) {
			return "Validation failed: Plan contains an invalid tool name: '" + step.getToolToUse() + "' in step "
					+ step.getStep();
		}

		if (step.getToolParameters() == null) {
			return "Validation failed: Tool parameters are missing for step " + step.getStep();
		}

		switch (step.getToolToUse()) {
			case SQL_GENERATE_NODE:
				if (!StringUtils.hasText(step.getToolParameters().getInstruction())) {
					return "Validation failed: SQL generation node is missing description in step " + step.getStep();
				}
				break;
			case PYTHON_GENERATE_NODE:
				if (!StringUtils.hasText(step.getToolParameters().getInstruction())) {
					return "Validation failed: Python generation node is missing instruction in step " + step.getStep();
				}
				break;
			case REPORT_GENERATOR_NODE:
				if (!StringUtils.hasText(step.getToolParameters().getSummaryAndRecommendations())) {
					return "Validation failed: Report generation node is missing summary_and_recommendations in step "
							+ step.getStep();
				}
				break;
			default:
				// 前面已校验 toolToUse 属于受支持集合，不会走到这里
				break;
		}
		return null;
	}

}
