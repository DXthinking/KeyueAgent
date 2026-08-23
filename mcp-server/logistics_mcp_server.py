#!/usr/bin/env python3
"""Minimal JSON-RPC 2.0 MCP server using stdio for logistics lookups."""
import json
import sys


def result(request_id, payload):
    return {"jsonrpc": "2.0", "id": request_id, "result": payload}


def handle(request):
    method = request.get("method")
    request_id = request.get("id")
    if method == "initialize":
        return result(request_id, {"protocolVersion": "2024-11-05", "capabilities": {"tools": {}}, "serverInfo": {"name": "logistics-mcp", "version": "1.0.0"}})
    if method == "notifications/initialized":
        return None
    if method == "tools/list":
        return result(request_id, {"tools": [
            {"name": "queryExpressDeliveryTime", "description": "查询快递标准时效", "inputSchema": {"type": "object", "properties": {"carrier": {"type": "string"}, "originCity": {"type": "string"}, "destinationCity": {"type": "string"}}, "required": ["carrier", "originCity", "destinationCity"]}},
            {"name": "getCarrierInfo", "description": "查询快递公司信息", "inputSchema": {"type": "object", "properties": {"carrier": {"type": "string"}}, "required": ["carrier"]}}
        ]})
    if method == "tools/call":
        params = request.get("params", {})
        name = params.get("name")
        args = params.get("arguments", {})
        if name == "queryExpressDeliveryTime":
            text = f"{args.get('carrier', '快递')}从{args.get('originCity', '发货地')}到{args.get('destinationCity', '目的地')}标准时效为1-3天，超过3天可按物流异常流程处理。"
        elif name == "getCarrierInfo":
            text = f"{args.get('carrier', '该快递')}覆盖主要城市，支持普通件和生鲜/大件等不同服务类型（演示数据）。"
        else:
            text = "未找到该物流工具。"
        return result(request_id, {"content": [{"type": "text", "text": text}]})
    return result(request_id, {"error": {"code": -32601, "message": f"Method not found: {method}"}})


for line in sys.stdin:
    try:
        request = json.loads(line)
        response = handle(request)
        if response is not None:
            sys.stdout.write(json.dumps(response, ensure_ascii=False) + "\n")
            sys.stdout.flush()
    except Exception as exc:
        sys.stdout.write(json.dumps({"jsonrpc": "2.0", "id": None, "error": {"code": -32603, "message": str(exc)}}, ensure_ascii=False) + "\n")
        sys.stdout.flush()
