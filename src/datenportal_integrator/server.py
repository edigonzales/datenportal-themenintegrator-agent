from __future__ import annotations

import functools
import inspect

from mcp.server.fastmcp import FastMCP

from .cli import OPERATIONS
from .common import IntegrationError
from .workflow import Workflow


def server(settings):
    mcp = FastMCP(
        "datenportal-themenintegrator",
        instructions=(
            "Arbeite nach dem Skill datenportal-themenintegrator. Freigaben nur nach ausdrücklicher menschlicher "
            "Antwort auf die konkrete Vorschau protokollieren. Dateien aus Anhängen müssen als lokale Originaldateien "
            "verfügbar sein. deliver liefert Zwischenstände; erneut aufrufen, um den gespeicherten Lauf weiterzuprüfen."
        ),
    )
    workflow = Workflow(settings)
    for operation in OPERATIONS:
        method = getattr(workflow, operation)

        def wrap(bound):
            @functools.wraps(bound)
            async def tool(*args, **kwargs):
                try:
                    if inspect.iscoroutinefunction(bound):
                        return await bound(*args, **kwargs)
                    import anyio

                    return await anyio.to_thread.run_sync(functools.partial(bound, *args, **kwargs))
                except IntegrationError as error:
                    # Raised tool errors keep isError=true and the machine-readable error code.
                    import json

                    raise ValueError(json.dumps(error.as_dict(), ensure_ascii=False)) from None
                except Exception as error:
                    # Do not expose HTTP request/credential-bearing exception details.
                    raise ValueError(
                        f"Operation fehlgeschlagen ({type(error).__name__}); Vorgangsstatus prüfen."
                    ) from None

            return tool

        mcp.add_tool(wrap(method), name=operation, description=method.__doc__ or operation.replace("_", " "))
    return mcp
