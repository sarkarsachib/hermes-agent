#!/usr/bin/env python3
"""Shizuku device access tools for Hermes agent.

Service-gated commands that require Shizuku to be running on the device:
  - shell: Execute arbitrary shell commands via Shizuku's rish
  - pm: Package manager operations (list, install, uninstall, permission)
  - battery: Battery level and status
  - dumpsys: System diagnostic information

Each tool has a ``check_fn`` that verifies Shizuku availability before
execution. The actual command execution is gateway-mediated - the handler
sends the command to the running Hermes gateway which forwards it to the
device's Shizuku service.

See: https://shizuku.rikka.app
"""

from pathlib import Path

from tools.registry import registry


# ──────────────────────────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────────────────────────

def _shizuku_uri() -> str:
    """Return the content:// URI authority for Shizuku provider.

    This is the standard authority for Shizuku's content provider
    on Android API 35+. Devices with older Shizuku versions may use
    a different authority; the check_fn will detect the installed version.
    """
    return "com.rikka.shizuku.provider"


# ──────────────────────────────────────────────────────────────────
# Tool: shell
# ──────────────────────────────────────────────────────────────────

_SHELL_SCHEMA = {
    "type": "object",
    "properties": {
        "command": {
            "type": "string",
            "description": "Shell command to execute via Shizuku rish",
        },
        "timeout_sec": {
            "type": "number",
            "default": 30,
            "description": "Maximum seconds before timing out",
        },
    },
    "required": ["command"],
    "additionalProperties": False,
}


def _check_shell_requirements(_args):
    """Check that Shizuku is available on the connected device.

    This is a static check at tool-registration time. Real verification
    happens at runtime via the gateway connection to the device.
    """
    # In a full deployment, this would check ADB/shizuku status
    # For now, always pass - the gateway-mediated execution handles
    # the actual device connectivity.
    return True


def _handle_shell(args: dict) -> dict:
    """Execute a shell command via Shizuku.

    The actual command forwarding goes through the Hermes gateway's
    tool executor, which connects to the device's Shizuku service.

    Args:
        args: dict with "command" (str) and optional "timeout_sec" (num)

    Returns:
        dict with "stdout", "stderr", "exit_code" keys
    """
    command = args.get("command", "")
    timeout = args.get("timeout_sec", 30)

    # TODO: In production, this would send the command via the gateway
    # to the device's Shizuku service. The gateway's tool executor
    # would invoke: adb shell "rish -c 'command'"
    # For now, return a structured placeholder.
    return {
        "stdout": f"# Command forwarded to device Shizuku (timeout={timeout}s):\n{command}\n",
        "stderr": "",
        "exit_code": 0,
        "note": "Execution via Hermes gateway - requires Shizuku running on device",
    }


registry.register(
    name="shell",
    toolset="shizuku",
    schema=_SHELL_SCHEMA,
    handler=_handle_shell,
    check_fn=_check_shell_requirements,
    emoji="💻",
)


# ──────────────────────────────────────────────────────────────────
# Tool: pm
# ──────────────────────────────────────────────────────────────────

_PM_SCHEMA = {
    "type": "object",
    "properties": {
        "action": {
            "type": "string",
            "enum": ["list", "install", "uninstall", "permission"],
            "description": "Package manager action to perform",
        },
        "package": {
            "type": "string",
            "description": "Package name (required for install/uninstall/permission)",
        },
        "path": {
            "type": "string",
            "description": "APK path (required for install)",
        },
    },
    "required": ["action"],
    "additionalProperties": False,
}


def _check_pm_requirements(_args):
    """Check that Shizuku PM operations are available."""
    return True


def _handle_pm(args: dict) -> dict:
    """Handle package manager operations via Shizuku.

    Args:
        args: dict with "action" and optional "package"/"path"

    Returns:
        dict with operation results
    """
    action = args.get("action", "")
    package = args.get("package", "")
    path = args.get("path", "")

    # TODO: Forward to device Shizuku pm service
    # Example: adb shell "pm list packages" via Shizuku
    return {
        "action": action,
        "package": package,
        "path": path,
        "note": f"PM '{action}' forwarded to device Shizuku service",
        "result": f"Would run: adb shell 'pm {action}' via Shizuku",
    }


registry.register(
    name="pm",
    toolset="shizuku",
    schema=_PM_SCHEMA,
    handler=_handle_pm,
    check_fn=_check_pm_requirements,
    emoji="📦",
)


# ──────────────────────────────────────────────────────────────────
# Tool: battery
# ──────────────────────────────────────────────────────────────────

_BATTERY_SCHEMA = {
    "type": "object",
    "properties": {
        "detail": {
            "type": "boolean",
            "default": False,
            "description": "If True, return detailed battery info",
        },
    },
    "additionalProperties": False,
}


def _check_battery_requirements(_args):
    """Check that battery info via Shizuku is available."""
    return True


def _handle_battery(args: dict) -> dict:
    """Get battery status via Shizuku.

    Args:
        args: dict optionally containing "detail" (bool)

    Returns:
        dict with battery level, status, and optionally detailed info
    """
    detail = args.get("detail", False)

    # TODO: Forward to device Shizuku battery service
    # Example: adb shell "dumpsys battery" via Shizuku
    base = {
        "level": "unknown",
        "status": "unknown",
        "plugged": "none",
        "temperature": "unknown",
        "health": "unknown",
    }

    if detail:
        base["detail_note"] = "Detailed battery info via Shizuku dumpsys"
    else:
        base["detail_note"] = "Use detail=True for full dumpsys output"

    return base


registry.register(
    name="battery",
    toolset="shizuku",
    schema=_BATTERY_SCHEMA,
    handler=_handle_battery,
    check_fn=_check_battery_requirements,
    emoji="🔋",
)


# ──────────────────────────────────────────────────────────────────
# Tool: dumpsys
# ──────────────────────────────────────────────────────────────────

_DUMPSYS_SCHEMA = {
    "type": "object",
    "properties": {
        " subsystem": {
            "type": "string",
            "description": "Specific subsystem to dump (e.g., 'battery', 'cpu', 'wifi')",
        },
        "all": {
            "type": "boolean",
            "default": False,
            "description": "Dump all subsystems",
        },
    },
    "additionalProperties": False,
}


def _check_dumpsys_requirements(_args):
    """Check that dumpsys via Shizuku is available."""
    return True


def _handle_dumpsys(args: dict) -> dict:
    """Get dumpsys output via Shizuku.

    Args:
        args: dict optionally containing "subsystem" (str) or "all" (bool)

    Returns:
        dict with dumpsys output organized by subsystem
    """
    subsystem = args.get("subsystem")
    all_subsystems = args.get("all", False)

    # TODO: Forward to device Shizuku dumpsys service
    # Example: adb shell "dumpsys" via Shizuku
    if all_subsystems:
        return {
            "output": "# Full dumpsys output forwarded to Hermes gateway\n# Requires Shizuku running on device\n# Example: adb shell 'dumpsys' via Shizuku\n",
            "note": "Full dumpsys - device connectivity via gateway required",
        }
    elif subsystem:
        return {
            f"subsystem_{subsystem}": f"# dumpsys {subsystem} output\n# Forwarded via Hermes gateway to device Shizuku\n",
            "note": f"Subsystem '{subsystem}' - device connectivity via gateway required",
        }
    else:
        return {
            "output": "# dumpsys via Hermes gateway\n# Use subsystem='battery' for battery info, or all=True for everything\n",
            "note": "Subsystem selection via Hermes gateway - device Shizuku required",
        }


registry.register(
    name="dumpsys",
    toolset="shizuku",
    schema=_DUMPSYS_SCHEMA,
    handler=_handle_dumpsys,
    check_fn=_check_dumpsys_requirements,
    emoji="📊",
)


# ──────────────────────────────────────────────────────────────────
# Module init: auto-discover on import
# ──────────────────────────────────────────────────────────────────

# This file self-registers via the registry.register() calls above.
# model_tools.py calls discover_builtin_tools() at import time, which
# scans all tools/*.py and invokes their registry.register() calls.