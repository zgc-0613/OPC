from ipaddress import IPv6Address, ip_address


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
