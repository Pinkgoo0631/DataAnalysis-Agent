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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.PYTHON_GENERATE_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.REPORT_GENERATOR_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_NODE;

import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;

import java.util.List;
import java.util.Set;

/**
 * 校验 Planner 生成的执行计划是否合法，并向 Planner 返回可读的修复反馈。
 *
 * <p>
 * 仅负责静态校验（结构、节点白名单、参数完整性）；真正的路由与执行由
 * {@code PlanExecutorNode} 完成。
 *
 * @author zhangshenghang
 */
public final class PlanValidator {

	/** Planner 在 execution_plan 中允许选用的执行节点白名单 */
	public static final Set<String> SUPPORTED_NODES = Set.of(SQL_GENERATE_NODE, PYTHON_GENERATE_NODE,
			REPORT_GENERATOR_NODE);

	private PlanValidator() {
	}

	/**
	 * 校验执行计划。计划合法时返回 {@code null}，否则返回描述问题的错误信息，该信息会
	 * 作为修复反馈回传给 Planner。
	 */
	public static String validate(Plan plan) {
		if (plan == null) {
			return "执行计划不能为空";
		}
		List<ExecutionStep> steps = plan.getExecutionPlan();
		if (steps == null || steps.isEmpty()) {
			return "执行计划为空：至少需要包含一个执行步骤";
		}
		for (int i = 0; i < steps.size(); i++) {
			ExecutionStep step = steps.get(i);
			int stepNo = i + 1;
			String prefix = "第 " + stepNo + " 步";
			if (step == null) {
				return prefix + "为空";
			}
			if (step.getStep() != stepNo) {
				return prefix + "步骤号不正确：期望 " + stepNo + "，实际为 " + step.getStep()
						+ "。execution_plan 的步骤号必须从 1 开始、按顺序连续递增";
			}
			String tool = step.getToolToUse();
			if (tool == null || tool.isBlank()) {
				return prefix + "缺少 tool_to_use";
			}
			if (!SUPPORTED_NODES.contains(tool)) {
				return prefix + "使用了不支持的节点 tool_to_use=" + tool + "，仅允许使用：" + SUPPORTED_NODES;
			}
			ExecutionStep.ToolParameters params = step.getToolParameters();
			if (params == null) {
				return prefix + "缺少 tool_parameters";
			}
			if (SQL_GENERATE_NODE.equals(tool) && isBlank(params.getInstruction())) {
				return prefix + "使用 " + SQL_GENERATE_NODE + " 时必须在 tool_parameters 中提供非空 instruction";
			}
			if (PYTHON_GENERATE_NODE.equals(tool) && isBlank(params.getInstruction())) {
				return prefix + "使用 " + PYTHON_GENERATE_NODE + " 时必须在 tool_parameters 中提供非空 instruction";
			}
			if (REPORT_GENERATOR_NODE.equals(tool) && isBlank(params.getSummaryAndRecommendations())) {
				return prefix + "使用 " + REPORT_GENERATOR_NODE
						+ " 时必须在 tool_parameters 中提供非空 summary_and_recommendations";
			}
		}
		return null;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
