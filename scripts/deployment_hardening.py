import json
import os
import re
import secrets
import subprocess
import time
from dataclasses import dataclass
from ipaddress import IPv6Address, ip_address
from pathlib import Path


LOCAL_DEPLOY_SECRET_KEYS = (
    "OPC_SSH_PASSWORD",
    "OPC_ASSISTANT_CURSOR_HMAC_SECRET",
    "OPC_INITIAL_ADMIN_USERNAME",
    "OPC_INITIAL_ADMIN_PASSWORD",
)


@dataclass(frozen=True, repr=False)
class InitialAdminCredentials:
    username: str
    password: str

    @property
    def password_present(self):
        return bool(self.password)


def load_local_deploy_secrets(environ, path):
    secret_path = Path(path)
    if secret_path.exists():
        raw = secret_path.read_bytes()
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as exception:
            raise RuntimeError("Local deployment credential file is not valid UTF-8") from exception
        if raw.startswith(b"\xef\xbb\xbf") or b"\r" in raw.replace(b"\r\n", b"") or b"\x00" in raw \
                or any(character in text for character in ("\u0085", "\u2028", "\u2029")):
            raise RuntimeError("Local deployment credential file contains an illegal newline or encoding marker")
        text = text.replace("\r\n", "\n")
        records = text[:-1].split("\n") if text.endswith("\n") else text.split("\n")
        if records == [""]:
            records = []
        seen = set()
        for line_number, line in enumerate(records, start=1):
            if not line:
                raise RuntimeError(f"Malformed local deployment credential at line {line_number}")
            key, separator, value = line.partition("=")
            if not separator or not re.fullmatch(r"[A-Z][A-Z0-9_]*", key) or not value:
                raise RuntimeError(f"Malformed local deployment credential at line {line_number}")
            if key not in LOCAL_DEPLOY_SECRET_KEYS:
                raise RuntimeError(f"Unknown local deployment credential key at line {line_number}")
            if key in seen:
                raise RuntimeError(f"Duplicate local deployment credential key at line {line_number}: {key}")
            seen.add(key)
            if separator and key not in environ:
                environ[key] = value
    return {
        key: isinstance(environ.get(key), str) and bool(environ[key].strip())
        for key in LOCAL_DEPLOY_SECRET_KEYS
    }


def ensure_stable_cursor_hmac_secret(environ, path):
    secret_path = Path(path)
    secret_path.parent.mkdir(parents=True, exist_ok=True)
    secret_path.touch(exist_ok=True)
    lock_path = secret_path.with_suffix(secret_path.suffix + ".lock")
    deadline = time.monotonic() + 5
    lock_fd = None
    while lock_fd is None:
        try:
            lock_fd = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        except FileExistsError:
            if time.monotonic() >= deadline:
                raise RuntimeError("Local deployment credential file is busy")
            time.sleep(0.05)
    os.close(lock_fd)
    try:
        file_environment = {}
        load_local_deploy_secrets(file_environment, secret_path)
        key = "OPC_ASSISTANT_CURSOR_HMAC_SECRET"
        if key in file_environment:
            if key not in environ:
                environ[key] = file_environment[key]
        else:
            value = environ.get(key)
            if not isinstance(value, str) or not value.strip():
                value = secrets.token_urlsafe(48)
                environ[key] = value
            require_cursor_hmac_secret_environment({key: value})
            existing = secret_path.read_bytes()
            separator = b"" if not existing or existing.endswith((b"\n", b"\r\n")) else b"\n"
            payload = existing + separator + f"{key}={value}\n".encode("utf-8")
            temporary = secret_path.with_name(
                secret_path.name + ".tmp-" + secrets.token_hex(8))
            descriptor = os.open(temporary, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            try:
                with os.fdopen(descriptor, "wb") as handle:
                    handle.write(payload)
                    handle.flush()
                    os.fsync(handle.fileno())
                os.replace(temporary, secret_path)
            finally:
                if temporary.exists():
                    temporary.unlink()
        _restrict_local_secret_acl(secret_path)
    finally:
        lock_path.unlink(missing_ok=True)
    return {
        key: isinstance(environ.get(key), str) and bool(environ[key].strip())
        for key in LOCAL_DEPLOY_SECRET_KEYS
    }


def _restrict_local_secret_acl(path):
    secret_path = Path(path)
    if os.name == "nt":
        username = os.environ.get("USERNAME")
        if not username:
            raise RuntimeError("Current Windows user is unavailable for local credential ACL")
        result = subprocess.run(
            ["icacls.exe", str(secret_path), "/inheritance:r", "/grant:r", f"{username}:F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        if result.returncode != 0:
            raise RuntimeError("Unable to restrict the local deployment credential ACL")
    else:
        secret_path.chmod(0o600)


def require_initial_admin_credentials(environ, existing_admin_count):
    if existing_admin_count > 0:
        return None
    username = require_secret_environment(environ, "OPC_INITIAL_ADMIN_USERNAME")
    password = require_secret_environment(environ, "OPC_INITIAL_ADMIN_PASSWORD")
    if not re.fullmatch(r"[A-Za-z0-9_.-]{3,64}", username):
        raise RuntimeError("OPC_INITIAL_ADMIN_USERNAME has an invalid format")
    return InitialAdminCredentials(username=username, password=password)


def require_secret_environment(environ, name):
    value = environ.get(name)
    if not isinstance(value, str) or not value.strip():
        raise RuntimeError(f"{name} is not set; deployment stopped before SSH")
    return value


def require_cursor_hmac_secret_environment(environ):
    name = "OPC_ASSISTANT_CURSOR_HMAC_SECRET"
    value = require_secret_environment(environ, name)
    if len(value) < 32:
        raise RuntimeError(f"{name} must contain at least 32 characters; deployment stopped before SSH")
    if len(value) > 256 or not re.fullmatch(r"[A-Za-z0-9._~+/=-]+", value):
        raise RuntimeError(f"{name} must be environment-file-safe; deployment stopped before SSH")
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
        "provider", "model", "prompt_version", "finish_reason", "internal_request_id", "provider_request_id"
    ):
        required_text(field)
    if record.get("prompt_version") != "agent-research-v2":
        raise ValueError("Agent probe did not execute agent-research-v2")

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


def validate_candidate_agent_probe_record(record, max_model_rounds, max_tool_calls):
    if not isinstance(record, dict):
        raise ValueError("Candidate Agent probe record is not an object")
    status = record.get("status")
    if status == "completed":
        validate_agent_probe_record(record, max_model_rounds, max_tool_calls)
    elif status == "evidence_insufficient":
        for field in ("provider", "model", "prompt_version", "finish_reason"):
            value = record.get(field)
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"Candidate Agent probe field {field} is missing")
        if record.get("prompt_version") != "agent-research-v2":
            raise ValueError("Candidate Agent probe did not execute agent-research-v2")
        for field in ("prompt_tokens", "completion_tokens", "total_tokens", "latency_ms", "model_rounds"):
            value = record.get(field)
            if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
                raise ValueError(f"Candidate Agent probe field {field} must be positive")
        for field in (
            "provider_call_count", "tool_call_count", "completed_tool_count",
            "citation_count", "unknown_citation_count",
        ):
            value = record.get(field)
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ValueError(f"Candidate Agent probe field {field} must be non-negative")
        if record["total_tokens"] != record["prompt_tokens"] + record["completion_tokens"]:
            raise ValueError("Candidate Agent probe token totals are inconsistent")
        if record["provider_call_count"] != record["model_rounds"] \
                or record["model_rounds"] > max_model_rounds:
            raise ValueError("Candidate Agent probe model-round audit is inconsistent")
        if record["tool_call_count"] > max_tool_calls \
                or record["completed_tool_count"] > record["tool_call_count"]:
            raise ValueError("Candidate Agent probe tool-call audit is inconsistent")
        if record["unknown_citation_count"] != 0:
            raise ValueError("Candidate Agent probe contains an unknown citation")
    else:
        raise ValueError("Candidate Agent probe did not reach a controlled terminal state")

    if record.get("release_switched") is not False:
        raise ValueError("Candidate Agent probe ran after the production release switch")
    if record.get("settlement_status") not in {"settled_actual", "settled_estimated"} \
            or record.get("reserved_tokens") != 0:
        raise ValueError("Candidate Agent probe usage was not fully settled")

    coverage_counts = []
    evidence_counts = []
    for item_type in ("case", "policy", "source"):
        coverage = record.get(f"coverage_{item_type}_count")
        evidence = record.get(f"evidence_{item_type}_count")
        if isinstance(coverage, bool) or not isinstance(coverage, int) or coverage < 0 \
                or isinstance(evidence, bool) or not isinstance(evidence, int) or evidence < 0:
            raise ValueError("Candidate Agent evidence coverage counts are invalid")
        coverage_counts.append(coverage)
        evidence_counts.append(evidence)
    if coverage_counts != evidence_counts:
        raise ValueError("Candidate Agent evidence coverage differs from the authorized evidence snapshot")

    expected_coverage = "insufficient"
    if status != "evidence_insufficient" and evidence_counts[2] > 0:
        expected_coverage = "sufficient" if evidence_counts[0] > 0 and evidence_counts[1] > 0 else "partial"
    if record.get("coverage_status") != expected_coverage:
        raise ValueError("Candidate Agent evidence coverage status is inconsistent")
    return record


def candidate_probe_failure_message(diagnostic_code, record):
    code = diagnostic_code if isinstance(diagnostic_code, str) \
        and re.fullmatch(r"[A-Z][A-Z0-9_]{2,63}", diagnostic_code) else "CANDIDATE_AGENT_FAILED"

    def non_negative_int(name):
        value = record.get(name) if isinstance(record, dict) else None
        return value if isinstance(value, int) and not isinstance(value, bool) and value >= 0 else 0

    def safe_label(name, default):
        value = record.get(name) if isinstance(record, dict) else None
        if isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9._:/-]{1,100}", value):
            return value
        return default

    provider_calls = non_negative_int("provider_call_count")
    release_switched = bool(record.get("release_switched")) if isinstance(record, dict) else False
    return (
        f"{code}: candidate Agent v2 probe failed "
        f"(provider={safe_label('provider', 'configured')}, provider_called={'true' if provider_calls else 'false'}, "
        f"model_rounds={non_negative_int('model_rounds')}, "
        f"tool_call_count={non_negative_int('tool_call_count')}, "
        f"total_tokens={non_negative_int('total_tokens')}, "
        f"finish_reason={safe_label('finish_reason', 'not_provided')}, "
        f"latency_ms={non_negative_int('latency_ms')}, "
        f"release_switched={'true' if release_switched else 'false'})"
    )


def validate_agent_evidence_probe(data, expected_run_id):
    if not isinstance(data, dict):
        raise ValueError("Agent evidence probe is not an object")
    if data.get("runId") != expected_run_id or data.get("status") != "completed":
        raise ValueError("Agent evidence probe does not match the completed run")
    items = data.get("items")
    groups = data.get("groups")
    if not isinstance(items, list) or not isinstance(groups, dict):
        raise ValueError("Agent evidence probe is missing grouped items")

    available_counts = {"case": 0, "policy": 0, "source": 0}
    for item in items:
        if not isinstance(item, dict) or item.get("itemType") not in available_counts:
            raise ValueError("Agent evidence probe contains an invalid item")
        if item.get("available") is not True:
            continue
        if not isinstance(item.get("itemId"), int) or item["itemId"] <= 0:
            raise ValueError("Agent evidence probe contains an invalid item ID")
        if not isinstance(item.get("sourceId"), int) or item["sourceId"] <= 0:
            raise ValueError("Agent evidence probe contains an invalid source ID")
        if not isinstance(item.get("title"), str) or not item["title"].strip():
            raise ValueError("Agent evidence probe contains an untitled item")
        available_counts[item["itemType"]] += 1

    for item_type in ("case", "policy"):
        group_count = groups.get(item_type)
        if not isinstance(group_count, int) or group_count < 1 or available_counts[item_type] < 1:
            raise ValueError(f"Agent evidence probe is missing available {item_type} evidence")
    return data


def validate_agent_runtime_postcheck(output):
    lines = [line for line in output.splitlines() if line.strip()]
    if not lines:
        raise ValueError("Agent Runtime postcheck returned no result")

    fields = lines[-1].split("\t")
    if len(fields) != 8:
        raise ValueError(f"Agent Runtime postcheck returned {len(fields)} fields instead of 8")

    counts = fields[:5]
    missing_indexes = fields[5] or "none"
    forward_workspace_indexes = {
        "ai_agent_sessions.idx_agent_sessions_history_active",
        "ai_agent_sessions.idx_agent_sessions_history_archived",
        "ai_agent_sessions.idx_agent_sessions_purge_due",
    }
    reported_unexpected = {item for item in fields[6].split(",") if item}
    unsupported_unexpected = sorted(reported_unexpected - forward_workspace_indexes)
    unexpected_indexes = ",".join(unsupported_unexpected) or "none"
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
    if len(fields) != 14:
        raise ValueError(f"assistant workspace postcheck returned {len(fields)} fields instead of 14")

    (
        columns,
        stability_columns,
        indexes,
        message_index_columns,
        purge_audit_tables,
        purge_audit_columns,
        purge_audit_indexes,
        purge_audit_foreign_keys,
        rollout_boundaries,
        invalid_modes,
        missing_archived,
        missing_indexes,
        invalid_index_definitions,
        historic_auto_titles,
    ) = fields
    if [
        columns,
        stability_columns,
        indexes,
        message_index_columns,
        purge_audit_tables,
        purge_audit_columns,
        purge_audit_indexes,
        purge_audit_foreign_keys,
        rollout_boundaries,
        invalid_modes,
        missing_archived,
        historic_auto_titles,
    ] != ["6", "5", "3", "2", "1", "9", "2", "0", "1", "0", "0", "0"] \
            or missing_indexes or invalid_index_definitions:
        raise ValueError(
            "assistant workspace database postcheck failed "
            f"(columns={columns}, stability_columns={stability_columns}, indexes={indexes}, "
            f"message_index_columns={message_index_columns}, rollout_boundaries={rollout_boundaries}, "
            f"purge_audit_tables={purge_audit_tables}, purge_audit_columns={purge_audit_columns}, "
            f"purge_audit_indexes={purge_audit_indexes}, "
            f"purge_audit_foreign_keys={purge_audit_foreign_keys}, "
            f"invalid_title_modes={invalid_modes}, missing_archived={missing_archived}, "
            f"missing_indexes={missing_indexes or 'none'}, "
            f"invalid_index_definitions={invalid_index_definitions or 'none'}, "
            f"historic_auto_titles={historic_auto_titles})"
        )

    return {
        "workspace_columns": int(columns),
        "stability_columns": int(stability_columns),
        "workspace_indexes": int(indexes),
        "message_index_columns": int(message_index_columns),
        "purge_audit_tables": int(purge_audit_tables),
        "purge_audit_columns": int(purge_audit_columns),
        "purge_audit_indexes": int(purge_audit_indexes),
        "purge_audit_foreign_keys": int(purge_audit_foreign_keys),
        "rollout_boundaries": int(rollout_boundaries),
    }


def validate_assistant_history_revision_postcheck(output):
    lines = [line for line in output.splitlines() if line.strip()]
    if not lines:
        raise ValueError("assistant history revision postcheck returned no result")
    fields = lines[-1].split("\t")
    if fields != ["1", "1", "0"]:
        raise ValueError(
            "assistant history revision database postcheck failed "
            f"(fields={','.join(fields)})"
        )
    return {
        "revision_columns": 1,
        "valid_revision_definitions": 1,
        "invalid_revision_values": 0,
    }


def validate_atomic_start_replay(first, replay):
    first_session = (first.get("session") or {}).get("sessionId")
    replay_session = (replay.get("session") or {}).get("sessionId")
    first_identity = (first_session, first.get("messageId"), first.get("runId"))
    replay_identity = (replay_session, replay.get("messageId"), replay.get("runId"))
    if any(value in (None, "") for value in first_identity) or first_identity != replay_identity:
        raise ValueError(
            "atomic start replay returned different identity "
            f"(first={first_identity}, replay={replay_identity})"
        )
    return {
        "session_id": int(first_session),
        "message_id": int(first.get("messageId")),
        "run_id": int(first.get("runId")),
    }


def validate_cursor_second_page(first, second, *, id_field, cursor_field, expected_total):
    first_items = first.get("items") or []
    second_items = second.get("items") or []
    first_ids = [item.get(id_field) for item in first_items]
    second_ids = [item.get(id_field) for item in second_items]
    all_ids = first_ids + second_ids
    if (
        len(first_items) != 50
        or first.get("hasMore") is not True
        or not first.get(cursor_field)
        or len(all_ids) != expected_total
        or second.get("hasMore") is not False
        or second.get(cursor_field) not in (None, "")
        or any(value in (None, "") for value in all_ids)
        or len(set(all_ids)) != expected_total
        or set(first_ids).intersection(second_ids)
    ):
        raise ValueError(
            "cursor pagination did not return complete disjoint pages "
            f"(first={len(first_items)}, second={len(second_items)}, expected={expected_total})"
        )
    return all_ids


def validate_history_cursor_stale_response(response):
    diagnostic_code = (response.get("data") or {}).get("diagnosticCode")
    if response.get("code") != 409 or diagnostic_code != "HISTORY_CURSOR_STALE":
        raise ValueError(
            "history cursor stale probe did not return the controlled diagnostic "
            f"(code={response.get('code')}, diagnostic={diagnostic_code or 'missing'})"
        )
    return {"code": 409, "diagnostic_code": diagnostic_code}


def validate_purge_barrier_record(record):
    session_generation = int(record.get("session_generation") or 0)
    run_generation = int(record.get("run_generation") or 0)
    readable_counts = [
        int(record.get("readable_messages") or 0),
        int(record.get("readable_tools") or 0),
        int(record.get("readable_run_results") or 0),
    ]
    generation_matches = int(record.get("generation_matches") or 0)
    if (
        not record.get("purged_at")
        or session_generation <= run_generation
        or any(readable_counts)
        or generation_matches
    ):
        raise ValueError(
            "purge barrier validation failed "
            f"(purged_at={record.get('purged_at') or 'missing'}, "
            f"session_generation={session_generation}, run_generation={run_generation}, "
            f"readable_counts={readable_counts}, generation_matches={generation_matches})"
        )
    return {
        "session_generation": session_generation,
        "run_generation": run_generation,
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
