import json
import re
from ipaddress import IPv6Address, ip_address


def require_secret_environment(environ, name):
    value = environ.get(name)
    if not isinstance(value, str) or not value.strip():
        raise RuntimeError(f"{name} is not set; deployment stopped before SSH")
    return value


def validate_agent_probe_record(record, max_model_rounds, max_tool_calls):
    if not isinstance(record, dict):
        raise ValueError("Agent probe record is not an object")

    def required_text(name):
        value = record.get(name)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"Agent probe field {name} is missing")
        return value.strip()

    def positive_int(name):
        value = record.get(name)
        if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
            raise ValueError(f"Agent probe field {name} must be positive")
        return value

    if record.get("status") != "completed":
        raise ValueError("Agent probe did not complete")
    for field in (
        "provider", "model", "finish_reason", "internal_request_id", "provider_request_id"
    ):
        required_text(field)

    prompt_tokens = positive_int("prompt_tokens")
    completion_tokens = positive_int("completion_tokens")
    total_tokens = positive_int("total_tokens")
    if total_tokens != prompt_tokens + completion_tokens:
        raise ValueError("Agent probe token totals are inconsistent")
    positive_int("latency_ms")

    model_rounds = positive_int("model_rounds")
    provider_calls = positive_int("provider_call_count")
    tool_calls = positive_int("tool_call_count")
    completed_tools = positive_int("completed_tool_count")
    citations = positive_int("citation_count")
    if model_rounds > max_model_rounds or provider_calls != model_rounds:
        raise ValueError("Agent probe model-round audit is inconsistent")
    if tool_calls > max_tool_calls or completed_tools != tool_calls:
        raise ValueError("Agent probe tool-call audit is inconsistent")
    if record.get("unknown_citation_count") != 0 or citations < 1:
        raise ValueError("Agent probe citations are outside the run evidence snapshot")

    serialized = json.dumps(record, ensure_ascii=False)
    if re.search(r'(?i)"(?:api_?key|authorization|secret|chain_of_thought)"\s*:', serialized) \
            or re.search(r'(?i)\bsk-[a-z0-9._-]{8,}', serialized):
        raise ValueError("Agent probe report contains secret material")

    return record


def validate_agent_runtime_postcheck(output):
    lines = [line for line in output.splitlines() if line.strip()]
    if not lines:
        raise ValueError("Agent Runtime postcheck returned no result")

    fields = lines[-1].split("\t")
    if len(fields) != 8:
        raise ValueError(f"Agent Runtime postcheck returned {len(fields)} fields instead of 8")

    counts = fields[:5]
    missing_indexes = fields[5] or "none"
    unexpected_indexes = fields[6] or "none"
    invalid_settings = fields[7]
    if counts != ["4", "21", "10", "7", "8"] or invalid_settings != "0" \
            or missing_indexes != "none" or unexpected_indexes != "none":
        raise ValueError(
            "Agent Runtime database postcheck failed "
            f"(counts={','.join(counts)}, invalid_settings={invalid_settings}, "
            f"missing={missing_indexes}, unexpected={unexpected_indexes})"
        )

    return {
        "agent_tables": int(counts[0]),
        "run_columns": int(counts[1]),
        "settings_columns": int(counts[2]),
        "foreign_keys": int(counts[3]),
        "unique_indexes": int(counts[4]),
    }


def validate_assistant_workspace_postcheck(output):
    lines = [line for line in output.splitlines() if line.strip()]
    if not lines:
        raise ValueError("assistant workspace postcheck returned no result")

    fields = lines[-1].split("\t")
    if len(fields) == 5:
        fields.append("")
    if len(fields) != 6:
        raise ValueError(f"assistant workspace postcheck returned {len(fields)} fields instead of 6")

    columns, indexes, message_index_columns, invalid_modes, missing_archived, missing_indexes = fields
    if [columns, indexes, message_index_columns, invalid_modes, missing_archived] != ["6", "3", "2", "0", "0"] \
            or missing_indexes:
        raise ValueError(
            "assistant workspace database postcheck failed "
            f"(columns={columns}, indexes={indexes}, message_index_columns={message_index_columns}, "
            f"invalid_title_modes={invalid_modes}, missing_archived={missing_archived}, "
            f"missing_indexes={missing_indexes or 'none'})"
        )

    return {
        "workspace_columns": int(columns),
        "workspace_indexes": int(indexes),
        "message_index_columns": int(message_index_columns),
    }


def is_loopback_listener(value, expected_port):
    listeners = [line.strip() for line in value.splitlines() if line.strip()]
    if not listeners:
        return False

    for listener in listeners:
        try:
            if listener.startswith("["):
                closing_bracket = listener.rindex("]")
                host = listener[1:closing_bracket]
                port = int(listener[closing_bracket + 2 :])
            else:
                host, raw_port = listener.rsplit(":", 1)
                port = int(raw_port)
            address = ip_address(host)
        except (ValueError, IndexError):
            return False

        if isinstance(address, IPv6Address) and address.ipv4_mapped is not None:
            address = address.ipv4_mapped
        if port != expected_port or not address.is_loopback:
            return False

    return True
