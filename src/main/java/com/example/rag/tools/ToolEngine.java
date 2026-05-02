package com.example.rag.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 手动工具执行引擎：从 @Tool 类提取规格 + 执行 ToolExecutionRequest
 */
public class ToolEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ToolSpecification> specifications = new ArrayList<>();
    private final Map<String, MethodInvoker> invokers = new LinkedHashMap<>();

    public ToolEngine(Object... toolObjects) {
        for (Object obj : toolObjects) {
            List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(obj);
            specifications.addAll(specs);
            for (Method method : obj.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    invokers.put(method.getName(), new MethodInvoker(obj, method));
                }
            }
        }
    }

    public List<ToolSpecification> getSpecifications() {
        return Collections.unmodifiableList(specifications);
    }

    public boolean hasTools() {
        return !specifications.isEmpty();
    }

    public String execute(ToolExecutionRequest request) {
        MethodInvoker invoker = invokers.get(request.name());
        if (invoker == null) {
            return "错误: 未知工具 '" + request.name() + "'";
        }
        try {
            return invoker.invoke(request.arguments());
        } catch (Exception e) {
            return "工具执行错误 (" + request.name() + "): " + e.getMessage();
        }
    }

    private static class MethodInvoker {
        private final Object instance;
        private final Method method;
        private final String[] paramNames;

        MethodInvoker(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
            method.setAccessible(true);

            java.lang.reflect.Parameter[] params = method.getParameters();
            this.paramNames = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                this.paramNames[i] = params[i].getName();
            }
        }

        String invoke(String argumentsJson) throws Exception {
            Object[] args = new Object[paramNames.length];
            if (paramNames.length > 0 && argumentsJson != null && !argumentsJson.isBlank()) {
                JsonNode node = MAPPER.readTree(argumentsJson);
                for (int i = 0; i < paramNames.length; i++) {
                    JsonNode val = node.get(paramNames[i]);
                    if (val != null && !val.isNull()) {
                        args[i] = MAPPER.treeToValue(val, method.getParameterTypes()[i]);
                    }
                }
            }
            Object result = method.invoke(instance, args);
            return result != null ? result.toString() : "null";
        }
    }
}
