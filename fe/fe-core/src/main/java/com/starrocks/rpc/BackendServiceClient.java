// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/main/java/org/apache/doris/rpc/BackendServiceProxy.java

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.starrocks.rpc;

import com.baidu.jprotobuf.pbrpc.utils.TalkTimeoutController;
import com.google.common.base.Preconditions;
import com.starrocks.common.Config;
import com.starrocks.common.profile.Timer;
import com.starrocks.common.profile.Tracers;
import com.starrocks.proto.ExecuteCommandRequestPB;
import com.starrocks.proto.ExecuteCommandResultPB;
import com.starrocks.proto.PCancelPlanFragmentRequest;
import com.starrocks.proto.PCancelPlanFragmentResult;
import com.starrocks.proto.PCollectQueryStatisticsResult;
import com.starrocks.proto.PExecPlanFragmentResult;
import com.starrocks.proto.PFetchDataResult;
import com.starrocks.proto.PGetFileSchemaResult;
import com.starrocks.proto.PListFailPointResponse;
import com.starrocks.proto.PMVMaintenanceTaskResult;
import com.starrocks.proto.PPlanFragmentCancelReason;
import com.starrocks.proto.PProcessDictionaryCacheRequest;
import com.starrocks.proto.PProcessDictionaryCacheResult;
import com.starrocks.proto.PProxyRequest;
import com.starrocks.proto.PProxyResult;
import com.starrocks.proto.PPulsarProxyRequest;
import com.starrocks.proto.PPulsarProxyResult;
import com.starrocks.proto.PTriggerProfileReportResult;
import com.starrocks.proto.PUniqueId;
import com.starrocks.proto.PUpdateFailPointStatusRequest;
import com.starrocks.proto.PUpdateFailPointStatusResponse;
import com.starrocks.thrift.TExecPlanFragmentParams;
import com.starrocks.thrift.TMVMaintenanceTasks;
import com.starrocks.thrift.TNetworkAddress;
import com.starrocks.thrift.TStatusCode;
import com.starrocks.thrift.TUniqueId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TException;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Future;

public class BackendServiceClient {
    private static final Logger LOG = LogManager.getLogger(BackendServiceClient.class);

    private BackendServiceClient() {
    }

    public static BackendServiceClient getInstance() {
        return BackendServiceClient.SingletonHolder.INSTANCE;
    }

    /**
     * 提取 Thrift 对象的所有字段并格式化为标准 JSON
     */
    private String extractThriftKeyFields(TExecPlanFragmentParams params) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        
        try {
            // protocol_version (required)
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append("\"protocol_version\":").append(params.protocol_version != null 
                    ? params.protocol_version.getValue() : "null");
            
            // fragment (optional)
            if (params.fragment != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"fragment\":").append(thriftObjectToJson(params.fragment));
            }
            
            // desc_tbl (optional)
            if (params.desc_tbl != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"desc_tbl\":").append(thriftObjectToJson(params.desc_tbl));
            }
            
            // params (optional) - 提取关键字段
            if (params.params != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"params\":{");
                boolean paramsFirst = true;
                if (params.params.query_id != null) {
                    sb.append("\"query_id\":{\"hi\":").append(params.params.query_id.hi)
                            .append(",\"lo\":").append(params.params.query_id.lo).append("}");
                    paramsFirst = false;
                }
                if (params.params.fragment_instance_id != null) {
                    if (!paramsFirst) {
                        sb.append(",");
                    }
                    sb.append("\"fragment_instance_id\":{\"hi\":").append(params.params.fragment_instance_id.hi)
                            .append(",\"lo\":").append(params.params.fragment_instance_id.lo).append("}");
                    paramsFirst = false;
                }
                if (params.params.isSetSender_id()) {
                    if (!paramsFirst) {
                        sb.append(",");
                    }
                    sb.append("\"sender_id\":").append(params.params.sender_id);
                    paramsFirst = false;
                }
                if (params.params.isSetNum_senders()) {
                    if (!paramsFirst) {
                        sb.append(",");
                    }
                    sb.append("\"num_senders\":").append(params.params.num_senders);
                    paramsFirst = false;
                }
                if (params.params.isSetUse_vectorized()) {
                    if (!paramsFirst) {
                        sb.append(",");
                    }
                    sb.append("\"use_vectorized\":").append(params.params.use_vectorized);
                    paramsFirst = false;
                }
                if (params.params.isSetInstances_number()) {
                    if (!paramsFirst) {
                        sb.append(",");
                    }
                    sb.append("\"instances_number\":").append(params.params.instances_number);
                    paramsFirst = false;
                }
                if (params.params.isSetEnable_exchange_pass_through()) {
                    if (!paramsFirst) {
                        sb.append(",");
                    }
                    sb.append("\"enable_exchange_pass_through\":").append(params.params.enable_exchange_pass_through);
                    paramsFirst = false;
                }
                if (params.params.isSetEnable_exchange_perf()) {
                    if (!paramsFirst) {
                        sb.append(",");
                    }
                    sb.append("\"enable_exchange_perf\":").append(params.params.enable_exchange_perf);
                    paramsFirst = false;
                }
                if (params.params.isSetPipeline_sink_dop()) {
                    if (!paramsFirst) {
                        sb.append(",");
                    }
                    sb.append("\"pipeline_sink_dop\":").append(params.params.pipeline_sink_dop);
                    paramsFirst = false;
                }
                if (params.params.isSetReport_when_finish()) {
                    if (!paramsFirst) {
                        sb.append(",");
                    }
                    sb.append("\"report_when_finish\":").append(params.params.report_when_finish);
                }
                sb.append("}");
            }
            
            // coord (optional)
            if (params.coord != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"coord\":{\"hostname\":\"").append(escapeJsonString(params.coord.hostname))
                        .append("\",\"port\":").append(params.coord.port).append("}");
            }
            
            // backend_num (optional)
            if (params.isSetBackend_num()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"backend_num\":").append(params.backend_num);
            }
            
            // query_globals (optional)
            if (params.query_globals != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"query_globals\":").append(thriftObjectToJson(params.query_globals));
            }
            
            // query_options (optional)
            if (params.query_options != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"query_options\":").append(thriftObjectToJson(params.query_options));
            }
            
            // enable_profile (optional)
            if (params.isSetEnable_profile()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"enable_profile\":").append(params.enable_profile);
            }
            
            // resource_info (optional) - 使用简化表示
            if (params.resource_info != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"resource_info\":\"<TResourceInfo>\"");
            }
            
            // import_label (optional)
            if (params.isSetImport_label()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"import_label\":\"").append(escapeJsonString(params.import_label)).append("\"");
            }
            
            // db_name (optional)
            if (params.isSetDb_name()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"db_name\":\"").append(escapeJsonString(params.db_name)).append("\"");
            }
            
            // load_job_id (optional)
            if (params.isSetLoad_job_id()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"load_job_id\":").append(params.load_job_id);
            }
            
            // load_error_hub_info (optional) - 使用简化表示
            if (params.load_error_hub_info != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"load_error_hub_info\":\"<TLoadErrorHubInfo>\"");
            }
            
            // is_pipeline (optional)
            if (params.isSetIs_pipeline()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"is_pipeline\":").append(params.is_pipeline);
            }
            
            // pipeline_dop (optional)
            if (params.isSetPipeline_dop()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"pipeline_dop\":").append(params.pipeline_dop);
            }
            
            // per_scan_node_dop (optional)
            if (params.isSetPer_scan_node_dop() && params.per_scan_node_dop != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"per_scan_node_dop\":").append(mapToJson(params.per_scan_node_dop));
            }
            
            // workgroup (optional)
            if (params.workgroup != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"workgroup\":").append(thriftObjectToJson(params.workgroup));
            }
            
            // enable_resource_group (optional)
            if (params.isSetEnable_resource_group()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"enable_resource_group\":").append(params.enable_resource_group);
            }
            
            // func_version (optional)
            if (params.isSetFunc_version()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"func_version\":").append(params.func_version);
            }
            
            // enable_shared_scan (optional)
            if (params.isSetEnable_shared_scan()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"enable_shared_scan\":").append(params.enable_shared_scan);
            }
            
            // is_stream_pipeline (optional)
            if (params.isSetIs_stream_pipeline()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"is_stream_pipeline\":").append(params.is_stream_pipeline);
            }
            
            // adaptive_dop_param (optional) - 使用简化表示
            if (params.adaptive_dop_param != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"adaptive_dop_param\":\"<TAdaptiveDopParam>\"");
            }
            
            // group_execution_scan_dop (optional)
            if (params.isSetGroup_execution_scan_dop()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"group_execution_scan_dop\":").append(params.group_execution_scan_dop);
            }
        } catch (Exception e) {
            return "{\"error\":\"" + escapeJsonString(e.getMessage()) + "\"}";
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * 将 Thrift 对象转换为 JSON（使用反射获取所有字段）
     */
    private String thriftObjectToJson(Object obj) {
        return thriftObjectToJson(obj, new java.util.HashSet<>(), 0);
    }
    
    /**
     * 将 Thrift 对象转换为 JSON（使用反射获取所有字段），带循环检测
     */
    private String thriftObjectToJson(Object obj, java.util.Set<Integer> visited, int depth) {
        if (obj == null) {
            return "null";
        }
        // 检测循环引用 - 使用 System.identityHashCode 来检测对象引用
        int objId = System.identityHashCode(obj);
        if (visited.contains(objId)) {
            return "\"<circular_reference>\"";
        }
        visited.add(objId);
        try {
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (java.lang.reflect.Field field : fields) {
                // 跳过一些可能导致问题的字段
                String fieldName = field.getName();
                // 跳过 Thrift 内部字段、静态字段、final 字段等
                if (fieldName.startsWith("__") 
                        || fieldName.equals("__isset_bitfield")
                        || java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                try {
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    Object value = field.get(obj);
                    if (value != null) {
                        // 跳过基本类型包装类，直接处理
                        if (value instanceof String || value instanceof Number || value instanceof Boolean 
                                || value instanceof Enum || value instanceof Character) {
                            if (!first) {
                                sb.append(", ");
                            }
                            first = false;
                            sb.append("\"").append(fieldName).append("\":");
                            appendJsonValue(sb, value, visited, depth + 1);
                        } else if (value instanceof java.util.List || value instanceof java.util.Map 
                                || value instanceof byte[]) {
                            // 集合类型，需要递归处理
                            if (!first) {
                                sb.append(", ");
                            }
                            first = false;
                            sb.append("\"").append(fieldName).append("\":");
                            appendJsonValue(sb, value, visited, depth + 1);
                        } else {
                            // 复杂对象，检查是否已经访问过
                            int valueId = System.identityHashCode(value);
                            if (visited.contains(valueId)) {
                                // 循环引用，跳过
                                if (!first) {
                                    sb.append(", ");
                                }
                                first = false;
                                sb.append("\"").append(fieldName).append("\":\"<circular_reference>\"");
                            } else {
                                // 继续递归
                                if (!first) {
                                    sb.append(", ");
                                }
                                first = false;
                                sb.append("\"").append(fieldName).append("\":");
                                appendJsonValue(sb, value, visited, depth + 1);
                            }
                        }
                    }
                } catch (Exception e) {
                    // 跳过无法访问的字段
                    continue;
                }
            }
            sb.append("}");
            visited.remove(objId);
            return sb.toString();
        } catch (Exception e) {
            visited.remove(objId);
            return "\"<serialization_error:" + escapeJsonString(e.getMessage()) + ">\"";
        }
    }
    
    /**
     * 将 Map 转换为 JSON
     */
    private String mapToJson(java.util.Map<?, ?> map) {
        return mapToJson(map, new java.util.HashSet<Integer>(), 0);
    }
    
    /**
     * 将 Map 转换为 JSON，带循环检测
     */
    private String mapToJson(java.util.Map<?, ?> map, java.util.Set<Integer> visited, int depth) {
        if (map == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            appendJsonValue(sb, entry.getValue(), visited, depth + 1);
        }
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * 追加 JSON 值
     */
    private void appendJsonValue(StringBuilder sb, Object value) {
        appendJsonValue(sb, value, new java.util.HashSet<Integer>(), 0);
    }
    
    /**
     * 追加 JSON 值，带深度限制和循环检测
     */
    private void appendJsonValue(StringBuilder sb, Object value, java.util.Set<Integer> visited, int depth) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append("\"").append(escapeJsonString((String) value)).append("\"");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Enum) {
            sb.append("\"").append(escapeJsonString(value.toString())).append("\"");
        } else if (value instanceof java.util.List) {
            sb.append("[");
            java.util.List<?> list = (java.util.List<?>) value;
            boolean listFirst = true;
            for (Object item : list) {
                if (!listFirst) {
                    sb.append(", ");
                }
                listFirst = false;
                appendJsonValue(sb, item, visited, depth + 1);
            }
            sb.append("]");
        } else if (value instanceof java.util.Map) {
            sb.append(mapToJson((java.util.Map<?, ?>) value, visited, depth + 1));
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            if (bytes.length > 100) {
                sb.append("\"byte[").append(bytes.length).append("]\"");
            } else {
                sb.append("\"").append(java.util.Base64.getEncoder().encodeToString(bytes)).append("\"");
            }
        } else {
            // 嵌套对象，递归调用
            sb.append(thriftObjectToJson(value, visited, depth + 1));
        }
    }
    
    /**
     * 将 Protobuf 对象转换为 JSON 字符串，用于日志打印
     */
    private static String protobufToJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            // 使用反射获取所有字段并构建 JSON
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (java.lang.reflect.Field field : fields) {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                Object value = field.get(obj);
                if (value != null) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;
                    sb.append("\"").append(field.getName()).append("\":");
                    if (value instanceof byte[]) {
                        // 对于字节数组，转换为 base64 或显示长度
                        byte[] bytes = (byte[]) value;
                        if (bytes.length > 100) {
                            sb.append("\"byte[").append(bytes.length).append("]\"");
                        } else {
                            sb.append("\"").append(java.util.Base64.getEncoder().encodeToString(bytes)).append("\"");
                        }
                    } else if (value instanceof String) {
                        sb.append("\"").append(escapeJsonString((String) value)).append("\"");
                    } else if (value instanceof Number || value instanceof Boolean) {
                        sb.append(value);
                    } else if (value instanceof java.util.List) {
                        sb.append("[");
                        java.util.List<?> list = (java.util.List<?>) value;
                        boolean listFirst = true;
                        for (Object item : list) {
                            if (!listFirst) {
                                sb.append(", ");
                            }
                            listFirst = false;
                            if (item instanceof String) {
                                sb.append("\"").append(escapeJsonString((String) item)).append("\"");
                            } else {
                                sb.append(item != null ? item.toString() : "null");
                            }
                        }
                        sb.append("]");
                    } else {
                        // 嵌套对象，递归调用
                        sb.append(protobufToJson(value));
                    }
                }
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + escapeJsonString(e.getMessage()) + "\",\"fallback\":\""
                    + escapeJsonString(obj.toString()) + "\"}";
        }
    }
    
    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private static String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private Future<PExecPlanFragmentResult> sendPlanFragmentAsync(TNetworkAddress address, PExecPlanFragmentRequest pRequest)
            throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        String protocol = pRequest.attachmentProtocol != null ? pRequest.attachmentProtocol : "unknown";
        int requestSize = pRequest.serializedRequest != null ? pRequest.serializedRequest.length : 0;
        
        // 尝试反序列化请求并提取关键字段打印为 JSON
        String requestDetails = "null";
        try {
            if (pRequest.serializedRequest != null && pRequest.serializedRequest.length > 0) {
                TExecPlanFragmentParams params = new TExecPlanFragmentParams();
                pRequest.getRequest(params);
                // 提取关键字段并格式化为标准 JSON
                requestDetails = extractThriftKeyFields(params);
            }
        } catch (Exception e) {
            // 反序列化失败，使用 base64 预览
            requestDetails = pRequest.serializedRequest != null && pRequest.serializedRequest.length > 0
                    ? "{\"error\":\"" + escapeJsonString(e.getMessage()) + "\",\"base64_preview\":\""
                    + java.util.Base64.getEncoder().encodeToString(
                            java.util.Arrays.copyOf(pRequest.serializedRequest,
                                    Math.min(200, pRequest.serializedRequest.length))) + "\"}"
                    : "null";
        }
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] exec_plan_fragment START, request_uuid={}, address={}, protocol={}, request_size={}",
                requestUuid, addressStr, protocol, requestSize);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] exec_plan_fragment REQUEST, request_uuid={}, address={}, protocol={}, "
                        + "request_size={}, request_details={}",
                requestUuid, addressStr, protocol, requestSize, requestDetails);
        
        Tracers.count(Tracers.Module.SCHEDULER, "DeployDataSize", pRequest.serializedRequest.length);
        try (Timer ignored = Tracers.watchScope(Tracers.Module.SCHEDULER, "DeployAsyncSendTime")) {
            final PBackendService service = BrpcProxy.getBackendService(address);
            TalkTimeoutController.setTalkTimeout(Config.brpc_send_plan_fragment_timeout_ms);
            Future<PExecPlanFragmentResult> future = service.execPlanFragmentAsync(pRequest);
            
            // 包装 Future 以记录响应
            return new LoggingFuture<>(future, addressStr, "exec_plan_fragment", requestUuid);
        } catch (NoSuchElementException e) {
            try {
                // retry
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interruptedException) {
                    // do nothing
                }
                final PBackendService service = BrpcProxy.getBackendService(address);
                Future<PExecPlanFragmentResult> retryFuture = service.execPlanFragmentAsync(pRequest);
                return new LoggingFuture<>(retryFuture, addressStr, "exec_plan_fragment", requestUuid);
            } catch (NoSuchElementException noSuchElementException) {
                // 第一行：摘要日志（失败）
                LOG.warn("[FE->BE RPC] exec_plan_fragment END, request_uuid={}, address={}, status=FAILED, error=retry_failed",
                        requestUuid, addressStr);
                // 第二行：完整异常信息
                LOG.warn("[FE->BE RPC] exec_plan_fragment RESPONSE, request_uuid={}, address={}, exception={}",
                        requestUuid, addressStr, noSuchElementException.toString(), noSuchElementException);
                throw new RpcException(address.hostname, e.getMessage());
            }
        } catch (Throwable e) {
            // 第一行：摘要日志（失败）
            LOG.warn("[FE->BE RPC] exec_plan_fragment END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            // 第二行：完整异常信息
            LOG.warn("[FE->BE RPC] exec_plan_fragment RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PExecPlanFragmentResult> execPlanFragmentAsync(
            TNetworkAddress address, byte[] request, String protocol)
            throws TException, RpcException {
        final PExecPlanFragmentRequest pRequest = new PExecPlanFragmentRequest();
        pRequest.setAttachmentProtocol(protocol);
        pRequest.setRequest(request);
        return sendPlanFragmentAsync(address, pRequest);
    }

    public Future<PExecPlanFragmentResult> execPlanFragmentAsync(
            TNetworkAddress address, TExecPlanFragmentParams tRequest, String protocol)
            throws TException, RpcException {
        final PExecPlanFragmentRequest pRequest = new PExecPlanFragmentRequest();
        pRequest.setAttachmentProtocol(protocol);
        pRequest.setRequest(tRequest, protocol);
        return sendPlanFragmentAsync(address, pRequest);
    }

    public Future<PCancelPlanFragmentResult> cancelPlanFragmentAsync(
            TNetworkAddress address, TUniqueId queryId, TUniqueId finstId, PPlanFragmentCancelReason cancelReason,
            boolean isPipeline) throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        String queryIdStr = queryId != null ? queryId.toString() : "null";
        String finstIdStr = finstId != null ? finstId.toString() : "null";
        String reasonStr = cancelReason != null ? cancelReason.name() : "null";
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] cancel_plan_fragment START, request_uuid={}, address={}, query_id={}, "
                        + "finst_id={}, reason={}, is_pipeline={}",
                requestUuid, addressStr, queryIdStr, finstIdStr, reasonStr, isPipeline);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] cancel_plan_fragment REQUEST, request_uuid={}, address={}, query_id={}, "
                        + "finst_id={}, reason={}, is_pipeline={}",
                requestUuid, addressStr, queryIdStr, finstIdStr, reasonStr, isPipeline);
        
        final PCancelPlanFragmentRequest pRequest = new PCancelPlanFragmentRequest();
        PUniqueId uid = new PUniqueId();
        uid.hi = finstId.hi;
        uid.lo = finstId.lo;
        pRequest.finstId = uid;
        pRequest.cancelReason = cancelReason;
        pRequest.isPipeline = isPipeline;
        PUniqueId qid = new PUniqueId();
        qid.hi = queryId.hi;
        qid.lo = queryId.lo;
        pRequest.queryId = qid;
        try {
            final PBackendService service = BrpcProxy.getBackendService(address);
            Future<PCancelPlanFragmentResult> future = service.cancelPlanFragmentAsync(pRequest);
            return new LoggingFuture<>(future, addressStr, "cancel_plan_fragment", requestUuid);
        } catch (NoSuchElementException e) {
            // retry
            try {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interruptedException) {
                    // do nothing
                }
                final PBackendService service = BrpcProxy.getBackendService(address);
                return service.cancelPlanFragmentAsync(pRequest);
            } catch (NoSuchElementException noSuchElementException) {
                LOG.warn("[FE->BE RPC] cancel_plan_fragment END, request_uuid={}, address={}, status=FAILED, error=retry_failed",
                        requestUuid, addressStr);
                LOG.warn("[FE->BE RPC] cancel_plan_fragment RESPONSE, request_uuid={}, address={}, exception={}",
                        requestUuid, addressStr, noSuchElementException.toString(), noSuchElementException);
                throw new RpcException(address.hostname, e.getMessage());
            }
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] cancel_plan_fragment END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] cancel_plan_fragment RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PFetchDataResult> fetchDataAsync(TNetworkAddress address, PFetchDataRequest request) throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        String finstIdStr = request.finstId != null ? request.finstId.toString() : "null";
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] fetch_data START, request_uuid={}, address={}, finst_id={}",
                requestUuid, addressStr, finstIdStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] fetch_data REQUEST, request_uuid={}, address={}, finst_id={}, request={}",
                requestUuid, addressStr, finstIdStr, request.toString());
        
        try {
            PBackendService service = BrpcProxy.getBackendService(address);
            Future<PFetchDataResult> future = service.fetchDataAsync(request);
            return new LoggingFuture<>(future, addressStr, "fetch_data", requestUuid);
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] fetch_data END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] fetch_data RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PTriggerProfileReportResult> triggerProfileReportAsync(
            TNetworkAddress address, PTriggerProfileReportRequest request) throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        String queryIdStr = request.queryId != null ? request.queryId.toString() : "null";
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] trigger_profile_report START, request_uuid={}, address={}, query_id={}",
                requestUuid, addressStr, queryIdStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] trigger_profile_report REQUEST, request_uuid={}, address={}, query_id={}, request={}",
                requestUuid, addressStr, queryIdStr, request.toString());
        
        try {
            final PBackendService service = BrpcProxy.getBackendService(address);
            Future<PTriggerProfileReportResult> future = service.triggerProfileReport(request);
            return new LoggingFuture<>(future, addressStr, "trigger_profile_report", requestUuid);
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] trigger_profile_report END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] trigger_profile_report RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PCollectQueryStatisticsResult> collectQueryStatisticsAsync(
            TNetworkAddress address, PCollectQueryStatisticsRequest request) throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] collect_query_statistics START, request_uuid={}, address={}",
                requestUuid, addressStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] collect_query_statistics REQUEST, request_uuid={}, address={}, request={}",
                requestUuid, addressStr, request.toString());
        
        try {
            final PBackendService service = BrpcProxy.getBackendService(address);
            Future<PCollectQueryStatisticsResult> future = service.collectQueryStatistics(request);
            return new LoggingFuture<>(future, addressStr, "collect_query_statistics", requestUuid);
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] collect_query_statistics END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] collect_query_statistics RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PProxyResult> getInfo(TNetworkAddress address, PProxyRequest request) throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] get_info START, request_uuid={}, address={}",
                requestUuid, addressStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] get_info REQUEST, request_uuid={}, address={}, request={}",
                requestUuid, addressStr, request.toString());
        
        try {
            final PBackendService service = BrpcProxy.getBackendService(address);
            Future<PProxyResult> future = service.getInfo(request);
            return new LoggingFuture<>(future, addressStr, "get_info", requestUuid);
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] get_info END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] get_info RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PPulsarProxyResult> getPulsarInfo(
            TNetworkAddress address, PPulsarProxyRequest request) throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] get_pulsar_info START, request_uuid={}, address={}",
                requestUuid, addressStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] get_pulsar_info REQUEST, request_uuid={}, address={}, request={}",
                requestUuid, addressStr, request.toString());
        
        try {
            final PBackendService service = BrpcProxy.getBackendService(address);
            Future<PPulsarProxyResult> future = service.getPulsarInfo(request);
            return new LoggingFuture<>(future, addressStr, "get_pulsar_info", requestUuid);
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] get_pulsar_info END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] get_pulsar_info RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PGetFileSchemaResult> getFileSchema(
            TNetworkAddress address, PGetFileSchemaRequest request) throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] get_file_schema START, request_uuid={}, address={}",
                requestUuid, addressStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] get_file_schema REQUEST, request_uuid={}, address={}, request={}",
                requestUuid, addressStr, request.toString());
        
        try {
            final PBackendService service = BrpcProxy.getBackendService(address);
            Future<PGetFileSchemaResult> future = service.getFileSchema(request);
            return new LoggingFuture<>(future, addressStr, "get_file_schema", requestUuid);
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] get_file_schema END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] get_file_schema RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PMVMaintenanceTaskResult> submitMVMaintenanceTaskAsync(
            TNetworkAddress address, TMVMaintenanceTasks tRequest)
            throws TException, RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] submit_mv_maintenance_task START, request_uuid={}, address={}",
                requestUuid, addressStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] submit_mv_maintenance_task REQUEST, request_uuid={}, address={}, request={}",
                requestUuid, addressStr, tRequest != null ? tRequest.toString() : "null");
        
        PMVMaintenanceTaskRequest pRequest = new PMVMaintenanceTaskRequest();
        pRequest.setRequest(tRequest);

        Future<PMVMaintenanceTaskResult> resultFuture = null;
        for (int i = 1; i <= Config.max_query_retry_time && resultFuture == null; ++i) {
            try {
                final PBackendService service = BrpcProxy.getBackendService(address);
                resultFuture = service.submitMVMaintenanceTaskAsync(pRequest);
            } catch (NoSuchElementException e) {
                // Retry `RETRY_TIMES`, when NoSuchElementException occurs.
                if (i >= Config.max_query_retry_time) {
                    LOG.warn("[FE->BE RPC] submit_mv_maintenance_task END, request_uuid={}, address={}, "
                                    + "status=FAILED, error=retry_failed",
                            requestUuid, addressStr);
                    LOG.warn("[FE->BE RPC] submit_mv_maintenance_task RESPONSE, request_uuid={}, address={}, exception={}",
                            requestUuid, addressStr, e.toString(), e);
                    throw new RpcException(address.hostname, e.getMessage());
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            } catch (Throwable e) {
                LOG.warn("[FE->BE RPC] submit_mv_maintenance_task END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                        requestUuid, addressStr, e.getMessage());
                LOG.warn("[FE->BE RPC] submit_mv_maintenance_task RESPONSE, request_uuid={}, address={}, exception={}",
                        requestUuid, addressStr, e.toString(), e);
                throw new RpcException(address.hostname, e.getMessage());
            }
        }

        Preconditions.checkState(resultFuture != null);
        return new LoggingFuture<>(resultFuture, addressStr, "submit_mv_maintenance_task", requestUuid);
    }

    public Future<ExecuteCommandResultPB> executeCommand(TNetworkAddress address, ExecuteCommandRequestPB request)
            throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        String commandStr = request.command != null ? request.command : "null";
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] execute_command START, request_uuid={}, address={}, command={}",
                requestUuid, addressStr, commandStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] execute_command REQUEST, request_uuid={}, address={}, command={}, request={}",
                requestUuid, addressStr, commandStr, request.toString());
        
        try {
            final PBackendService service = BrpcProxy.getBackendService(address);
            Future<ExecuteCommandResultPB> future = service.executeCommandAsync(request);
            return new LoggingFuture<>(future, addressStr, "execute_command", requestUuid);
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] execute_command END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] execute_command RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PUpdateFailPointStatusResponse> updateFailPointStatusAsync(
            TNetworkAddress address, PUpdateFailPointStatusRequest request) throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] update_failpoint_status START, request_uuid={}, address={}",
                requestUuid, addressStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] update_failpoint_status REQUEST, request_uuid={}, address={}, request={}",
                requestUuid, addressStr, request.toString());
        
        try {
            final PBackendService service = BrpcProxy.getBackendService(address);
            Future<PUpdateFailPointStatusResponse> future = service.updateFailPointStatusAsync(request);
            return new LoggingFuture<>(future, addressStr, "update_failpoint_status", requestUuid);
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] update_failpoint_status END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] update_failpoint_status RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PListFailPointResponse> listFailPointAsync(
            TNetworkAddress address, PListFailPointRequest request) throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] list_failpoint START, request_uuid={}, address={}",
                requestUuid, addressStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] list_failpoint REQUEST, request_uuid={}, address={}, request={}",
                requestUuid, addressStr, request.toString());
        
        try {
            final PBackendService service = BrpcProxy.getBackendService(address);
            Future<PListFailPointResponse> future = service.listFailPointAsync(request);
            return new LoggingFuture<>(future, addressStr, "list_failpoint", requestUuid);
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] list_failpoint END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] list_failpoint RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    public Future<PProcessDictionaryCacheResult> processDictionaryCache(
            TNetworkAddress address, PProcessDictionaryCacheRequest request) throws RpcException {
        String requestUuid = UUID.randomUUID().toString();
        String addressStr = address.getHostname() + ":" + address.getPort();
        
        // 第一行：摘要日志（调用前）
        LOG.info("[FE->BE RPC] process_dictionary_cache START, request_uuid={}, address={}",
                requestUuid, addressStr);
        
        // 第二行：完整请求参数
        LOG.info("[FE->BE RPC] process_dictionary_cache REQUEST, request_uuid={}, address={}, request={}",
                requestUuid, addressStr, request.toString());
        
        try {
            final PBackendService service = BrpcProxy.getBackendService(address);
            Future<PProcessDictionaryCacheResult> future = service.processDictionaryCache(request);
            return new LoggingFuture<>(future, addressStr, "process_dictionary_cache", requestUuid);
        } catch (Throwable e) {
            LOG.warn("[FE->BE RPC] process_dictionary_cache END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                    requestUuid, addressStr, e.getMessage());
            LOG.warn("[FE->BE RPC] process_dictionary_cache RESPONSE, request_uuid={}, address={}, exception={}",
                    requestUuid, addressStr, e.toString(), e);
            throw new RpcException(address.hostname, e.getMessage());
        }
    }

    /**
     * 包装 Future 以记录 RPC 响应日志
     */
    private static class LoggingFuture<T> implements Future<T> {
        private final Future<T> delegate;
        private final String address;
        private final String methodName;
        private final String requestUuid;
        private final Logger logger = LogManager.getLogger(LoggingFuture.class);

        LoggingFuture(Future<T> delegate, String address, String methodName, String requestUuid) {
            this.delegate = delegate;
            this.address = address;
            this.methodName = methodName;
            this.requestUuid = requestUuid;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public T get() throws java.util.concurrent.ExecutionException, InterruptedException {
            try {
                T result = delegate.get();
                logResponse(result, null);
                return result;
            } catch (Exception e) {
                logResponse(null, e);
                throw e;
            }
        }

        @Override
        public T get(long timeout, java.util.concurrent.TimeUnit unit)
                throws java.util.concurrent.ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
            try {
                T result = delegate.get(timeout, unit);
                logResponse(result, null);
                return result;
            } catch (Exception e) {
                logResponse(null, e);
                throw e;
            }
        }

        private void logResponse(T result, Exception exception) {
            if (exception != null) {
                // 第一行：摘要日志（失败）
                logger.warn("[FE->BE RPC] {} END, request_uuid={}, address={}, status=EXCEPTION, error={}",
                        methodName, requestUuid, address, exception.getMessage());
                // 第二行：完整异常信息
                logger.warn("[FE->BE RPC] {} RESPONSE, request_uuid={}, address={}, exception={}",
                        methodName, requestUuid, address, exception.toString(), exception);
            } else if (result != null) {
                // 第一行：摘要日志（成功）
                String status = "SUCCESS";
                String responseDetails = "null";
                if (result instanceof PExecPlanFragmentResult) {
                    PExecPlanFragmentResult r = (PExecPlanFragmentResult) result;
                    if (r.status != null) {
                        // StatusPB.statusCode is int32, TStatusCode.OK = 0
                        boolean isSuccess = (r.status.statusCode == TStatusCode.OK.getValue());
                        status = isSuccess ? "SUCCESS" : "FAILED";
                    }
                    // 使用 JSON 格式打印所有字段
                    responseDetails = protobufToJson(r);
                } else {
                    // 其他类型的响应，使用 JSON 格式打印
                    responseDetails = protobufToJson(result);
                }
                logger.info("[FE->BE RPC] {} END, request_uuid={}, address={}, status={}",
                        methodName, requestUuid, address, status);
                
                // 第二行：完整响应（打印所有字段）
                logger.info("[FE->BE RPC] {} RESPONSE, request_uuid={}, address={}, result={}",
                        methodName, requestUuid, address, responseDetails);
            }
        }
    }

    private static class SingletonHolder {
        private static final BackendServiceClient INSTANCE = new BackendServiceClient();
    }
}
